package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.BrowseRepository
import com.arcsus.arctv.data.BrowseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowseViewModel(private val repository: BrowseRepository) : ViewModel() {

    data class UiState(
        val query: String = "",
        val searching: Boolean = false,
        val results: List<BrowseResult> = emptyList(),
        val error: String? = null,
        val searched: Boolean = false,
        val adding: Set<String> = emptySet(),
        val added: Set<String> = emptySet(),
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty() || _state.value.searching) return
        _state.update { it.copy(searching = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val results = repository.search(q)
                _state.update { it.copy(searching = false, results = results, searched = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(searching = false, searched = true, error = e.message ?: "Search failed.")
                }
            }
        }
    }

    fun add(result: BrowseResult) {
        val magnet = result.magnet
        val s = _state.value
        if (magnet.isBlank() || magnet in s.adding || magnet in s.added) return
        _state.update { it.copy(adding = it.adding + magnet, message = null) }
        viewModelScope.launch {
            try {
                repository.add(magnet)
                _state.update {
                    it.copy(
                        adding = it.adding - magnet,
                        added = it.added + magnet,
                        message = "Added \"${result.title.take(40)}\" to Real-Debrid",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(adding = it.adding - magnet, message = e.message ?: "Couldn't add that one.")
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
