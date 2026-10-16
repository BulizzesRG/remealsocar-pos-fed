package com.posfab.shared.features.sale.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.sale.domain.SaleCustomer
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
internal fun CustomerCreditPanel(
    credit: CreditState,
    enabled: Boolean,
    viewModel: CashierSaleViewModel,
    onCustomerFocusChange: (Boolean) -> Unit,
    onAbonoFocusChange: (Boolean) -> Unit,
    focusAbonoOnSelect: Boolean,
    modifier: Modifier = Modifier,
    debtorsOnly: Boolean = false,
    abonoProminent: Boolean = true,
    amounts: @Composable () -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
        val form = credit.newCustomer
        val customer = credit.customer
        when {
            form != null -> NewCustomerSection(
                form = form,
                viewModel = viewModel,
                onFocusChange = onCustomerFocusChange,
            )
            customer == null -> CustomerPicker(
                credit = credit,
                enabled = enabled,
                viewModel = viewModel,
                onFocusChange = onCustomerFocusChange,
                debtorsOnly = debtorsOnly,
            )
            else -> {
                CustomerHeader(
                    customer = customer,
                    canChange = enabled && !credit.isAbonoBusy,
                    onChange = viewModel::changeCustomer,
                )
                amounts()
            }
        }
        if (customer != null && credit.showsAbonoRow) {
            AbonoRow(
                credit = credit,
                enabled = enabled,
                viewModel = viewModel,
                onFocusChange = onAbonoFocusChange,
                requestFocus = focusAbonoOnSelect,
                customerId = customer.id,
                prominent = abonoProminent,
            )
        }
        if (credit.isAbonoLocked) {
            AbonoLockedHint()
        }
        credit.lastAbono?.let { abono ->
            PosNoticeRow(
                notice = SaleStrings.abonoRegistered(abono.amount, abono.customerName, abono.balanceAfter),
                errorMessage = null,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun NewCustomerSection(
    form: NewCustomerForm,
    viewModel: CashierSaleViewModel,
    onFocusChange: (Boolean) -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { nameFocus.requestFocus() }
    }
    DisposableEffect(Unit) {
        onDispose { onFocusChange(false) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
        Text(SaleStrings.NEW_CUSTOMER, style = MaterialTheme.typography.titleLarge)
        PosTextField(
            value = form.name,
            onValueChange = viewModel::onNewCustomerName,
            label = SaleStrings.CUSTOMER_NAME_LABEL,
            enabled = !form.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocus)
                .onFocusChanged { onFocusChange(it.hasFocus || it.isFocused) },
        )
        PosTextField(
            value = form.nickname,
            onValueChange = viewModel::onNewCustomerNickname,
            label = SaleStrings.CUSTOMER_NICKNAME_LABEL,
            enabled = !form.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.hasFocus || it.isFocused) },
        )
        PosTextField(
            value = form.phone,
            onValueChange = viewModel::onNewCustomerPhone,
            label = SaleStrings.CUSTOMER_PHONE_LABEL,
            enabled = !form.isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.hasFocus || it.isFocused) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = SaleStrings.CANCEL,
                onClick = viewModel::cancelNewCustomer,
                enabled = !form.isSaving,
                modifier = Modifier.weight(1f).focusProperties { canFocus = false },
            )
            PosPrimaryButton(
                text = SaleStrings.SAVE,
                onClick = viewModel::saveNewCustomer,
                enabled = form.name.isNotBlank(),
                isLoading = form.isSaving,
                modifier = Modifier.weight(1f).focusProperties { canFocus = false },
            )
        }
    }
}

@Composable
private fun CustomerPicker(
    credit: CreditState,
    enabled: Boolean,
    viewModel: CashierSaleViewModel,
    onFocusChange: (Boolean) -> Unit,
    debtorsOnly: Boolean,
) {
    val queryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { queryFocus.requestFocus() }
    }
    DisposableEffect(Unit) {
        onDispose { onFocusChange(false) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        PosTextField(
            value = credit.pickerQuery,
            onValueChange = viewModel::onCustomerQueryChange,
            label = SaleStrings.CUSTOMER_SEARCH_LABEL,
            enabled = enabled,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (credit.isSearching) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(queryFocus)
                .onFocusChanged { onFocusChange(it.hasFocus || it.isFocused) },
        )
        if (credit.showsPickerHint) {
            Text(
                SaleStrings.PICKER_HINT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        credit.pickerResults.forEach { customer ->
            CustomerResultRow(
                customer = customer,
                enabled = enabled,
                onClick = { viewModel.selectCustomer(customer) },
            )
        }
        if (credit.showsNoMatches) {
            Text(
                if (debtorsOnly) SaleStrings.NO_DEBTORS_FOUND else SaleStrings.NO_CUSTOMERS_FOUND,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!debtorsOnly) {
            PosSecondaryButton(
                text = SaleStrings.NEW_CUSTOMER,
                onClick = viewModel::startNewCustomer,
                enabled = enabled,
                icon = Icons.Filled.PersonAdd,
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }
    }
}

@Composable
private fun CustomerResultRow(customer: SaleCustomer, enabled: Boolean, onClick: () -> Unit) {
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
            if (customer.hasDebt) {
                Text(
                    SaleStrings.owesAmount(customer.balance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    SaleStrings.NO_DEBT,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CustomerHeader(customer: SaleCustomer, canChange: Boolean, onChange: () -> Unit) {
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
                customer.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("·", style = MaterialTheme.typography.titleLarge)
            Text(
                if (customer.hasDebt) SaleStrings.owesAmount(customer.balance) else SaleStrings.NO_DEBT,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (customer.hasDebt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        PosSecondaryButton(
            text = SaleStrings.CHANGE_CUSTOMER,
            onClick = onChange,
            enabled = canChange,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

@Composable
private fun AbonoRow(
    credit: CreditState,
    enabled: Boolean,
    viewModel: CashierSaleViewModel,
    onFocusChange: (Boolean) -> Unit,
    requestFocus: Boolean,
    customerId: Long,
    prominent: Boolean,
) {
    val abonoFocus = remember { FocusRequester() }
    val inputEnabled = enabled && !credit.isAbonoBusy
    LaunchedEffect(customerId) {
        if (requestFocus) runCatching { abonoFocus.requestFocus() }
    }
    DisposableEffect(Unit) {
        onDispose { onFocusChange(false) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosTextField(
            value = credit.abonoInput,
            onValueChange = viewModel::onAbonoInputChange,
            label = SaleStrings.ABONO_FIELD_LABEL,
            enabled = inputEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = { Text(SaleStrings.PESOS_SUFFIX) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(abonoFocus)
                .onFocusChanged { onFocusChange(it.hasFocus || it.isFocused) },
        )
        PosSecondaryButton(
            text = SaleStrings.ABONO_ALL,
            onClick = viewModel::payAllDebt,
            enabled = inputEnabled,
            modifier = Modifier.height(56.dp).focusProperties { canFocus = false },
        )
        val abonoModifier = Modifier.width(180.dp).height(56.dp).focusProperties { canFocus = false }
        if (prominent) {
            PosPrimaryButton(
                text = SaleStrings.abonoLabel(credit.abonoAmount),
                onClick = viewModel::confirmAbono,
                enabled = enabled && credit.canConfirmAbono,
                isLoading = credit.isAbonoInFlight,
                modifier = abonoModifier,
            )
        } else {
            PosSecondaryButton(
                text = if (credit.isAbonoInFlight) SaleStrings.ABONO else SaleStrings.abonoLabel(credit.abonoAmount),
                onClick = viewModel::confirmAbono,
                enabled = enabled && credit.canConfirmAbono,
                modifier = abonoModifier,
            )
        }
    }
}

@Composable
private fun AbonoLockedHint() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            SaleStrings.ABONO_LOCKED,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
