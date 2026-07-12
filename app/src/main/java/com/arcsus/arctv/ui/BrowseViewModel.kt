package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.BrowseRepository
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.data.CatalogRow
import com.arcsus.arctv.data.Episode
import com.arcsus.arctv.data.ResolvedStream
import com.arcsus.arctv.data.Season
import com.arcsus.arctv.data.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowseViewModel(private val repository: BrowseRepository) : ViewModel() {

    /** The pop-up selection flow: seasons → episodes → sources. */
    sealed interface Sheet {
        data object Hidden : Sheet
        data class Loading(val label: String) : Sheet
        data class Seasons(val item: CatalogItem, val seasons: List<Season>) : Sheet
        data class Episodes(val item: CatalogItem, val season: Int, val episodes: List<Episode>) : Sheet
        data class Sources(
            val item: CatalogItem,
            val season: Int?,
            val episode: Int?,
            val sources: List<Source>,
            val playing: String? = null, // magnet currently being played
            val note: String? = null,
        ) : Sheet
        data class Error(val message: String) : Sheet
    }

    data class UiState(
        val query: String = "",
        val loadingHome: Boolean = false,
        val rows: List<CatalogRow> = emptyList(),
        val searching: Boolean = false,
        val searchResults: List<CatalogItem>? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _sheet = MutableStateFlow<Sheet>(Sheet.Hidden)
    val sheet: StateFlow<Sheet> = _sheet

    private val _playRequest = MutableStateFlow<ResolvedStream?>(null)
    val playRequest: StateFlow<ResolvedStream?> = _playRequest

    init {
        loadHome()
    }

    fun loadHome() {
        if (_state.value.loadingHome) return
        _state.update { it.copy(loadingHome = true, error = null) }
        viewModelScope.launch {
            try {
                val rows = repository.home()
                _state.update { it.copy(loadingHome = false, rows = rows) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingHome = false, error = e.message ?: "Couldn't load catalogue.") }
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty() || _state.value.searching) return
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            try {
                val results = repository.search(q)
                _state.update { it.copy(searching = false, searchResults = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, error = e.message ?: "Search failed.") }
            }
        }
    }

    fun clearSearch() = _state.update { it.copy(query = "", searchResults = null, error = null) }

    /** Entry point when a poster is chosen: movies go straight to sources, TV to seasons. */
    fun openTitle(item: CatalogItem) {
        if (item.isTv) loadSeasons(item) else loadSources(item, null, null)
    }

    private fun loadSeasons(item: CatalogItem) {
        _sheet.value = Sheet.Loading("Loading seasons…")
        launchSheet {
            val seasons = repository.seasons(item)
            _sheet.value = if (seasons.isEmpty()) {
                Sheet.Error("No season information for this show.")
            } else {
                Sheet.Seasons(item, seasons)
            }
        }
    }

    fun selectSeason(item: CatalogItem, season: Int) {
        _sheet.value = Sheet.Loading("Loading episodes…")
        launchSheet {
            val episodes = repository.episodes(item, season)
            _sheet.value = if (episodes.isEmpty()) {
                Sheet.Error("No episodes found for that season.")
            } else {
                Sheet.Episodes(item, season, episodes)
            }
        }
    }

    fun selectEpisode(item: CatalogItem, season: Int, episode: Int) = loadSources(item, season, episode)

    private fun loadSources(item: CatalogItem, season: Int?, episode: Int?) {
        _sheet.value = Sheet.Loading("Finding sources…")
        launchSheet {
            val sources = repository.sources(item, season, episode)
            _sheet.value = if (sources.isEmpty()) {
                Sheet.Error("No sources found for this title.")
            } else {
                Sheet.Sources(item, season, episode, sources)
            }
        }
    }

    /** Play the best-ranked source without picking manually. */
    fun autoPlay() {
        val s = _sheet.value as? Sheet.Sources ?: return
        s.sources.firstOrNull()?.let { playSource(it) }
    }

    fun playSource(source: Source) {
        val s = _sheet.value as? Sheet.Sources ?: return
        if (s.playing != null) return
        _sheet.value = s.copy(playing = source.magnet, note = null)
        viewModelScope.launch {
            try {
                val stream = repository.play(source)
                _sheet.value = Sheet.Hidden
                _playRequest.value = stream
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stay on the sheet so the user can pick another source.
                val current = _sheet.value as? Sheet.Sources ?: return@launch
                _sheet.value = current.copy(
                    playing = null,
                    note = e.message ?: "That source isn't cached. Try another.",
                )
            }
        }
    }

    fun dismissSheet() {
        _sheet.value = Sheet.Hidden
    }

    fun consumePlayRequest() {
        _playRequest.value = null
    }

    private fun launchSheet(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sheet.value = Sheet.Error(e.message ?: "Something went wrong.")
            }
        }
    }
}
