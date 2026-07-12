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
data class BrowseResult(
    val title: String = "",
    val size: String = "",
    val seeds: Int = 0,
    val leeches: Int = 0,
    val magnet: String = "",
    val origin: String = "",
)

@Serializable
private data class SearchResponse(
    val results: List<BrowseResult> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class AddResponse(
    val ok: Boolean = false,
    val id: String? = null,
    val error: String? = null,
)

class BrowseException(message: String) : Exception(message)

/**
 * Talks to the Arc TV browse Edge Function: searches torrents via Apify and adds
 * a chosen magnet to Real-Debrid. The caller's RD access token is sent so the
 * function verifies it and adds to the signed-in account.
 */
class BrowseRepository(private val tokenStore: TokenStore) {

    companion object {
        private const val FUNCTION_URL =
            "https://rrzhigacwzkfyhzysirt.supabase.co/functions/v1/arc-browse"
        private const val SUPABASE_KEY = "sb_publishable_ayHyFxZX7DyInLz3qvZnng_0EUT_EFw"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // Apify runs can take a while
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    suspend fun search(query: String): List<BrowseResult> {
        val rdToken = requireToken()
        return withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("action", "search")
                put("query", query)
            }
            val response = post(body, rdToken)
            val parsed = json.decodeFromString<SearchResponse>(response)
            if (parsed.error != null) throw BrowseException(parsed.error)
            parsed.results
        }
    }

    suspend fun add(magnet: String): String {
        val rdToken = requireToken()
        return withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("action", "add")
                put("magnet", magnet)
            }
            val response = post(body, rdToken)
            val parsed = json.decodeFromString<AddResponse>(response)
            if (!parsed.ok) throw BrowseException(parsed.error ?: "Couldn't add to Real-Debrid.")
            parsed.id ?: ""
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
