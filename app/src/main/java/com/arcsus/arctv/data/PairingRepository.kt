package com.arcsus.arctv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * "Send to TV": the TV shows a short code, the viewer's phone attaches
 * playlists to it from the web app, and the TV collects them. Saves typing a
 * long panel URL with the remote. Codes live ten minutes on the server and are
 * deleted the moment they're collected.
 */
class PairingRepository {

    companion object {
        const val PAIR_WEB_URL = "https://arc-tv-web.vercel.app/pair"
        private const val FUNCTION_URL =
            "https://rrzhigacwzkfyhzysirt.supabase.co/functions/v1/arc-pair"
        private const val SUPABASE_KEY = "sb_publishable_ayHyFxZX7DyInLz3qvZnng_0EUT_EFw"

        // No 0/O or 1/I: the viewer types this from a TV screen.
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 6
    }

    sealed interface Poll {
        data object Waiting : Poll
        data object Expired : Poll
        data class Ready(val playlists: List<SavedPlaylist>) : Poll
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    fun newCode(): String =
        buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Register [code] so the phone can find it. */
    suspend fun open(code: String) {
        val body = call(buildJsonObject { put("action", "open"); put("code", code) })
        if (body["ok"]?.jsonPrimitive?.content != "true") {
            throw IOException(body["error"]?.jsonPrimitive?.content ?: "Could not start pairing.")
        }
    }

    /** One poll; the server deletes the row when it hands the payload over. */
    suspend fun take(code: String): Poll {
        val body = call(buildJsonObject { put("action", "take"); put("code", code) })
        return when (body["status"]?.jsonPrimitive?.content) {
            "ready" -> Poll.Ready(parsePlaylists(body["payload"] as? JsonObject))
            "expired" -> Poll.Expired
            else -> Poll.Waiting
        }
    }

    private fun parsePlaylists(payload: JsonObject?): List<SavedPlaylist> {
        val list = payload?.get("playlists") as? JsonArray ?: return emptyList()
        return list.mapNotNull { e ->
            val o = (e as? JsonObject) ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.content?.trim().orEmpty()
            val url = s("url")
            if (url.isBlank()) return@mapNotNull null
            val kind = if (s("kind") == "xtream") "xtream" else "m3u"
            val host = runCatching { java.net.URI(url).host }.getOrNull()
            SavedPlaylist(
                name = s("name").ifBlank { host ?: "Playlist" },
                url = url,
                kind = kind,
                username = s("username"),
                password = s("password"),
            )
        }
    }

    private suspend fun call(payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(FUNCTION_URL)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                throw IOException(
                    parsed?.get("error")?.jsonPrimitive?.content ?: "Pairing server answered HTTP ${response.code}",
                )
            }
            parsed ?: throw IOException("Pairing server sent an unexpected reply.")
        }
    }
}
