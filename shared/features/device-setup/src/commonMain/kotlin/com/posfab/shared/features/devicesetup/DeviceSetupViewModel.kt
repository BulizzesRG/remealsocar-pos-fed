package com.posfab.shared.features.devicesetup

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.device.DeviceStorage
import com.posfab.shared.core.model.DeviceRegistration
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.device.DeviceApi
import com.posfab.shared.network.device.DeviceRegisterRequestDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceSetupViewModel(
    private val deviceApi: DeviceApi,
    private val deviceStorage: DeviceStorage,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(DeviceSetupState())
    val state: StateFlow<DeviceSetupState> = _state.asStateFlow()

    private val _registrationComplete = MutableSharedFlow<DeviceRegistration>()
    val registrationComplete = _registrationComplete.asSharedFlow()

    fun onAdminUsernameChange(value: String) {
        _state.value = _state.value.copy(adminUsername = value)
    }

    fun onAdminPasswordChange(value: String) {
        _state.value = _state.value.copy(adminPassword = value)
    }

    fun onTerminalSelect(code: String) {
        _state.value = _state.value.copy(selectedTerminalCode = code)
    }

    fun submit() {
        val snapshot = _state.value
        if (!validate(snapshot)) return
        _state.value = snapshot.copy(isConfirmingRegistration = true, error = null)
    }

    fun editRegistration() {
        _state.value = _state.value.copy(isConfirmingRegistration = false)
    }

    fun confirmAndRegister() {
        val snapshot = _state.value
        if (!validate(snapshot)) return

        scope.launch {
            _state.value = _state.value.copy(isRegistering = true, error = null)

            val request = DeviceRegisterRequestDto(
                username = snapshot.adminUsername.trim(),
                password = snapshot.adminPassword,
                terminalCode = snapshot.selectedTerminalCode!!,
                deviceName = snapshot.selectedTerminalCode,
            )

            when (val result = deviceApi.register(request)) {
                is AppResult.Success -> {
                    val response = result.value
                    val registration = DeviceRegistration(
                        deviceToken = response.deviceToken,
                        terminalId = response.terminal.id,
                        terminalCode = response.terminal.code,
                        terminalType = response.terminal.terminalType,
                        businessUnitId = response.terminal.businessUnitId,
                    )
                    deviceStorage.save(registration)
                    _state.value = _state.value.copy(
                        adminPassword = "",
                        isRegistering = false,
                        isConfirmingRegistration = false,
                        registeredDevice = registration,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isRegistering = false,
                        error = DeviceSetupErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun confirmAndContinue() {
        val registration = _state.value.registeredDevice ?: return
        scope.launch { _registrationComplete.emit(registration) }
    }

    private fun validate(snapshot: DeviceSetupState): Boolean {
        if (snapshot.adminUsername.isBlank() || snapshot.adminPassword.isBlank()) {
            _state.value = snapshot.copy(error = DeviceSetupStrings.ERROR_EMPTY_CREDS)
            return false
        }
        if (snapshot.selectedTerminalCode == null) {
            _state.value = snapshot.copy(error = DeviceSetupStrings.ERROR_MISSING_TERMINAL)
            return false
        }
        return true
    }
}
