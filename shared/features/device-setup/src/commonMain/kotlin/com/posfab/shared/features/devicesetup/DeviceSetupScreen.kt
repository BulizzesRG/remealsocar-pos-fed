package com.posfab.shared.features.devicesetup

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.unit.dp
import com.posfab.shared.core.model.terminalDisplayName
import com.posfab.shared.ui.components.PosBrandMark
import com.posfab.shared.ui.components.PosBrandPanel
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosAmberPrimary
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosNeutral900
import com.posfab.shared.ui.theme.PosSpacing
import com.posfab.shared.ui.theme.PosTextSecondary

@Composable
fun DeviceSetupScreen(viewModel: DeviceSetupViewModel) {
    val state by viewModel.state.collectAsState()
    DeviceSetupScreenContent(
        state = state,
        onAdminUsernameChange = viewModel::onAdminUsernameChange,
        onAdminPasswordChange = viewModel::onAdminPasswordChange,
        onTerminalSelect = viewModel::onTerminalSelect,
        onSubmit = viewModel::submit,
        onEditRegistration = viewModel::editRegistration,
        onConfirmAndRegister = viewModel::confirmAndRegister,
        onConfirmAndContinue = viewModel::confirmAndContinue,
    )
}

@Composable
fun DeviceSetupScreenContent(
    state: DeviceSetupState,
    onAdminUsernameChange: (String) -> Unit,
    onAdminPasswordChange: (String) -> Unit,
    onTerminalSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onEditRegistration: () -> Unit,
    onConfirmAndRegister: () -> Unit,
    onConfirmAndContinue: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val brandWeight = if (maxWidth >= 1200.dp) 0.35f else 0.42f

        Row(modifier = Modifier.fillMaxSize()) {
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
                        text = "Configuración inicial",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PosTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f - brandWeight)
                    .fillMaxHeight(),
                color = PosNeutral900,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = PosSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.registeredDevice != null -> DeviceSetupSuccess(
                            state = state,
                            onContinue = onConfirmAndContinue,
                        )
                        state.isConfirmingRegistration -> DeviceSetupConfirmation(
                            state = state,
                            onBack = onEditRegistration,
                            onConfirm = onConfirmAndRegister,
                        )
                        else -> DeviceSetupForm(
                            state = state,
                            onAdminUsernameChange = onAdminUsernameChange,
                            onAdminPasswordChange = onAdminPasswordChange,
                            onTerminalSelect = onTerminalSelect,
                            onSubmit = onSubmit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSetupForm(
    state: DeviceSetupState,
    onAdminUsernameChange: (String) -> Unit,
    onAdminPasswordChange: (String) -> Unit,
    onTerminalSelect: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(PosLayout.loginFormWidth)
            .padding(horizontal = PosSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = DeviceSetupStrings.TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = DeviceSetupStrings.SUBTITLE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(PosSpacing.lg))

        PosTextField(
            value = state.adminUsername,
            onValueChange = onAdminUsernameChange,
            label = DeviceSetupStrings.FIELD_ADMIN_USER,
            enabled = !state.isRegistering,
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        PosTextField(
            value = state.adminPassword,
            onValueChange = onAdminPasswordChange,
            label = DeviceSetupStrings.FIELD_ADMIN_PASS,
            enabled = !state.isRegistering,
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
                text = DeviceSetupStrings.TERMINAL_LABEL,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                state.terminalOptions.forEachIndexed { index, code ->
                    SegmentedButton(
                        selected = state.selectedTerminalCode == code,
                        onClick = { onTerminalSelect(code) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = state.terminalOptions.size,
                        ),
                        enabled = !state.isRegistering,
                    ) {
                        Text(terminalDisplayName(code))
                    }
                }
            }
            Text(
                text = DeviceSetupStrings.TERMINAL_HELP,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(PosSpacing.sm))

        state.error?.let { error ->
            PosErrorBanner(error = error)
        }

        PosPrimaryButton(
            text = DeviceSetupStrings.REVIEW_SUBMIT,
            onClick = onSubmit,
            enabled = !state.isRegistering,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeviceSetupConfirmation(
    state: DeviceSetupState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(PosLayout.loginFormWidth)
            .padding(horizontal = PosSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = DeviceSetupStrings.CONFIRM_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = DeviceSetupStrings.CONFIRM_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        PosSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(terminalDisplayName(state.selectedTerminalCode.orEmpty()))
        }

        state.error?.let { error ->
            PosErrorBanner(error = error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            PosSecondaryButton(
                text = DeviceSetupStrings.CONFIRM_BACK,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            PosPrimaryButton(
                text = DeviceSetupStrings.CONFIRM_REGISTER,
                onClick = onConfirm,
                isLoading = state.isRegistering,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DeviceSetupSuccess(
    state: DeviceSetupState,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(PosLayout.loginFormWidth)
            .padding(horizontal = PosSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = DeviceSetupStrings.SUCCESS_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = terminalDisplayName(state.registeredDevice?.terminalCode.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PosSpacing.lg))
        PosPrimaryButton(
            text = DeviceSetupStrings.SUCCESS_CONTINUE,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
