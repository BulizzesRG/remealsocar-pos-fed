package com.posfab.app.previews

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.posfab.shared.core.model.DeviceRegistration
import com.posfab.shared.features.devicesetup.DeviceSetupScreenContent
import com.posfab.shared.features.devicesetup.DeviceSetupState
import com.posfab.shared.ui.theme.PosTheme

@Preview
@Composable
fun DeviceSetupPreview_Empty() {
    PosTheme {
        DeviceSetupScreenContent(
            state = DeviceSetupState(),
            onAdminUsernameChange = {},
            onAdminPasswordChange = {},
            onTerminalSelect = {},
            onSubmit = {},
            onEditRegistration = {},
            onConfirmAndRegister = {},
            onConfirmAndContinue = {},
        )
    }
}

@Preview
@Composable
fun DeviceSetupPreview_WithError() {
    PosTheme {
        DeviceSetupScreenContent(
            state = DeviceSetupState(
                adminUsername = "admin",
                selectedTerminalCode = "POS1",
                error = "Credenciales de administrador incorrectas.",
            ),
            onAdminUsernameChange = {},
            onAdminPasswordChange = {},
            onTerminalSelect = {},
            onSubmit = {},
            onEditRegistration = {},
            onConfirmAndRegister = {},
            onConfirmAndContinue = {},
        )
    }
}

@Preview
@Composable
fun DeviceSetupPreview_Confirming() {
    PosTheme {
        DeviceSetupScreenContent(
            state = DeviceSetupState(
                adminUsername = "admin",
                adminPassword = "secret",
                selectedTerminalCode = "POS1",
                isConfirmingRegistration = true,
            ),
            onAdminUsernameChange = {},
            onAdminPasswordChange = {},
            onTerminalSelect = {},
            onSubmit = {},
            onEditRegistration = {},
            onConfirmAndRegister = {},
            onConfirmAndContinue = {},
        )
    }
}

@Preview
@Composable
fun DeviceSetupPreview_Success() {
    PosTheme {
        DeviceSetupScreenContent(
            state = DeviceSetupState(
                registeredDevice = DeviceRegistration(
                    deviceToken = "token",
                    terminalId = 1L,
                    terminalCode = "POS1",
                    terminalType = "POS",
                    businessUnitId = 1L,
                ),
            ),
            onAdminUsernameChange = {},
            onAdminPasswordChange = {},
            onTerminalSelect = {},
            onSubmit = {},
            onEditRegistration = {},
            onConfirmAndRegister = {},
            onConfirmAndContinue = {},
        )
    }
}
