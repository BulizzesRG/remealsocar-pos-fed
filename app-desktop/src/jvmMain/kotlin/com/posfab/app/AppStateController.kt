package com.posfab.app

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.auth.usecase.RestoreSessionUseCase
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.device.DeviceStorage
import com.posfab.shared.core.model.DeviceRegistration
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.device.DeviceApi
import com.posfab.shared.network.device.DeviceVerifyRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Loading : AppScreen
    data object DeviceSetup : AppScreen
    data object Login : AppScreen
    data class Shell(val session: UserSession) : AppScreen
}

class AppStateController(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val sessionManager: SessionManager,
    private val deviceStorage: DeviceStorage,
    private val deviceApi: DeviceApi,
) : BaseViewModel() {
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _deviceRegistration = MutableStateFlow<DeviceRegistration?>(null)
    val deviceRegistration: StateFlow<DeviceRegistration?> = _deviceRegistration.asStateFlow()

    init {
        observeSession()
        scope.launch { initDevice() }
    }

    fun onLoggedIn(session: UserSession) {
        _screen.value = AppScreen.Shell(session)
    }

    fun onLoggedOut() {
        _screen.value = AppScreen.Login
    }

    fun onDeviceRegistered(registration: DeviceRegistration) {
        _deviceRegistration.value = registration
        _screen.value = AppScreen.Login
    }

    private suspend fun initDevice() {
        val stored = deviceStorage.load()
        if (stored == null) {
            _screen.value = AppScreen.DeviceSetup
            return
        }

        val verifyResult = deviceApi.verify(DeviceVerifyRequestDto(deviceToken = stored.deviceToken))
        when (verifyResult) {
            is AppResult.Success -> {
                val response = verifyResult.value
                val updated = stored.copy(deviceToken = response.deviceToken)
                deviceStorage.save(updated)
                _deviceRegistration.value = updated
                restoreSession()
            }
            is AppResult.Failure -> {
                when (verifyResult.error) {
                    is AppError.Unauthorized, is AppError.Validation -> {
                        deviceStorage.clear()
                        _screen.value = AppScreen.DeviceSetup
                    }
                    else -> {
                        // Network error — proceed with stored device, try session restore
                        _deviceRegistration.value = stored
                        restoreSession()
                    }
                }
            }
        }
    }

    private suspend fun restoreSession() {
        _screen.value = when (val restored = restoreSessionUseCase()) {
            is AppResult.Success -> restored.value?.let { AppScreen.Shell(it) } ?: AppScreen.Login
            is AppResult.Failure -> AppScreen.Login
        }
    }

    private fun observeSession() {
        scope.launch {
            sessionManager.session.collect { session ->
                if (session == null && _screen.value is AppScreen.Shell) {
                    _screen.value = AppScreen.Login
                }
            }
        }
    }
}
