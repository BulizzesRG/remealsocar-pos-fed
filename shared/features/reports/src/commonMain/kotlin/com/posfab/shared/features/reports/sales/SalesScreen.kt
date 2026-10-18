package com.posfab.shared.features.reports.sales

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosLazyColumn
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing
import kotlinx.datetime.TimeZone

@Composable
fun SalesScreen(viewModel: SalesViewModel) {
    val state by viewModel.state.collectAsState()
    val rootFocus = remember { FocusRequester() }
    var dateFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(state.showsDetail) {
        dateFieldFocused = false
        runCatching { rootFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> handleKey(event, state, dateFieldFocused, viewModel) }
            .focusRequester(rootFocus)
            .focusable(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxSize()
                .padding(PosLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        ) {
            if (state.showsDetail) {
                DetailView(state, viewModel.timeZone, viewModel::back, viewModel::refresh)
            } else {
                DayView(
                    state = state,
                    timeZone = viewModel.timeZone,
                    viewModel = viewModel,
                    onDateFocus = { dateFieldFocused = it },
                    onGo = {
                        viewModel.goToTypedDate()
                        if (viewModel.state.value.notice == null) runCatching { rootFocus.requestFocus() }
                    },
                )
            }
        }
    }
}

private fun handleKey(
    event: KeyEvent,
    state: SalesState,
    dateFieldFocused: Boolean,
    viewModel: SalesViewModel,
): Boolean {
    val isArrow = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
    if (state.showsDetail) {
        if (event.key != Key.Escape) return false
        if (event.type == KeyEventType.KeyUp) viewModel.back()
        return true
    }
    if (!isArrow || dateFieldFocused) return false
    if (event.type == KeyEventType.KeyUp) {
        if (event.key == Key.DirectionLeft) viewModel.previousDay() else viewModel.nextDay()
    }
    return true
}

@Composable
private fun ColumnScope.DayView(
    state: SalesState,
    timeZone: TimeZone,
    viewModel: SalesViewModel,
    onDateFocus: (Boolean) -> Unit,
    onGo: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val wide = maxWidth >= WIDE_LAYOUT
        val wideHeader = maxWidth >= WIDE_HEADER_LAYOUT
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            DayHeader(state, wideHeader, viewModel, onDateFocus, onGo)
            state.dayError?.let { PosErrorBanner(it) }
            PosLazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            ) {
                item(key = "sold") { SoldSummary(state.total, state.count, state.showsAmounts) }
                item(key = "breakdown") { Breakdown(state, wide) }
                when {
                    state.sales.isEmpty() && state.isLoading -> item(key = "loading") { CenteredLoading() }
                    state.sales.isEmpty() && state.dayError == null -> item(key = "empty") {
                        CenteredMessage(if (state.isToday) SalesStrings.EMPTY_TODAY else SalesStrings.EMPTY_DAY)
                    }
                    else -> items(state.sales, key = { it.id }) { sale ->
                        SaleRow(sale, timeZone) { viewModel.select(sale.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    state: SalesState,
    wide: Boolean,
    viewModel: SalesViewModel,
    onDateFocus: (Boolean) -> Unit,
    onGo: () -> Unit,
) {
    val navigation: @Composable () -> Unit = {
        DayNavigation(state, viewModel)
    }
    val tools: @Composable () -> Unit = {
        DateTools(state, viewModel, onDateFocus, onGo)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
    ) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) {
                Box(modifier = Modifier.weight(1f)) { navigation() }
                tools()
            }
        } else {
            navigation()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) { tools() }
        }
        state.notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DayNavigation(state: SalesState, viewModel: SalesViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        FilledTonalIconButton(
            onClick = viewModel::previousDay,
            modifier = Modifier.size(NAV_BUTTON_SIZE),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = SalesStrings.PREVIOUS_DAY,
                modifier = Modifier.size(NAV_ICON_SIZE),
            )
        }
        Text(
            text = dayTitle(state.day, state.today),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(DAY_TITLE_WIDTH),
        )
        FilledTonalIconButton(
            onClick = viewModel::nextDay,
            enabled = !state.isToday,
            modifier = Modifier.size(NAV_BUTTON_SIZE),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = SalesStrings.NEXT_DAY,
                modifier = Modifier.size(NAV_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun DateTools(
    state: SalesState,
    viewModel: SalesViewModel,
    onDateFocus: (Boolean) -> Unit,
    onGo: () -> Unit,
) {
    val enter = remember { EnterLatch() }
    PosTextField(
        value = state.dateInput,
        onValueChange = viewModel::onDateInput,
        label = SalesStrings.GO_TO_DATE,
        modifier = Modifier
            .width(DATE_FIELD_WIDTH)
            .onFocusChanged { onDateFocus(it.isFocused) }
            .onEnterKey(enter, onGo),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    PosSecondaryButton(text = SalesStrings.GO, onClick = onGo)
    PosSecondaryButton(
        text = SalesStrings.REFRESH,
        onClick = viewModel::refresh,
        enabled = !state.isLoading,
        icon = Icons.Filled.Refresh,
    )
}

@Composable
private fun SoldSummary(total: Double, count: Int, showsAmounts: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.padding(bottom = PosSpacing.sm)) {
        Text(
            text = SalesStrings.SOLD,
            style = MaterialTheme.typography.titleMedium,
            color = muted,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Text(
                text = if (showsAmounts) SalesStrings.money(total) else SalesStrings.NO_VALUE,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (showsAmounts) {
                Text(
                    text = SalesStrings.saleCount(count),
                    style = MaterialTheme.typography.bodyLarge,
                    color = muted,
                    modifier = Modifier.padding(bottom = PosSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun Breakdown(state: SalesState, wide: Boolean) {
    val byPayment = state.byPayment
    val payments: @Composable (Modifier) -> Unit = { modifier ->
        PosSectionCard(modifier = modifier, title = SalesStrings.BY_PAYMENT) {
            SalePayment.entries.forEach { payment ->
                BreakdownRow(SalesStrings.paymentLabel(payment), byPayment[payment].takeIf { state.showsAmounts })
            }
        }
    }
    val units: @Composable (Modifier) -> Unit = { modifier ->
        val values = state.units
        PosSectionCard(modifier = modifier, title = SalesStrings.BY_UNIT) {
            BreakdownRow(SalesStrings.TIENDA, values?.tienda)
            BreakdownRow(SalesStrings.FONDA, values?.fonda)
            BreakdownRow(SalesStrings.TORTERIA, values?.torteria)
        }
    }
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = PosSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            payments(Modifier.weight(1f))
            units(Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = PosSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            payments(Modifier.fillMaxWidth())
            units(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BreakdownRow(label: String, amount: Double?) {
    val isZero = amount == null || amount < MIN_VISIBLE_AMOUNT
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = BREAKDOWN_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount?.let(SalesStrings::money) ?: SalesStrings.NO_VALUE,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (isZero) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SaleRow(sale: SaleListItem, timeZone: TimeZone, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (hovered || focused) colors.surfaceContainerHigh else colors.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SALE_ROW_HEIGHT)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Column(
                modifier = Modifier.width(TIME_COLUMN_WIDTH),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                Text(text = listTimeLabel(sale.soldAt, timeZone), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = sale.folio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                Text(
                    text = SalesStrings.paymentText(sale.payment, sale.customerName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (sale.channel == SaleChannel.ONSITE) OnsiteTag()
            }
            Text(
                text = SalesStrings.money(sale.total),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(CHEVRON_SIZE),
            )
        }
    }
}

@Composable
private fun OnsiteTag() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = SalesStrings.ONSITE,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = PosSpacing.sm, vertical = PosSpacing.xxs),
        )
    }
}

@Composable
private fun ColumnScope.DetailView(
    state: SalesState,
    timeZone: TimeZone,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSecondaryButton(
            text = SalesStrings.BACK,
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        if (state.detail == null && state.detailError != null) {
            PosSecondaryButton(
                text = SalesStrings.REFRESH,
                onClick = onRetry,
                enabled = !state.isDetailLoading,
                icon = Icons.Filled.Refresh,
            )
        }
    }

    state.detailError?.let { PosErrorBanner(it) }

    val detail = state.detail
    if (detail == null) {
        if (state.isDetailLoading) CenteredLoading()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        Text(
            text = SalesStrings.saleTitle(detail.folio),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = detailSubtitle(detail, state.today, timeZone),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
    LineHeader()

    PosLazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
    ) {
        items(detail.lines) { line -> LineRow(line) }
    }

    DetailFooter(detail)
}

@Composable
private fun LineHeader() {
    val style = MaterialTheme.typography.labelLarge
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Text(text = SalesStrings.COLUMN_PRODUCT, style = style, color = muted, modifier = Modifier.weight(1f))
        Text(
            text = SalesStrings.COLUMN_QTY,
            style = style,
            color = muted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(QTY_COLUMN_WIDTH),
        )
        Text(
            text = SalesStrings.COLUMN_PRICE,
            style = style,
            color = muted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MONEY_COLUMN_WIDTH),
        )
        Text(
            text = SalesStrings.COLUMN_AMOUNT,
            style = style,
            color = muted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MONEY_COLUMN_WIDTH),
        )
    }
}

@Composable
private fun LineRow(line: SaleDetailLine) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = LINE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Text(
            text = line.productName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = qtyLabel(line.qty, line.unitCode, line.priceType),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.width(QTY_COLUMN_WIDTH),
        )
        Text(
            text = SalesStrings.money(line.unitPrice),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MONEY_COLUMN_WIDTH),
        )
        Text(
            text = SalesStrings.money(line.lineTotal),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(MONEY_COLUMN_WIDTH),
        )
    }
}

@Composable
private fun DetailFooter(detail: SaleDetail) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        HorizontalDivider()
        FooterRow(SalesStrings.SUBTOTAL, SalesStrings.money(detail.subtotal))
        if (detail.cashDiscount >= MIN_VISIBLE_AMOUNT) {
            FooterRow(SalesStrings.CASH_DISCOUNT, SalesStrings.minusMoney(detail.cashDiscount))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = SalesStrings.TOTAL,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = SalesStrings.money(detail.total),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FooterRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CenteredLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = PosSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        CircularProgressIndicator()
        Text(
            text = SalesStrings.LOADING,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = PosSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private class EnterLatch {
    var pressed: Boolean = false
}

private fun Modifier.onEnterKey(latch: EnterLatch, action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
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

private const val MIN_VISIBLE_AMOUNT = 0.005
private val CONTENT_MAX_WIDTH: Dp = 880.dp
private val WIDE_LAYOUT: Dp = 720.dp
private val WIDE_HEADER_LAYOUT: Dp = 780.dp
private val NAV_BUTTON_SIZE: Dp = 56.dp
private val DAY_TITLE_WIDTH: Dp = 240.dp
private val NAV_ICON_SIZE: Dp = 32.dp
private val CHEVRON_SIZE: Dp = 24.dp
private val DATE_FIELD_WIDTH: Dp = 180.dp
private val BREAKDOWN_ROW_HEIGHT: Dp = 40.dp
private val SALE_ROW_HEIGHT: Dp = 64.dp
private val LINE_ROW_HEIGHT: Dp = 48.dp
private val TIME_COLUMN_WIDTH: Dp = 120.dp
private val QTY_COLUMN_WIDTH: Dp = 96.dp
private val MONEY_COLUMN_WIDTH: Dp = 112.dp
