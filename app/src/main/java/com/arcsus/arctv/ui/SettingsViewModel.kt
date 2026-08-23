package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.AllDebridException
import com.arcsus.arctv.data.AllDebridRepository
import com.arcsus.arctv.data.AuthExpiredException
import com.arcsus.arctv.data.RdRepository
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.data.SettingsStore
import com.arcsus.arctv.data.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val rdRepository: RdRepository,
    private val adRepository: AllDebridRepository,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /** Inline connect flow for adding the second provider from Settings. */
    sealed interface ConnectState {
        data object Idle : ConnectState
        data class Code(val provider: DebridProvider, val userCode: String, val verificationUrl: String) : ConnectState
        data class Error(val provider: DebridProvider, val message: String) : ConnectState
    }

    data class UiState(
        val rdConnected: Boolean = false,
        val adConnected: Boolean = false,
        val torboxToken: String = "",
        val playlists: List<SavedPlaylist> = emptyList(),
        val connect: ConnectState = ConnectState.Idle,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var connectJob: Job? = null

    init {
        viewModelScope.launch {
            tokenStore.tokens.collect { _state.value = _state.value.copy(rdConnected = it != null) }
        }
        viewModelScope.launch {
            tokenStore.adApiKey.collect { key ->
                _state.value = _state.value.copy(adConnected = key != null)
                // Provider connected: the inline code screen is done.
                if (key != null && (_state.value.connect as? ConnectState.Code)?.provider == DebridProvider.ALL_DEBRID) {
                    _state.value = _state.value.copy(connect = ConnectState.Idle)
                }
            }
        }
        viewModelScope.launch {
            settingsStore.torboxToken.collect { _state.value = _state.value.copy(torboxToken = it) }
        }
        viewModelScope.launch {
            settingsStore.playlists.collect { _state.value = _state.value.copy(playlists = it) }
        }
    }

    fun connect(provider: DebridProvider) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            try {
                when (provider) {
                    DebridProvider.REAL_DEBRID -> {
                        val device = rdRepository.startDeviceAuth()
                        _state.value = _state.value.copy(
                            connect = ConnectState.Code(provider, device.userCode, device.verificationUrl),
                        )
                        rdRepository.pollForTokens(device)
                        _state.value = _state.value.copy(connect = ConnectState.Idle)
                    }

                    DebridProvider.ALL_DEBRID -> {
                        val pin = adRepository.startPinAuth()
                        _state.value = _state.value.copy(
                            connect = ConnectState.Code(provider, pin.pin, pin.userUrl),
                        )
                        adRepository.pollForApiKey(pin)
                        _state.value = _state.value.copy(connect = ConnectState.Idle)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthExpiredException) {
                _state.value = _state.value.copy(
                    connect = ConnectState.Error(provider, "The code expired. Try again."),
                )
            } catch (e: AllDebridException) {
                _state.value = _state.value.copy(
                    connect = ConnectState.Error(provider, e.message ?: "AllDebrid error."),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    connect = ConnectState.Error(provider, e.message ?: "Something went wrong."),
                )
            }
        }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        _state.value = _state.value.copy(connect = ConnectState.Idle)
    }

    fun disconnect(provider: DebridProvider) {
        viewModelScope.launch {
            when (provider) {
                DebridProvider.REAL_DEBRID -> rdRepository.signOut()
                DebridProvider.ALL_DEBRID -> adRepository.signOut()
            }
        }
    }

    fun saveTorboxToken(token: String) {
        viewModelScope.launch { settingsStore.saveTorboxToken(token) }
    }

    fun addPlaylist(playlist: SavedPlaylist) {
        viewModelScope.launch { settingsStore.addPlaylist(playlist) }
    }

    fun removePlaylist(key: String) {
        viewModelScope.launch { settingsStore.removePlaylist(key) }
    }
}
