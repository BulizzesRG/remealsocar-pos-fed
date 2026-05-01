package com.posfab.shared.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(PosLayout.loginFormWidth)
                .fillMaxWidth()
                .padding(PosLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Text(
                text = "POS Fonda",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Iniciar sesion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(PosSpacing.sm))

            PosTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = "Usuario",
                enabled = !state.isLoading,
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            PosTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contrasena",
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            Text(
                text = "Terminal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TerminalCode.entries.forEachIndexed { index, terminal ->
                    SegmentedButton(
                        selected = state.terminal == terminal,
                        onClick = { viewModel.onTerminalChange(terminal) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TerminalCode.entries.size,
                        ),
                        enabled = !state.isLoading,
                    ) {
                        Text(terminal.name)
                    }
                }
            }

            PosPrimaryButton(
                text = "Iniciar sesion",
                onClick = viewModel::submit,
                enabled = !state.isLoading,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
