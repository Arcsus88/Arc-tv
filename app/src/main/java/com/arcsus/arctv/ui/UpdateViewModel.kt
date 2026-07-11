package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.update.UpdateChecker
import com.arcsus.arctv.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateViewModel(private val checker: UpdateChecker) : ViewModel() {

    data class UiState(
        val available: UpdateInfo? = null,
        val downloading: Boolean = false,
        val error: String? = null,
        val dismissed: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            try {
                val info = checker.check()
                _state.update { it.copy(available = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Update check is best-effort; stay quiet on failure.
            }
        }
    }

    fun install() {
        val info = _state.value.available ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, error = null) }
        viewModelScope.launch {
            try {
                checker.downloadAndInstall(info)
                _state.update { it.copy(downloading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, error = "Update failed: ${e.message}") }
            }
        }
    }

    fun dismiss() {
        _state.update { it.copy(dismissed = true) }
    }
}
