package com.arcsus.arctv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** A saved Live TV playlist: a plain M3U link, or an Xtream panel login. */
@Serializable
data class SavedPlaylist(
    val name: String = "",
    /** M3U URL, or the Xtream panel base URL (http://host:port). */
    val url: String = "",
    val kind: String = "m3u", // "m3u" | "xtream"
    val username: String = "",
    val password: String = "",
) {
    val isXtream: Boolean get() = kind == "xtream"

    /** Stable identity used for caching and remembering the active playlist. */
    val key: String get() = if (isXtream) "xtream:$url|$username" else url
}

/** User settings: TorBox token override and the Live TV playlists. */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val TORBOX_TOKEN = stringPreferencesKey("torbox_token")
        val PLAYLISTS = stringPreferencesKey("live_playlists")
        val ACTIVE_PLAYLIST = stringPreferencesKey("active_playlist_key")
    }

    val torboxToken: Flow<String> = dataStore.data.map { it[Keys.TORBOX_TOKEN].orEmpty() }

    suspend fun currentTorboxToken(): String = torboxToken.first()

    suspend fun saveTorboxToken(token: String) {
        dataStore.edit { it[Keys.TORBOX_TOKEN] = token.trim() }
    }

    val playlists: Flow<List<SavedPlaylist>> = dataStore.data.map { prefs ->
        decodePlaylists(prefs[Keys.PLAYLISTS])
    }

    suspend fun currentPlaylists(): List<SavedPlaylist> = playlists.first()

    suspend fun savePlaylists(playlists: List<SavedPlaylist>) {
        dataStore.edit { prefs ->
            prefs[Keys.PLAYLISTS] = json.encodeToString(playlists.filter { it.url.isNotBlank() })
        }
    }

    suspend fun addPlaylist(playlist: SavedPlaylist) {
        val current = currentPlaylists()
        if (current.any { it.key == playlist.key }) return
        savePlaylists(current + playlist)
    }

    suspend fun removePlaylist(key: String) {
        savePlaylists(currentPlaylists().filterNot { it.key == key })
    }

    val activePlaylistKey: Flow<String?> = dataStore.data.map { it[Keys.ACTIVE_PLAYLIST] }

    suspend fun saveActivePlaylistKey(key: String) {
        dataStore.edit { it[Keys.ACTIVE_PLAYLIST] = key }
    }

    private fun decodePlaylists(raw: String?): List<SavedPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SavedPlaylist>>(raw) }
            .getOrDefault(emptyList())
            .filter { it.url.isNotBlank() }
    }
}
