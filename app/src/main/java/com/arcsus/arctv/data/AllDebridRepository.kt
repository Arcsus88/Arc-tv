package com.arcsus.arctv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AllDebridException(message: String) : Exception(message)

/** PIN handed to the user: enter [pin] at [userUrl] from a phone or computer. */
data class AdPin(
    val pin: String,
    val check: String,
    val userUrl: String,
    val expiresIn: Int,
)

/**
 * AllDebrid API v4 client. Auth is TV-friendly: the PIN flow shows a short
 * code the user enters at alldebrid.com/pin, then polling returns an API key.
 * Magnet resolution (upload → files → unlock) runs directly on this device —
 * AllDebrid refuses those endpoints for server IPs, so the backend can't do
 * it, but a living-room IP is exactly what they allow.
 */
class AllDebridRepository(private val tokenStore: TokenStore) {

    companion object {
        private const val API = "https://api.alldebrid.com/v4"
        private const val AGENT = "arcTv"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    // ---- Auth (PIN flow) ----

    suspend fun startPinAuth(): AdPin = withContext(Dispatchers.IO) {
        val data = dataOf(get(url("/pin/get")))
        AdPin(
            pin = data.str("pin") ?: throw AllDebridException("AllDebrid sent no PIN."),
            check = data.str("check") ?: throw AllDebridException("AllDebrid sent no check token."),
            userUrl = data.str("base_url") ?: "https://alldebrid.com/pin/",
            expiresIn = data.int("expires_in") ?: 600,
        )
    }

    /**
     * Polls until the user submits the PIN, then persists the API key. Throws
     * [AuthExpiredException] when the PIN expires unused.
     */
    suspend fun pollForApiKey(pin: AdPin) = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + pin.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val body = try {
                get(url("/pin/check", "check" to pin.check, "pin" to pin.pin))
            } catch (e: java.io.IOException) {
                null // transient network problem; keep polling
            }
            if (body != null) {
                val data = runCatching { dataOf(body) }.getOrNull()
                val apiKey = data?.str("apikey")
                if (data?.bool("activated") == true && apiKey != null) {
                    tokenStore.saveAdApiKey(apiKey)
                    return@withContext
                }
            }
            delay(5_000)
        }
        throw AuthExpiredException()
    }

    suspend fun signOut() = tokenStore.clearAdApiKey()

    // ---- Playback ----

    /**
     * Resolve a magnet to a direct stream URL via the signed-in AllDebrid
     * account. Throws [AllDebridException] when the magnet isn't cached or the
     * account can't play it.
     */
    suspend fun resolveMagnet(magnet: String): ResolvedStream = withContext(Dispatchers.IO) {
        val key = tokenStore.currentAdApiKey()
            ?: throw AllDebridException("Not signed in to AllDebrid.")

        val upload = dataOf(post(url("/magnet/upload"), key, FormBody.Builder().add("magnets[]", magnet).build()))
        val m = upload.arr("magnets")?.firstOrNull()?.jsonObject
            ?: throw AllDebridException("AllDebrid did not accept the magnet.")
        (m["error"] as? JsonObject)?.let { throw AllDebridException(it.str("message") ?: "AllDebrid rejected the magnet.") }
        val id = m.str("id") ?: throw AllDebridException("AllDebrid sent no magnet id.")
        if (m.bool("ready") != true) {
            deleteMagnet(key, id)
            throw AllDebridException("Not cached on AllDebrid yet.")
        }

        val filesBody = dataOf(get(url("/magnet/files", "id[]" to id), key))
        val files = mutableListOf<Triple<String, Long, String>>() // name, size, link
        fun walk(entries: JsonArray?) {
            for (e in entries ?: return) {
                val o = e as? JsonObject ?: continue
                val children = o["e"] as? JsonArray
                if (children != null) walk(children)
                else o.str("l")?.let { files.add(Triple(o.str("n") ?: "", o.long("s") ?: 0L, it)) }
            }
        }
        walk(filesBody.arr("magnets")?.firstOrNull()?.jsonObject?.get("files") as? JsonArray)
        val best = files.maxByOrNull { it.second }
        if (best == null) {
            deleteMagnet(key, id)
            throw AllDebridException("AllDebrid returned no files.")
        }

        val unlock = dataOf(get(url("/link/unlock", "link" to best.third), key))
        val link = unlock.str("link") ?: run {
            deleteMagnet(key, id)
            throw AllDebridException("AllDebrid could not unlock the link.")
        }
        ResolvedStream(link, unlock.str("filename") ?: best.first)
    }

    private fun deleteMagnet(key: String, id: String) {
        runCatching { get(url("/magnet/delete", "id" to id), key) }
    }

    // ---- Plumbing ----

    private fun url(path: String, vararg params: Pair<String, String>): HttpUrl {
        val builder = "$API$path".toHttpUrl().newBuilder().addQueryParameter("agent", AGENT)
        for ((k, v) in params) builder.addQueryParameter(k, v)
        return builder.build()
    }

    private fun get(url: HttpUrl, apiKey: String? = null): String = execute(
        Request.Builder().url(url).apply { apiKey?.let { header("Authorization", "Bearer $it") } }.build()
    )

    private fun post(url: HttpUrl, apiKey: String, body: FormBody): String = execute(
        Request.Builder().url(url).header("Authorization", "Bearer $apiKey").post(body).build()
    )

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            response.body?.string().orEmpty().ifBlank {
                throw AllDebridException("AllDebrid returned an empty response (HTTP ${response.code}).")
            }
        }

    /** Unwrap AllDebrid's {status, data|error} envelope, throwing on errors. */
    private fun dataOf(body: String): JsonObject {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw AllDebridException("AllDebrid returned an unreadable response.")
        if (root.str("status") != "success") {
            val err = root["error"] as? JsonObject
            val message = err?.str("message")?.replace(Regex("<[^>]*>"), "")?.trim()
            throw AllDebridException(message ?: "AllDebrid request failed.")
        }
        return root["data"] as? JsonObject ?: JsonObject(emptyMap())
    }
}

// Small typed accessors over kotlinx-serialization's JSON tree.
private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

private fun JsonObject.int(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.content?.toDouble()?.toInt() }.getOrNull()

private fun JsonObject.long(key: String): Long? =
    runCatching { this[key]?.jsonPrimitive?.content?.toDouble()?.toLong() }.getOrNull()

private fun JsonObject.bool(key: String): Boolean? =
    runCatching { this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() }.getOrNull()

private fun JsonObject.arr(key: String): JsonArray? =
    runCatching { this[key]?.jsonArray }.getOrNull()
