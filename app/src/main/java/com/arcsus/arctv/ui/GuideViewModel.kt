package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.EpgEntry
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.data.LiveRepository
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class GuideGroup(val name: String, val count: Int)

class GuideViewModel(
    private val liveRepository: LiveRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    data class UiState(
        val playlist: SavedPlaylist? = null,
        val epgCapable: Boolean = false,
        val channels: List<LiveChannel> = emptyList(),
        val groups: List<GuideGroup> = emptyList(),
        val selectedGroups: List<String> = emptyList(),
        val activeGroup: String? = null,
        val epg: Map<String, List<EpgEntry>> = emptyMap(),
        val loading: Boolean = true,
        val epgLoading: Boolean = false,
        val error: String? = null,
        val nowMs: Long = System.currentTimeMillis(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var epgJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.guideGroups.collect { groups ->
                _state.value = _state.value.copy(selectedGroups = groups)
            }
        }
        viewModelScope.launch {
            settingsStore.activeGuideGroup.collect { group ->
                _state.value = _state.value.copy(activeGroup = group)
                refreshEpg()
            }
        }
        viewModelScope.launch {
            // Keep the "on air" position moving.
            while (true) {
                delay(60_000)
                _state.value = _state.value.copy(nowMs = System.currentTimeMillis())
            }
        }
        viewModelScope.launch {
            // Resolve the playlist the Live tab uses and load its channels.
            settingsStore.playlists.collect { playlists ->
                val activeKey = settingsStore.activePlaylistKey.first()
                val playlist = playlists.firstOrNull { it.key == activeKey } ?: playlists.firstOrNull()
                if (playlist == null) {
                    _state.value = _state.value.copy(playlist = null, loading = false)
                    return@collect
                }
                if (playlist.key == _state.value.playlist?.key && _state.value.channels.isNotEmpty()) {
                    return@collect
                }
                _state.value = _state.value.copy(
                    playlist = playlist,
                    epgCapable = liveRepository.supportsEpg(playlist),
                    loading = true,
                    error = null,
                )
                try {
                    val channels = liveRepository.channels(playlist)
                    val groups = channels
                        .groupingBy { it.group }
                        .eachCount()
                        .map { (name, count) -> GuideGroup(name, count) }
                        .sortedBy { it.name.lowercase() }
                    _state.value = _state.value.copy(
                        channels = channels,
                        groups = groups,
                        loading = false,
                    )
                    refreshEpg()
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

    /** The group actually shown: the active one if still selected, else the first. */
    fun currentGroup(s: UiState): String? {
        val valid = s.selectedGroups.filter { name -> s.groups.any { it.name == name } }
        return if (s.activeGroup != null && valid.contains(s.activeGroup)) s.activeGroup else valid.firstOrNull()
    }

    fun selectGroup(name: String) {
        viewModelScope.launch { settingsStore.saveActiveGuideGroup(name) }
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            val current = _state.value.selectedGroups
            if (!current.contains(name)) settingsStore.saveGuideGroups(current + name)
            settingsStore.saveActiveGuideGroup(name)
        }
    }

    fun removeGroup(name: String) {
        viewModelScope.launch {
            settingsStore.saveGuideGroups(_state.value.selectedGroups - name)
        }
    }

    private fun refreshEpg() {
        val s = _state.value
        val playlist = s.playlist ?: return
        if (!s.epgCapable || s.channels.isEmpty()) return
        val group = currentGroup(s) ?: return
        val groupChannels = s.channels.filter { it.group == group }
        if (groupChannels.isEmpty()) return
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            _state.value = _state.value.copy(epgLoading = true)
            try {
                val epg = liveRepository.epg(playlist, groupChannels)
                _state.value = _state.value.copy(
                    epg = _state.value.epg + epg,
                    epgLoading = false,
                    nowMs = System.currentTimeMillis(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(epgLoading = false)
            }
        }
    }
}
