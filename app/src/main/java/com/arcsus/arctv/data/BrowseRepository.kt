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
    val id: Int = 0,
    val type: String = "movie",
    val title: String = "",
    val year: String = "",
    val poster: String = "",
    val overview: String = "",
) {
    val isTv: Boolean get() = type == "tv"
}

@Serializable
data class CatalogRow(val title: String = "", val items: List<CatalogItem> = emptyList())

@Serializable
data class Genre(val id: Int = 0, val name: String = "")

@Serializable
data class Genres(val movie: List<Genre> = emptyList(), val tv: List<Genre> = emptyList())

@Serializable
data class DiscoverPage(
    val items: List<CatalogItem> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val error: String? = null,
)

@Serializable
data class Season(val number: Int = 0, val name: String = "", val episodeCount: Int = 0)

@Serializable
data class Episode(val episode: Int = 0, val name: String = "")

@Serializable
data class Source(
    val title: String = "",
    val size: String = "",
    val seeds: Int = 0,
    val quality: Int = 0,
    val magnet: String = "",
    val cached: Boolean = false,
)

@Serializable
private data class HomeResponse(val rows: List<CatalogRow> = emptyList(), val error: String? = null)

@Serializable
private data class SearchResponse(val items: List<CatalogItem> = emptyList(), val error: String? = null)

@Serializable
private data class SeasonsResponse(val seasons: List<Season> = emptyList(), val error: String? = null)

@Serializable
private data class EpisodesResponse(val episodes: List<Episode> = emptyList(), val error: String? = null)

@Serializable
private data class SourcesResponse(val sources: List<Source> = emptyList(), val error: String? = null)

@Serializable
private data class PlayResponse(
    val ok: Boolean = false,
    val streamUrl: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val error: String? = null,
)

class BrowseException(message: String) : Exception(message)

/** Playable result of resolving a source through the backend. */
data class ResolvedStream(val streamUrl: String, val filename: String)

/**
 * Talks to the Arc TV Edge Function: TMDB catalogue, TV seasons/episodes, torrent
 * source listing, and playing a chosen source through Real-Debrid. The caller's RD
 * token is sent so the backend adds to the signed-in account.
 */
class BrowseRepository(private val tokenStore: TokenStore) {

    companion object {
        private const val FUNCTION_URL =
            "https://rrzhigacwzkfyhzysirt.supabase.co/functions/v1/arc-browse"
        private const val SUPABASE_KEY = "sb_publishable_ayHyFxZX7DyInLz3qvZnng_0EUT_EFw"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    suspend fun home(): List<CatalogRow> = call { rd ->
        val p = json.decodeFromString<HomeResponse>(post(buildJsonObject { put("action", "home") }, rd))
        p.error?.let { throw BrowseException(it) }
        p.rows
    }

    suspend fun search(query: String): List<CatalogItem> = call { rd ->
        val body = buildJsonObject { put("action", "search"); put("query", query) }
        val p = json.decodeFromString<SearchResponse>(post(body, rd))
        p.error?.let { throw BrowseException(it) }
        p.items
    }

    suspend fun genres(): Genres = call { rd ->
        json.decodeFromString<Genres>(post(buildJsonObject { put("action", "genres") }, rd))
    }

    suspend fun discover(type: String, genreId: Int?, sort: String, page: Int): DiscoverPage = call { rd ->
        val body = buildJsonObject {
            put("action", "discover")
            put("type", type)
            if (genreId != null && genreId != 0) put("genre", genreId)
            put("sort", sort)
            put("page", page)
        }
        val p = json.decodeFromString<DiscoverPage>(post(body, rd))
        p.error?.let { throw BrowseException(it) }
        p
    }

    suspend fun seasons(item: CatalogItem): List<Season> = call { rd ->
        val body = buildJsonObject { put("action", "seasons"); put("id", item.id) }
        val p = json.decodeFromString<SeasonsResponse>(post(body, rd))
        p.error?.let { throw BrowseException(it) }
        p.seasons
    }

    suspend fun episodes(item: CatalogItem, season: Int): List<Episode> = call { rd ->
        val body = buildJsonObject {
            put("action", "episodes"); put("id", item.id); put("season", season)
        }
        val p = json.decodeFromString<EpisodesResponse>(post(body, rd))
        p.error?.let { throw BrowseException(it) }
        p.episodes
    }

    suspend fun sources(item: CatalogItem, season: Int?, episode: Int?): List<Source> = call { rd ->
        val body = buildJsonObject {
            put("action", "sources")
            put("title", item.title)
            put("year", item.year)
            put("type", item.type)
            if (season != null) put("season", season)
            if (episode != null) put("episode", episode)
        }
        val p = json.decodeFromString<SourcesResponse>(post(body, rd))
        p.error?.let { throw BrowseException(it) }
        p.sources
    }

    /** Add one chosen magnet to RD and return a playable link (throws if not cached). */
    suspend fun play(source: Source): ResolvedStream = call { rd ->
        val body = buildJsonObject { put("action", "play"); put("magnet", source.magnet) }
        val p = json.decodeFromString<PlayResponse>(post(body, rd))
        if (!p.ok || p.streamUrl == null) throw BrowseException(p.error ?: "Not playable.")
        ResolvedStream(p.streamUrl, p.filename ?: source.title)
    }

    private suspend fun <T> call(block: (String) -> T): T {
        val rd = tokenStore.currentTokens()?.accessToken
            ?: throw BrowseException("Not signed in to Real-Debrid.")
        return withContext(Dispatchers.IO) { block(rd) }
    }

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
