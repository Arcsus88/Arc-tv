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

/**
 * Something the viewer played, for the Continue Watching row. A film or a
 * series (with the episode played and, when known, the one after it), or a
 * live channel. Playback runs in another app, so this is "what you were
 * watching", not "where you got to".
 */
@Serializable
data class WatchEntry(
    val kind: String = "movie", // "movie" | "tv" | "live"
    val item: CatalogItem? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val nextSeason: Int? = null,
    val nextEpisode: Int? = null,
    val channel: SavedChannel? = null,
    val group: String = "",
    val at: Long = 0L,
) {
    /** One entry per title or channel, whichever episode was played last. */
    val key: String get() = if (kind == "live") "live:${channel?.url}" else "$kind:${item?.id}"
    val title: String get() = if (kind == "live") channel?.name.orEmpty() else item?.title.orEmpty()
}

/** User settings: TorBox token override and the Live TV playlists. */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val MAX_CONTINUE_WATCHING = 20
        const val DEFAULT_LIVE_REGION = "UK"
    }

    private object Keys {
        val TORBOX_TOKEN = stringPreferencesKey("torbox_token")
        val PLAYLISTS = stringPreferencesKey("live_playlists")
        val ACTIVE_PLAYLIST = stringPreferencesKey("active_playlist_key")
        val GUIDE_GROUPS = stringPreferencesKey("guide_groups")
        val GUIDE_ACTIVE_GROUP = stringPreferencesKey("guide_active_group")
        val FAVORITES = stringPreferencesKey("favorites")
        val FAVORITE_CHANNELS = stringPreferencesKey("favorite_channels")
        val CONTINUE_WATCHING = stringPreferencesKey("continue_watching")
        val LIVE_REGION = stringPreferencesKey("live_region")
        val MOVIE_PLAYER = stringPreferencesKey("movie_player")
        val LIVE_PLAYER = stringPreferencesKey("live_player")
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

    /**
     * Which app opens films and series: "" for the system default, "ask" for
     * the chooser, or a package name.
     */
    val moviePlayer: Flow<String> = dataStore.data.map { it[Keys.MOVIE_PLAYER].orEmpty() }

    suspend fun saveMoviePlayer(player: String) {
        dataStore.edit { it[Keys.MOVIE_PLAYER] = player }
    }

    /**
     * Which app opens live channels: as above, plus "arc" for Arc's own
     * player. Defaults to the system default: an external player can sit
     * inside the VPN's app list while Arc TV stays outside it. (Carries the
     * older on/off switch forward for anyone who had turned Arc's player on.)
     */
    val livePlayer: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.LIVE_PLAYER] ?: if (prefs[Keys.LIVE_IN_APP_PLAYER] == true) "arc" else ""
    }

    suspend fun saveLivePlayer(player: String) {
        dataStore.edit { it[Keys.LIVE_PLAYER] = player }
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

    /**
     * The region stamp the viewer cares about ("UK"): its groups come first
     * on the Live tab and load ahead so search can see their channels.
     */
    val liveRegion: Flow<String> = dataStore.data.map { it[Keys.LIVE_REGION] ?: DEFAULT_LIVE_REGION }

    suspend fun saveLiveRegion(region: String) {
        dataStore.edit { it[Keys.LIVE_REGION] = region.trim() }
    }

    /** What the viewer played most recently, newest first. */
    val continueWatching: Flow<List<WatchEntry>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.CONTINUE_WATCHING]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<WatchEntry>>(raw) }.getOrDefault(emptyList())
    }

    /** Puts [entry] at the front, replacing an older entry for the same title or channel. */
    suspend fun recordWatch(entry: WatchEntry) {
        val stamped = entry.copy(at = System.currentTimeMillis())
        val next = (listOf(stamped) + continueWatching.first().filterNot { it.key == stamped.key })
            .take(MAX_CONTINUE_WATCHING)
        dataStore.edit { it[Keys.CONTINUE_WATCHING] = json.encodeToString(next) }
    }

    suspend fun removeWatch(key: String) {
        val next = continueWatching.first().filterNot { it.key == key }
        dataStore.edit { it[Keys.CONTINUE_WATCHING] = json.encodeToString(next) }
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
