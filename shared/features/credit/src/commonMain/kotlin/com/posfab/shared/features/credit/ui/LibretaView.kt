package com.posfab.shared.features.credit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.credit.domain.CustomerMatch
import com.posfab.shared.features.credit.domain.LoadedCustomer
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

private enum class LibretaField { NAME, NICKNAME, PHONE, AMOUNT, SINCE }

@Composable
internal fun LibretaView(libreta: LibretaState, viewModel: CreditViewModel) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf<LibretaField?>(null) }
    val nameFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    val selected = libreta.form.selected

    LaunchedEffect(libreta.focusRequest) {
        runCatching { if (selected == null) nameFocus.requestFocus() else amountFocus.requestFocus() }
    }

    val trackFocus: (LibretaField) -> Modifier = { field ->
        Modifier.onFocusChanged { state ->
            if (state.isFocused) {
                focused = field
            } else if (focused == field) {
                focused = null
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                when {
                    event.isEnterUp() -> {
                        when (focused) {
                            LibretaField.NAME, LibretaField.NICKNAME, LibretaField.PHONE ->
                                focusManager.moveFocus(FocusDirection.Next)
                            else -> viewModel.saveLibreta()
                        }
                        true
                    }
                    event.isEscapeUp() -> {
                        viewModel.onLibretaEscape()
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (maxWidth >= TWO_COLUMN_MIN_WIDTH) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.xl),
            ) {
                Column(
                    modifier = Modifier.weight(FORM_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
                ) {
                    LibretaForm(libreta, viewModel, nameFocus, amountFocus, trackFocus)
                }
                Column(
                    modifier = Modifier.weight(LOADED_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                ) {
                    LoadedToday(libreta, viewModel)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            ) {
                LibretaForm(libreta, viewModel, nameFocus, amountFocus, trackFocus)
                Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    LoadedToday(libreta, viewModel)
                }
            }
        }
    }
}

@Composable
private fun LibretaForm(
    libreta: LibretaState,
    viewModel: CreditViewModel,
    nameFocus: FocusRequester,
    amountFocus: FocusRequester,
    trackFocus: (LibretaField) -> Modifier,
) {
    val form = libreta.form
    val selected = form.selected
    val editable = !form.isSaving

    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        Text(CreditStrings.LOAD_LIBRETA, style = MaterialTheme.typography.headlineSmall)
        Text(
            CreditStrings.LIBRETA_HELP,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    libreta.errorMessage?.let { PosErrorBanner(it) }
    libreta.savedNotice?.let { notice ->
        PosNoticeRow(
            notice = notice,
            errorMessage = null,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    PosSectionCard {
        if (selected != null) {
            SelectedCustomerHeader(
                customer = selected,
                canChange = editable,
                onChange = viewModel::clearSelection,
            )
        } else {
            PosTextField(
                value = form.name,
                onValueChange = viewModel::onLibretaName,
                label = CreditStrings.NAME_LABEL,
                enabled = editable,
                trailingIcon = if (libreta.isSearching) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).then(trackFocus(LibretaField.NAME)),
            )
            if (libreta.suggestions.isNotEmpty()) {
                Text(
                    CreditStrings.SUGGESTIONS_HINT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            libreta.suggestions.forEach { match ->
                SuggestionRow(
                    customer = match,
                    enabled = editable,
                    onClick = { viewModel.pickSuggestion(match.id) },
                )
            }
            PosTextField(
                value = form.nickname,
                onValueChange = viewModel::onLibretaNickname,
                label = CreditStrings.NICKNAME_LABEL,
                enabled = editable,
                modifier = Modifier.fillMaxWidth().then(trackFocus(LibretaField.NICKNAME)),
            )
            PosTextField(
                value = form.phone,
                onValueChange = viewModel::onLibretaPhone,
                label = CreditStrings.PHONE_LABEL,
                enabled = editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().then(trackFocus(LibretaField.PHONE)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosTextField(
                value = form.amount,
                onValueChange = viewModel::onLibretaAmount,
                label = CreditStrings.OWED_FIELD_LABEL,
                enabled = editable,
                leadingIcon = { Text(CreditStrings.PESO_SIGN) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).focusRequester(amountFocus).then(trackFocus(LibretaField.AMOUNT)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                var sinceField by remember {
                    mutableStateOf(TextFieldValue(form.owedSince, TextRange(form.owedSince.length)))
                }
                if (sinceField.text != form.owedSince) {
                    sinceField = TextFieldValue(form.owedSince, TextRange(form.owedSince.length))
                }
                PosTextField(
                    value = sinceField,
                    onValueChange = {
                        sinceField = it
                        viewModel.onLibretaOwedSince(it.text)
                    },
                    label = CreditStrings.SINCE_LABEL,
                    enabled = editable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().then(trackFocus(LibretaField.SINCE)),
                )
                Text(
                    CreditStrings.SINCE_EXAMPLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSecondaryButton(
            text = CreditStrings.FINISH,
            onClick = viewModel::closeLibreta,
            enabled = editable,
            modifier = Modifier.focusProperties { canFocus = false },
        )
        PosPrimaryButton(
            text = CreditStrings.SAVE_NEXT,
            onClick = viewModel::saveLibreta,
            isLoading = form.isSaving,
            modifier = Modifier.weight(1f).focusProperties { canFocus = false },
        )
    }
}

@Composable
private fun LoadedToday(libreta: LibretaState, viewModel: CreditViewModel) {
    if (libreta.loadedToday.isEmpty()) return
    Text(
        CreditStrings.loadedToday(libreta.loadedToday.size),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    libreta.loadedToday.forEach { loaded ->
        LoadedRow(
            loaded = loaded,
            enabled = !libreta.form.isSaving,
            onCorrect = { viewModel.correct(loaded.customerId) },
        )
    }
}

@Composable
private fun SelectedCustomerHeader(customer: CustomerMatch, canChange: Boolean, onChange: () -> Unit) {
    val hasDebt = customer.balance > 0.0
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
        Text(
            CreditStrings.EXISTING_CUSTOMER_HINT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    CreditStrings.withPhone(customer.displayName, customer.phone),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(CreditStrings.SEPARATOR.trim(), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (hasDebt) CreditStrings.owes(customer.balance) else CreditStrings.NO_DEBT,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (hasDebt) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasDebt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            PosSecondaryButton(
                text = CreditStrings.CHANGE_CUSTOMER,
                onClick = onChange,
                enabled = canChange,
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }
    }
}

@Composable
private fun SuggestionRow(customer: CustomerMatch, enabled: Boolean, onClick: () -> Unit) {
    val hasDebt = customer.balance > 0.0
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PosLayout.touchTarget)
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    customer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                customer.phone?.let { phone ->
                    Text(
                        phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                if (hasDebt) CreditStrings.owes(customer.balance) else CreditStrings.NO_DEBT,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (hasDebt) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasDebt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadedRow(loaded: LoadedCustomer, enabled: Boolean, onCorrect: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                loaded.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                CreditStrings.money(loaded.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PosSecondaryButton(
                text = CreditStrings.CORRECT,
                onClick = onCorrect,
                enabled = enabled,
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }
    }
}

private fun KeyEvent.isEnterUp(): Boolean =
    type == KeyEventType.KeyUp && (key == Key.Enter || key == Key.NumPadEnter)

private fun KeyEvent.isEscapeUp(): Boolean = type == KeyEventType.KeyUp && key == Key.Escape

private val TWO_COLUMN_MIN_WIDTH = 1000.dp
private const val FORM_WEIGHT = 0.6f
private const val LOADED_WEIGHT = 0.4f
