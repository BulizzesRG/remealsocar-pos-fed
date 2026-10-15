package com.posfab.shared.features.sale.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosDestructiveButton
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun CashierSaleScreen(viewModel: CashierSaleViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    when (val phase = state.phase) {
        SalePhase.Loading -> LoadingView()
        SalePhase.CashClosed -> CashClosedView(onOpenDay = viewModel.onNavigateToCash, onRetry = viewModel::retry)
        is SalePhase.CashUnavailable -> CashUnavailableView(message = phase.message, onRetry = viewModel::retry)
        SalePhase.Selling, SalePhase.Charging, SalePhase.Done -> SellingView(state = state, viewModel = viewModel)
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            CircularProgressIndicator()
            Text(SaleStrings.LOADING, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CashClosedView(onOpenDay: () -> Unit, onRetry: () -> Unit) {
    CenteredCard {
        Icon(
            Icons.Filled.Payments,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(SaleStrings.CASH_CLOSED_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(
            SaleStrings.CASH_CLOSED_BODY,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
            PosSecondaryButton(text = SaleStrings.RETRY, onClick = onRetry)
            PosPrimaryButton(text = SaleStrings.OPEN_DAY, onClick = onOpenDay, icon = Icons.Filled.Payments)
        }
    }
}

@Composable
private fun CashUnavailableView(message: String, onRetry: () -> Unit) {
    CenteredCard {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        PosSecondaryButton(text = SaleStrings.RETRY, onClick = onRetry)
    }
}

@Composable
private fun CenteredCard(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(460.dp),
        ) {
            Column(
                modifier = Modifier.padding(PosSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SellingView(state: CashierSaleState, viewModel: CashierSaleViewModel) {
    val searchFocus = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    var paidFocused by remember { mutableStateOf(false) }
    val focusSearch: () -> Unit = { runCatching { searchFocus.requestFocus() } }

    LaunchedEffect(state.phase) {
        if (state.phase != SalePhase.Charging) paidFocused = false
        if (state.phase == SalePhase.Selling || state.phase == SalePhase.Done) focusSearch()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!event.isEnterUp() || state.confirm != null) return@onPreviewKeyEvent false
                when {
                    state.phase == SalePhase.Charging && paidFocused -> {
                        viewModel.confirmCharge()
                        true
                    }
                    searchFocused && (state.phase == SalePhase.Selling || state.phase == SalePhase.Done) -> {
                        viewModel.onSubmit()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(PosLayout.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                AccountStrip(
                    accounts = state.accounts,
                    activeDraftId = state.activeDraftId,
                    canOpen = state.canOpenAccount,
                    enabled = state.phase != SalePhase.Charging && state.confirm == null,
                    onSelect = { id ->
                        viewModel.selectAccount(id)
                        focusSearch()
                    },
                    onNew = {
                        viewModel.openAccount()
                        focusSearch()
                    },
                )
                PosNoticeRow(notice = state.notice, errorMessage = null)
                if (state.phase != SalePhase.Charging) {
                    state.errorMessage?.let { PosErrorBanner(error = it) }
                }
                val result = state.lastResult
                if (state.phase == SalePhase.Done && result != null) {
                    DonePanel(
                        result = result,
                        onNext = {
                            viewModel.startNextSale()
                            focusSearch()
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    CartPanel(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
                }
            }
            ProductPanel(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onAdd = { product ->
                    viewModel.addProduct(product)
                    focusSearch()
                },
                searchModifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocus)
                    .onFocusChanged { searchFocused = it.hasFocus || it.isFocused },
                modifier = Modifier.width(380.dp).fillMaxHeight(),
            )
        }

        if (state.phase == SalePhase.Charging) {
            ChargeOverlay(
                state = state,
                viewModel = viewModel,
                onPaidFocusChange = { paidFocused = it },
            )
        }

        if (state.confirm == SaleConfirm.ClearCart) {
            PosConfirmDialog(
                title = SaleStrings.CLEAR_TITLE,
                message = SaleStrings.CLEAR_MESSAGE,
                confirmLabel = SaleStrings.CLEAR_CONFIRM,
                cancelLabel = SaleStrings.CLEAR_CANCEL,
                isDestructive = true,
                onConfirm = viewModel::confirmClear,
                onDismiss = viewModel::dismissConfirm,
            )
        }
    }
}

@Composable
private fun AccountStrip(
    accounts: List<SaleDraft>,
    activeDraftId: Long?,
    canOpen: Boolean,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val chipRanges = remember { mutableStateMapOf<Long, IntRange>() }
    LaunchedEffect(activeDraftId, accounts.size) {
        val range = activeDraftId?.let { chipRanges[it] } ?: return@LaunchedEffect
        val viewportStart = scrollState.value
        val viewportEnd = viewportStart + scrollState.viewportSize
        when {
            range.first < viewportStart -> scrollState.animateScrollTo(range.first)
            range.last > viewportEnd -> scrollState.animateScrollTo(range.last - scrollState.viewportSize)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accounts.forEach { account ->
            AccountChip(
                account = account,
                isActive = account.id == activeDraftId,
                enabled = enabled,
                onClick = { onSelect(account.id) },
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    val x = coordinates.positionInParent().x.toInt()
                    chipRanges[account.id] = x..(x + coordinates.size.width)
                },
            )
        }
        PosSecondaryButton(
            text = SaleStrings.NEW_ACCOUNT,
            onClick = onNew,
            enabled = enabled && canOpen,
            icon = Icons.Filled.Add,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

@Composable
private fun AccountChip(
    account: SaleDraft,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isEmpty = account.lines.isEmpty()
    val label = if (isEmpty) {
        SaleStrings.EMPTY_ACCOUNT
    } else {
        SaleStrings.accountChip(account.totals.cashTotal, account.lines.size)
    }
    val description = if (isEmpty) {
        SaleStrings.EMPTY_ACCOUNT_DESCRIPTION
    } else {
        SaleStrings.accountDescription(account.totals.cashTotal, account.lines.size)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isActive) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (isActive) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = if (isActive) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outline),
        modifier = modifier
            .heightIn(min = PosLayout.touchTarget)
            .semantics {
                selected = isActive
                contentDescription = description
                if (isActive) stateDescription = SaleStrings.ACCOUNT_SELECTED
            }
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.heightIn(min = PosLayout.touchTarget).padding(horizontal = PosSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun CartPanel(state: CashierSaleState, viewModel: CashierSaleViewModel, modifier: Modifier = Modifier) {
    val lines = state.draft?.lines.orEmpty()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        if (lines.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    SaleStrings.EMPTY_CART,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            ) {
                items(lines, key = { it.id }) { line ->
                    CartLineRow(
                        line = line,
                        selected = line.id == state.selectedLineId,
                        onClick = { viewModel.selectLine(line.id) },
                    )
                }
            }
        }
        state.selectedLine?.let { line ->
            SelectedLineActions(
                line = line,
                qtyInput = state.qtyInput,
                viewModel = viewModel,
            )
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(SaleStrings.TOTAL, style = MaterialTheme.typography.headlineLarge)
            PosMoneyText(amount = state.cashTotal, style = MaterialTheme.typography.displaySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val footerLabel = when {
                lines.isNotEmpty() -> SaleStrings.CLEAR
                state.canCloseEmptyAccount -> SaleStrings.CLOSE_ACCOUNT
                else -> null
            }
            if (footerLabel != null) {
                TextButton(onClick = viewModel::requestClear, enabled = !state.isBusy) {
                    Text(footerLabel)
                }
            }
            PosPrimaryButton(
                text = SaleStrings.chargeLabel(state.cashTotal),
                onClick = viewModel::startCharge,
                enabled = state.canCharge,
                icon = Icons.Filled.Payments,
                modifier = Modifier.weight(1f).height(64.dp),
            )
        }
    }
}

@Composable
private fun CartLineRow(line: SaleLine, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    line.productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${SaleStrings.qtyTimes(line.qty)}  ${SaleStrings.money(line.cashUnitPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PosMoneyText(
                amount = line.lineTotal,
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun SelectedLineActions(line: SaleLine, qtyInput: String, viewModel: CashierSaleViewModel) {
    var qtyFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = viewModel::decrementQty, enabled = line.qty > 1) {
            Icon(Icons.Filled.Remove, contentDescription = SaleStrings.DECREASE)
        }
        OutlinedTextField(
            value = qtyInput,
            onValueChange = viewModel::onQtyInputChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(72.dp)
                .onFocusChanged {
                    if (qtyFocused && !it.isFocused) viewModel.applyQty()
                    qtyFocused = it.isFocused
                }
                .onPreviewKeyEvent { event ->
                    if (event.isEnterUp()) {
                        viewModel.applyQty()
                        true
                    } else {
                        false
                    }
                },
        )
        FilledTonalIconButton(onClick = viewModel::incrementQty) {
            Icon(Icons.Filled.Add, contentDescription = SaleStrings.INCREASE)
        }
        Spacer(modifier = Modifier.weight(1f))
        PosDestructiveButton(
            text = SaleStrings.REMOVE,
            onClick = viewModel::removeSelectedLine,
            icon = Icons.Filled.Delete,
        )
    }
}

@Composable
private fun DonePanel(result: CashCheckoutResult, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(460.dp),
        ) {
            Column(
                modifier = Modifier.padding(PosSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(SaleStrings.CHARGED, style = MaterialTheme.typography.headlineMedium)
                if (result.change <= 0.0) {
                    Text(SaleStrings.EXACT_PAYMENT, style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(
                        SaleStrings.CHANGE,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PosMoneyText(amount = result.change, style = MaterialTheme.typography.displayMedium)
                }
                if (result.folio.isNotBlank()) {
                    Text(
                        SaleStrings.folio(result.folio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PosPrimaryButton(
                    text = SaleStrings.NEXT,
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductPanel(
    state: CashierSaleState,
    onQueryChange: (String) -> Unit,
    onAdd: (SaleProduct) -> Unit,
    searchModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
        PosTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = SaleStrings.SEARCH_LABEL,
            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            modifier = searchModifier,
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            state.searchResults.forEach { product ->
                SearchResultRow(product = product, onClick = { onAdd(product) })
            }
            SaleKitchenKind.entries.forEach { kind ->
                val products = state.kitchenProducts.filter { it.kitchenKind == kind }
                if (products.isNotEmpty()) {
                    Text(
                        kind.label(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = PosSpacing.sm),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    ) {
                        products.forEach { product ->
                            KitchenCard(product = product, onClick = { onAdd(product) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(product: SaleProduct, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Text(
                product.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            product.cashPrice?.let {
                PosMoneyText(amount = it, style = MaterialTheme.typography.bodyLarge, color = LocalContentColor.current)
            }
        }
    }
}

@Composable
private fun KitchenCard(product: SaleProduct, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(150.dp).height(88.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(PosSpacing.sm),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            product.cashPrice?.let {
                PosMoneyText(amount = it, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChargeOverlay(
    state: CashierSaleState,
    viewModel: CashierSaleViewModel,
    onPaidFocusChange: (Boolean) -> Unit,
) {
    val amountEnabled = !state.isCheckoutInFlight && !state.isPaidLocked
    val paidFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { paidFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(520.dp),
        ) {
            Column(
                modifier = Modifier.padding(PosSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(SaleStrings.TOTAL, style = MaterialTheme.typography.headlineSmall)
                    PosMoneyText(amount = state.cashTotal, style = MaterialTheme.typography.displayMedium)
                }
                Text(SaleStrings.CHARGE_QUESTION, style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                ) {
                    PosSecondaryButton(
                        text = SaleStrings.EXACT,
                        onClick = viewModel::payExact,
                        enabled = amountEnabled,
                    )
                    SaleStrings.QUICK_BILLS.forEach { bill ->
                        PosSecondaryButton(
                            text = SaleStrings.quickBill(bill),
                            onClick = { viewModel.payWith(bill) },
                            enabled = amountEnabled,
                        )
                    }
                }
                PosTextField(
                    value = state.paidInput,
                    onValueChange = viewModel::onPaidInputChange,
                    label = SaleStrings.PAID_LABEL,
                    enabled = amountEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(paidFocus)
                        .onFocusChanged { onPaidFocusChange(it.hasFocus || it.isFocused) },
                )
                if (state.isPaidLocked) {
                    Text(
                        SaleStrings.RETRY_WITH_DONE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.change?.let { change ->
                    if (change >= 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(SaleStrings.CHANGE, style = MaterialTheme.typography.titleLarge)
                            PosMoneyText(amount = change, style = MaterialTheme.typography.displaySmall)
                        }
                    } else {
                        Text(
                            SaleStrings.missing(-change),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                state.errorMessage?.let { PosErrorBanner(error = it) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                ) {
                    PosSecondaryButton(
                        text = SaleStrings.BACK,
                        onClick = viewModel::cancelCharge,
                        enabled = !state.isCheckoutInFlight,
                        modifier = Modifier.weight(1f),
                    )
                    PosPrimaryButton(
                        text = SaleStrings.DONE_CONFIRM,
                        onClick = viewModel::confirmCharge,
                        enabled = state.canConfirmCharge,
                        isLoading = state.isCheckoutInFlight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun SaleKitchenKind.label(): String = when (this) {
    SaleKitchenKind.FONDA -> SaleStrings.KITCHEN_FONDA
    SaleKitchenKind.TORTERIA -> SaleStrings.KITCHEN_TORTERIA
}

private fun KeyEvent.isEnterUp(): Boolean =
    type == KeyEventType.KeyUp && (key == Key.Enter || key == Key.NumPadEnter)

private const val DISABLED_ALPHA = 0.5f
