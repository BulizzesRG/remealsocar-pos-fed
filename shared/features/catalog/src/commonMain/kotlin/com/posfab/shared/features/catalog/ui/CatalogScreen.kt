package com.posfab.shared.features.catalog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.catalog.common.priceHistoryDateLabel
import com.posfab.shared.features.catalog.common.priceHistoryLine
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.KitchenKind
import com.posfab.shared.features.catalog.domain.PriceType
import com.posfab.shared.ui.components.PosChoiceRow
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

private val ListPaneWidth = 380.dp

@Composable
fun CatalogScreen(viewModel: CatalogViewModel) {
    val state by viewModel.state.collectAsState()
    val searchFocus = remember { FocusRequester() }
    var searchHasFocus by remember { mutableStateOf(false) }
    var wasSaving by remember { mutableStateOf(false) }

    LaunchedEffect(state.mode) {
        if (state.mode == CatalogFormMode.IDLE) searchFocus.requestFocusSafely()
    }
    LaunchedEffect(state.isSaving) {
        if (wasSaving && !state.isSaving && state.errorMessage == null) searchFocus.requestFocusSafely()
        wasSaving = state.isSaving
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (state.confirm == null && searchHasFocus && isEnter && event.type == KeyEventType.KeyUp) {
                    viewModel.onSearchSubmit()
                    true
                } else {
                    false
                }
            },
    ) {
        ProductListPane(
            state = state,
            searchFocus = searchFocus,
            onSearchFocusChange = { searchHasFocus = it },
            onNew = { viewModel.startCreate() },
            onQueryChange = viewModel::onQueryChange,
            onActiveOnlyChange = viewModel::onActiveOnlyChange,
            onSelect = viewModel::selectProduct,
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.width(ListPaneWidth).fillMaxHeight(),
        )
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (state.mode == CatalogFormMode.IDLE) {
                EmptyDetail(state)
            } else {
                ProductFormPane(state = state, viewModel = viewModel)
            }
        }
    }

    state.confirm?.let { confirm ->
        CatalogConfirmDialog(
            confirm = confirm,
            state = state,
            onConfirmSave = viewModel::confirmSave,
            onConfirmDiscard = viewModel::confirmDiscard,
            onDismiss = viewModel::dismissConfirm,
        )
    }
}

@Composable
private fun CatalogConfirmDialog(
    confirm: CatalogConfirm,
    state: CatalogState,
    onConfirmSave: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = state.form.name.trim().ifBlank { state.selected?.name.orEmpty() }
    when (confirm) {
        CatalogConfirm.Save -> {
            val creating = state.mode == CatalogFormMode.CREATING
            PosConfirmDialog(
                title = if (creating) CatalogStrings.CONFIRM_CREATE_TITLE else CatalogStrings.CONFIRM_EDIT_TITLE,
                message = if (creating) CatalogStrings.confirmCreateMessage(name) else CatalogStrings.confirmEditMessage(name),
                confirmLabel = if (creating) CatalogStrings.CONFIRM_CREATE else CatalogStrings.CONFIRM_EDIT,
                cancelLabel = CatalogStrings.CONFIRM_BACK,
                onConfirm = onConfirmSave,
                onDismiss = onDismiss,
            )
        }
        is CatalogConfirm.Discard -> PosConfirmDialog(
            title = CatalogStrings.DISCARD_TITLE,
            message = CatalogStrings.discardMessage(name),
            confirmLabel = CatalogStrings.DISCARD_CONFIRM,
            cancelLabel = CatalogStrings.DISCARD_KEEP,
            isDestructive = true,
            onConfirm = onConfirmDiscard,
            onDismiss = onDismiss,
        )
    }
}

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}

@Composable
private fun ProductListPane(
    state: CatalogState,
    searchFocus: FocusRequester,
    onSearchFocusChange: (Boolean) -> Unit,
    onNew: () -> Unit,
    onQueryChange: (String) -> Unit,
    onActiveOnlyChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(PosLayout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        PosPrimaryButton(
            text = CatalogStrings.NEW_PRODUCT,
            onClick = onNew,
            icon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        PosTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = CatalogStrings.SEARCH_LABEL,
            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocus)
                .onFocusChanged { onSearchFocusChange(it.isFocused) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            FilterChip(
                selected = state.activeOnly,
                onClick = { onActiveOnlyChange(true) },
                label = { Text(CatalogStrings.FILTER_SELLING) },
            )
            FilterChip(
                selected = !state.activeOnly,
                onClick = { onActiveOnlyChange(false) },
                label = { Text(CatalogStrings.FILTER_ALL) },
            )
        }
        if (state.isLoadingList) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.products.isEmpty() && !state.isLoadingList) {
                Text(
                    text = if (state.query.isBlank()) CatalogStrings.EMPTY_LIST else CatalogStrings.EMPTY_SEARCH,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = PosSpacing.lg),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    items(state.products, key = { it.id }) { product ->
                        ProductRow(
                            product = product,
                            selected = state.selected?.id == product.id,
                            onClick = { onSelect(product.id) },
                        )
                    }
                }
            }
        }
        if (state.products.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = CatalogStrings.showing(state.products.size, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.canLoadMore) {
                    TextButton(onClick = onLoadMore, enabled = !state.isLoadingMore && !state.isLoadingList) {
                        Text(CatalogStrings.LOAD_MORE)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: CatalogProductSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val cash = product.cashTakeaway
            if (product.priceType == PriceType.OPEN || cash == null) {
                Text(
                    text = CatalogStrings.OPEN_PRICE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PosMoneyText(amount = cash, style = MaterialTheme.typography.bodyMedium)
            }
            val chips = productChips(product)
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    chips.forEach { (label, warning) -> SmallChip(label, warning) }
                }
            }
        }
    }
}

private fun productChips(product: CatalogProductSummary): List<Pair<String, Boolean>> = buildList {
    when (product.kitchenKind) {
        KitchenKind.FONDA -> add(CatalogStrings.CHIP_FONDA to false)
        KitchenKind.TORTERIA -> add(CatalogStrings.CHIP_TORTERIA to false)
        null -> Unit
    }
    if (product.priceType == PriceType.WEIGHT) add(CatalogStrings.CHIP_WEIGHT to false)
    if (!product.active) add(CatalogStrings.CHIP_INACTIVE to true)
}

@Composable
private fun SmallChip(label: String, warning: Boolean) {
    val background: Color
    val content: Color
    if (warning) {
        background = MaterialTheme.colorScheme.errorContainer
        content = MaterialTheme.colorScheme.onErrorContainer
    } else {
        background = MaterialTheme.colorScheme.secondaryContainer
        content = MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = background, contentColor = content, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = PosSpacing.sm, vertical = PosSpacing.xxs),
        )
    }
}

@Composable
private fun EmptyDetail(state: CatalogState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PosLayout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        state.errorMessage?.let { PosErrorBanner(it) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = CatalogStrings.EMPTY_DETAIL,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProductFormPane(state: CatalogState, viewModel: CatalogViewModel) {
    val form = state.form
    val moneyKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(PosLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(PosLayout.sectionSpacing),
        ) {
            Text(
                text = if (state.mode == CatalogFormMode.CREATING) CatalogStrings.NEW_TITLE else state.selected?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.errorMessage?.let { PosErrorBanner(it) }
            PosNoticeRow(notice = state.notice, errorMessage = null)

            PosSectionCard {
                PosTextField(
                    value = form.name,
                    onValueChange = viewModel::onNameChange,
                    label = CatalogStrings.NAME,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PosSectionCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosTextField(
                        value = form.barcode,
                        onValueChange = viewModel::onBarcodeChange,
                        label = if (form.generateCodeOnSave) CatalogStrings.CODE_ON_SAVE else CatalogStrings.BARCODE,
                        modifier = Modifier.weight(1f),
                    )
                    if (form.barcode.isBlank()) {
                        PosSecondaryButton(
                            text = when {
                                form.generateCodeOnSave -> CatalogStrings.DONT_GENERATE_CODE
                                state.isGeneratingCode -> CatalogStrings.GENERATING_CODE
                                else -> CatalogStrings.GENERATE_CODE
                            },
                            onClick = viewModel::requestGenerateCode,
                            enabled = !state.isGeneratingCode && !state.isSaving,
                        )
                    }
                }
            }

            PosSectionCard {
                SectionLabel(CatalogStrings.HOW_SOLD)
                PosChoiceRow(
                    options = listOf(
                        PriceType.PIECE to CatalogStrings.BY_PIECE,
                        PriceType.WEIGHT to CatalogStrings.BY_WEIGHT,
                        PriceType.OPEN to CatalogStrings.BY_OPEN_PRICE,
                    ),
                    selected = form.priceType,
                    onSelect = viewModel::onPriceTypeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (form.priceType != PriceType.OPEN) {
                PosSectionCard {
                    PriceRow(
                        cashLabel = CatalogStrings.CASH_TAKEAWAY,
                        cash = form.cashTakeaway,
                        onCashChange = viewModel::onCashTakeawayChange,
                        card = form.listTakeaway,
                        onCardChange = viewModel::onListTakeawayChange,
                        cardEnabled = !form.suggestedPrice,
                        keyboard = moneyKeyboard,
                    )
                    PriceRow(
                        cashLabel = CatalogStrings.CASH_ONSITE,
                        cash = form.cashOnsite,
                        onCashChange = viewModel::onCashOnsiteChange,
                        card = form.listOnsite,
                        onCardChange = viewModel::onListOnsiteChange,
                        cardEnabled = !form.suggestedPrice && form.cashOnsite.isNotBlank(),
                        keyboard = moneyKeyboard,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = form.suggestedPrice, onCheckedChange = viewModel::onSuggestedPriceChange)
                        Text(CatalogStrings.SUGGESTED_PRICE, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = CatalogStrings.cardHint(state.cashDiscountPct),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.mode == CatalogFormMode.EDITING && form.priceType != PriceType.OPEN) {
                PriceHistorySection(state = state, onToggle = viewModel::togglePriceHistory)
            }

            PosSectionCard {
                SectionLabel(CatalogStrings.KITCHEN)
                PosChoiceRow(
                    options = listOf(
                        null to CatalogStrings.KITCHEN_NONE,
                        KitchenKind.FONDA to CatalogStrings.KITCHEN_FONDA,
                        KitchenKind.TORTERIA to CatalogStrings.KITCHEN_TORTERIA,
                    ),
                    selected = form.kitchenKind,
                    onSelect = viewModel::onKitchenKindChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PosSectionCard {
                TextButton(onClick = viewModel::toggleCountSection) {
                    Text(CatalogStrings.COUNT_SECTION)
                    Icon(
                        imageVector = if (state.showCountSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                if (state.showCountSection) {
                    if (form.priceType != PriceType.OPEN) {
                        LabeledSwitch(
                            label = CatalogStrings.COUNTED,
                            description = CatalogStrings.COUNTED_HINT,
                            checked = form.counted,
                            onCheckedChange = viewModel::onCountedChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (form.priceType != PriceType.WEIGHT) {
                        PosTextField(
                            value = form.piecesPerPack,
                            onValueChange = viewModel::onPiecesPerPackChange,
                            label = CatalogStrings.PIECES_PER_PACK,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            PosSectionCard {
                LabeledSwitch(
                    label = CatalogStrings.ACTIVE,
                    description = CatalogStrings.ACTIVE_HINT,
                    checked = form.active,
                    onCheckedChange = viewModel::onActiveChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(PosLayout.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.hasUnsavedChanges) {
                Text(
                    text = CatalogStrings.UNSAVED_CHANGES,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            PosSecondaryButton(
                text = CatalogStrings.CANCEL,
                onClick = viewModel::cancel,
                enabled = !state.isSaving,
            )
            PosPrimaryButton(
                text = CatalogStrings.SAVE,
                onClick = viewModel::save,
                isLoading = state.isSaving,
                enabled = !state.isGeneratingCode,
            )
        }
    }
}

@Composable
private fun PriceHistorySection(state: CatalogState, onToggle: () -> Unit) {
    PosSectionCard {
        TextButton(onClick = onToggle) {
            Text(CatalogStrings.PRICE_HISTORY)
            Icon(
                imageVector = if (state.showPriceHistory) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (state.showPriceHistory) {
            val history = state.priceHistory
            val error = state.priceHistoryError
            when {
                state.isLoadingPriceHistory -> HistoryText(CatalogStrings.PRICE_HISTORY_LOADING)
                error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                history != null -> {
                    history.currentSince?.let(::priceHistoryDateLabel)?.let {
                        Text(CatalogStrings.currentPriceSince(it), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (history.entries.isEmpty()) {
                        HistoryText(CatalogStrings.PRICE_HISTORY_EMPTY)
                    } else {
                        history.entries.forEach { HistoryText(priceHistoryLine(it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
}

@Composable
private fun PriceRow(
    cashLabel: String,
    cash: String,
    onCashChange: (String) -> Unit,
    card: String,
    onCardChange: (String) -> Unit,
    cardEnabled: Boolean,
    keyboard: KeyboardOptions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        PosTextField(
            value = cash,
            onValueChange = onCashChange,
            label = cashLabel,
            keyboardOptions = keyboard,
            modifier = Modifier.weight(1f),
        )
        PosTextField(
            value = card,
            onValueChange = onCardChange,
            label = CatalogStrings.WITH_CARD,
            enabled = cardEnabled,
            keyboardOptions = keyboard,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
