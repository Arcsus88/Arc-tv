package com.arcsus.arctv.ui

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
