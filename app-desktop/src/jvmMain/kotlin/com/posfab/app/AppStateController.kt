package com.posfab.app

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.usecase.RestoreSessionUseCase
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Loading : AppScreen
    data object Login : AppScreen
    data class Shell(val session: UserSession) : AppScreen
}

class AppStateController(
    private val restoreSessionUseCase: RestoreSessionUseCase,
) : BaseViewModel() {
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    init {
        restoreSession()
    }

    fun onLoggedIn(session: UserSession) {
        _screen.value = AppScreen.Shell(session)
    }

    fun onLoggedOut() {
        _screen.value = AppScreen.Login
    }

    private fun restoreSession() {
        scope.launch {
            _screen.value = when (val restored = restoreSessionUseCase()) {
                is AppResult.Success -> restored.value?.let { AppScreen.Shell(it) } ?: AppScreen.Login
                is AppResult.Failure -> AppScreen.Login
            }
        }
    }
}
