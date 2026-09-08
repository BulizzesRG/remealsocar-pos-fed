package com.posfab.shared.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.components.PosBrandMark
import com.posfab.shared.ui.components.PosBrandPanel
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosAmberPrimary
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosNeutral900
import com.posfab.shared.ui.theme.PosSpacing
import com.posfab.shared.ui.theme.PosTextSecondary

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()
    LoginScreenContent(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun LoginScreenContent(
    state: LoginState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val brandWeight = if (maxWidth >= 1200.dp) 0.35f else 0.42f

        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT PANEL — Brand
            PosBrandPanel(
                modifier = Modifier
                    .weight(brandWeight)
                    .fillMaxHeight(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                ) {
                    PosBrandMark()
                    Spacer(Modifier.height(PosSpacing.lg))
                    Text(
                        text = "POS FONDA",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = PosAmberPrimary,
                    )
                    Text(
                        text = "Sistema de Punto de Venta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PosTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(PosSpacing.xxl))
                    Text(
                        text = "${LoginStrings.TERMINAL_LABEL}: ${state.terminalCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PosTextSecondary,
                    )
                }
            }

            // RIGHT PANEL — Form
            Surface(
                modifier = Modifier
                    .weight(1f - brandWeight)
                    .fillMaxHeight(),
                color = PosNeutral900,
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
                            text = LoginStrings.TITLE,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = LoginStrings.SUBTITLE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(PosSpacing.lg))

                        PosTextField(
                            value = state.username,
                            onValueChange = onUsernameChange,
                            label = LoginStrings.FIELD_USERNAME,
                            enabled = !state.isLoading,
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PosTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = LoginStrings.FIELD_PASSWORD,
                            enabled = !state.isLoading,
                            visualTransformation = PasswordVisualTransformation(),
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(PosSpacing.sm))

                        state.error?.let { error ->
                            PosErrorBanner(error = error)
                        }

                        PosPrimaryButton(
                            text = LoginStrings.SUBMIT,
                            onClick = onSubmit,
                            enabled = !state.isLoading,
                            isLoading = state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
