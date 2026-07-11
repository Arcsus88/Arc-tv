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
        val prefKey = stringPreferencesKey(media.cacheKey)
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
        tvMazePoster(media.title) ?: itunesPoster(media.title, "tvShow")
    } else {
        itunesPoster(media.title, "movie") ?: tvMazePoster(media.title)
    }

    private fun tvMazePoster(title: String): String? {
        val url = "https://api.tvmaze.com/singlesearch/shows?q=" + encode(title)
        return fetch(url)?.let { body ->
            val show = json.decodeFromString<TvMazeShow>(body)
            show.image?.original ?: show.image?.medium
        }
    }

    private fun itunesPoster(title: String, mediaType: String): String? {
        val url = "https://itunes.apple.com/search?limit=1&media=$mediaType&term=" + encode(title)
        return fetch(url)?.let { body ->
            json.decodeFromString<ItunesResponse>(body)
                .results.firstOrNull()
                ?.artworkUrl100
                ?.replace("100x100", "600x600")
        }
    }

    private fun fetch(url: String): String? =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

@Serializable
private data class TvMazeImage(val medium: String? = null, val original: String? = null)

@Serializable
private data class TvMazeShow(val image: TvMazeImage? = null)

@Serializable
private data class ItunesResult(val artworkUrl100: String? = null)

@Serializable
private data class ItunesResponse(val results: List<ItunesResult> = emptyList())
