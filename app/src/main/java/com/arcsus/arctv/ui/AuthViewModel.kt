package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.AllDebridException
import com.arcsus.arctv.data.AllDebridRepository
import com.arcsus.arctv.data.AuthExpiredException
import com.arcsus.arctv.data.RdRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

enum class DebridProvider(val label: String) {
    REAL_DEBRID("Real-Debrid"),
    ALL_DEBRID("AllDebrid"),
}

class AuthViewModel(
    private val rdRepository: RdRepository,
    private val adRepository: AllDebridRepository,
) : ViewModel() {

    sealed interface State {
        data object Choose : State
        data class Loading(val provider: DebridProvider) : State
        data class CodeReady(
            val provider: DebridProvider,
            val userCode: String,
            val verificationUrl: String,
        ) : State

        data class Error(val provider: DebridProvider, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Choose)
    val state: StateFlow<State> = _state

    private var job: Job? = null

    fun choose() {
        job?.cancel()
        _state.value = State.Choose
    }

    fun start(provider: DebridProvider) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = State.Loading(provider)
            try {
                when (provider) {
                    DebridProvider.REAL_DEBRID -> {
                        val device = rdRepository.startDeviceAuth()
                        _state.value = State.CodeReady(provider, device.userCode, device.verificationUrl)
                        // Suspends until authorised; once tokens are saved the
                        // main screen takes over via TokenStore.isAuthorized.
                        rdRepository.pollForTokens(device)
                    }

                    DebridProvider.ALL_DEBRID -> {
                        val pin = adRepository.startPinAuth()
                        _state.value = State.CodeReady(provider, pin.pin, pin.userUrl)
                        adRepository.pollForApiKey(pin)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthExpiredException) {
                _state.value = State.Error(provider, "The code expired. Get a new one and try again.")
            } catch (e: AllDebridException) {
                _state.value = State.Error(provider, e.message ?: "AllDebrid error. Try again.")
            } catch (e: IOException) {
                _state.value = State.Error(provider, "Network error talking to ${provider.label}. Check the connection and try again.")
            } catch (e: HttpException) {
                _state.value = State.Error(provider, "${provider.label} returned an error (HTTP ${e.code()}). Try again.")
            } catch (e: Exception) {
                _state.value = State.Error(provider, "Unexpected error: ${e.message}")
            }
        }
    }
}
