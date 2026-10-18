package com.posfab.shared.features.sale.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SalePriceType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.roundMoney
import com.posfab.shared.ui.components.PosChoiceRow
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosDestructiveButton
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosLazyColumn
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosScrollableColumn
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
    var customerFocused by remember { mutableStateOf(false) }
    var abonoFocused by remember { mutableStateOf(false) }
    val focusSearch: () -> Unit = { runCatching { searchFocus.requestFocus() } }

    val promptOpen = state.prompt != null
    var wasPromptOpen by remember { mutableStateOf(promptOpen) }
    val sellingOrDone = state.phase == SalePhase.Selling || state.phase == SalePhase.Done

    LaunchedEffect(state.phase, state.paymentType, state.abonoOverlayOpen) {
        if (state.phase != SalePhase.Charging || state.paymentType != SalePaymentType.CASH) paidFocused = false
        if (!state.isCustomerStepActive) {
            customerFocused = false
            abonoFocused = false
        }
        if (state.prompt == null && !state.abonoOverlayOpen && sellingOrDone) focusSearch()
    }

    LaunchedEffect(state.isDeliveryAccount) {
        if (!state.isDeliveryAccount && state.prompt == null && !state.abonoOverlayOpen && sellingOrDone) focusSearch()
    }

    LaunchedEffect(state.credit.customer?.id) {
        if (state.isCreditCharge && state.credit.customer != null) focusSearch()
    }

    LaunchedEffect(promptOpen) {
        if (wasPromptOpen && !promptOpen && !state.abonoOverlayOpen && sellingOrDone) {
            focusSearch()
        }
        wasPromptOpen = promptOpen
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.isEscapeUp()) {
                    if (state.prompt != null) {
                        viewModel.dismissPrompt()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent viewModel.dismissCreditStep()
                }
                if (!event.isEnterUp()) return@onPreviewKeyEvent false
                if (state.prompt != null) {
                    viewModel.confirmPrompt()
                    return@onPreviewKeyEvent true
                }
                if (state.confirm != null) return@onPreviewKeyEvent false
                when {
                    customerFocused && state.credit.newCustomer != null -> {
                        viewModel.saveNewCustomer()
                        true
                    }
                    customerFocused -> {
                        viewModel.submitCustomerQuery()
                        true
                    }
                    abonoFocused -> {
                        viewModel.confirmAbono()
                        true
                    }
                    state.abonoOverlayOpen -> true
                    state.phase == SalePhase.Charging &&
                        (paidFocused || state.paymentType != SalePaymentType.CASH) -> {
                        viewModel.confirmChargeFromKeyboard()
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
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                ) {
                    AccountStrip(
                        accounts = state.saleAccounts,
                        deliveries = state.deliveryAccounts,
                        activeDraftId = state.activeDraftId,
                        canOpen = state.canOpenAccount,
                        enabled = state.phase != SalePhase.Charging &&
                            state.confirm == null &&
                            state.prompt == null &&
                            !state.abonoOverlayOpen,
                        onSelect = { id ->
                            viewModel.selectAccount(id)
                            focusSearch()
                        },
                        onNew = {
                            viewModel.openAccount()
                            focusSearch()
                        },
                        onRefreshDeliveries = {
                            viewModel.refreshDeliveries()
                            focusSearch()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isDeliveryAccount) {
                        Box(
                            modifier = Modifier.width(240.dp).height(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                SaleStrings.DELIVERY_TAKEAWAY,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        PosChoiceRow(
                            options = listOf(
                                SaleChannel.TAKEAWAY to SaleStrings.CHANNEL_TAKEAWAY,
                                SaleChannel.ONSITE to SaleStrings.CHANNEL_ONSITE,
                            ),
                            selected = state.channel,
                            onSelect = viewModel::setChannel,
                            enabled = state.canSetChannel,
                            height = 48.dp,
                            modifier = Modifier.width(240.dp).focusProperties { canFocus = false },
                        )
                    }
                }
                PosNoticeRow(
                    notice = state.notice,
                    errorMessage = null,
                    modifier = Modifier.defaultMinSize(minHeight = NOTICE_MIN_HEIGHT),
                )
                if (state.phase != SalePhase.Charging && state.prompt == null && !state.abonoOverlayOpen) {
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
                onVarios = viewModel::addProduct,
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
                onCustomerFocusChange = { customerFocused = it },
                onAbonoFocusChange = { abonoFocused = it },
            )
        }

        if (state.abonoOverlayOpen) {
            AbonoOverlay(
                state = state,
                viewModel = viewModel,
                onCustomerFocusChange = { customerFocused = it },
                onAbonoFocusChange = { abonoFocused = it },
            )
        }

        state.prompt?.let { prompt ->
            LinePromptOverlay(prompt = prompt, state = state, viewModel = viewModel)
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
    deliveries: List<SaleDraft>,
    activeDraftId: Long?,
    canOpen: Boolean,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onRefreshDeliveries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipRow(
                accounts = accounts,
                activeDraftId = activeDraftId,
                enabled = enabled,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            ) {
                PosSecondaryButton(
                    text = SaleStrings.NEW_ACCOUNT,
                    onClick = onNew,
                    enabled = enabled && canOpen,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            }
            if (deliveries.isEmpty()) {
                RefreshDeliveriesButton(enabled = enabled, onClick = onRefreshDeliveries)
            }
        }
        if (deliveries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    SaleStrings.deliveriesLabel(deliveries.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow(
                    accounts = deliveries,
                    activeDraftId = activeDraftId,
                    enabled = enabled,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                RefreshDeliveriesButton(enabled = enabled, onClick = onRefreshDeliveries)
            }
        }
    }
}

@Composable
private fun ChipRow(
    accounts: List<SaleDraft>,
    activeDraftId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
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
        modifier = modifier.horizontalScroll(scrollState),
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
        trailing()
    }
}

@Composable
private fun RefreshDeliveriesButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(PosLayout.touchTarget).focusProperties { canFocus = false },
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = SaleStrings.REFRESH_DELIVERIES)
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
    val description = when {
        account.isDelivery -> SaleStrings.deliveryChip(account.customerShortName, account.totals.cashTotal)
        isEmpty -> SaleStrings.EMPTY_ACCOUNT_DESCRIPTION
        else -> SaleStrings.accountDescription(account.totals.listTotal, account.lines.size)
    }
    val container = when {
        account.isDelivery && isActive -> colors.tertiary.copy(alpha = ACTIVE_DELIVERY_ALPHA).compositeOver(colors.tertiaryContainer)
        account.isDelivery -> colors.tertiaryContainer
        isActive -> colors.primaryContainer
        else -> colors.surfaceVariant
    }
    val content = when {
        account.isDelivery -> colors.onTertiaryContainer
        isActive -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    val accent = if (account.isDelivery) colors.tertiary else colors.primary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = if (isActive) BorderStroke(2.dp, accent) else BorderStroke(1.dp, colors.outline),
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
            if (account.isDelivery) {
                Icon(Icons.Filled.DeliveryDining, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    SaleStrings.deliveryChipName(account.customerShortName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp).clearAndSetSemantics {},
                )
                Text(
                    "· ${SaleStrings.money(account.totals.cashTotal)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            } else {
                Text(
                    if (isEmpty) SaleStrings.EMPTY_ACCOUNT else SaleStrings.accountChip(account.totals.listTotal, account.lines.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun CartPanel(state: CashierSaleState, viewModel: CashierSaleViewModel, modifier: Modifier = Modifier) {
    val lines = state.draft?.lines.orEmpty()
    val delivery = state.draft?.takeIf { it.isDelivery }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        delivery?.let { DeliveryHeader(it) }
        if (lines.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    SaleStrings.EMPTY_CART,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PosLazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            ) {
                items(lines, key = { it.id }) { line ->
                    CartLineRow(
                        line = line,
                        selected = delivery == null && line.id == state.selectedLineId,
                        onClick = if (delivery == null) ({ viewModel.selectLine(line.id) }) else null,
                    )
                }
            }
        }
        state.selectedLine?.takeIf { delivery == null }?.let { line ->
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
            PosMoneyText(amount = state.cartTotal, style = MaterialTheme.typography.displaySmall)
        }
        if (delivery == null && state.cashDiscount > 0.0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    SaleStrings.IN_CASH,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    SaleStrings.money(state.cashTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val footerLabel = when {
                delivery != null -> null
                lines.isNotEmpty() -> SaleStrings.CLEAR
                state.canCloseEmptyAccount -> SaleStrings.CLOSE_ACCOUNT
                else -> null
            }
            if (footerLabel != null) {
                TextButton(
                    onClick = viewModel::requestClear,
                    enabled = !state.abonoOverlayOpen,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Text(footerLabel)
                }
            }
            if (delivery == null) {
                PosSecondaryButton(
                    text = SaleStrings.ABONO,
                    onClick = viewModel::openAbono,
                    enabled = state.canOpenAbono,
                    icon = Icons.Filled.AccountBalanceWallet,
                    modifier = Modifier.height(64.dp).focusProperties { canFocus = false },
                )
            }
            PosPrimaryButton(
                text = SaleStrings.chargeLabel(state.cartTotal),
                onClick = viewModel::startCharge,
                enabled = state.canCharge,
                icon = Icons.Filled.Payments,
                modifier = Modifier.weight(1f).height(64.dp).focusProperties { canFocus = false },
            )
        }
    }
}

@Composable
private fun DeliveryHeader(delivery: SaleDraft) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(PosSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DeliveryDining, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    SaleStrings.deliveryDone(delivery.customerName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            delivery.deliveryAddress?.let { address ->
                Text(
                    address,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                SaleStrings.DELIVERY_READ_ONLY_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onTertiaryContainer,
            )
            delivery.paysWith?.let { amount ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        SaleStrings.paysWith(amount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val change = roundMoney(amount - delivery.totals.cashTotal)
                    if (change > 0.0) {
                        Text(
                            SaleStrings.changeAmount(change),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(line: SaleLine, selected: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val readOnly = onClick == null
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = when {
            readOnly -> Color.Transparent
            selected -> colors.primaryContainer
            else -> colors.surfaceVariant
        },
        border = if (readOnly) BorderStroke(1.dp, colors.outline.copy(alpha = READ_ONLY_BORDER_ALPHA)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
                    SaleStrings.lineQty(line),
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
    if (line.priceType != SalePriceType.PIECE) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosSecondaryButton(
                text = SaleStrings.CHANGE_LINE,
                onClick = viewModel::changeSelectedLine,
                icon = Icons.Filled.Edit,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            PosDestructiveButton(
                text = SaleStrings.REMOVE,
                onClick = viewModel::removeSelectedLine,
                icon = Icons.Filled.Delete,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        return
    }
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
private fun DonePanel(result: CheckoutResult, onNext: () -> Unit, modifier: Modifier = Modifier) {
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
                val onCredit = result.paymentType == SalePaymentType.CREDIT
                if (onCredit) {
                    Text(
                        result.customerName?.let(SaleStrings::creditDoneTitle) ?: SaleStrings.CREDIT_DONE,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(SaleStrings.CHARGED, style = MaterialTheme.typography.headlineMedium)
                    if (result.isDelivery) {
                        Text(
                            SaleStrings.deliveryDone(result.deliveryCustomerName),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(SaleStrings.paymentTypeLabel(result.paymentType), style = MaterialTheme.typography.titleLarge)
                }
                PosMoneyText(amount = result.totalCharged, style = MaterialTheme.typography.headlineMedium)
                if (onCredit) {
                    result.customerBalanceAfter?.let { balance ->
                        Text(
                            SaleStrings.OWES_NOW,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PosMoneyText(amount = balance, style = MaterialTheme.typography.displayMedium)
                    }
                }
                result.change?.let { change ->
                    if (change <= 0.0) {
                        Text(SaleStrings.EXACT_PAYMENT, style = MaterialTheme.typography.titleLarge)
                    } else {
                        Text(
                            SaleStrings.CHANGE,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PosMoneyText(amount = change, style = MaterialTheme.typography.displayMedium)
                    }
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
                    modifier = Modifier.fillMaxWidth().height(56.dp).focusProperties { canFocus = false },
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
    onVarios: (SaleProduct) -> Unit,
    searchModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
        PosTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = if (state.isDeliveryAccount) SaleStrings.DELIVERY_SEARCH_DISABLED else SaleStrings.SEARCH_LABEL,
            enabled = !state.isDeliveryAccount,
            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            modifier = searchModifier,
        )
        state.variosProduct?.let { varios ->
            PosSecondaryButton(
                text = SaleStrings.VARIOS,
                onClick = { onVarios(varios) },
                icon = Icons.Filled.AttachMoney,
                modifier = Modifier.fillMaxWidth().height(48.dp).focusProperties { canFocus = false },
            )
        }
        PosScrollableColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            state.searchResults.forEach { product ->
                SearchResultRow(product = product, channel = state.channel, onClick = { onAdd(product) })
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
                            KitchenCard(product = product, channel = state.channel, onClick = { onAdd(product) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(product: SaleProduct, channel: SaleChannel, onClick: () -> Unit) {
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
            ProductPriceLabel(
                product = product,
                channel = channel,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun KitchenCard(product: SaleProduct, channel: SaleChannel, onClick: () -> Unit) {
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
            ProductPriceLabel(
                product = product,
                channel = channel,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProductPriceLabel(product: SaleProduct, channel: SaleChannel, style: TextStyle) {
    when (product.priceType) {
        SalePriceType.OPEN -> Text(
            SaleStrings.OPEN_PRICE,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SalePriceType.WEIGHT -> product.shownPrice(channel)?.let {
            Text(
                SaleStrings.perKilo(it),
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SalePriceType.PIECE -> product.shownPrice(channel)?.let {
            PosMoneyText(amount = it, style = style, color = LocalContentColor.current)
        }
    }
}

@Composable
private fun ModalScrim(width: Dp = 520.dp, content: @Composable () -> Unit) {
    BoxWithConstraints(
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
            modifier = Modifier.width(width).heightIn(max = maxHeight - MODAL_VERTICAL_MARGIN),
        ) {
            PosScrollableColumn(
                contentPadding = PaddingValues(PosSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinePromptOverlay(prompt: SalePrompt, state: CashierSaleState, viewModel: CashierSaleViewModel) {
    val promptFocus = remember { FocusRequester() }
    val weight = prompt as? SalePrompt.Weight
    val mode = weight?.mode
    val product = prompt.product
    val price = product.shownPrice(state.channel)
    LaunchedEffect(mode) {
        runCatching { promptFocus.requestFocus() }
    }
    ModalScrim {
        Text(
            if (weight == null) SaleStrings.PROMPT_OPEN_TITLE else SaleStrings.PROMPT_WEIGHT_TITLE,
            style = MaterialTheme.typography.headlineSmall,
        )
        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (weight != null) {
                Text(
                    SaleStrings.perKilo(price ?: 0.0),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (weight != null) {
            PosChoiceRow(
                options = listOf(
                    WeightMode.KILOS to SaleStrings.KILOS_LABEL,
                    WeightMode.PESOS to SaleStrings.PESOS_LABEL,
                ),
                selected = weight.mode,
                onSelect = viewModel::setWeightMode,
                height = 48.dp,
                modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
            )
        }
        if (mode != WeightMode.PESOS) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                if (weight == null) {
                    SaleStrings.QUICK_AMOUNTS.forEach { amount ->
                        PosSecondaryButton(
                            text = SaleStrings.quickBill(amount),
                            onClick = { viewModel.pickQuickAmount(amount) },
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                    }
                } else {
                    SaleStrings.QUICK_WEIGHTS.forEach { (kilos, label) ->
                        PosSecondaryButton(
                            text = label,
                            onClick = { viewModel.pickWeight(kilos) },
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                    }
                }
            }
        }
        PosTextField(
            value = state.promptInput,
            onValueChange = viewModel::onPromptInputChange,
            label = when (mode) {
                null -> SaleStrings.AMOUNT_LABEL
                WeightMode.KILOS -> SaleStrings.KILOS_LABEL
                WeightMode.PESOS -> SaleStrings.PESOS_LABEL
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = { Text(if (mode == WeightMode.KILOS) SaleStrings.KG_SUFFIX else SaleStrings.PESOS_SUFFIX) },
            modifier = Modifier.fillMaxWidth().focusRequester(promptFocus),
        )
        state.errorMessage?.let { PosErrorBanner(error = it) }
        promptPreview(state, mode, price ?: 0.0)?.let { preview ->
            Column(
                modifier = Modifier
                    .alpha(if (preview.isPlaceholder) PLACEHOLDER_ALPHA else 1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    preview.result,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    preview.formula,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = SaleStrings.CANCEL,
                onClick = viewModel::dismissPrompt,
                modifier = Modifier.weight(1f).height(56.dp).focusProperties { canFocus = false },
            )
            PosPrimaryButton(
                text = if (prompt.lineId == null) SaleStrings.ADD else SaleStrings.CHANGE_LINE,
                onClick = viewModel::confirmPrompt,
                enabled = state.canConfirmPrompt,
                modifier = Modifier.weight(1f).height(56.dp).focusProperties { canFocus = false },
            )
        }
    }
}

private data class PromptPreview(val result: String, val formula: String, val isPlaceholder: Boolean)

private fun promptPreview(state: CashierSaleState, mode: WeightMode?, price: Double): PromptPreview? {
    val value = state.promptValue
    val kilos = state.promptKilos
    return when (mode) {
        null -> null
        WeightMode.KILOS -> if (kilos == null) {
            PromptPreview(SaleStrings.money(0.0), SaleStrings.weightFormula(0.0, price), isPlaceholder = true)
        } else {
            PromptPreview(
                SaleStrings.money(roundMoney(kilos * price)),
                SaleStrings.weightFormula(kilos, price),
                isPlaceholder = false,
            )
        }
        WeightMode.PESOS -> if (value == null || kilos == null) {
            PromptPreview(SaleStrings.kilos(0.0), SaleStrings.pesosPreview(0.0, 0.0), isPlaceholder = true)
        } else {
            PromptPreview(SaleStrings.kilos(kilos), SaleStrings.pesosPreview(value, kilos), isPlaceholder = false)
        }
    }
}

private const val PLACEHOLDER_ALPHA = 0.6f
private val MODAL_VERTICAL_MARGIN = 32.dp
private val NOTICE_MIN_HEIGHT = 24.dp

@Composable
private fun ChargeOverlay(
    state: CashierSaleState,
    viewModel: CashierSaleViewModel,
    onPaidFocusChange: (Boolean) -> Unit,
    onCustomerFocusChange: (Boolean) -> Unit,
    onAbonoFocusChange: (Boolean) -> Unit,
) {
    val amountEnabled = !state.isCheckoutInFlight && !state.isPaidLocked
    val onCredit = state.paymentType == SalePaymentType.CREDIT
    val newCustomerOpen = onCredit && state.credit.newCustomer != null
    val paidFocus = remember { FocusRequester() }
    LaunchedEffect(state.paymentType) {
        if (state.paymentType == SalePaymentType.CASH && amountEnabled) {
            runCatching { paidFocus.requestFocus() }
        }
    }
    ModalScrim(width = CHARGE_OVERLAY_WIDTH) {
        val onlyOption = state.paymentOptions.singleOrNull()
        if (onlyOption != null) {
            SinglePaymentOption(onlyOption)
        } else {
            PosChoiceRow(
                options = state.paymentOptions.map { it to SaleStrings.paymentOptionLabel(it) },
                selected = state.paymentType,
                onSelect = viewModel::selectPaymentType,
                enabled = amountEnabled && !state.credit.isAbonoInFlight,
                height = 56.dp,
                textStyle = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
            )
        }
        if (state.isPaidLocked) {
            PaidLockedHint()
        }
        if (onCredit) {
            CustomerCreditPanel(
                credit = state.credit,
                enabled = amountEnabled,
                viewModel = viewModel,
                onCustomerFocusChange = onCustomerFocusChange,
                onAbonoFocusChange = onAbonoFocusChange,
                focusAbonoOnSelect = false,
                abonoProminent = false,
                amounts = { ChargeAmounts(state = state) },
            )
        } else {
            ChargeAmounts(state = state)
        }
        if (state.paymentType == SalePaymentType.CASH) {
            CashPaymentInput(
                state = state,
                viewModel = viewModel,
                amountEnabled = amountEnabled,
                paidModifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(paidFocus)
                    .onFocusChanged { onPaidFocusChange(it.hasFocus || it.isFocused) },
            )
        }
        state.errorMessage?.let { PosErrorBanner(error = it) }
        if (!newCustomerOpen) {
            ChargeFooter(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun SinglePaymentOption(type: SalePaymentType) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Payments, contentDescription = null)
            Text(
                SaleStrings.paymentOptionLabel(type),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChargeFooter(state: CashierSaleState, viewModel: CashierSaleViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosSecondaryButton(
            text = SaleStrings.BACK,
            onClick = viewModel::cancelCharge,
            enabled = !state.isCheckoutInFlight && !state.credit.isAbonoBusy,
            modifier = Modifier.weight(1f).focusProperties { canFocus = false },
        )
        PosPrimaryButton(
            text = SaleStrings.confirmLabel(state.paymentType, state.amountDue),
            onClick = viewModel::confirmCharge,
            enabled = state.canConfirmCharge,
            isLoading = state.isCheckoutInFlight,
            modifier = Modifier.weight(1f).height(56.dp).focusProperties { canFocus = false },
        )
    }
}

@Composable
private fun AbonoOverlay(
    state: CashierSaleState,
    viewModel: CashierSaleViewModel,
    onCustomerFocusChange: (Boolean) -> Unit,
    onAbonoFocusChange: (Boolean) -> Unit,
) {
    ModalScrim {
        Text(SaleStrings.ABONO, style = MaterialTheme.typography.headlineSmall)
        CustomerCreditPanel(
            credit = state.credit,
            enabled = true,
            viewModel = viewModel,
            onCustomerFocusChange = onCustomerFocusChange,
            onAbonoFocusChange = onAbonoFocusChange,
            focusAbonoOnSelect = true,
            debtorsOnly = true,
        )
        state.errorMessage?.let { PosErrorBanner(error = it) }
        PosSecondaryButton(
            text = SaleStrings.BACK,
            onClick = viewModel::closeAbono,
            enabled = !state.credit.isAbonoBusy,
            modifier = Modifier.fillMaxWidth().height(56.dp).focusProperties { canFocus = false },
        )
    }
}

@Composable
private fun ChargeAmounts(state: CashierSaleState) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        if (state.paymentType == SalePaymentType.CARD || state.paymentType == SalePaymentType.CREDIT) {
            AmountRow(
                label = if (state.paymentType == SalePaymentType.CREDIT) SaleStrings.AMOUNT_ON_TAB else SaleStrings.AMOUNT_DUE,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                PosMoneyText(amount = state.listTotal, style = MaterialTheme.typography.displayMedium)
            }
        } else {
            AmountRow(label = SaleStrings.TOTAL) {
                Text(SaleStrings.money(state.listTotal), style = MaterialTheme.typography.titleLarge)
            }
            if (state.cashDiscount > 0.0) {
                AmountRow(label = SaleStrings.CASH_DISCOUNT, muted = true) {
                    Text(
                        SaleStrings.discount(state.cashDiscount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AmountRow(
                label = SaleStrings.AMOUNT_DUE,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                PosMoneyText(amount = state.amountDue, style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

@Composable
private fun AmountRow(
    label: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    amount: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (muted) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
        amount()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashPaymentInput(
    state: CashierSaleState,
    viewModel: CashierSaleViewModel,
    amountEnabled: Boolean,
    paidModifier: Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.lg)) {
        Text(SaleStrings.CHARGE_QUESTION, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            PosSecondaryButton(
                text = SaleStrings.EXACT,
                onClick = viewModel::payExact,
                enabled = amountEnabled,
                modifier = Modifier.focusProperties { canFocus = false },
            )
            SaleStrings.QUICK_BILLS.forEach { bill ->
                PosSecondaryButton(
                    text = SaleStrings.quickBill(bill),
                    onClick = { viewModel.payWith(bill) },
                    enabled = amountEnabled,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            }
        }
        var paidValue by remember { mutableStateOf(TextFieldValue(state.paidInput)) }
        val shownPaid = if (paidValue.text == state.paidInput) {
            paidValue
        } else {
            TextFieldValue(state.paidInput, TextRange(state.paidInput.length))
        }
        PosTextField(
            value = shownPaid,
            onValueChange = { typed ->
                paidValue = typed
                if (typed.text != state.paidInput) viewModel.onPaidInputChange(typed.text)
            },
            label = SaleStrings.PAID_LABEL,
            enabled = amountEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = paidModifier.onFocusChanged { focus ->
                if (focus.isFocused) paidValue = shownPaid.copy(selection = TextRange(0, shownPaid.text.length))
            },
        )
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
    }
}

@Composable
private fun PaidLockedHint() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            SaleStrings.PAID_LOCKED,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun SaleProduct.shownPrice(channel: SaleChannel): Double? = listPriceFor(channel) ?: cashPriceFor(channel)

private fun SaleKitchenKind.label(): String = when (this) {
    SaleKitchenKind.FONDA -> SaleStrings.KITCHEN_FONDA
    SaleKitchenKind.TORTERIA -> SaleStrings.KITCHEN_TORTERIA
}

private fun KeyEvent.isEnterUp(): Boolean =
    type == KeyEventType.KeyUp && (key == Key.Enter || key == Key.NumPadEnter)

private fun KeyEvent.isEscapeUp(): Boolean = type == KeyEventType.KeyUp && key == Key.Escape

private const val DISABLED_ALPHA = 0.5f
private const val ACTIVE_DELIVERY_ALPHA = 0.35f
private const val READ_ONLY_BORDER_ALPHA = 0.6f
private val CHARGE_OVERLAY_WIDTH = 600.dp
