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
        val checking: Boolean = false,
        /** True after a manual check that found no newer version. */
        val upToDate: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        // Silent check on launch; the banner appears only if something is found.
        checkForUpdate(manual = false)
    }

    /** Re-check GitHub for a newer release. [manual] surfaces progress/"up to date" feedback. */
    fun checkForUpdate(manual: Boolean = true) {
        if (_state.value.checking) return
        _state.update {
            it.copy(checking = true, upToDate = false, dismissed = false, error = null)
        }
        viewModelScope.launch {
            try {
                val info = checker.check()
                _state.update {
                    it.copy(
                        checking = false,
                        available = info,
                        upToDate = manual && info == null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        error = if (manual) "Couldn't check for updates. Try again." else null,
                    )
                }
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
