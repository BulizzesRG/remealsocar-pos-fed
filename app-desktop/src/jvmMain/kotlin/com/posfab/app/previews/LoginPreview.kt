package com.posfab.app.previews

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.posfab.shared.features.login.LoginScreenContent
import com.posfab.shared.features.login.LoginState
import com.posfab.shared.ui.theme.PosTheme

@Preview
@Composable
fun LoginScreenPreview_Empty() {
    PosTheme {
        LoginScreenContent(
            state = LoginState(terminalCode = "POS1"),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

@Preview
@Composable
fun LoginScreenPreview_WithError() {
    PosTheme {
        LoginScreenContent(
            state = LoginState(
                terminalCode = "POS1",
                username = "admin",
                error = "Usuario o contrasena incorrectos.",
            ),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

@Preview
@Composable
fun LoginScreenPreview_Loading() {
    PosTheme {
        LoginScreenContent(
            state = LoginState(
                terminalCode = "POS1",
                username = "admin",
                password = "secret",
                isLoading = true,
            ),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
