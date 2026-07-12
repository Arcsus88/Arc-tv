package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.BrowseRepository
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.data.CatalogRow
import com.arcsus.arctv.data.ResolvedStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowseViewModel(private val repository: BrowseRepository) : ViewModel() {

    data class UiState(
        val query: String = "",
        val loadingHome: Boolean = false,
        val rows: List<CatalogRow> = emptyList(),
        val searching: Boolean = false,
        val searchResults: List<CatalogItem>? = null, // null = not in search mode
        val error: String? = null,
        val resolving: CatalogItem? = null, // the title currently being resolved
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Emits a stream URL to launch in the player; consumed by the UI. */
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
                _state.update { it.copy(loadingHome = false, error = e.message ?: "Couldn't load catalog.") }
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

    /** Resolve a title to a playable link and request playback. */
    fun play(item: CatalogItem) {
        if (_state.value.resolving != null) return
        _state.update { it.copy(resolving = item, message = null) }
        viewModelScope.launch {
            try {
                val stream = repository.resolve(item)
                _state.update { it.copy(resolving = null) }
                _playRequest.value = stream
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(resolving = null, message = e.message ?: "Couldn't find a source.") }
            }
        }
    }

    fun consumePlayRequest() {
        _playRequest.value = null
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
