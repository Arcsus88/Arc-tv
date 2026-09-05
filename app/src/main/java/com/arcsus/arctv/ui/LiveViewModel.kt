package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.data.LiveRepository
import com.arcsus.arctv.data.SavedChannel
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LiveGroup(val name: String, val count: Int)

/** How many of the region's groups load ahead of being opened. */
private const val MAX_PRELOAD_GROUPS = 40

class LiveViewModel(
    private val liveRepository: LiveRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    data class UiState(
        val playlists: List<SavedPlaylist> = emptyList(),
        val active: SavedPlaylist? = null,
        val channels: List<LiveChannel> = emptyList(),
        val groups: List<LiveGroup> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        /** Hearted channels, newest first. */
        val favorites: List<SavedChannel> = emptyList(),
        /** Group whose channels are being fetched on demand, if any. */
        val groupLoading: String? = null,
        /** The viewer's region tag ("UK"): its groups lead and preload. */
        val region: String = "UK",
    ) {
        val favoriteUrls: Set<String> get() = favorites.mapTo(HashSet()) { it.url }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var preloadJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.favoriteChannels.collect { favs ->
                _state.value = _state.value.copy(favorites = favs)
            }
        }
        viewModelScope.launch {
            settingsStore.liveRegion.collect { region ->
                _state.value = _state.value.copy(region = region)
                preloadRegion()
            }
        }
        viewModelScope.launch {
            // Keep the playlist list in sync with the Settings tab.
            settingsStore.playlists.collect { playlists ->
                _state.value = _state.value.copy(playlists = playlists)
                val current = _state.value.active
                if (current == null && playlists.isNotEmpty()) {
                    val savedKey = settingsStore.activePlaylistKey.first()
                    val restored = playlists.firstOrNull { it.key == savedKey } ?: playlists.first()
                    load(restored)
                } else if (current != null && playlists.none { it.key == current.key }) {
                    // The active playlist was removed in Settings.
                    _state.value = _state.value.copy(active = null, channels = emptyList(), groups = emptyList())
                }
            }
        }
    }

    /** Fetch a group's channels if the capped load didn't include them. */
    fun openGroup(name: String) {
        val s = _state.value
        if (s.channels.any { it.group == name } || s.groupLoading == name) return
        viewModelScope.launch {
            _state.value = _state.value.copy(groupLoading = name)
            fetchGroup(name)
            _state.value = _state.value.copy(groupLoading = null)
        }
    }

    private suspend fun fetchGroup(name: String) {
        val playlist = _state.value.active ?: return
        try {
            val extra = liveRepository.channelsForGroup(playlist, name)
            val known = _state.value.channels.mapTo(HashSet()) { it.url }
            val fresh = extra.filter { it.url !in known }
            _state.value = _state.value.copy(
                channels = _state.value.channels + fresh,
                groups = _state.value.groups.map {
                    if (it.name == name && it.count == 0) LiveGroup(name, extra.size) else it
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The group shows its empty state; opening it again retries.
        }
    }

    /**
     * Load the region's groups ahead of time, one after another in the
     * background, so their channels show counts and turn up in search
     * without the viewer opening each group first. Capped: a panel with
     * hundreds of "UK" groups would otherwise hammer it.
     */
    private fun preloadRegion() {
        preloadJob?.cancel()
        val s = _state.value
        if (s.active == null || s.groups.isEmpty()) return
        val pending = s.groups
            .filter { it.count == 0 && LiveMatch.inRegion(it.name, s.region) }
            .take(MAX_PRELOAD_GROUPS)
        if (pending.isEmpty()) return
        preloadJob = viewModelScope.launch {
            for (group in pending) {
                if (_state.value.channels.any { it.group == group.name }) continue
                fetchGroup(group.name)
            }
        }
    }

    fun toggleFavorite(channel: LiveChannel) {
        viewModelScope.launch {
            settingsStore.toggleFavoriteChannel(
                SavedChannel(name = channel.name, url = channel.url, logo = channel.logo),
            )
        }
    }

    fun load(playlist: SavedPlaylist, refresh: Boolean = false) {
        preloadJob?.cancel()
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val channels = liveRepository.channels(playlist, refresh)
                val counts = channels.groupingBy { it.group }.eachCount()
                // The channel load is capped on huge panels, and the panel
                // lists its categories in its own order -- whatever sits past
                // the cap (UK groups, on some panels) simply vanished. The
                // panel's category list fills those in; their channels are
                // fetched when the group is opened.
                val panelNames = runCatching { liveRepository.panelGroups(playlist) }
                    .getOrDefault(emptyList())
                val groups = (counts.keys + panelNames)
                    .distinct()
                    .map { name -> LiveGroup(name, counts[name] ?: 0) }
                _state.value = _state.value.copy(
                    active = playlist,
                    channels = channels,
                    groups = groups,
                    loading = false,
                )
                settingsStore.saveActivePlaylistKey(playlist.key)
                preloadRegion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load the playlist.",
                )
            }
        }
    }
}
