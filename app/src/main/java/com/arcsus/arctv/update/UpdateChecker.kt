package com.arcsus.arctv.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.arcsus.arctv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList(),
    val prerelease: Boolean = false,
    val draft: Boolean = false,
)

data class UpdateInfo(val version: String, val apkUrl: String)

/** Checks GitHub Releases for a newer build and installs it via the package installer. */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Arcsus88/Arc-tv/releases/latest"
    }

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            // Don't swallow failures as "no update" — GitHub rate-limits
            // anonymous API calls per IP, and that must not read as up-to-date.
            if (!response.isSuccessful) throw IOException("GitHub answered HTTP ${response.code}")
            val release = json.decodeFromString<GithubRelease>(response.body!!.string())
            if (release.draft || release.prerelease) return@withContext null
            val remoteVersion = release.tagName.removePrefix("v")
            if (!isNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@withContext null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@withContext null
            UpdateInfo(remoteVersion, apk.browserDownloadUrl)
        }
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        val apkFile = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "arc-tv-${info.version}.apk")
            val request = Request.Builder().url(info.apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                file.outputStream().use { out ->
                    response.body!!.byteStream().copyTo(out)
                }
            }
            file
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** Compares dotted numeric versions; non-numeric parts compare as 0. */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
