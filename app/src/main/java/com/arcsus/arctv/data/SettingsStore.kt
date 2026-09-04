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

/** A live channel the user hearted, kept independent of playlist reloads. */
@Serializable
data class SavedChannel(
    val name: String = "",
    val url: String = "",
    val logo: String = "",
)

/** User settings: TorBox token override and the Live TV playlists. */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val TORBOX_TOKEN = stringPreferencesKey("torbox_token")
        val PLAYLISTS = stringPreferencesKey("live_playlists")
        val ACTIVE_PLAYLIST = stringPreferencesKey("active_playlist_key")
        val GUIDE_GROUPS = stringPreferencesKey("guide_groups")
        val GUIDE_ACTIVE_GROUP = stringPreferencesKey("guide_active_group")
        val FAVORITES = stringPreferencesKey("favorites")
        val FAVORITE_CHANNELS = stringPreferencesKey("favorite_channels")
        val LIVE_SETUP_DONE = androidx.datastore.preferences.core.booleanPreferencesKey("live_setup_done")
        val LIVE_IN_APP_PLAYER = androidx.datastore.preferences.core.booleanPreferencesKey("live_in_app_player")
    }

    /**
     * Whether first-run setup should still offer the Live TV step. Anyone who
     * already has playlists (or has dismissed the step) skips it.
     */
    val liveSetupNeeded: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.LIVE_SETUP_DONE] != true && decodePlaylists(prefs[Keys.PLAYLISTS]).isEmpty()
    }

    suspend fun markLiveSetupDone() {
        dataStore.edit { it[Keys.LIVE_SETUP_DONE] = true }
    }

    /** Play live channels in Arc's own player (default) rather than an external app. */
    val liveInAppPlayer: Flow<Boolean> = dataStore.data.map { it[Keys.LIVE_IN_APP_PLAYER] ?: true }

    suspend fun setLiveInAppPlayer(enabled: Boolean) {
        dataStore.edit { it[Keys.LIVE_IN_APP_PLAYER] = enabled }
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

    /** Channel groups the user picked for the TV guide. */
    val guideGroups: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.GUIDE_GROUPS]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveGuideGroups(groups: List<String>) {
        dataStore.edit { it[Keys.GUIDE_GROUPS] = json.encodeToString(groups) }
    }

    val activeGuideGroup: Flow<String?> = dataStore.data.map { it[Keys.GUIDE_ACTIVE_GROUP] }

    suspend fun saveActiveGuideGroup(group: String) {
        dataStore.edit { it[Keys.GUIDE_ACTIVE_GROUP] = group }
    }

    /** Titles the user marked as favourites, newest first. */
    val favorites: Flow<List<CatalogItem>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.FAVORITES]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<CatalogItem>>(raw) }.getOrDefault(emptyList())
    }

    /** Adds the title, or removes it when it is already a favourite. */
    suspend fun toggleFavorite(item: CatalogItem) {
        val current = favorites.first()
        val without = current.filterNot { it.id == item.id && it.type == item.type }
        val next = if (without.size == current.size) listOf(item) + current else without
        dataStore.edit { it[Keys.FAVORITES] = json.encodeToString(next) }
    }

    /** Live channels the user hearted, newest first (keyed by stream URL). */
    val favoriteChannels: Flow<List<SavedChannel>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.FAVORITE_CHANNELS]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<SavedChannel>>(raw) }.getOrDefault(emptyList())
    }

    /** Adds the channel, or removes it when it is already a favourite. */
    suspend fun toggleFavoriteChannel(channel: SavedChannel) {
        val current = favoriteChannels.first()
        val without = current.filterNot { it.url == channel.url }
        val next = if (without.size == current.size) listOf(channel) + current else without
        dataStore.edit { it[Keys.FAVORITE_CHANNELS] = json.encodeToString(next) }
    }

    private fun decodePlaylists(raw: String?): List<SavedPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SavedPlaylist>>(raw) }
            .getOrDefault(emptyList())
            .filter { it.url.isNotBlank() }
    }
}
