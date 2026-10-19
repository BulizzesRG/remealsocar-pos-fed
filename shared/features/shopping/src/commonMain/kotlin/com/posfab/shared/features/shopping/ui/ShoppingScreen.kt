package com.posfab.shared.features.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingProduct
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosDestructiveButton
import com.posfab.shared.ui.components.PosErrorBanner
import com.posfab.shared.ui.components.PosKeyHint
import com.posfab.shared.ui.components.PosLazyColumn
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosScrollableColumn
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel) {
    val state by viewModel.state.collectAsState()
    val inputFocus = remember { FocusRequester() }

    LaunchedEffect(viewModel) { viewModel.load() }

    LaunchedEffect(state.isAdding, state.hasDialog, state.view) {
        if (!state.isAdding && !state.hasDialog && state.view == ShoppingView.Lists) {
            withFrameNanos { }
            runCatching { inputFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> handleEscape(event, viewModel) },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxSize()
                .padding(PosLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            when (val view = state.view) {
                ShoppingView.Lists -> ListsView(state, viewModel, inputFocus)
                is ShoppingView.HistoryDetail -> HistoryDetailView(state, view.id, viewModel)
            }
        }

        state.edit?.let { EditOverlay(it, viewModel) }
        when (val confirm = state.confirm) {
            ShoppingConfirm.Close -> PosConfirmDialog(
                title = ShoppingStrings.CLOSE_TITLE,
                message = ShoppingStrings.CLOSE_TEXT,
                confirmLabel = ShoppingStrings.CLOSE_CONFIRM,
                cancelLabel = ShoppingStrings.KEEP,
                onConfirm = viewModel::confirmClose,
                onDismiss = viewModel::dismissConfirm,
            )
            is ShoppingConfirm.DeleteItem -> PosConfirmDialog(
                title = ShoppingStrings.DELETE_TITLE,
                message = confirm.text,
                confirmLabel = ShoppingStrings.DELETE_CONFIRM,
                cancelLabel = ShoppingStrings.KEEP,
                isDestructive = true,
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::dismissConfirm,
            )
            is ShoppingConfirm.Finish -> FinishOverlay(confirm, state.isConfirming, viewModel)
            null -> Unit
        }
    }
}

private fun handleEscape(event: KeyEvent, viewModel: ShoppingViewModel): Boolean {
    if (event.key != Key.Escape || event.type != KeyEventType.KeyDown) return false
    return viewModel.onEscape()
}

@Composable
private fun ColumnScope.ListsView(state: ShoppingState, viewModel: ShoppingViewModel, inputFocus: FocusRequester) {
    Header(
        title = ShoppingStrings.TITLE,
        onRefresh = viewModel::refresh,
        refreshing = state.isLoading,
    )
    MessageSlot(state.notice, state.errorMessage)
    CaptureCard(state, viewModel, inputFocus)
    PosLazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        contentPadding = PaddingValues(bottom = PosSpacing.lg),
    ) {
        item(key = "open") { OpenSection(state, viewModel) }
        state.toShop?.let { toShop ->
            item(key = "toShop") { ToShopSection(state, toShop, viewModel) }
        }
        item(key = "history") { HistorySection(state, viewModel) }
    }
}

@Composable
private fun Header(title: String, onRefresh: () -> Unit, refreshing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        PosSecondaryButton(
            text = ShoppingStrings.REFRESH,
            onClick = onRefresh,
            icon = Icons.Filled.Refresh,
            enabled = !refreshing,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

@Composable
private fun MessageSlot(notice: String?, errorMessage: String?) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_SLOT_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        PosNoticeRow(notice = notice, errorMessage = errorMessage)
    }
}

@Composable
private fun CaptureCard(state: ShoppingState, viewModel: ShoppingViewModel, inputFocus: FocusRequester) {
    PosSectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                label = ShoppingStrings.INPUT_LABEL,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(inputFocus)
                    .onEnterDown(viewModel::onInputSubmit),
            )
            PosTextField(
                value = state.qtyInput,
                onValueChange = viewModel::onQtyChange,
                label = ShoppingStrings.QTY_LABEL,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .width(QTY_FIELD_WIDTH)
                    .onEnterDown(viewModel::onInputSubmit),
            )
            PosPrimaryButton(
                text = ShoppingStrings.ADD,
                onClick = viewModel::addFromButton,
                icon = Icons.Filled.Add,
                isLoading = state.isAdding,
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }
        if (state.suggestions.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                state.suggestions.take(MAX_SUGGESTIONS).forEach { product ->
                    SuggestionRow(product, enabled = !state.isAdding) { viewModel.pickSuggestion(product) }
                }
            }
        }
        state.inputError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        PosKeyHint(ShoppingStrings.INPUT_KEYS)
    }
}

@Composable
private fun SuggestionRow(product: ShoppingProduct, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SUGGESTION_ROW_HEIGHT)
            .clickable(enabled = enabled, onClick = onClick)
            .focusProperties { canFocus = false }
            .padding(horizontal = PosSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(SMALL_ICON),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            product.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        product.barcode?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OpenSection(state: ShoppingState, viewModel: ShoppingViewModel) {
    val open = state.open
    PosSectionCard(
        modifier = Modifier.fillMaxWidth(),
        title = open?.let(viewModel::titleOf),
        actions = if (open != null) {
            {
                Text(
                    ShoppingStrings.itemCount(open.itemCount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (state.showCloseButton) {
                    PosPrimaryButton(
                        text = ShoppingStrings.CLOSE,
                        onClick = viewModel::requestClose,
                        enabled = !state.hasDialog,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }
            }
        } else {
            null
        },
    ) {
        when {
            open == null && state.isLoading -> LoadingRow()
            open == null || open.items.isEmpty() -> Text(
                ShoppingStrings.EMPTY_OPEN,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> open.items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                OpenItemRow(item, enabled = !state.hasDialog) { viewModel.startEdit(item) }
            }
        }
    }
}

@Composable
private fun OpenItemRow(item: ShoppingItem, enabled: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ITEM_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemTexts(item, secondary = secondaryLine(item), modifier = Modifier.weight(1f))
        TextButton(
            onClick = onFix,
            enabled = enabled,
            modifier = Modifier.heightIn(min = PosLayout.touchTarget).focusProperties { canFocus = false },
        ) {
            Text(ShoppingStrings.FIX, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ItemTexts(item: ShoppingItem, secondary: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.isLinked) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(SMALL_ICON),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                ShoppingStrings.itemLine(item),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (secondary.isNotBlank()) {
            Text(
                secondary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToShopSection(state: ShoppingState, toShop: ShoppingList, viewModel: ShoppingViewModel) {
    PosSectionCard(
        modifier = Modifier.fillMaxWidth(),
        title = "${ShoppingStrings.TO_SHOP} · ${viewModel.titleOf(toShop)}",
        actions = {
            Text(
                ShoppingStrings.progress(toShop.boughtCount, toShop.itemCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        toShop.items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = ITEM_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemTexts(item, secondary = secondaryLine(item), modifier = Modifier.weight(1f))
                val enabled = state.busyItemId != item.id && !state.hasDialog
                StatusToggle(
                    text = ShoppingStrings.BOUGHT,
                    selected = item.status == ShoppingItemStatus.BOUGHT,
                    enabled = enabled,
                    onClick = { viewModel.setStatus(item, ShoppingItemStatus.BOUGHT) },
                )
                StatusToggle(
                    text = ShoppingStrings.NOT_FOUND,
                    selected = item.status == ShoppingItemStatus.NOT_FOUND,
                    enabled = enabled,
                    onClick = { viewModel.setStatus(item, ShoppingItemStatus.NOT_FOUND) },
                )
            }
        }
        if (state.showFinishButton) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PosPrimaryButton(
                    text = ShoppingStrings.FINISH,
                    onClick = viewModel::requestFinish,
                    enabled = !state.hasDialog,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            }
        }
    }
}

@Composable
private fun StatusToggle(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.widthIn(min = STATUS_BUTTON_MIN_WIDTH).focusProperties { canFocus = false }
    if (selected) {
        PosPrimaryButton(text = text, onClick = onClick, icon = Icons.Filled.Check, enabled = enabled, modifier = modifier)
    } else {
        PosSecondaryButton(text = text, onClick = onClick, enabled = enabled, modifier = modifier)
    }
}

@Composable
private fun HistorySection(state: ShoppingState, viewModel: ShoppingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        SectionToggle(
            text = ShoppingStrings.historyToggle(if (state.historyLoaded) state.history.size else null),
            expanded = state.showHistory,
            onClick = viewModel::toggleHistory,
        )
        if (state.showHistory) {
            PosSectionCard(modifier = Modifier.fillMaxWidth()) {
                val historyError = state.historyError
                when {
                    state.isLoadingHistory -> LoadingRow()
                    historyError != null -> ErrorWithRetry(historyError, viewModel::loadHistory)
                    state.history.isEmpty() -> Text(
                        ShoppingStrings.NO_HISTORY,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> state.history.forEachIndexed { index, summary ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PosLayout.touchTarget)
                                .clickable { viewModel.openHistory(summary.id) }
                                .focusProperties { canFocus = false }
                                .padding(horizontal = PosSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                viewModel.historyLabel(summary),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.HistoryDetailView(state: ShoppingState, id: Long, viewModel: ShoppingViewModel) {
    val detail = state.historyDetail
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosSecondaryButton(
            text = ShoppingStrings.BACK,
            onClick = viewModel::backFromHistory,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            shortcut = ESC_HINT,
        )
        Text(
            detail?.let(viewModel::titleOf) ?: ShoppingStrings.HISTORY,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Text(
                ShoppingStrings.progress(detail.boughtCount, detail.itemCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    PosLazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        contentPadding = PaddingValues(bottom = PosSpacing.lg),
    ) {
        item(key = "detail") {
            PosSectionCard(modifier = Modifier.fillMaxWidth()) {
                val detailError = state.detailError
                when {
                    state.isLoadingDetail -> LoadingRow()
                    detailError != null -> ErrorWithRetry(detailError) { viewModel.openHistory(id) }
                    detail == null || detail.items.isEmpty() -> Text(
                        ShoppingStrings.itemCount(0),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> detail.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = ITEM_ROW_HEIGHT),
                            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ItemTexts(item, secondary = secondaryLine(item), modifier = Modifier.weight(1f))
                            StatusLabel(item.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(status: ShoppingItemStatus) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        ShoppingItemStatus.BOUGHT -> colors.tertiaryContainer to colors.onTertiaryContainer
        ShoppingItemStatus.NOT_FOUND -> colors.errorContainer to colors.onErrorContainer
        ShoppingItemStatus.PENDING -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            ShoppingStrings.historyStatus(status),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.xs),
        )
    }
}

@Composable
private fun EditOverlay(edit: ItemEditState, viewModel: ShoppingViewModel) {
    val textFocus = remember { FocusRequester() }
    LaunchedEffect(edit.id) {
        withFrameNanos { }
        runCatching { textFocus.requestFocus() }
    }
    val canSave = edit.hasChanges && !edit.isSaving

    ModalScrim {
        Text(ShoppingStrings.EDIT_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        PosTextField(
            value = edit.text,
            onValueChange = viewModel::updateEditText,
            label = ShoppingStrings.EDIT_TEXT_LABEL,
            enabled = !edit.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(textFocus)
                .onEnterDown(viewModel::saveEdit),
        )
        PosTextField(
            value = edit.qtyInput,
            onValueChange = viewModel::updateEditQty,
            label = ShoppingStrings.EDIT_QTY_LABEL,
            enabled = !edit.isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .width(QTY_FIELD_WIDTH)
                .onEnterDown(viewModel::saveEdit),
        )
        edit.error?.let { PosErrorBanner(it) }
        PosKeyHint(ShoppingStrings.EDIT_KEYS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosDestructiveButton(
                text = ShoppingStrings.REMOVE,
                onClick = { viewModel.requestDelete(edit.id) },
                enabled = !edit.isSaving,
            )
            Spacer(Modifier.weight(1f))
            PosSecondaryButton(
                text = ShoppingStrings.CANCEL,
                onClick = viewModel::cancelEdit,
                enabled = !edit.isSaving,
            )
            PosPrimaryButton(
                text = ShoppingStrings.SAVE,
                onClick = viewModel::saveEdit,
                enabled = canSave,
                isLoading = edit.isSaving,
            )
        }
    }
}

@Composable
private fun FinishOverlay(confirm: ShoppingConfirm.Finish, isConfirming: Boolean, viewModel: ShoppingViewModel) {
    ModalScrim {
        Text(ShoppingStrings.FINISH_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (confirm.hasCandidates) ShoppingStrings.FINISH_TEXT else ShoppingStrings.FINISH_ALL_BOUGHT,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (confirm.hasCandidates) {
            Column {
                confirm.candidates.forEach { item ->
                    val checked = item.id in confirm.carry
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PosLayout.touchTarget)
                            .clickable(enabled = !isConfirming) { viewModel.toggleCarry(item.id) },
                        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { viewModel.toggleCarry(item.id) },
                            enabled = !isConfirming,
                        )
                        Text(
                            ShoppingStrings.itemLine(item),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.status == ShoppingItemStatus.NOT_FOUND) {
                            Text(
                                ShoppingStrings.NOT_FOUND,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            PosSecondaryButton(
                text = ShoppingStrings.KEEP,
                onClick = viewModel::dismissConfirm,
                enabled = !isConfirming,
                modifier = Modifier.weight(1f),
            )
            PosPrimaryButton(
                text = ShoppingStrings.FINISH_CONFIRM,
                onClick = viewModel::confirmFinish,
                isLoading = isConfirming,
                modifier = Modifier.weight(1f),
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
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        PosSecondaryButton(
            text = ShoppingStrings.RETRY,
            onClick = onRetry,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

@Composable
private fun LoadingRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(SPINNER_SIZE))
        Text(ShoppingStrings.LOADING, style = MaterialTheme.typography.bodyLarge)
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

private fun secondaryLine(item: ShoppingItem): String = listOfNotNull(
    ShoppingStrings.byLine(item.createdBy).takeIf { it.isNotBlank() },
    ShoppingStrings.CARRIED.takeIf { item.isCarried },
).joinToString(SEPARATOR)

private fun Modifier.onEnterDown(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) action()
    true
}

private const val SEPARATOR = " · "
private const val ESC_HINT = "Esc"
private const val MAX_SUGGESTIONS = 5
private const val SCRIM_ALPHA = 0.5f
private val CONTENT_MAX_WIDTH = 1000.dp
private val MESSAGE_SLOT_HEIGHT = 28.dp
private val QTY_FIELD_WIDTH = 140.dp
private val SUGGESTION_ROW_HEIGHT = 44.dp
private val ITEM_ROW_HEIGHT = 56.dp
private val STATUS_BUTTON_MIN_WIDTH = 132.dp
private val SMALL_ICON = 18.dp
private val SPINNER_SIZE = 24.dp
private val OVERLAY_WIDTH = 560.dp
private val MODAL_MARGIN = 32.dp
