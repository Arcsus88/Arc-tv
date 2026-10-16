package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.EpgEntry
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.data.LiveRepository
import com.arcsus.arctv.data.SavedChannel
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.data.SettingsStore
import com.arcsus.arctv.data.SkyChannel
import com.arcsus.arctv.data.SkyEpg
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

    companion object {
        /** Virtual guide group holding the user's hearted channels. */
        const val FAV_GROUP = "♥ Favourites"

        /** Channels per group dressed from Sky's guide (the web guide shows 60). */
        private const val SKY_MAX_CHANNELS = 150

        /** "UK | Sports" -> ["uk", "sport"]: lower-case words, plural "s" dropped. */
        internal fun wordsOf(name: String): List<String> =
            name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
                .map { if (it.length > 3 && it.endsWith("s")) it.dropLast(1) else it }

        /**
         * The playlist group a label like "UK Sports" means, whatever the
         * panel's spelling ("UK | Sports", "UK: SPORTS", "UK SPORTS FHD"):
         * every word of the label must be in the group's name; the fewest
         * extra words wins, then the bigger group. (The web guide's findGroup.)
         */
        internal fun findGroup(groups: List<GuideGroup>, label: String): String? {
            val want = wordsOf(label)
            return groups
                .filter { g -> wordsOf(g.name).containsAll(want) }
                .minWithOrNull(compareBy<GuideGroup> { wordsOf(it.name).size - want.size }.thenByDescending { it.count })
                ?.name
        }
    }

    data class UiState(
        val playlist: SavedPlaylist? = null,
        val epgCapable: Boolean = false,
        val channels: List<LiveChannel> = emptyList(),
        val groups: List<GuideGroup> = emptyList(),
        val selectedGroups: List<String> = emptyList(),
        val activeGroup: String? = null,
        val epg: Map<String, List<EpgEntry>> = emptyMap(),
        /** Hearted channels shown under the virtual [FAV_GROUP] chip. */
        val favChannels: List<LiveChannel> = emptyList(),
        val loading: Boolean = true,
        val epgLoading: Boolean = false,
        val error: String? = null,
        val nowMs: Long = System.currentTimeMillis(),
        /** Sky's numbers, logos, stills and listings, by channel URL. */
        val sky: Map<String, SkyChannel> = emptyMap(),
        /** Sky's guide for the current group is still on its way. */
        val skyLoading: Boolean = false,
        /** Open the group picker with this search (a banner's group the playlist lacks). */
        val pickerRequest: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var epgJob: Job? = null
    private var skyJob: Job? = null

    /** A group to open on once the playlist's groups are known (from a home banner). */
    private var pendingFocus: String? = null

    init {
        viewModelScope.launch {
            settingsStore.guideGroups.collect { groups ->
                _state.value = _state.value.copy(selectedGroups = groups)
            }
        }
        viewModelScope.launch {
            settingsStore.favoriteChannels.collect { favs ->
                _state.value = _state.value.copy(
                    favChannels = favs.mapIndexed { i, c ->
                        LiveChannel(
                            id = "fav:$i:${c.url}",
                            name = c.name,
                            logo = c.logo,
                            group = FAV_GROUP,
                            url = c.url,
                        )
                    },
                )
                if (currentGroup(_state.value) == FAV_GROUP) {
                    refreshEpg()
                    refreshSky()
                }
            }
        }
        viewModelScope.launch {
            settingsStore.activeGuideGroup.collect { group ->
                _state.value = _state.value.copy(activeGroup = group)
                ensureGroupLoaded()
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
                    val counts = channels.groupingBy { it.group }.eachCount()
                    // The channel load is capped on huge panels, so the panel's
                    // own category list fills in groups the cap cut off (their
                    // channels are fetched on demand when picked).
                    val panelNames = runCatching { liveRepository.panelGroups(playlist) }
                        .getOrDefault(emptyList())
                    val groups = (counts.keys + panelNames)
                        .distinct()
                        .map { name -> GuideGroup(name, counts[name] ?: 0) }
                        .sortedBy { it.name.lowercase() }
                    _state.value = _state.value.copy(
                        channels = channels,
                        groups = groups,
                        loading = false,
                    )
                    if (!applyPendingFocus()) ensureGroupLoaded()
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

    /** Selectable chips: the virtual Favourites group first, then picked groups. */
    fun validGroups(s: UiState): List<String> {
        val selected = s.selectedGroups.filter { name -> s.groups.any { it.name == name } }
        return if (s.favChannels.isEmpty()) selected else listOf(FAV_GROUP) + selected
    }

    fun channelsFor(s: UiState, group: String): List<LiveChannel> =
        if (group == FAV_GROUP) s.favChannels else s.channels.filter { it.group == group }

    /** The group actually shown: the active one if still selected, else the first. */
    fun currentGroup(s: UiState): String? {
        val valid = validGroups(s)
        return if (s.activeGroup != null && valid.contains(s.activeGroup)) s.activeGroup else valid.firstOrNull()
    }

    fun toggleFavorite(channel: LiveChannel) {
        viewModelScope.launch {
            settingsStore.toggleFavoriteChannel(
                SavedChannel(name = channel.name, url = channel.url, logo = channel.logo),
            )
        }
    }

    /**
     * Open the guide on a group by its everyday name -- the home page's Sky
     * Sports banner asks for "UK Sports". The group joins the guide's groups
     * and is shown; a playlist with no such group gets the picker, searching
     * for the closest ones ("sport"), as on the web.
     */
    fun openGroup(label: String) {
        pendingFocus = label
        applyPendingFocus()
    }

    private fun applyPendingFocus(): Boolean {
        val label = pendingFocus ?: return false
        val s = _state.value
        if (s.loading || s.groups.isEmpty()) return false
        pendingFocus = null
        val target = findGroup(s.groups, label)
        if (target != null) {
            addGroup(target)
        } else {
            _state.value = s.copy(pickerRequest = wordsOf(label).lastOrNull().orEmpty())
        }
        return true
    }

    fun consumePickerRequest() {
        _state.value = _state.value.copy(pickerRequest = null)
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

    private var groupLoadJob: Job? = null

    /**
     * Make sure the current group's channels are in memory (fetching the
     * category on demand when the capped full load missed it), then load EPG.
     */
    /** Channels already asked for, so scrolling doesn't re-request them. */
    private val epgRequested = mutableSetOf<String>()

    private fun ensureGroupLoaded() {
        val s = _state.value
        val playlist = s.playlist ?: return
        val group = currentGroup(s) ?: return
        if (group == FAV_GROUP || s.loading || s.channels.any { it.group == group }) {
            refreshEpg()
            refreshSky()
            return
        }
        groupLoadJob?.cancel()
        groupLoadJob = viewModelScope.launch {
            _state.value = _state.value.copy(epgLoading = true)
            try {
                val extra = liveRepository.channelsForGroup(playlist, group)
                if (extra.isNotEmpty()) {
                    val known = _state.value.channels.mapTo(HashSet()) { it.url }
                    _state.value = _state.value.copy(
                        channels = _state.value.channels + extra.filter { it.url !in known },
                        groups = _state.value.groups.map {
                            if (it.name == group && it.count == 0) GuideGroup(group, extra.size) else it
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                /* group stays empty; the list shows its empty state */
            }
            _state.value = _state.value.copy(epgLoading = false)
            refreshEpg()
            refreshSky()
        }
    }

    /**
     * Sky's guide for the current group: channel numbers and logos, a still
     * and synopsis per programme, and listings for channels the panel's EPG
     * misses. A bonus -- if Sky is unreachable the panel's EPG still stands.
     */
    private fun refreshSky() {
        val s = _state.value
        val group = currentGroup(s) ?: return
        val channels = channelsFor(s, group).take(SKY_MAX_CHANNELS)
        if (channels.isEmpty()) return
        skyJob?.cancel()
        skyJob = viewModelScope.launch {
            _state.value = _state.value.copy(skyLoading = true)
            val guide = try {
                SkyEpg.guide(channels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
            _state.value = _state.value.copy(sky = _state.value.sky + guide, skyLoading = false)
        }
    }

    /**
     * Guide data for rows the user is looking at. The list calls this as it
     * scrolls, so a big group fills in on the way down instead of stopping
     * dead after the first batch.
     */
    fun loadEpgFor(channels: List<LiveChannel>) {
        val s = _state.value
        val playlist = s.playlist ?: return
        if (!s.epgCapable) return
        val missing = channels.filter { it.url !in s.epg && it.url !in epgRequested }
        if (missing.isEmpty()) return
        missing.forEach { epgRequested.add(it.url) }
        viewModelScope.launch {
            try {
                val epg = liveRepository.epg(playlist, missing, limit = missing.size)
                _state.value = _state.value.copy(epg = _state.value.epg + epg)
            } catch (e: CancellationException) {
                missing.forEach { epgRequested.remove(it.url) }
                throw e
            } catch (e: Exception) {
                // Let a failed stretch be retried the next time it scrolls by.
                missing.forEach { epgRequested.remove(it.url) }
            }
        }
    }

    private fun refreshEpg() {
        val s = _state.value
        val playlist = s.playlist ?: return
        if (!s.epgCapable) return
        val group = currentGroup(s) ?: return
        val groupChannels = channelsFor(s, group)
        if (groupChannels.isEmpty()) return
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            _state.value = _state.value.copy(epgLoading = true)
            try {
                val epg = liveRepository.epg(playlist, groupChannels)
                groupChannels.take(60).forEach { epgRequested.add(it.url) }
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
