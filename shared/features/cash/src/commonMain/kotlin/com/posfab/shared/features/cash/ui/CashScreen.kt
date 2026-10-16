package com.posfab.shared.features.cash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.CashSessionSummary
import com.posfab.shared.features.cash.domain.DrawerDifference
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.ui.components.PosChoiceRow
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun CashScreen(viewModel: CashViewModel) {
    val state by viewModel.state.collectAsState()
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        runCatching { rootFocus.requestFocus() }
        viewModel.load()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> handleEscape(event, state, viewModel) }
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
            when (val phase = state.phase) {
                CashPhase.Loading -> CenteredLoading()
                is CashPhase.Closed -> ClosedView(state, phase, viewModel, rootFocus)
                is CashPhase.Open -> OpenView(state, phase.session, viewModel, rootFocus)
                is CashPhase.Unavailable -> UnavailableView(phase.message, viewModel::retry)
                is CashPhase.DayClosed -> DayClosedView(phase.result, viewModel::finishDay)
            }
        }

        val session = state.openSession
        val payout = state.payout
        val closing = state.closing
        if (session != null && payout != null) {
            PayoutOverlay(payout, viewModel, rootFocus)
        } else if (session != null && closing != null) {
            CloseOverlay(state, closing, session, viewModel, rootFocus)
        }
    }
}

private fun handleEscape(event: KeyEvent, state: CashState, viewModel: CashViewModel): Boolean {
    if (event.type != KeyEventType.KeyUp || event.key != Key.Escape) return false
    return when {
        state.payout != null -> {
            viewModel.onPayoutEscape()
            true
        }
        state.closing != null -> {
            viewModel.onCloseEscape()
            true
        }
        else -> false
    }
}

@Composable
private fun ColumnScope.ScrollingBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        content = content,
    )
}

@Composable
private fun ColumnScope.ClosedView(
    state: CashState,
    phase: CashPhase.Closed,
    viewModel: CashViewModel,
    rootFocus: FocusRequester,
) {
    val fieldFocus = remember { FocusRequester() }
    val enter = remember { EnterLatch() }
    var field by remember {
        mutableStateOf(TextFieldValue(state.openingInput, TextRange(0, state.openingInput.length)))
    }
    var lastTyped by remember { mutableStateOf<String?>(null) }
    if (field.text != state.openingInput) {
        val text = state.openingInput
        field = if (lastTyped == field.text) {
            TextFieldValue(text, TextRange(text.length))
        } else {
            TextFieldValue(text, TextRange(0, text.length))
        }
    }
    LaunchedEffect(state.isOpening) {
        runCatching { if (state.isOpening) rootFocus.requestFocus() else fieldFocus.requestFocus() }
    }

    ScrollingBody {
        Text(
            text = CashStrings.CLOSED_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        NoticeLine(state.notice)
        PosSectionCard {
            PosTextField(
                value = field,
                onValueChange = {
                    field = it
                    lastTyped = it.text
                    viewModel.onOpeningAmountChange(it.text)
                },
                label = CashStrings.OPENING_LABEL,
                enabled = !state.isOpening,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onEnterKey(enter, viewModel::openDay),
                leadingIcon = { PesoSign() },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = amountFieldStyle(),
            )
            Text(
                text = phase.previousClosingAmount?.let(CashStrings::lastClosing) ?: CashStrings.FIRST_TIME,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.errorMessage?.let { PosErrorBanner(it) }
            PosPrimaryButton(
                text = CashStrings.OPEN_DAY,
                onClick = viewModel::openDay,
                enabled = state.canOpenDay,
                isLoading = state.isOpening,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.OpenView(
    state: CashState,
    session: CashSessionSummary,
    viewModel: CashViewModel,
    rootFocus: FocusRequester,
) {
    val takeMoneyFocus = remember { FocusRequester() }
    var hadOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(state.hasOverlay) {
        if (!state.hasOverlay && hadOverlay) {
            runCatching { if (state.canManage) takeMoneyFocus.requestFocus() else rootFocus.requestFocus() }
        }
        hadOverlay = state.hasOverlay
    }

    Text(
        text = CashStrings.openSince(session.openedAt),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.errorMessage?.let { PosErrorBanner(it) }
    NoticeLine(state.notice)

    ScrollingBody {
        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Text(
                text = CashStrings.EXPECTED_HEADLINE,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = CashStrings.money(session.expectedInDrawer),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (session.openAccounts > 0) {
            WarningCard(CashStrings.openAccounts(session.openAccounts))
        }
        PosSectionCard {
            DrawerRows(session)
        }
        PosSectionCard(title = CashStrings.NOT_IN_DRAWER) {
            OutsideDrawerRows(session)
        }
        PayoutList(session.payoutList)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        if (state.canManage) {
            PosSecondaryButton(
                text = CashStrings.TAKE_MONEY,
                onClick = viewModel::openPayout,
                enabled = !state.hasOverlay,
                modifier = Modifier.focusRequester(takeMoneyFocus),
            )
        } else {
            Text(
                text = CashStrings.NO_MANAGE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        PosSecondaryButton(
            text = if (state.isRefreshing) CashStrings.REFRESHING else CashStrings.REFRESH,
            onClick = viewModel::refresh,
            enabled = !state.isRefreshing && !state.hasOverlay,
            icon = Icons.Filled.Refresh,
        )
        if (state.canManage) {
            Spacer(modifier = Modifier.weight(1f))
            PosPrimaryButton(
                text = CashStrings.CLOSE_DAY,
                onClick = viewModel::openClose,
                enabled = !state.hasOverlay,
            )
        }
    }
}

@Composable
private fun NoticeLine(notice: String?) {
    notice?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun WarningCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = PosSpacing.lg, vertical = PosSpacing.md),
        )
    }
}

@Composable
private fun PayoutList(payouts: List<CashPayout>) {
    if (payouts.isEmpty()) return
    PosSectionCard(title = CashStrings.PAYOUTS_TODAY) {
        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
            payouts.forEach { payout ->
                Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                    Text(
                        text = CashStrings.payoutLine(payout),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    payout.notes?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerRows(session: CashSessionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
        AmountRow(CashStrings.STARTED_WITH, CashStrings.money(session.openingAmount))
        AmountRow(CashStrings.CASH_SALES, CashStrings.money(session.cashSales))
        AmountRow(CashStrings.CREDIT_PAYMENTS, CashStrings.money(session.creditPayments))
        AmountRow(CashStrings.PAYOUTS, CashStrings.minusMoney(session.payouts))
        if (session.otherOut > 0.0) {
            AmountRow(CashStrings.OTHER_OUT, CashStrings.minusMoney(session.otherOut))
        }
    }
}

@Composable
private fun OutsideDrawerRows(session: CashSessionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
        AmountRow(CashStrings.TRANSFERS, CashStrings.money(session.transferSales), secondary = true)
        AmountRow(CashStrings.CARD, CashStrings.money(session.cardSales), secondary = true)
        AmountRow(CashStrings.CREDIT, CashStrings.money(session.creditSales), secondary = true)
    }
}

@Composable
private fun AmountRow(
    label: String,
    amount: String,
    emphasized: Boolean = false,
    secondary: Boolean = false,
) {
    val style = if (secondary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    val weight = if (emphasized) FontWeight.Bold else FontWeight.Normal
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = style, color = muted, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(
            text = amount,
            style = style,
            color = if (secondary) muted else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
        )
    }
}

@Composable
private fun PayoutOverlay(form: PayoutForm, viewModel: CashViewModel, rootFocus: FocusRequester) {
    val amountFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val enter = remember { EnterLatch() }
    val reasons = remember { PayoutReason.entries.map { it to CashStrings.reasonLabel(it) } }
    val confirm = { if (form.canConfirm) viewModel.confirmPayout() }
    LaunchedEffect(form.isSubmitting, form.isLocked) {
        runCatching {
            when {
                form.isSubmitting -> rootFocus.requestFocus()
                form.isLocked -> confirmFocus.requestFocus()
                else -> amountFocus.requestFocus()
            }
        }
    }

    ModalScrim {
        Text(
            text = CashStrings.TAKE_MONEY,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        PosTextField(
            value = form.amount,
            onValueChange = viewModel::onPayoutAmountChange,
            label = CashStrings.PAYOUT_AMOUNT_LABEL,
            enabled = !form.isLocked,
            modifier = Modifier.fillMaxWidth().focusRequester(amountFocus).onEnterKey(enter, confirm),
            leadingIcon = { PesoSign() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = amountFieldStyle(),
        )
        Text(
            text = CashStrings.PAYOUT_REASON_LABEL,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PosChoiceRow(
            options = reasons,
            selected = form.reason,
            onSelect = viewModel::onPayoutReasonChange,
            enabled = !form.isLocked,
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.needsNotes) {
            PosTextField(
                value = form.notes,
                onValueChange = viewModel::onPayoutNotesChange,
                label = CashStrings.PAYOUT_NOTES_LABEL,
                enabled = !form.isLocked,
                modifier = Modifier.fillMaxWidth().onEnterKey(enter, confirm),
            )
        }
        form.errorMessage?.let { PosErrorBanner(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = CashStrings.CANCEL,
                onClick = viewModel::dismissPayout,
                enabled = !form.isLocked,
                modifier = Modifier.weight(1f),
            )
            PosPrimaryButton(
                text = CashStrings.payoutButton(form.parsedAmount),
                onClick = viewModel::confirmPayout,
                enabled = form.canConfirm,
                isLoading = form.isSubmitting,
                modifier = Modifier.weight(1f).focusRequester(confirmFocus),
            )
        }
    }
}

@Composable
private fun CloseOverlay(
    state: CashState,
    form: CloseForm,
    session: CashSessionSummary,
    viewModel: CashViewModel,
    rootFocus: FocusRequester,
) {
    val countedFocus = remember { FocusRequester() }
    val enter = remember { EnterLatch() }
    LaunchedEffect(form.isSubmitting) {
        runCatching { if (form.isSubmitting) rootFocus.requestFocus() else countedFocus.requestFocus() }
    }

    ModalScrim {
        Text(
            text = CashStrings.CLOSE_DAY,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (session.openAccounts > 0) {
            WarningCard(CashStrings.closeBlocked(session.openAccounts))
        }
        PosTextField(
            value = form.counted,
            onValueChange = viewModel::onCountedChange,
            label = CashStrings.COUNTED_LABEL,
            enabled = !form.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(countedFocus)
                .onEnterKey(enter) { if (state.canConfirmClose) viewModel.confirmClose() },
            leadingIcon = { PesoSign() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = amountFieldStyle(),
        )
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = RESULT_SLOT_HEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            val difference = state.closeDifference
            if (difference != null) {
                DifferenceText(difference)
            } else {
                Text(
                    text = CashStrings.COUNT_PLACEHOLDER,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        form.errorMessage?.let { PosErrorBanner(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = CashStrings.CANCEL,
                onClick = viewModel::dismissClose,
                enabled = !form.isSubmitting,
                modifier = Modifier.weight(1f),
            )
            PosPrimaryButton(
                text = CashStrings.closeButton(form.parsedCounted?.takeIf { state.canConfirmClose }),
                onClick = viewModel::confirmClose,
                enabled = state.canConfirmClose,
                isLoading = form.isSubmitting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DifferenceText(difference: DrawerDifference) {
    Text(
        text = CashStrings.difference(difference),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = differenceColor(difference),
    )
}

@Composable
private fun differenceColor(difference: DrawerDifference): Color = when (difference) {
    DrawerDifference.Cuadra -> MaterialTheme.colorScheme.tertiary
    is DrawerDifference.Sobran -> MaterialTheme.colorScheme.primary
    is DrawerDifference.Faltan -> MaterialTheme.colorScheme.error
}

@Composable
private fun ColumnScope.DayClosedView(result: CashCloseResult, onDone: () -> Unit) {
    val summary = result.summary
    val doneFocus = remember { FocusRequester() }
    val enter = remember { EnterLatch() }
    LaunchedEffect(Unit) { runCatching { doneFocus.requestFocus() } }

    ScrollingBody {
        PosSectionCard(title = CashStrings.DAY_CLOSED) {
            DifferenceText(result.difference)
            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
                AmountRow(CashStrings.EXPECTED, CashStrings.money(summary.expectedInDrawer), emphasized = true)
                AmountRow(CashStrings.COUNTED, CashStrings.money(result.countedAmount), emphasized = true)
            }
            HorizontalDivider()
            DrawerRows(summary)
        }
        PosSectionCard(title = CashStrings.NOT_IN_DRAWER) {
            OutsideDrawerRows(summary)
        }
        PayoutList(summary.payoutList)
    }
    PosPrimaryButton(
        text = CashStrings.DONE,
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().focusRequester(doneFocus).onEnterKey(enter, onDone),
    )
}

@Composable
private fun UnavailableView(message: String, onRetry: () -> Unit) {
    PosSectionCard {
        Text(
            text = CashStrings.UNAVAILABLE_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        PosErrorBanner(message)
        PosPrimaryButton(text = CashStrings.RETRY, onClick = onRetry, icon = Icons.Filled.Refresh)
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
            text = CashStrings.LOADING,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PesoSign() {
    Text(text = CashStrings.PESO_SIGN, style = amountFieldStyle())
}

@Composable
private fun amountFieldStyle(): TextStyle =
    MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)

@Composable
private fun ModalScrim(content: @Composable ColumnScope.() -> Unit) {
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
            modifier = Modifier.width(OVERLAY_WIDTH).heightIn(max = maxHeight - MODAL_VERTICAL_MARGIN),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(PosSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
                content = content,
            )
        }
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

private const val SCRIM_ALPHA = 0.5f
private val CONTENT_MAX_WIDTH = 720.dp
private val OVERLAY_WIDTH = 560.dp
private val MODAL_VERTICAL_MARGIN = 32.dp
private val RESULT_SLOT_HEIGHT = 56.dp
