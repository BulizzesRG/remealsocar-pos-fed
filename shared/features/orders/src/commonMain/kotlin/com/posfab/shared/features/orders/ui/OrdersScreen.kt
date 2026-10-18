package com.posfab.shared.features.orders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.orders.domain.Order
import com.posfab.shared.features.orders.domain.OrderCustomer
import com.posfab.shared.features.orders.domain.OrderKitchenKind
import com.posfab.shared.features.orders.domain.OrderLine
import com.posfab.shared.features.orders.domain.OrderPriceType
import com.posfab.shared.features.orders.domain.OrderProduct
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.domain.OrderStatus
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosDestructiveButton
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosScrollableColumn
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing
import kotlinx.datetime.TimeZone

@Composable
fun OrdersScreen(viewModel: OrdersViewModel) {
    val state by viewModel.state.collectAsState()
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        runCatching { rootFocus.requestFocus() }
        viewModel.load()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> handleEscape(event, viewModel) }
            .focusRequester(rootFocus)
            .focusable(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val form = state.editing
        if (state.isEditing && form != null) {
            EditorView(state, form, viewModel)
        } else {
            ListView(state, viewModel)
        }

        val prompt = form?.prompt
        if (form != null && prompt != null) {
            PromptOverlay(form, prompt, viewModel)
        }
        state.cancelDialog?.let { CancelOverlay(state, it, viewModel) }
        if (form?.confirmLeave == true) {
            PosConfirmDialog(
                title = OrdersStrings.LEAVE_TITLE,
                message = OrdersStrings.LEAVE_MESSAGE,
                confirmLabel = OrdersStrings.LEAVE_CONFIRM,
                cancelLabel = OrdersStrings.STAY,
                isDestructive = true,
                onConfirm = viewModel::confirmLeave,
                onDismiss = viewModel::stay,
            )
        }
    }
}

private fun handleEscape(event: KeyEvent, viewModel: OrdersViewModel): Boolean {
    if (event.key != Key.Escape) return false
    if (event.type != KeyEventType.KeyUp) return event.type == KeyEventType.KeyDown && viewModel.state.value.editing != null
    return viewModel.onEscape()
}

@Composable
private fun ListView(state: OrdersState, viewModel: OrdersViewModel) {
    Column(
        modifier = Modifier
            .widthIn(max = LIST_MAX_WIDTH)
            .fillMaxSize()
            .padding(PosLayout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                OrdersStrings.TITLE,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            PosSecondaryButton(
                text = OrdersStrings.REFRESH,
                onClick = viewModel::refresh,
                icon = Icons.Filled.Refresh,
                enabled = !state.isLoading,
            )
            PosPrimaryButton(
                text = OrdersStrings.NEW_ORDER,
                onClick = viewModel::newOrder,
                icon = Icons.Filled.Add,
                isLoading = state.isCreating,
                enabled = state.openingId == null,
                modifier = Modifier.height(BIG_BUTTON_HEIGHT),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_SLOT_HEIGHT), contentAlignment = Alignment.CenterStart) {
            val error = state.errorMessage
            val notice = state.notice
            when {
                error != null -> Text(
                    error,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                notice != null -> Text(
                    notice,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PosScrollableColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Text(
                OrdersStrings.openSection(state.open.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.open.isEmpty()) {
                when {
                    state.isLoading -> LoadingRow()
                    state.errorMessage != null -> PosSecondaryButton(
                        text = OrdersStrings.RETRY,
                        onClick = viewModel::load,
                        icon = Icons.Filled.Refresh,
                    )
                    else -> EmptyCard(OrdersStrings.EMPTY_OPEN, OrdersStrings.EMPTY_OPEN_HINT)
                }
            }
            state.open.forEach { order ->
                OpenOrderCard(
                    order = order,
                    timeZone = viewModel.timeZone,
                    isBusy = state.busyOrderId == order.id || state.openingId == order.id,
                    enabled = state.busyOrderId == null && state.openingId == null && !state.isCreating,
                    onOpen = { viewModel.open(order.id) },
                    onSent = { viewModel.markSent(order.id) },
                    onCancel = { viewModel.startCancel(order.id) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = PosSpacing.sm))
            SectionToggle(
                text = OrdersStrings.paidToday(state.paidToday.size),
                expanded = state.showPaid,
                onClick = viewModel::togglePaid,
            )
            if (state.showPaid) {
                if (state.paidToday.isEmpty()) EmptyCard(OrdersStrings.NONE_TODAY)
                state.paidToday.forEach { ClosedOrderRow(it, viewModel.timeZone) }
            }
            SectionToggle(
                text = OrdersStrings.cancelledToday(state.cancelledToday.size),
                expanded = state.showCancelled,
                onClick = viewModel::toggleCancelled,
            )
            if (state.showCancelled) {
                if (state.cancelledToday.isEmpty()) EmptyCard(OrdersStrings.NONE_TODAY)
                state.cancelledToday.forEach { ClosedOrderRow(it, viewModel.timeZone) }
            }
        }
    }
}

@Composable
private fun OpenOrderCard(
    order: Order,
    timeZone: TimeZone,
    isBusy: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSent: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = CARD_MIN_HEIGHT),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PosSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    CardHeadline(order, timeZone)
                    Text(
                        order.address ?: OrdersStrings.NO_ADDRESS,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    order.notes?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    order.capturedPaysWith?.let { paysWith ->
                        Text(
                            OrdersStrings.paysWithLine(paysWith, order.shownChange),
                            style = MaterialTheme.typography.titleMedium,
                            color = changeColor(order.shownChange),
                            maxLines = 2,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                ) {
                    StageChip(order.stage)
                    Text(
                        OrdersStrings.money(order.total),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.xl, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                TextButton(
                    onClick = onCancel,
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = PosLayout.touchTarget).focusProperties { canFocus = false },
                ) {
                    Text(OrdersStrings.CANCEL, style = MaterialTheme.typography.titleMedium)
                }
                if (order.stage == OrderStage.ANOTADO) {
                    PosPrimaryButton(
                        text = OrdersStrings.MARK_SENT,
                        onClick = onSent,
                        enabled = enabled,
                        modifier = Modifier.height(BIG_BUTTON_HEIGHT).focusProperties { canFocus = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeadline(order: Order, timeZone: TimeZone) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            OrdersStrings.timeLabel(order.createdAt, timeZone),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(TIME_WIDTH),
        )
        Text(
            order.customer?.displayName?.takeIf { it.isNotBlank() } ?: OrdersStrings.NO_NAME,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        order.phone?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ClosedOrderRow(order: Order, timeZone: TimeZone) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(PosSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                Text(
                    headline(order, timeZone),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = if (order.status == OrderStatus.CANCELLED) {
                    OrdersStrings.cancelledLine(order.cancelReason, order.cancelNotes)
                } else {
                    listOfNotNull(order.address, order.folio?.let(OrdersStrings::folio)).joinToString(OrdersStrings.SEPARATOR)
                }
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                OrdersStrings.money(order.total),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SectionToggle(text: String, expanded: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = PosLayout.touchTarget).focusProperties { canFocus = false },
    ) {
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        Spacer(Modifier.width(PosSpacing.xs))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StageChip(stage: OrderStage?) {
    val sent = stage == OrderStage.ENVIADO
    Surface(
        color = if (sent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (sent) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            OrdersStrings.stageLabel(stage),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.xs),
        )
    }
}

@Composable
private fun EditorView(state: OrdersState, form: OrderForm, viewModel: OrdersViewModel) {
    val focus = remember { EditorFocus() }
    val enabled = !state.editorLocked
    LaunchedEffect(form.orderId, form.focusRequest) {
        runCatching {
            when (form.focus) {
                OrderFocus.CUSTOMER -> focus.customer.requestFocus()
                OrderFocus.NEW_CUSTOMER_NAME -> focus.newName.requestFocus()
                OrderFocus.ADDRESS -> focus.address.requestFocus()
                OrderFocus.PRODUCT -> focus.product.requestFocus()
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= TWO_COLUMN_MIN_WIDTH
        Column(
            modifier = Modifier.fillMaxSize().padding(PosLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosSecondaryButton(
                    text = OrdersStrings.BACK,
                    onClick = viewModel::back,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    enabled = !form.isBusy,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                Text(
                    form.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!form.isNew) StageChip(form.stage)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
                    ) {
                        PosScrollableColumn(
                            modifier = Modifier.weight(LEFT_WEIGHT).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
                        ) {
                            CustomerSection(form, enabled, viewModel, focus)
                        }
                        Column(
                            modifier = Modifier.weight(RIGHT_WEIGHT).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
                        ) {
                            ProductSearchField(form, enabled, viewModel, focus)
                            PosScrollableColumn(
                                modifier = Modifier.weight(PRODUCTS_WEIGHT).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                            ) {
                                ProductPanel(state, form, enabled, viewModel)
                            }
                            HorizontalDivider()
                            PosScrollableColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = LINES_MIN_HEIGHT)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                            ) {
                                LinesSection(form, enabled, viewModel)
                            }
                        }
                    }
                } else {
                    PosScrollableColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
                    ) {
                        CustomerSection(form, enabled, viewModel, focus)
                        ProductSearchField(form, enabled, viewModel, focus)
                        ProductPanel(state, form, enabled, viewModel)
                        HorizontalDivider()
                        LinesSection(form, enabled, viewModel)
                    }
                }
            }
            CobroBar(form, enabled, wide, viewModel, focus)
            form.errorMessage?.takeIf { form.prompt == null }?.let { PosErrorBanner(it) }
            EditorFooter(form, viewModel)
        }
    }
}

private class EditorFocus {
    val customer = FocusRequester()
    val newName = FocusRequester()
    val newPhone = FocusRequester()
    val address = FocusRequester()
    val notes = FocusRequester()
    val paysWith = FocusRequester()
    val product = FocusRequester()
}

@Composable
private fun CustomerSection(form: OrderForm, enabled: Boolean, viewModel: OrdersViewModel, focus: EditorFocus) {
    PosSectionCard(title = OrdersStrings.WHO_AND_WHERE) {
        val selected = form.selected
        val newCustomer = form.newCustomer
        when {
            selected != null -> {
                SelectedCustomer(selected, enabled, viewModel::clearCustomer)
                AddressField(form, enabled, viewModel, focus)
            }
            newCustomer != null -> {
                Text(
                    OrdersStrings.NEW_CUSTOMER,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                PosTextField(
                    value = newCustomer.name,
                    onValueChange = viewModel::onNewCustomerName,
                    label = OrdersStrings.NEW_CUSTOMER_NAME,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus.newName)
                        .onEnterKey { runCatching { focus.newPhone.requestFocus() } },
                )
                PosTextField(
                    value = newCustomer.phone,
                    onValueChange = viewModel::onNewCustomerPhone,
                    label = OrdersStrings.NEW_CUSTOMER_PHONE,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus.newPhone)
                        .onEnterKey { runCatching { focus.address.requestFocus() } },
                )
                AddressField(form, enabled, viewModel, focus, onEnter = viewModel::saveNewCustomer)
                form.customerError?.let { PosErrorBanner(it) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosPrimaryButton(
                        text = OrdersStrings.SAVE_CUSTOMER,
                        onClick = viewModel::saveNewCustomer,
                        icon = Icons.Filled.PersonAdd,
                        enabled = form.canSaveCustomer && enabled,
                        isLoading = form.isSavingCustomer,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                    TextButton(
                        onClick = viewModel::cancelNewCustomer,
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = PosLayout.touchTarget).focusProperties { canFocus = false },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(PosSpacing.xs))
                        Text(OrdersStrings.SEARCH_CUSTOMER)
                    }
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosTextField(
                        value = form.customerQuery,
                        onValueChange = viewModel::onCustomerQuery,
                        label = OrdersStrings.CUSTOMER_QUERY,
                        enabled = enabled,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (form.isSearchingCustomers) {
                            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus.customer)
                            .onEnterKey(viewModel::onCustomerQueryEnter),
                    )
                    PosSecondaryButton(
                        text = OrdersStrings.NEW_CUSTOMER,
                        onClick = viewModel::startNewCustomer,
                        icon = Icons.Filled.PersonAdd,
                        enabled = enabled,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SUGGESTION_SLOT_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                ) {
                    val searchError = form.customerSearchError
                    when {
                        searchError != null -> Text(
                            searchError,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        form.showsNoCustomerMatch -> Text(
                            OrdersStrings.NO_CUSTOMER_MATCH,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    form.suggestions.forEach { customer ->
                        SuggestionRow(customer, enabled) { viewModel.pickCustomer(customer.id) }
                    }
                }
                AddressField(form, enabled, viewModel, focus)
            }
        }
        PosTextField(
            value = form.notes,
            onValueChange = viewModel::onNotes,
            label = OrdersStrings.NOTES,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus.notes)
                .onEnterKey { runCatching { focus.paysWith.requestFocus() } },
        )
    }
}

@Composable
private fun AddressField(
    form: OrderForm,
    enabled: Boolean,
    viewModel: OrdersViewModel,
    focus: EditorFocus,
    onEnter: (() -> Unit)? = null,
) {
    PosTextField(
        value = form.address,
        onValueChange = viewModel::onAddress,
        label = OrdersStrings.ADDRESS,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus.address)
            .onEnterKey { if (onEnter != null) onEnter() else runCatching { focus.notes.requestFocus() } },
    )
}

@Composable
private fun SelectedCustomer(customer: OrderCustomer, enabled: Boolean, onOther: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    customer.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                customer.phone?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(
                onClick = onOther,
                enabled = enabled,
                modifier = Modifier.heightIn(min = PosLayout.touchTarget).focusProperties { canFocus = false },
            ) {
                Text(OrdersStrings.OTHER_CUSTOMER)
            }
        }
    }
}

@Composable
private fun SuggestionRow(customer: OrderCustomer, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PosLayout.touchTarget)
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm)) {
            Text(
                customer.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(customer.phone, customer.address).joinToString(OrdersStrings.SEPARATOR)
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CobroBar(form: OrderForm, enabled: Boolean, wide: Boolean, viewModel: OrdersViewModel, focus: EditorFocus) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(PosSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TotalText(form, Modifier.weight(1f))
                PaysWithField(form, enabled, viewModel, focus, Modifier.widthIn(min = PAYS_WITH_MIN_WIDTH, max = PAYS_WITH_MAX_WIDTH))
                ChangeSlot(form, Modifier.widthIn(min = CHANGE_MIN_WIDTH))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(PosSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TotalText(form, Modifier.weight(1f))
                    ChangeSlot(form)
                }
                PaysWithField(form, enabled, viewModel, focus, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TotalText(form: OrderForm, modifier: Modifier) {
    Text(
        OrdersStrings.total(form.total),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

@Composable
private fun PaysWithField(
    form: OrderForm,
    enabled: Boolean,
    viewModel: OrdersViewModel,
    focus: EditorFocus,
    modifier: Modifier,
) {
    PosTextField(
        value = form.paysWithInput,
        onValueChange = viewModel::onPaysWith,
        label = OrdersStrings.PAYS_WITH,
        enabled = enabled,
        leadingIcon = { Text(OrdersStrings.PESO_SIGN, style = amountStyle()) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = amountStyle(),
        modifier = modifier.focusRequester(focus.paysWith).onEnterKey {},
    )
}

@Composable
private fun ChangeSlot(form: OrderForm, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(CHANGE_SLOT_HEIGHT), contentAlignment = Alignment.CenterEnd) {
        form.change?.let { change ->
            Text(
                OrdersStrings.changeOrMissing(change),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = changeColor(change),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ProductSearchField(form: OrderForm, enabled: Boolean, viewModel: OrdersViewModel, focus: EditorFocus) {
    PosTextField(
        value = form.productQuery,
        onValueChange = viewModel::onProductQuery,
        label = OrdersStrings.SEARCH_PRODUCT,
        enabled = enabled,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().focusRequester(focus.product).onEnterKey(viewModel::onProductQueryEnter),
    )
}

@Composable
private fun ColumnScope.ProductPanel(state: OrdersState, form: OrderForm, enabled: Boolean, viewModel: OrdersViewModel) {
    if (form.productQuery.trim().length >= MIN_PRODUCT_QUERY && !form.isSearchingProducts && form.productResults.isEmpty()) {
        Text(
            OrdersStrings.NO_PRODUCTS_FOUND,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    form.productResults.forEach { product ->
        ProductResultRow(product, enabled) { viewModel.addProduct(product) }
    }
    OrderKitchenKind.entries.forEach { kind ->
        val products = state.kitchenProducts.filter { it.kitchenKind == kind }
        if (products.isNotEmpty()) {
            Text(
                OrdersStrings.kitchenLabel(kind),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = PosSpacing.sm),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                products.forEach { product ->
                    KitchenCard(product, enabled) { viewModel.addProduct(product) }
                }
            }
        }
    }
}

@Composable
private fun ProductResultRow(product: OrderProduct, enabled: Boolean, onClick: () -> Unit) {
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
            Text(priceLabel(product), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun KitchenCard(product: OrderProduct, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(KITCHEN_CARD_WIDTH).heightIn(min = KITCHEN_CARD_HEIGHT).focusProperties { canFocus = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PosSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
        ) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(priceLabel(product), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun LinesSection(form: OrderForm, enabled: Boolean, viewModel: OrdersViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        Text(
            OrdersStrings.ORDER_LINES,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (form.pendingMutations > 0) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
    if (form.lines.isEmpty()) {
        Text(
            OrdersStrings.EMPTY_CART,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    form.lines.forEach { line ->
        LineRow(
            line = line,
            enabled = enabled,
            onMinus = { viewModel.decrementLine(line.id) },
            onPlus = { viewModel.incrementLine(line.id) },
            onRemove = { viewModel.removeLine(line.id) },
        )
    }
}

@Composable
private fun LineRow(line: OrderLine, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit, onRemove: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    line.productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val qty = OrdersStrings.qtyLabel(line.qty, line.unitCode, line.priceType)
                if (qty.isNotEmpty()) {
                    Text(qty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (line.priceType == OrderPriceType.PIECE) {
                IconButton(onClick = onMinus, enabled = enabled, modifier = Modifier.focusProperties { canFocus = false }) {
                    Icon(Icons.Filled.Remove, contentDescription = OrdersStrings.LESS)
                }
                IconButton(onClick = onPlus, enabled = enabled, modifier = Modifier.focusProperties { canFocus = false }) {
                    Icon(Icons.Filled.Add, contentDescription = OrdersStrings.MORE)
                }
            }
            Text(
                OrdersStrings.money(line.lineTotal),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = LINE_PRICE_MIN_WIDTH),
            )
            Spacer(Modifier.width(PosSpacing.md))
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(PosLayout.touchTarget).focusProperties { canFocus = false },
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = OrdersStrings.REMOVE,
                    tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditorFooter(form: OrderForm, viewModel: OrdersViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosPrimaryButton(
            text = OrdersStrings.SAVE,
            onClick = viewModel::save,
            enabled = form.canSave,
            isLoading = form.isSaving,
            modifier = Modifier.widthIn(min = FOOTER_BUTTON_MIN_WIDTH).height(BIG_BUTTON_HEIGHT),
        )
        Box(modifier = Modifier.weight(1f)) {
            form.saveHint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!form.isNew && form.stage == OrderStage.ANOTADO) {
            PosSecondaryButton(
                text = OrdersStrings.MARK_SENT,
                onClick = { viewModel.markSent(form.orderId) },
                enabled = form.canMarkSent,
                modifier = Modifier.widthIn(min = FOOTER_BUTTON_MIN_WIDTH).height(BIG_BUTTON_HEIGHT),
            )
        }
        PosDestructiveButton(
            text = OrdersStrings.CANCEL_ORDER,
            onClick = { viewModel.startCancel(form.orderId) },
            enabled = form.canCancel,
            modifier = Modifier.height(BIG_BUTTON_HEIGHT),
        )
    }
}

@Composable
private fun PromptOverlay(form: OrderForm, prompt: OrderPrompt, viewModel: OrdersViewModel) {
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(prompt.product.id) { runCatching { inputFocus.requestFocus() } }
    val weight = prompt is OrderPrompt.Weight

    ModalScrim {
        Text(
            if (weight) OrdersStrings.WEIGHT_PROMPT else OrdersStrings.OPEN_PROMPT,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(prompt.product.name, style = MaterialTheme.typography.titleLarge)
        if (weight) {
            prompt.product.shownPrice?.let {
                Text(
                    OrdersStrings.perKilo(it),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                OrdersStrings.QUICK_WEIGHTS.forEach { (kilos, label) ->
                    PosSecondaryButton(
                        text = label,
                        onClick = { viewModel.pickWeight(kilos) },
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            PosTextField(
                value = prompt.input,
                onValueChange = viewModel::onPromptInput,
                label = if (weight) OrdersStrings.KILOS else OrdersStrings.PESOS,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = if (weight) null else ({ Text(OrdersStrings.PESO_SIGN, style = amountStyle()) }),
                trailingIcon = if (weight) ({ Text(OrdersStrings.KG_SUFFIX) }) else null,
                textStyle = amountStyle(),
                modifier = Modifier.fillMaxWidth().focusRequester(inputFocus).onEnterKey(viewModel::confirmPrompt),
            )
            Text(
                OrdersStrings.PROMPT_KEYS,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        form.errorMessage?.let { PosErrorBanner(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = OrdersStrings.CANCEL,
                onClick = viewModel::dismissPrompt,
                modifier = Modifier.weight(1f),
            )
            PosPrimaryButton(
                text = OrdersStrings.addButton(prompt.previewTotal),
                onClick = viewModel::confirmPrompt,
                enabled = prompt.parsed != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CancelOverlay(state: OrdersState, dialog: CancelDialog, viewModel: OrdersViewModel) {
    val notesFocus = remember { FocusRequester() }
    LaunchedEffect(dialog.needsNotes) { if (dialog.needsNotes) runCatching { notesFocus.requestFocus() } }
    val form = state.editing?.takeIf { it.orderId == dialog.orderId }
    val listed = state.open.firstOrNull { it.id == dialog.orderId }
    val name = form?.selected?.displayName ?: listed?.customer?.displayName
    val total = form?.total ?: listed?.total ?: 0.0

    ModalScrim(width = CANCEL_OVERLAY_WIDTH) {
        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Text(
                OrdersStrings.CANCEL_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                OrdersStrings.cancelSubtitle(name, total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            OrdersStrings.CANCEL_REASONS.forEach { reason ->
                val modifier = Modifier
                    .fillMaxWidth(REASON_FRACTION)
                    .heightIn(min = REASON_HEIGHT)
                    .focusProperties { canFocus = false }
                if (dialog.reason == reason) {
                    PosPrimaryButton(
                        text = OrdersStrings.reasonLabel(reason),
                        onClick = { viewModel.pickCancelReason(reason) },
                        icon = Icons.Filled.Check,
                        enabled = !dialog.isSubmitting,
                        modifier = modifier,
                    )
                } else {
                    PosSecondaryButton(
                        text = OrdersStrings.reasonLabel(reason),
                        onClick = { viewModel.pickCancelReason(reason) },
                        enabled = !dialog.isSubmitting,
                        modifier = modifier,
                    )
                }
            }
        }
        if (dialog.needsNotes) {
            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                PosTextField(
                    value = dialog.notes,
                    onValueChange = viewModel::onCancelNotes,
                    label = OrdersStrings.CANCEL_NOTES,
                    enabled = !dialog.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(notesFocus)
                        .onEnterKey { if (dialog.canConfirm) viewModel.confirmCancel() },
                )
                if (dialog.notes.isBlank()) {
                    Text(
                        OrdersStrings.NOTES_REQUIRED,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        dialog.errorMessage?.let { PosErrorBanner(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = OrdersStrings.KEEP_ORDER,
                onClick = viewModel::dismissCancel,
                enabled = !dialog.isSubmitting,
                modifier = Modifier.weight(1f),
            )
            PosDestructiveButton(
                text = OrdersStrings.CANCEL_ORDER,
                onClick = viewModel::confirmCancel,
                enabled = dialog.canConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyCard(text: String, hint: String? = null) {
    PosSectionCard {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(OrdersStrings.LOADING, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ModalScrim(width: Dp = OVERLAY_WIDTH, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(if (width < maxWidth - MODAL_MARGIN) width else maxWidth - MODAL_MARGIN)
                .heightIn(max = maxHeight - MODAL_MARGIN),
        ) {
            PosScrollableColumn(
                contentPadding = PaddingValues(PosSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
                content = content,
            )
        }
    }
}

@Composable
private fun changeColor(change: Double?): Color =
    if (change != null && change < 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

@Composable
private fun amountStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)

private fun headline(order: Order, timeZone: TimeZone): String = listOfNotNull(
    OrdersStrings.timeLabel(order.createdAt, timeZone),
    order.customer?.displayName?.takeIf { it.isNotBlank() } ?: OrdersStrings.NO_NAME,
    order.phone,
).joinToString(OrdersStrings.SEPARATOR)

private fun priceLabel(product: OrderProduct): String = when (product.priceType) {
    OrderPriceType.OPEN -> OrdersStrings.OPEN_PRICE
    OrderPriceType.WEIGHT -> product.shownPrice?.let(OrdersStrings::perKilo).orEmpty()
    OrderPriceType.PIECE -> product.shownPrice?.let(OrdersStrings::money).orEmpty()
    OrderPriceType.OTHER -> ""
}

private class EnterLatch {
    var pressed: Boolean = false
}

@Composable
private fun Modifier.onEnterKey(action: () -> Unit): Modifier {
    val latch = remember { EnterLatch() }
    return onPreviewKeyEvent { event ->
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                latch.pressed = true
                true
            }
            KeyEventType.KeyUp -> {
                val wasPressed = latch.pressed
                latch.pressed = false
                if (wasPressed) action()
                true
            }
            else -> false
        }
    }
}

private const val SCRIM_ALPHA = 0.5f
private const val LEFT_WEIGHT = 0.45f
private const val RIGHT_WEIGHT = 0.55f
private const val PRODUCTS_WEIGHT = 1.4f
private const val REASON_FRACTION = 0.48f
private const val MIN_PRODUCT_QUERY = 2
private val LIST_MAX_WIDTH = 1100.dp
private val TWO_COLUMN_MIN_WIDTH = 720.dp
private val CARD_MIN_HEIGHT = 96.dp
private val TIME_WIDTH = 104.dp
private val BIG_BUTTON_HEIGHT = 56.dp
private val FOOTER_BUTTON_MIN_WIDTH = 160.dp
private val KITCHEN_CARD_WIDTH = 160.dp
private val KITCHEN_CARD_HEIGHT = 72.dp
private val LINES_MIN_HEIGHT = 120.dp
private val LINE_PRICE_MIN_WIDTH = 96.dp
private val SUGGESTION_SLOT_HEIGHT = 56.dp
private val MESSAGE_SLOT_HEIGHT = 28.dp
private val PAYS_WITH_MIN_WIDTH = 200.dp
private val PAYS_WITH_MAX_WIDTH = 260.dp
private val CHANGE_MIN_WIDTH = 220.dp
private val CHANGE_SLOT_HEIGHT = 48.dp
private val REASON_HEIGHT = 56.dp
private val OVERLAY_WIDTH = 520.dp
private val CANCEL_OVERLAY_WIDTH = 560.dp
private val MODAL_MARGIN = 32.dp
