package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.DownloadItem
import com.arcsus.arctv.data.RdRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

class DownloadsViewModel(private val repository: RdRepository) : ViewModel() {

    data class UiState(
        val items: List<DownloadItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val endReached: Boolean = false,
        val videoOnly: Boolean = true,
        val totalCount: Int = 0,
    )

    private val all = mutableListOf<DownloadItem>()
    private var page = 1

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // /downloads returns newest first.
                val result = repository.downloads(page, PAGE_SIZE)
                all += result.items
                page++
                val end = result.items.size < PAGE_SIZE
                _state.update {
                    it.copy(
                        loading = false,
                        endReached = end,
                        totalCount = if (result.totalCount >= 0) result.totalCount else all.size,
                        items = visibleItems(it.videoOnly),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun toggleVideoOnly() {
        _state.update { it.copy(videoOnly = !it.videoOnly, items = visibleItems(!it.videoOnly)) }
    }

    fun refresh() {
        all.clear()
        page = 1
        _state.update { it.copy(items = emptyList(), endReached = false, loading = false, error = null) }
        loadMore()
    }

    private fun visibleItems(videoOnly: Boolean): List<DownloadItem> =
        if (videoOnly) {
            all.filter { isStreamableFilename(it.filename) || it.mimeType?.startsWith("video/") == true }
        } else {
            all.toList()
        }
}

internal fun friendlyError(e: Exception): String = when (e) {
    is java.io.IOException -> "Network error. Check the connection and try again."
    is retrofit2.HttpException -> "Real-Debrid returned an error (HTTP ${e.code()})."
    else -> "Unexpected error: ${e.message}"
}
