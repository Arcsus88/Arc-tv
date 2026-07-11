package com.arcsus.arctv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val Context.artworkDataStore by preferencesDataStore(name = "artwork")

/**
 * Resolves poster artwork for release filenames using key-less public APIs:
 * TVmaze for shows and the iTunes Search API for films. Results (including
 * misses, stored as "") are cached in DataStore so each title is looked up once.
 */
class ArtworkRepository(context: Context) {

    private val dataStore = context.applicationContext.artworkDataStore
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    suspend fun posterFor(filename: String): String? {
        val media = FilenameParser.parse(filename) ?: return null
        // "v2" prefix invalidates posters cached by the earlier, looser matcher.
        val prefKey = stringPreferencesKey("v2:" + media.cacheKey)
        dataStore.data.first()[prefKey]?.let { cached ->
            return cached.ifEmpty { null }
        }

        val deferred = CompletableDeferred<String?>()
        inFlight.putIfAbsent(media.cacheKey, deferred)?.let { existing ->
            return existing.await()
        }
        var result: String? = null
        try {
            result = withContext(Dispatchers.IO) { lookup(media) }
            dataStore.edit { it[prefKey] = result.orEmpty() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // network hiccup: don't cache, retry on next launch
        } finally {
            deferred.complete(result)
            inFlight.remove(media.cacheKey)
        }
        return result
    }

    private fun lookup(media: ParsedMedia): String? = if (media.isTv) {
        tvMazePoster(media) ?: itunesPoster(media, "tvShow")
    } else {
        itunesPoster(media, "movie") ?: tvMazePoster(media)
    }

    private fun tvMazePoster(media: ParsedMedia): String? {
        val url = "https://api.tvmaze.com/singlesearch/shows?q=" + encode(media.title)
        val body = fetch(url) ?: return null
        val show = try {
            json.decodeFromString<TvMazeShow>(body)
        } catch (e: Exception) {
            return null
        }
        // Only trust the artwork if the matched show's name really is this title.
        if (!titleMatches(show.name, media.title)) return null
        return show.image?.original ?: show.image?.medium
    }

    private fun itunesPoster(media: ParsedMedia, mediaType: String): String? {
        val term = if (media.year != null && !media.isTv) "${media.title} ${media.year}" else media.title
        val url = "https://itunes.apple.com/search?limit=8&media=$mediaType&term=" + encode(term)
        val body = fetch(url) ?: return null
        val results = try {
            json.decodeFromString<ItunesResponse>(body).results
        } catch (e: Exception) {
            return null
        }
        // Pick the best candidate whose name matches the parsed title; prefer a
        // matching release year when we have one. Reject everything otherwise so
        // we show a placeholder rather than an unrelated film.
        val matches = results.filter { titleMatches(it.trackName, media.title) }
        val wantYear = media.year
        val candidate = if (wantYear != null) {
            // Require the release year to line up (±1 for theatrical/digital lag);
            // otherwise it's probably an older film of the same name — skip it.
            matches.firstOrNull { r ->
                val y = r.releaseYear()
                y != null && kotlin.math.abs(y - wantYear) <= 1
            }
        } else {
            matches.firstOrNull()
        }
        return candidate?.artworkUrl100?.replace("100x100bb", "600x600bb")?.replace("100x100", "600x600")
    }

    private fun fetch(url: String): String? =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        /** True when [candidate] is a confident match for [query] (order-insensitive tokens). */
        fun titleMatches(candidate: String?, query: String): Boolean {
            if (candidate.isNullOrBlank()) return false
            val c = normalize(candidate)
            val q = normalize(query)
            if (c.isEmpty() || q.isEmpty()) return false
            if (c == q) return true
            val ct = c.split(' ').filter { it.isNotEmpty() }.toSet()
            val qt = q.split(' ').filter { it.isNotEmpty() }.toSet()
            if (ct.isEmpty() || qt.isEmpty()) return false
            // Jaccard similarity — penalises extra words on either side, so a
            // one-word query no longer matches every longer title that contains it.
            val jaccard = ct.intersect(qt).size.toDouble() / ct.union(qt).size
            return jaccard >= 0.6
        }

        private fun normalize(s: String): String =
            s.lowercase(Locale.US)
                .replace("&", "and")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
    }
}

@Serializable
private data class TvMazeImage(val medium: String? = null, val original: String? = null)

@Serializable
private data class TvMazeShow(val name: String? = null, val image: TvMazeImage? = null)

@Serializable
private data class ItunesResult(
    val trackName: String? = null,
    val artworkUrl100: String? = null,
    val releaseDate: String? = null,
) {
    fun releaseYear(): Int? = releaseDate?.take(4)?.toIntOrNull()
}

@Serializable
private data class ItunesResponse(val results: List<ItunesResult> = emptyList())
