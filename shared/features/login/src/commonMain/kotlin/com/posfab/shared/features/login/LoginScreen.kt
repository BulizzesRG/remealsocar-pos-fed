package com.posfab.shared.features.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
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
import com.posfab.shared.ui.theme.PosNeutral100
import com.posfab.shared.ui.theme.PosNeutral300
import com.posfab.shared.ui.theme.PosNeutral950
import com.posfab.shared.ui.theme.PosRed500
import com.posfab.shared.ui.theme.PosSpacing
import com.posfab.shared.ui.theme.PosTheme

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // LEFT PANEL — Brand
        Box(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .background(PosNeutral950),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            ) {
                Text(
                    text = "POS",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = PosRed500,
                )
                Text(
                    text = "FONDA",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    color = PosNeutral100,
                )
                Spacer(Modifier.height(PosSpacing.xxl))
                Text(
                    text = "Sistema de Punto de Venta",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PosNeutral300,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // RIGHT PANEL — Form (light theme)
        PosTheme(darkMode = false) {
            Surface(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .width(PosLayout.loginFormWidth)
                            .padding(horizontal = PosSpacing.xxl),
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Iniciar sesión",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(PosSpacing.lg))

                        PosTextField(
                            value = state.username,
                            onValueChange = viewModel::onUsernameChange,
                            label = "Usuario",
                            enabled = !state.isLoading,
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PosTextField(
                            value = state.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = "Contraseña",
                            enabled = !state.isLoading,
                            visualTransformation = PasswordVisualTransformation(),
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        ) {
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
                        }

                        Spacer(Modifier.height(PosSpacing.sm))

                        PosPrimaryButton(
                            text = "Iniciar sesión",
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
        }
    }
}
