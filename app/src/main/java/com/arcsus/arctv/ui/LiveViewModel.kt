package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.data.LiveRepository
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LiveGroup(val name: String, val count: Int)

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
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
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

    fun load(playlist: SavedPlaylist, refresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val channels = liveRepository.channels(playlist, refresh)
                val groups = channels
                    .groupingBy { it.group }
                    .eachCount()
                    .map { (name, count) -> LiveGroup(name, count) }
                _state.value = _state.value.copy(
                    active = playlist,
                    channels = channels,
                    groups = groups,
                    loading = false,
                )
                settingsStore.saveActivePlaylistKey(playlist.key)
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
