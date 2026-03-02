package com.posfab.shared.features.login

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.result.AppResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
) : BaseViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _loggedIn = MutableSharedFlow<UserSession>()
    val loggedIn = _loggedIn.asSharedFlow()

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value)
    }

    fun onTerminalChange(value: TerminalCode) {
        _state.value = _state.value.copy(terminal = value)
    }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            _state.value = snapshot.copy(error = "Username and password are required")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = loginUseCase(snapshot.username.trim(), snapshot.password, snapshot.terminal)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false, error = null)
                    _loggedIn.emit(result.value)
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.error.toString())
                }
            }
        }
    }
}
