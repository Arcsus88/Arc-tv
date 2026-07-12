package com.arcsus.arctv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class CatalogItem(
    val type: String = "movie",
    val title: String = "",
    val year: String = "",
    val poster: String = "",
    val overview: String = "",
)

@Serializable
data class CatalogRow(val title: String = "", val items: List<CatalogItem> = emptyList())

@Serializable
private data class HomeResponse(val rows: List<CatalogRow> = emptyList(), val error: String? = null)

@Serializable
private data class SearchResponse(val items: List<CatalogItem> = emptyList(), val error: String? = null)

@Serializable
private data class ResolveResponse(
    val ok: Boolean = false,
    val streamUrl: String? = null,
    val filename: String? = null,
    val quality: String? = null,
    val error: String? = null,
)

class BrowseException(message: String) : Exception(message)

/** Playable result of resolving a catalog title through the backend. */
data class ResolvedStream(val streamUrl: String, val filename: String)

/**
 * Talks to the Arc TV Edge Function: TMDB catalog (home/search) plus resolve,
 * which finds a working, cached torrent for a title and returns a playable link.
 * The caller's RD token is sent so the backend adds to the signed-in account.
 */
class BrowseRepository(private val tokenStore: TokenStore) {

    companion object {
        private const val FUNCTION_URL =
            "https://rrzhigacwzkfyhzysirt.supabase.co/functions/v1/arc-browse"
        private const val SUPABASE_KEY = "sb_publishable_ayHyFxZX7DyInLz3qvZnng_0EUT_EFw"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // resolve can try several sources
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    suspend fun home(): List<CatalogRow> {
        val rdToken = requireToken()
        return withContext(Dispatchers.IO) {
            val text = post(buildJsonObject { put("action", "home") }, rdToken)
            val parsed = json.decodeFromString<HomeResponse>(text)
            if (parsed.error != null) throw BrowseException(parsed.error)
            parsed.rows
        }
    }

    suspend fun search(query: String): List<CatalogItem> {
        val rdToken = requireToken()
        return withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("action", "search")
                put("query", query)
            }
            val parsed = json.decodeFromString<SearchResponse>(post(body, rdToken))
            if (parsed.error != null) throw BrowseException(parsed.error)
            parsed.items
        }
    }

    suspend fun resolve(item: CatalogItem): ResolvedStream {
        val rdToken = requireToken()
        return withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("action", "resolve")
                put("title", item.title)
                put("year", item.year)
                put("type", item.type)
            }
            val parsed = json.decodeFromString<ResolveResponse>(post(body, rdToken))
            if (!parsed.ok || parsed.streamUrl == null) {
                throw BrowseException(parsed.error ?: "No playable source found.")
            }
            ResolvedStream(parsed.streamUrl, parsed.filename ?: item.title)
        }
    }

    private suspend fun requireToken(): String =
        tokenStore.currentTokens()?.accessToken
            ?: throw BrowseException("Not signed in to Real-Debrid.")

    private fun post(body: JsonObject, rdToken: String): String {
        val request = Request.Builder()
            .url(FUNCTION_URL)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("x-rd-token", rdToken)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw BrowseException("Real-Debrid session expired. Sign in again.")
            if (!response.isSuccessful && text.isBlank()) {
                throw BrowseException("Server error (HTTP ${response.code}).")
            }
            return text
        }
    }
}
