package com.arcsus.arctv.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val STREAMABLE_EXTENSIONS = setOf("mkv", "mp4", "avi")

fun isStreamableFilename(filename: String): Boolean =
    filename.substringAfterLast('.', "").lowercase(Locale.US) in STREAMABLE_EXTENSIONS

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

/** Formats an ISO-8601 timestamp from the Real-Debrid API as a short local date. */
fun formatDate(iso: String): String {
    if (iso.length < 19) return iso.take(10)
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.substring(0, 19))
        if (parsed != null) DateFormat.getDateInstance(DateFormat.MEDIUM).format(parsed) else iso.take(10)
    } catch (e: Exception) {
        iso.take(10)
    }
}

/**
 * Hands the URL to an external video player (VLC, Just Player, …).
 * Returns false when no player is installed.
 */
/** Player choices stored in Settings: the system default, the chooser, Arc's own live player, or a package name. */
const val PLAYER_DEFAULT = ""
const val PLAYER_ASK = "ask"
const val PLAYER_ARC = "arc"

/** A video app on this TV that can take a stream URL. */
data class PlayerApp(val label: String, val packageName: String)

/** Every installed app that opens video links, Arc itself excluded. */
fun installedVideoPlayers(context: Context): List<PlayerApp> {
    val probe = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse("http://example.com/video.mkv"), "video/*")
    }
    val pm = context.packageManager
    val found = runCatching { pm.queryIntentActivities(probe, android.content.pm.PackageManager.MATCH_ALL) }
        .getOrDefault(emptyList())
    return found
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            PlayerApp(label, pkg)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

/**
 * Open a stream in the chosen player: the system default, the "Open with"
 * chooser, or a specific app. An app that has since been uninstalled falls
 * back to the default rather than failing.
 */
fun playVideo(context: Context, url: String, title: String? = null, player: String = PLAYER_DEFAULT): Boolean {
    if (player == PLAYER_ASK) return playVideoWith(context, url, title)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        if (!title.isNullOrBlank()) putExtra("title", title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (player.isNotBlank() && player != PLAYER_ARC) setPackage(player)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (intent.`package` != null) playVideo(context, url, title, PLAYER_DEFAULT) else false
    }
}

/**
 * Play via the system's "Open with" chooser, so a file the default player
 * can't handle (the TV's own Media Player and DTS audio, say) can be handed
 * to VLC without changing the default first.
 */
fun playVideoWith(context: Context, url: String, title: String? = null): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        if (!title.isNullOrBlank()) putExtra("title", title)
    }
    val chooser = Intent.createChooser(intent, "Open with").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return try {
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** DTS audio: licensed, so the built-in player on many TVs plays silence or quits. */
fun hasDtsAudio(name: String): Boolean = Regex("\\bdts\\b|dts-hd|dts\\.hd|dtshd|\\bdts[-.]?x\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)

/**
 * Builds a play intent suitable for launching *for result*, so players that
 * report back (MX Player, VLC, Just Player) let us auto-advance to the next
 * episode. No NEW_TASK flag — result delivery requires the same task.
 */
fun buildPlayIntentForResult(url: String, title: String? = null): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        if (!title.isNullOrBlank()) putExtra("title", title)
        // Ask MX Player to return a result describing how playback ended.
        putExtra("return_result", true)
    }

private fun longExtra(data: Intent, key: String): Long? {
    if (!data.hasExtra(key)) return null
    val asLong = data.getLongExtra(key, Long.MIN_VALUE)
    if (asLong != Long.MIN_VALUE) return asLong
    val asInt = data.getIntExtra(key, Int.MIN_VALUE)
    return if (asInt != Int.MIN_VALUE) asInt.toLong() else null
}

/**
 * True when the returned player result indicates the video played to the end
 * (so we should auto-advance). Handles MX Player's `end_by`/position+duration
 * and VLC/Just Player's `extra_position`/`extra_duration`. Unknown players
 * simply return false (no auto-advance).
 */
fun playbackCompleted(data: Intent?): Boolean {
    if (data == null) return false
    if (data.getStringExtra("end_by") == "playback_completion") return true
    val position = longExtra(data, "position") ?: longExtra(data, "extra_position")
    val duration = longExtra(data, "duration") ?: longExtra(data, "extra_duration")
    return position != null && duration != null && duration > 0 && position >= duration - 5_000
}

/**
 * True when the player reported *any* playback info on exit — meaning we can
 * trust [playbackCompleted]'s answer and shouldn't fall back to the countdown
 * prompt. Players that report nothing (e.g. VLC in many configs) return false.
 */
fun hasPlaybackInfo(data: Intent?): Boolean {
    if (data == null) return false
    return data.hasExtra("end_by") ||
        data.hasExtra("position") || data.hasExtra("extra_position") ||
        data.hasExtra("duration") || data.hasExtra("extra_duration")
}

/** The app that will handle a plain video ACTION_VIEW for [url], if known. */
fun resolvePlayerLabel(context: Context, url: String): String? {
    val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), "video/*") }
    val info = context.packageManager.resolveActivity(intent, 0) ?: return null
    val label = info.loadLabel(context.packageManager)?.toString()
    return label?.takeUnless { it.isBlank() || it.equals("Android System", ignoreCase = true) }
}

/**
 * Ask the stream's server for its first bytes, to separate "the link is dead
 * or refused from this device" from "the player cannot decode the file".
 * Blocking — call from a background dispatcher.
 */
fun probeStream(url: String): String = try {
    val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    val request = okhttp3.Request.Builder().url(url).header("Range", "bytes=0-1").build()
    client.newCall(request).execute().use { response ->
        when {
            response.isSuccessful ->
                "Link check: HTTP ${response.code} — the link serves data, so the player " +
                    "likely can't decode this file. Try another source, or a different player app."
            response.code == 403 ->
                "Link check: HTTP 403 — the server refused this device (VPN or IP block?)."
            else ->
                "Link check: HTTP ${response.code} — the link isn't serving. Try another source."
        }
    }
} catch (e: Exception) {
    "Link check failed: ${e.message ?: "network error"} — the link isn't reachable from this device."
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Arc TV link", text))
    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
}
