package com.posfab.shared.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosTextField

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("POS Login")
        PosTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = "Username",
            enabled = !state.isLoading,
        )
        PosTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Password",
            enabled = !state.isLoading,
        )

        Text("Terminal", modifier = Modifier.padding(top = 12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalCode.entries.forEach { terminal ->
                Row(modifier = Modifier.padding(end = 12.dp)) {
                    RadioButton(
                        selected = state.terminal == terminal,
                        onClick = { viewModel.onTerminalChange(terminal) },
                        enabled = !state.isLoading,
                    )
                    Text(terminal.name, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        PosPrimaryButton(
            text = if (state.isLoading) "Signing in..." else "Sign In",
            onClick = viewModel::submit,
            enabled = !state.isLoading,
        )

        val error = state.error
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
