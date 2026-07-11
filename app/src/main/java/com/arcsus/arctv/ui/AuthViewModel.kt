package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.AuthExpiredException
import com.arcsus.arctv.data.RdRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class AuthViewModel(private val repository: RdRepository) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class CodeReady(
            val userCode: String,
            val verificationUrl: String,
        ) : State

        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    init {
        start()
    }

    fun start() {
        viewModelScope.launch {
            _state.value = State.Loading
            try {
                val device = repository.startDeviceAuth()
                _state.value = State.CodeReady(device.userCode, device.verificationUrl)
                // Suspends until authorised; once tokens are saved the main
                // screen takes over via TokenStore.isAuthorized.
                repository.pollForTokens(device)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthExpiredException) {
                _state.value = State.Error("The code expired. Get a new one and try again.")
            } catch (e: IOException) {
                _state.value = State.Error("Network error talking to Real-Debrid. Check the connection and try again.")
            } catch (e: HttpException) {
                _state.value = State.Error("Real-Debrid returned an error (HTTP ${e.code()}). Try again.")
            } catch (e: Exception) {
                _state.value = State.Error("Unexpected error: ${e.message}")
            }
        }
    }
}
