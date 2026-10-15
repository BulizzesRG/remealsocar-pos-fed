package com.posfab.shared.features.login

import com.posfab.shared.auth.domain.CajaAccess
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.usecase.LoginUseCase
import com.posfab.shared.auth.usecase.LogoutUseCase
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.DeviceRegistration
import com.posfab.shared.core.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val deviceRegistration: DeviceRegistration,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(LoginState(terminalCode = deviceRegistration.terminalCode))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _loggedIn = MutableSharedFlow<UserSession>()
    val loggedIn = _loggedIn.asSharedFlow()

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value)
    }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            _state.value = snapshot.copy(error = LoginStrings.ERROR_EMPTY_CREDS)
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = loginUseCase(snapshot.username.trim(), snapshot.password, deviceRegistration.terminalCode)) {
                is AppResult.Success -> onLoginSucceeded(result.value)
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(isLoading = false, error = LoginErrorText.from(result.error))
                }
            }
        }
    }

    private suspend fun onLoginSucceeded(session: UserSession) {
        if (CajaAccess.allows(session.user)) {
            _state.value = _state.value.copy(isLoading = false, error = null)
            _loggedIn.emit(session)
        } else {
            _state.value = _state.value.copy(isLoading = false, error = LoginStrings.ERROR_NO_CAJA_ACCESS)
            runCatching { logoutUseCase() }
        }
    }
}
