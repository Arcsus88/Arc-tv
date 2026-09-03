package com.arcsus.arctv

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records an uncaught exception to disk so the next launch can show it on
 * screen. There is no adb on a living-room TV; a photo of this dialog is the
 * stack trace.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                File(app.filesDir, FILE).writeText(
                    "Arc TV ${BuildConfig.VERSION_NAME} · $stamp · thread ${thread.name}\n$trace",
                )
            }
            // Let the system finish the crash (and restart the app) as usual.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The last recorded crash, cleared once read. */
    fun consume(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        return text?.takeIf { it.isNotBlank() }
    }

    /**
     * The lines worth photographing: the header, the exception, and the first
     * frames -- with the noisy framework frames thinned out so the app's own
     * frames stay on screen.
     */
    fun summarise(text: String, maxLines: Int = 18): String {
        val lines = text.lines()
        val head = lines.take(2)
        val frames = lines.drop(2)
            .filter { it.isNotBlank() }
            .filterNot { it.trim().startsWith("at kotlinx.coroutines") || it.trim().startsWith("at kotlin.coroutines") }
        val ours = frames.filter { "com.arcsus" in it || "Caused by" in it || !it.trim().startsWith("at ") }
        val picked = (ours.take(maxLines - head.size) + frames.take(6)).distinct().take(maxLines - head.size)
        return (head + picked).joinToString("\n") { it.trim().replace("\tat ", "at ") }
    }
}
