package com.arcsus.arctv.data

import kotlinx.coroutines.Dispatchers
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
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    // Session-lived cache so switching tabs never re-downloads a big playlist.
    private val cache = mutableMapOf<String, List<LiveChannel>>()

    suspend fun channels(playlist: SavedPlaylist, refresh: Boolean = false): List<LiveChannel> {
        if (!refresh) cache[playlist.key]?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (playlist.isXtream) loadXtream(playlist) else loadM3u(playlist.url)
        }
        if (loaded.isEmpty()) throw LiveException("The playlist contains no channels.")
        cache[playlist.key] = loaded
        return loaded
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
}
