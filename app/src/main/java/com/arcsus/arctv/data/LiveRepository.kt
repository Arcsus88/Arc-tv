package com.arcsus.arctv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LiveException(message: String) : Exception(message)

data class LiveChannel(
    val id: String,
    val name: String,
    val logo: String,
    val group: String,
    val url: String,
)

/** One programme in a channel's guide. Times are unix seconds. */
data class EpgEntry(
    val title: String,
    val description: String,
    val start: Long,
    val stop: Long,
)

/**
 * Loads Live TV channel lists. Two playlist kinds:
 *  - M3U: download and stream-parse the playlist file (Xtream get.php exports
 *    run to 100+ MB with every VOD title appended, so entries are capped).
 *  - Xtream: query player_api.php for live categories + streams — JSON, live
 *    channels only, so it's much faster and every group is present.
 */
class LiveRepository {

    companion object {
        private const val MAX_CHANNELS = 25_000
        /** Many IPTV panels reject unknown clients; a player UA keeps them happy. */
        private const val PLAYER_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
        private const val EPG_TTL_MS = 5 * 60 * 1000L
        private val STREAM_ID = Regex("/live/[^/]+/[^/]+/(\\d+)\\.(?:m3u8|ts)(?:[?#]|$)", RegexOption.IGNORE_CASE)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    // Session-lived cache so switching tabs never re-downloads a big playlist.
    private val cache = mutableMapOf<String, List<LiveChannel>>()

    // Guide listings cache: refetch after five minutes.
    private val epgCache = mutableMapOf<String, Pair<Long, Map<String, List<EpgEntry>>>>()

    /** Whether a playlist can serve guide data (Xtream login, direct or via get.php). */
    fun supportsEpg(playlist: SavedPlaylist): Boolean =
        playlist.isXtream || xtreamLoginFromM3uUrl(playlist.url) != null

    /**
     * Now/next listings for the given channels from the playlist's Xtream
     * panel, keyed by channel URL. Channels without a recognisable stream id
     * simply get no listings.
     */
    suspend fun epg(playlist: SavedPlaylist, channels: List<LiveChannel>): Map<String, List<EpgEntry>> =
        withContext(Dispatchers.IO) {
            val login = if (playlist.isXtream) playlist else xtreamLoginFromM3uUrl(playlist.url)
                ?: return@withContext emptyMap()
            val byId = LinkedHashMap<Long, String>()
            for (c in channels) {
                val id = STREAM_ID.find(c.url)?.groupValues?.get(1)?.toLongOrNull() ?: continue
                if (!byId.containsKey(id)) byId[id] = c.url
                if (byId.size >= 60) break
            }
            if (byId.isEmpty()) return@withContext emptyMap()

            val cacheKey = "${login.key}:${byId.keys.joinToString(",")}"
            epgCache[cacheKey]?.let { (at, data) ->
                if (System.currentTimeMillis() - at < EPG_TTL_MS) return@withContext data
            }

            val base = login.url.trimEnd('/')
            val sem = Semaphore(8)
            val fetched = coroutineScope {
                byId.keys.map { id ->
                    async {
                        sem.withPermit { id to fetchShortEpg(base, login.username, login.password, id) }
                    }
                }.awaitAll()
            }
            val result = buildMap {
                for ((id, listings) in fetched) {
                    val url = byId[id] ?: continue
                    put(url, listings)
                }
            }
            epgCache[cacheKey] = System.currentTimeMillis() to result
            result
        }

    private fun fetchShortEpg(base: String, user: String, pass: String, id: Long): List<EpgEntry> {
        return try {
            val url = "$base/player_api.php?username=${encode(user)}&password=${encode(pass)}&action=get_short_epg&stream_id=$id&limit=4"
            val body = fetchJson(url) as? JsonObject ?: return emptyList()
            val listings = body["epg_listings"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            listings.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val title = decodeBase64(o["title"]?.jsonPrimitive?.content)
                val stop = o["stop_timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                if (title.isBlank() || stop <= 0L) return@mapNotNull null
                EpgEntry(
                    title = title,
                    description = decodeBase64(o["description"]?.jsonPrimitive?.content),
                    start = o["start_timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    stop = stop,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeBase64(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: IllegalArgumentException) {
            value
        }
    }

    suspend fun channels(playlist: SavedPlaylist, refresh: Boolean = false): List<LiveChannel> {
        if (!refresh) cache[playlist.key]?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            withDerivedGroups(load(playlist))
        }
        if (loaded.isEmpty()) throw LiveException("The playlist contains no channels.")
        cache[playlist.key] = loaded
        return loaded
    }

    private fun load(playlist: SavedPlaylist): List<LiveChannel> {
        if (playlist.isXtream) return loadXtream(playlist)
        // An Xtream get.php M3U export carries the panel login in the URL.
        // Downloading the file is slow and often times out; the same
        // credentials via player_api.php load in seconds.
        val asXtream = xtreamLoginFromM3uUrl(playlist.url)
        if (asXtream != null) {
            try {
                return loadXtream(asXtream)
            } catch (e: Exception) {
                // Panel without player_api: fall back to the raw file.
            }
        }
        return loadM3u(playlist.url)
    }

    internal fun xtreamLoginFromM3uUrl(url: String): SavedPlaylist? {
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (parsed.path?.lowercase()?.endsWith("/get.php") != true) return null
        val params = (parsed.rawQuery ?: return null).split("&").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8")
        }.toMap()
        val username = params["username"] ?: return null
        val password = params["password"] ?: return null
        val port = if (parsed.port != -1) ":${parsed.port}" else ""
        return SavedPlaylist(
            name = parsed.host ?: "Playlist",
            url = "${parsed.scheme}://${parsed.host}$port",
            kind = "xtream",
            username = username,
            password = password,
        )
    }

    // ---- M3U ----

    private fun loadM3u(url: String): List<LiveChannel> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", PLAYER_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw LiveException("Playlist server responded with HTTP ${response.code}.")
            }
            val source = response.body?.source() ?: throw LiveException("Empty playlist response.")
            val channels = mutableListOf<LiveChannel>()
            var name = ""
            var logo = ""
            var group = ""
            var extGrp = ""
            var sawM3uMarker = false
            var lines = 0
            val extinf = Regex("^#EXTINF:\\s*-?[\\d.]+\\s*(.*?)\\s*,\\s*(.*)$")
            val attr = Regex("([a-zA-Z0-9_-]+)=\"([^\"]*)\"")
            while (channels.size < MAX_CHANNELS) {
                val raw = source.readUtf8Line() ?: break
                val line = raw.trim()
                lines++
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    if (line.contains("#EXTM3U")) sawM3uMarker = true
                    val inf = extinf.find(line)
                    if (inf != null) {
                        sawM3uMarker = true
                        val attrs = attr.findAll(inf.groupValues[1])
                            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        name = inf.groupValues[2].trim().ifEmpty { attrs["tvg-name"].orEmpty() }
                        logo = attrs["tvg-logo"].orEmpty()
                        group = attrs["group-title"].orEmpty()
                    } else if (line.uppercase().startsWith("#EXTGRP:")) {
                        extGrp = line.substring("#EXTGRP:".length).trim()
                    }
                    continue
                }
                if (!sawM3uMarker && lines > 50) {
                    throw LiveException("That URL didn't return an M3U playlist.")
                }
                channels.add(
                    LiveChannel(
                        id = "${channels.size}:$line",
                        name = name.ifEmpty { line },
                        logo = logo,
                        group = group.ifEmpty { extGrp },
                        url = line,
                    )
                )
                name = ""
                logo = ""
                group = ""
            }
            if (!sawM3uMarker) throw LiveException("That URL didn't return an M3U playlist.")
            return channels
        }
    }

    // ---- Xtream ----

    private fun loadXtream(playlist: SavedPlaylist): List<LiveChannel> {
        val base = playlist.url.trimEnd('/')
        val user = playlist.username
        val pass = playlist.password
        fun api(action: String) =
            "$base/player_api.php?username=${encode(user)}&password=${encode(pass)}&action=$action"

        val categories = fetchJson(api("get_live_categories"))
        val streams = fetchJson(api("get_live_streams"))
        if (streams !is JsonArray) {
            val auth = (streams as? JsonObject)?.get("user_info")?.let { info ->
                (info as? JsonObject)?.get("auth")?.jsonPrimitive?.content
            }
            throw LiveException(
                if (auth == "0") "Xtream login rejected — check the username and password."
                else "Unexpected response from the Xtream panel."
            )
        }

        val groupById = mutableMapOf<String, String>()
        if (categories is JsonArray) {
            for (c in categories) {
                val o = c as? JsonObject ?: continue
                val id = o["category_id"]?.jsonPrimitive?.content ?: continue
                groupById[id] = o["category_name"]?.jsonPrimitive?.content.orEmpty()
            }
        }

        val channels = mutableListOf<LiveChannel>()
        for (s in streams) {
            val o = s as? JsonObject ?: continue
            val streamId = o["stream_id"]?.jsonPrimitive?.content ?: continue
            val url = "$base/live/${encode(user)}/${encode(pass)}/$streamId.m3u8"
            channels.add(
                LiveChannel(
                    id = "${channels.size}:$url",
                    name = o["name"]?.jsonPrimitive?.content ?: "Channel $streamId",
                    logo = o["stream_icon"]?.jsonPrimitive?.content.orEmpty(),
                    group = groupById[o["category_id"]?.jsonPrimitive?.content].orEmpty(),
                    url = url,
                )
            )
            if (channels.size >= MAX_CHANNELS) break
        }
        return channels
    }

    private fun fetchJson(url: String): Any {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", PLAYER_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw LiveException("The panel responded with HTTP ${response.code}.")
            }
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
                ?: throw LiveException("The panel returned a non-JSON response — is this an Xtream server?")
            return runCatching { parsed.jsonArray }.getOrNull()
                ?: runCatching { parsed.jsonObject }.getOrNull()
                ?: throw LiveException("Unexpected response from the Xtream panel.")
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    // "UK: BBC One", "[UK] BBC One", "UK | BBC One", "UK - BBC One" -> "UK".
    private val namePrefix = Regex("^\\s*\\[?([A-Za-z]{2,3})]?\\s*[:|\\-\u2013]\\s*\\S")

    /**
     * Derive groups for ungrouped channels from "UK: BBC One"-style name
     * prefixes. Flat playlists often carry the country only in the channel
     * name, which buries everything in one giant "Ungrouped" bucket. A prefix
     * only becomes a group when several channels share it.
     */
    private fun withDerivedGroups(channels: List<LiveChannel>): List<LiveChannel> {
        fun prefixOf(c: LiveChannel): String? {
            if (c.group.isNotEmpty()) return null
            return namePrefix.find(c.name)?.groupValues?.get(1)?.uppercase()
        }
        val counts = mutableMapOf<String, Int>()
        for (c in channels) prefixOf(c)?.let { counts[it] = (counts[it] ?: 0) + 1 }
        if (counts.none { it.value >= 3 }) return channels
        return channels.map { c ->
            val p = prefixOf(c)
            if (p != null && (counts[p] ?: 0) >= 3) c.copy(group = p) else c
        }
    }
}
