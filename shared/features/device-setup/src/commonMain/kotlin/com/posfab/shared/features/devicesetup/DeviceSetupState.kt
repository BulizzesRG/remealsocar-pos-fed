package com.posfab.shared.features.devicesetup

import com.posfab.shared.core.model.DeviceRegistration

data class DeviceSetupState(
    val adminUsername: String = "",
    val adminPassword: String = "",
    val selectedTerminalCode: String? = null,
    val terminalOptions: List<String> = listOf("POS1", "POS2"),
    val isConfirmingRegistration: Boolean = false,
    val isRegistering: Boolean = false,
    val error: String? = null,
    val registeredDevice: DeviceRegistration? = null,
)
