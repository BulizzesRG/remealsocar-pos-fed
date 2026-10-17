package com.posfab.shared.features.credit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosLabeledSwitch
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosSpacing

private enum class EditField { NAME, NICKNAME, PHONE, ADDRESS }

@Composable
internal fun CustomerEditView(edit: CustomerEditState, viewModel: CreditViewModel) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf<EditField?>(null) }
    val nameFocus = remember { FocusRequester() }
    val form = edit.form
    val editable = !edit.isSaving

    LaunchedEffect(edit.focusRequest) {
        runCatching { nameFocus.requestFocus() }
    }

    val trackFocus: (EditField) -> Modifier = { field ->
        Modifier.onFocusChanged { state ->
            if (state.isFocused) {
                focused = field
            } else if (focused == field) {
                focused = null
            }
        }
    }

    PosSectionCard(
        title = CreditStrings.EDIT_TITLE,
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                when {
                    event.isEnterUp() -> when (focused) {
                        EditField.NAME, EditField.NICKNAME, EditField.PHONE -> {
                            focusManager.moveFocus(FocusDirection.Next)
                            true
                        }
                        EditField.ADDRESS -> {
                            viewModel.saveEdit()
                            true
                        }
                        null -> false
                    }
                    event.isEscapeUp() -> {
                        viewModel.onEditEscape()
                        true
                    }
                    else -> false
                }
            },
    ) {
        edit.errorMessage?.let { PosErrorBanner(it) }
        edit.code?.takeIf { it.isNotBlank() }?.let { code ->
            Text(
                CreditStrings.customerNumber(code),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PosTextField(
            value = form.name,
            onValueChange = viewModel::onEditName,
            label = CreditStrings.NAME_LABEL,
            enabled = editable,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).then(trackFocus(EditField.NAME)),
        )
        PosTextField(
            value = form.nickname,
            onValueChange = viewModel::onEditNickname,
            label = CreditStrings.NICKNAME_LABEL,
            enabled = editable,
            modifier = Modifier.fillMaxWidth().then(trackFocus(EditField.NICKNAME)),
        )
        PosTextField(
            value = form.phone,
            onValueChange = viewModel::onEditPhone,
            label = CreditStrings.PHONE_LABEL,
            enabled = editable,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().then(trackFocus(EditField.PHONE)),
        )
        PosTextField(
            value = form.address,
            onValueChange = viewModel::onEditAddress,
            label = CreditStrings.ADDRESS_LABEL,
            enabled = editable,
            modifier = Modifier.fillMaxWidth().then(trackFocus(EditField.ADDRESS)),
        )
        PosLabeledSwitch(
            label = CreditStrings.ACTIVE_LABEL,
            description = CreditStrings.ACTIVE_HINT,
            checked = form.active,
            onCheckedChange = { if (editable) viewModel.onEditActive(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = CreditStrings.CANCEL,
                onClick = viewModel::cancelEdit,
                enabled = editable,
                modifier = Modifier.focusProperties { canFocus = false },
            )
            PosPrimaryButton(
                text = CreditStrings.SAVE,
                onClick = viewModel::saveEdit,
                isLoading = edit.isSaving,
                modifier = Modifier.weight(1f).focusProperties { canFocus = false },
            )
        }
    }
}

@Composable
internal fun CustomerEditConfirmDialog(edit: CustomerEditState, customerName: String, viewModel: CreditViewModel) {
    when (edit.confirm) {
        CustomerEditConfirm.SAVE -> PosConfirmDialog(
            title = CreditStrings.saveTitle(customerName),
            message = CreditStrings.SAVE_MESSAGE,
            confirmLabel = CreditStrings.SAVE,
            cancelLabel = CreditStrings.KEEP_EDITING,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::dismissEditConfirm,
        )
        CustomerEditConfirm.DEACTIVATE -> PosConfirmDialog(
            title = CreditStrings.deactivateTitle(customerName),
            message = CreditStrings.DEACTIVATE_MESSAGE,
            confirmLabel = CreditStrings.DEACTIVATE_CONFIRM,
            cancelLabel = CreditStrings.KEEP_EDITING,
            isDestructive = true,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::dismissEditConfirm,
        )
        CustomerEditConfirm.DISCARD -> PosConfirmDialog(
            title = CreditStrings.DISCARD_TITLE,
            message = CreditStrings.DISCARD_MESSAGE,
            confirmLabel = CreditStrings.DISCARD_CONFIRM,
            cancelLabel = CreditStrings.KEEP_EDITING,
            isDestructive = true,
            onConfirm = viewModel::confirmDiscard,
            onDismiss = viewModel::dismissEditConfirm,
        )
        null -> Unit
    }
}

private fun KeyEvent.isEnterUp(): Boolean =
    type == KeyEventType.KeyUp && (key == Key.Enter || key == Key.NumPadEnter)

private fun KeyEvent.isEscapeUp(): Boolean = type == KeyEventType.KeyUp && key == Key.Escape
