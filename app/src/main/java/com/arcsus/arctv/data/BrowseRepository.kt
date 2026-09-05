package com.arcsus.arctv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    /** Wide 16:9 artwork (w780) — the Sky-style tile art; may be blank. */
    val backdrop: String = "",
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
data class ArtResponse(val art: Map<String, String> = emptyMap())

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
data class TitleDetails(
    val id: Int = 0,
    val type: String = "movie",
    val title: String = "",
    val year: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val overview: String = "",
    val rating: Double = 0.0,
    val genres: List<String> = emptyList(),
    val runtime: Int? = null,
    val tagline: String = "",
    val seasons: Int? = null,
) {
    val isTv: Boolean get() = type == "tv"
}

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

/** Tokens attached to every backend call (at least one debrid is required). */
private data class DebridTokens(val rd: String?, val ad: String?, val torbox: String?)

/**
 * Talks to the Arc TV Edge Function: TMDB catalogue, TV seasons/episodes, torrent
 * source listing, and playing a chosen source. Playback resolves server-side
 * (TorBox-cached, then Real-Debrid); AllDebrid is resolved on this device as a
 * fallback, because AllDebrid refuses magnet requests from server IPs.
 */
class BrowseRepository(
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val allDebrid: AllDebridRepository,
) {

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

    suspend fun home(): List<CatalogRow> = call { t ->
        val p = json.decodeFromString<HomeResponse>(post(buildJsonObject { put("action", "home") }, t))
        p.error?.let { throw BrowseException(it) }
        p.rows
    }

    suspend fun search(query: String): List<CatalogItem> = call { t ->
        val body = buildJsonObject { put("action", "search"); put("query", query) }
        val p = json.decodeFromString<SearchResponse>(post(body, t))
        p.error?.let { throw BrowseException(it) }
        p.items
    }

    suspend fun genres(): Genres = call { t ->
        json.decodeFromString<Genres>(post(buildJsonObject { put("action", "genres") }, t))
    }

    suspend fun details(item: CatalogItem): TitleDetails = call { t ->
        val body = buildJsonObject { put("action", "details"); put("id", item.id); put("type", item.type) }
        json.decodeFromString<TitleDetails>(post(body, t))
    }

    /**
     * Titled key art for tiles: TMDB's English-tagged backdrops carry the
     * title graphic, the way Sky's landscape tiles do. Keys are "type:id";
     * an empty value means TMDB has none for that title.
     */
    suspend fun art(items: List<CatalogItem>): Map<String, String> = call { t ->
        val body = buildJsonObject {
            put("action", "art")
            put(
                "items",
                kotlinx.serialization.json.buildJsonArray {
                    items.forEach { add(buildJsonObject { put("id", it.id); put("type", it.type) }) }
                },
            )
        }
        json.decodeFromString<ArtResponse>(post(body, t)).art
    }

    suspend fun discover(type: String, genreId: Int?, sort: String, page: Int): DiscoverPage = call { t ->
        val body = buildJsonObject {
            put("action", "discover")
            put("type", type)
            if (genreId != null && genreId != 0) put("genre", genreId)
            put("sort", sort)
            put("page", page)
        }
        val p = json.decodeFromString<DiscoverPage>(post(body, t))
        p.error?.let { throw BrowseException(it) }
        p
    }

    suspend fun seasons(item: CatalogItem): List<Season> = call { t ->
        val body = buildJsonObject { put("action", "seasons"); put("id", item.id) }
        val p = json.decodeFromString<SeasonsResponse>(post(body, t))
        p.error?.let { throw BrowseException(it) }
        p.seasons
    }

    suspend fun episodes(item: CatalogItem, season: Int): List<Episode> = call { t ->
        val body = buildJsonObject {
            put("action", "episodes"); put("id", item.id); put("season", season)
        }
        val p = json.decodeFromString<EpisodesResponse>(post(body, t))
        p.error?.let { throw BrowseException(it) }
        p.episodes
    }

    suspend fun sources(item: CatalogItem, season: Int?, episode: Int?): List<Source> = call { t ->
        val body = buildJsonObject {
            put("action", "sources")
            put("title", item.title)
            put("year", item.year)
            put("type", item.type)
            if (season != null) put("season", season)
            if (episode != null) put("episode", episode)
        }
        val p = json.decodeFromString<SourcesResponse>(post(body, t))
        p.error?.let { throw BrowseException(it) }
        p.sources
    }

    /**
     * Streaming source search: emits a growing, quality-first source list as each
     * backend query completes, so the UI can show sources as they're found.
     */
    fun sourcesStream(item: CatalogItem, season: Int?, episode: Int?): Flow<List<Source>> = flow {
        val tokens = currentDebridTokens()
        val body = buildJsonObject {
            put("action", "sources_stream")
            put("title", item.title)
            put("year", item.year)
            put("type", item.type)
            if (season != null) put("season", season)
            if (episode != null) put("episode", episode)
        }
        val request = requestFor(body, tokens)
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw BrowseException("Debrid sign-in rejected. Sign in again.")
            val bodySource = response.body?.source() ?: return@use
            while (!bodySource.exhausted()) {
                val line = bodySource.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val p = json.decodeFromString<SourcesResponse>(line)
                p.error?.let { throw BrowseException(it) }
                emit(p.sources)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Resolve one chosen magnet to a playable link: the backend first
     * (TorBox-cached, then Real-Debrid), then AllDebrid on this device.
     */
    suspend fun play(source: Source): ResolvedStream {
        val tokens = currentDebridTokens()
        val backendFailure = try {
            return withContext(Dispatchers.IO) {
                val body = buildJsonObject { put("action", "play"); put("magnet", source.magnet) }
                val p = json.decodeFromString<PlayResponse>(post(body, tokens))
                if (!p.ok || p.streamUrl == null) throw BrowseException(p.error ?: "Not playable.")
                ResolvedStream(p.streamUrl, p.filename ?: source.title)
            }
        } catch (e: BrowseException) {
            e
        }
        if (tokens.ad == null) throw backendFailure
        return try {
            allDebrid.resolveMagnet(source.magnet)
        } catch (e: AllDebridException) {
            // Prefer whichever error is more meaningful than a bare failure.
            throw BrowseException(e.message ?: backendFailure.message ?: "Not playable.")
        }
    }

    private suspend fun currentDebridTokens(): DebridTokens {
        val rd = tokenStore.currentTokens()?.accessToken
        val ad = tokenStore.currentAdApiKey()
        if (rd == null && ad == null) throw BrowseException("Not signed in to a debrid provider.")
        val torbox = settingsStore.currentTorboxToken().ifBlank { null }
        return DebridTokens(rd, ad, torbox)
    }

    private suspend fun <T> call(block: (DebridTokens) -> T): T {
        val tokens = currentDebridTokens()
        return withContext(Dispatchers.IO) { block(tokens) }
    }

    private fun requestFor(body: JsonObject, tokens: DebridTokens): Request {
        val builder = Request.Builder()
            .url(FUNCTION_URL)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .post(body.toString().toRequestBody(jsonMedia))
        tokens.rd?.let { builder.header("x-rd-token", it) }
        tokens.ad?.let { builder.header("x-ad-token", it) }
        tokens.torbox?.let { builder.header("x-torbox-token", it) }
        return builder.build()
    }

    private fun post(body: JsonObject, tokens: DebridTokens): String {
        client.newCall(requestFor(body, tokens)).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw BrowseException("Debrid sign-in rejected. Sign in again.")
            if (!response.isSuccessful && text.isBlank()) {
                throw BrowseException("Server error (HTTP ${response.code}).")
            }
            return text
        }
    }
}
