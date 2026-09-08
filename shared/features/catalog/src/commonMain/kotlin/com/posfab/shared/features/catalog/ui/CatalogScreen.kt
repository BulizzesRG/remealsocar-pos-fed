package com.posfab.shared.features.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.CatalogUnit
import com.posfab.shared.features.catalog.domain.PriceHistoryEntry
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosMoneyText
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun CatalogScreen(viewModel: CatalogViewModel) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT — Product list
            ProductListPanel(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onFilterChange = viewModel::onActiveFilterChange,
                onSelectProduct = viewModel::selectProduct,
                onNewProduct = viewModel::startCreateProduct,
                onQuickAdd = viewModel::openQuickAdd,
                onPrevPage = viewModel::goPrevPage,
                onNextPage = viewModel::goNextPage,
                onBarcodeInputChange = viewModel::onBarcodeCheckInputChange,
                onBarcodeSearch = viewModel::checkBarcode,
                modifier = Modifier.width(380.dp).fillMaxHeight(),
            )

            VerticalDivider()

            // RIGHT — Detail / Form
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (state.formMode) {
                    CatalogFormMode.IDLE -> EmptyDetailState(
                        total = state.total,
                        onNewProduct = viewModel::startCreateProduct,
                        onQuickAdd = viewModel::openQuickAdd,
                    )
                    CatalogFormMode.CREATING, CatalogFormMode.EDITING -> ProductFormPanel(
                        state = state,
                        onNameChange = viewModel::onFormNameChange,
                        onSkuChange = viewModel::onFormSkuChange,
                        onBarcodeChange = viewModel::onFormBarcodeChange,
                        onBaseUnitChange = viewModel::onFormBaseUnitChange,
                        onLotTrackedChange = viewModel::onFormLotTrackedChange,
                        onActiveChange = viewModel::onFormActiveChange,
                        onInitialPriceChange = viewModel::onFormInitialPriceChange,
                        onNewPriceChange = viewModel::onNewPriceChange,
                        onAddPrice = viewModel::addPriceEntry,
                        onNewUomUnitChange = viewModel::onNewUomUnitChange,
                        onNewUomFactorChange = viewModel::onNewUomFactorChange,
                        onAddUom = viewModel::addUomConversion,
                        onSave = viewModel::saveProduct,
                        onCancel = viewModel::cancelForm,
                        onDeselect = viewModel::cancelForm,
                        onToggleAdvanced = viewModel::toggleAdvancedFields,
                    )
                }
            }
        }

        // Quick-add dialog
        if (state.showQuickAddDialog) {
            QuickAddDialog(
                name = state.quickAddName,
                price = state.quickAddPrice,
                unit = state.quickAddUnit,
                units = state.units,
                isLoading = state.isQuickAdding,
                errorMessage = if (state.showQuickAddDialog) state.errorMessage else null,
                onNameChange = viewModel::onQuickAddNameChange,
                onPriceChange = viewModel::onQuickAddPriceChange,
                onUnitChange = viewModel::onQuickAddUnitChange,
                onSubmit = viewModel::submitQuickAdd,
                onDismiss = viewModel::closeQuickAdd,
            )
        }

        // Lot tracking warning dialog
        if (state.showLotTrackingWarning) {
            PosConfirmDialog(
                title = "Activar control de lote",
                message = "Al activar esto, cada venta de este producto requerira ingresar un numero de lote. Asegurate de tener lotes registrados en Operaciones antes de vender.",
                confirmLabel = "Activar",
                onConfirm = viewModel::confirmLotTracking,
                onDismiss = viewModel::dismissLotTrackingWarning,
            )
        }
    }
}

// ─── Left panel ──────────────────────────────────────────────────────────────

@Composable
private fun ProductListPanel(
    state: CatalogState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ActiveFilter) -> Unit,
    onSelectProduct: (String) -> Unit,
    onNewProduct: () -> Unit,
    onQuickAdd: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onBarcodeInputChange: (String) -> Unit,
    onBarcodeSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(PosSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        // Header
        Text(
            text = "Productos",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        // Action buttons — full width, stacked to avoid clipping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            PosPrimaryButton(
                text = "+ Nuevo",
                onClick = onNewProduct,
                modifier = Modifier.weight(1f),
            )
            PosSecondaryButton(
                text = "Rapido",
                onClick = onQuickAdd,
                modifier = Modifier.weight(1f),
            )
        }

        // Search
        PosTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = "Buscar por nombre...",
            modifier = Modifier.fillMaxWidth(),
        )

        // Barcode lookup — compact single row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosTextField(
                value = state.barcodeCheckInput,
                onValueChange = onBarcodeInputChange,
                label = "Codigo de barras",
                modifier = Modifier.weight(1f),
            )
            PosSecondaryButton(
                text = "Buscar",
                onClick = onBarcodeSearch,
                enabled = state.barcodeCheckInput.isNotBlank() && !state.isSaving,
                modifier = Modifier.width(80.dp),
            )
        }

        // Filters
        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            FilterChip(
                selected = state.activeFilter == ActiveFilter.ALL,
                onClick = { onFilterChange(ActiveFilter.ALL) },
                label = { Text("Todos") },
            )
            FilterChip(
                selected = state.activeFilter == ActiveFilter.ACTIVE_ONLY,
                onClick = { onFilterChange(ActiveFilter.ACTIVE_ONLY) },
                label = { Text("Activos") },
            )
            FilterChip(
                selected = state.activeFilter == ActiveFilter.INACTIVE_ONLY,
                onClick = { onFilterChange(ActiveFilter.INACTIVE_ONLY) },
                label = { Text("Inactivos") },
            )
        }

        // Pagination text + divider
        Text(
            text = state.paginationText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        // List
        if (state.isLoadingList) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                items(state.products) { product ->
                    ProductListItem(
                        product = product,
                        isSelected = state.selectedProduct?.id == product.id,
                        onClick = { onSelectProduct(product.id) },
                    )
                }
            }
        }

        // Pagination buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            PosSecondaryButton(
                text = "\u2190 Anterior",
                onClick = onPrevPage,
                enabled = state.canGoPrevPage,
                modifier = Modifier.weight(1f),
            )
            PosSecondaryButton(
                text = "Siguiente \u2192",
                onClick = onNextPage,
                enabled = state.canGoNextPage,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProductListItem(
    product: CatalogProductSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    val inactiveBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    val showInactiveBorder = !product.active

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (showInactiveBorder) {
                    Modifier.drawBehind {
                        drawRect(
                            color = inactiveBorderColor,
                            topLeft = Offset.Zero,
                            size = Size(4.dp.toPx(), size.height),
                        )
                    }
                } else Modifier
            ),
        color = bgColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (showInactiveBorder) PosSpacing.lg else PosSpacing.md,
                end = PosSpacing.md,
                top = PosSpacing.sm,
                bottom = PosSpacing.sm,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(product.baseUnit)
                        if (!product.barcode.isNullOrBlank()) append("  \u2022  ${product.barcode}")
                        if (!product.active) append("  \u2022  Inactivo")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(PosSpacing.sm))
            if (product.currentPrice != null && product.currentPrice > 0.0) {
                PosMoneyText(
                    amount = product.currentPrice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = "Sin precio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyDetailState(
    total: Int,
    onNewProduct: () -> Unit,
    onQuickAdd: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            )
            Text(
                text = "Catalogo de productos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (total > 0) {
                Text(
                    text = "$total producto${if (total != 1) "s" else ""} registrado${if (total != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Text(
                text = "Selecciona un producto o crea uno nuevo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(PosSpacing.md))
            PosPrimaryButton(
                text = "+ Nuevo producto",
                onClick = onNewProduct,
                modifier = Modifier.width(220.dp),
            )
            PosSecondaryButton(
                text = "+ Agregar rapido",
                onClick = onQuickAdd,
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

// ─── Form panel ──────────────────────────────────────────────────────────────

@Composable
private fun ProductFormPanel(
    state: CatalogState,
    onNameChange: (String) -> Unit,
    onSkuChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onBaseUnitChange: (String) -> Unit,
    onLotTrackedChange: (Boolean) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onInitialPriceChange: (String) -> Unit,
    onNewPriceChange: (String) -> Unit,
    onAddPrice: () -> Unit,
    onNewUomUnitChange: (String) -> Unit,
    onNewUomFactorChange: (String) -> Unit,
    onAddUom: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDeselect: () -> Unit,
    onToggleAdvanced: () -> Unit,
) {
    val isEditing = state.formMode == CatalogFormMode.EDITING

    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(PosSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        ) {
            // Form header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditing) state.selectedProduct?.name ?: "" else "Nuevo producto",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isEditing) {
                        val price = state.selectedProduct?.currentPrice
                        Text(
                            text = if (price != null && price > 0.0) "Precio actual: $${"%.2f".format(price)}" else "Sin precio registrado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onDeselect) {
                    Text("\u2715 Cerrar")
                }
            }

            // Messages
            PosNoticeRow(notice = state.notice, errorMessage = if (!state.showQuickAddDialog) state.errorMessage else null)

            if (state.isLoadingDetail) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Cargando...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Section 1: Informacion principal ──
            PosSectionCard(
                title = "Informacion principal",
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 1. Name (first, most important)
                PosTextField(
                    value = state.form.name,
                    onValueChange = onNameChange,
                    label = "Nombre del producto *",
                    modifier = Modifier.fillMaxWidth(),
                )

                // 2. Price (second — industry standard)
                if (isEditing) {
                    CurrentPriceDisplay(
                        currentPrice = state.selectedProduct?.currentPrice,
                        newPriceInput = state.newPriceInput,
                        onNewPriceChange = onNewPriceChange,
                        onAddPrice = onAddPrice,
                        isSaving = state.isSaving,
                    )
                } else {
                    PosTextField(
                        value = state.form.initialPrice,
                        onValueChange = onInitialPriceChange,
                        label = "Precio de venta",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 3. Barcode (optional)
                PosTextField(
                    value = state.form.barcode,
                    onValueChange = onBarcodeChange,
                    label = "Codigo de barras (opcional)",
                    modifier = Modifier.fillMaxWidth(),
                )

                // 4. Unit dropdown
                UnitDropdown(
                    units = state.units,
                    selected = state.form.baseUnit,
                    onSelect = onBaseUnitChange,
                    label = "Unidad de venta *",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Section 2: Datos adicionales (collapsed in create mode) ──
            AdvancedFieldsSection(
                isEditing = isEditing,
                showAdvanced = state.showAdvancedFields,
                sku = state.form.sku,
                onSkuChange = onSkuChange,
                onToggle = onToggleAdvanced,
            )

            // ── Section 3: Presentaciones (edit only) ──
            if (isEditing) {
                PresentacionesSection(
                    baseUnit = state.selectedProduct?.baseUnit ?: "pieza",
                    uoms = state.selectedProduct?.uoms.orEmpty(),
                    units = state.units,
                    newUomUnit = state.newUomUnit,
                    newUomFactorInput = state.newUomFactorInput,
                    onNewUomUnitChange = onNewUomUnitChange,
                    onNewUomFactorChange = onNewUomFactorChange,
                    onAddUom = onAddUom,
                    isSaving = state.isSaving,
                )
            }

            // ── Section 4: Historial de precios (edit only) ──
            if (isEditing) {
                val history = state.selectedProduct?.priceHistory.orEmpty()
                if (history.isNotEmpty()) {
                    PriceHistorySection(history = history)
                }
            }

            // ── Section 5: Estado ──
            PosSectionCard(
                title = "Estado",
                modifier = Modifier.fillMaxWidth(),
            ) {
                LabeledSwitch(
                    label = "Producto activo",
                    description = "Disponible para venta",
                    checked = state.form.active,
                    onCheckedChange = onActiveChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledSwitch(
                    label = "Control de lote",
                    description = "Requiere numero de lote en ventas",
                    checked = state.form.lotTracked,
                    onCheckedChange = onLotTrackedChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Bottom spacer so content doesn't hide behind sticky bar
            Spacer(Modifier.height(PosSpacing.xl))
        }

        // Sticky bottom save bar
        if (state.hasUnsavedChanges || state.formMode == CatalogFormMode.CREATING) {
            StickyActionBar(
                isEditing = isEditing,
                isSaving = state.isSaving,
                onSave = onSave,
                onCancel = onCancel,
            )
        }
    }
}

// ─── Sticky save bar ─────────────────────────────────────────────────────────

@Composable
private fun StickyActionBar(
    isEditing: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PosSpacing.xl, vertical = PosSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cambios sin guardar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.weight(1f))
            PosSecondaryButton(
                text = "Cancelar",
                onClick = onCancel,
                modifier = Modifier.width(120.dp),
            )
            PosPrimaryButton(
                text = if (isEditing) "Guardar cambios" else "Crear producto",
                onClick = onSave,
                enabled = !isSaving,
                isLoading = isSaving,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}

// ─── Current price display (edit mode) ───────────────────────────────────────

@Composable
private fun CurrentPriceDisplay(
    currentPrice: Double?,
    newPriceInput: String,
    onNewPriceChange: (String) -> Unit,
    onAddPrice: () -> Unit,
    isSaving: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        // Current price
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Precio actual",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (currentPrice != null && currentPrice > 0.0) {
                PosMoneyText(currentPrice, style = MaterialTheme.typography.titleLarge)
            } else {
                Text(
                    "Sin precio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        // Change price
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosTextField(
                value = newPriceInput,
                onValueChange = onNewPriceChange,
                label = "Nuevo precio",
                modifier = Modifier.weight(1f),
            )
            PosSecondaryButton(
                text = "Cambiar",
                onClick = onAddPrice,
                enabled = !isSaving && newPriceInput.isNotBlank(),
                modifier = Modifier.width(100.dp),
            )
        }
    }
}

// ─── Advanced fields (SKU, collapsed) ────────────────────────────────────────

@Composable
private fun AdvancedFieldsSection(
    isEditing: Boolean,
    showAdvanced: Boolean,
    sku: String,
    onSkuChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    val shouldShow = isEditing || showAdvanced

    PosSectionCard(
        title = "Datos adicionales",
        modifier = Modifier.fillMaxWidth(),
        actions = {
            if (!isEditing) {
                Text(
                    text = if (showAdvanced) "Ocultar" else "Mostrar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onToggle),
                )
            }
        },
    ) {
        if (shouldShow) {
            PosTextField(
                value = sku,
                onValueChange = onSkuChange,
                label = "SKU / Codigo interno (opcional)",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "Codigo interno, notas adicionales",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Presentaciones (renamed from UOM) ───────────────────────────────────────

@Composable
private fun PresentacionesSection(
    baseUnit: String,
    uoms: List<com.posfab.shared.features.catalog.domain.ProductUomConversion>,
    units: List<CatalogUnit>,
    newUomUnit: String,
    newUomFactorInput: String,
    onNewUomUnitChange: (String) -> Unit,
    onNewUomFactorChange: (String) -> Unit,
    onAddUom: () -> Unit,
    isSaving: Boolean,
) {
    PosSectionCard(
        title = "Presentaciones",
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (uoms.isEmpty()) {
            Text(
                "Solo se vende por $baseUnit. Agrega presentaciones si el producto tambien se vende en paquetes o cajas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Table header
            Row(
                modifier = Modifier.fillMaxWidth().background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.small,
                ).padding(horizontal = PosSpacing.md, vertical = PosSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Presentacion", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Contenido", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            uoms.forEachIndexed { index, uom ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(uom.unit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val factor = if (uom.factorToBase == uom.factorToBase.toLong().toDouble()) {
                        uom.factorToBase.toLong().toString()
                    } else {
                        uom.factorToBase.toString()
                    }
                    Text(
                        "Contiene $factor $baseUnit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < uoms.lastIndex) HorizontalDivider()
            }
        }

        HorizontalDivider()

        Text(
            "Agregar presentacion",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnitDropdown(
                units = units,
                selected = newUomUnit,
                onSelect = onNewUomUnitChange,
                label = "Presentacion",
                modifier = Modifier.width(200.dp),
            )
            PosTextField(
                value = newUomFactorInput,
                onValueChange = onNewUomFactorChange,
                label = "Contiene cuantas $baseUnit",
                modifier = Modifier.width(220.dp),
            )
            PosSecondaryButton(
                text = "Agregar",
                onClick = onAddUom,
                enabled = !isSaving,
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

// ─── Price history ───────────────────────────────────────────────────────────

@Composable
private fun PriceHistorySection(history: List<PriceHistoryEntry>) {
    PosSectionCard(
        title = "Historial de precios",
        modifier = Modifier.fillMaxWidth(),
    ) {
        history.take(5).forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatHumanDate(entry.effectiveAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PosMoneyText(entry.price, style = MaterialTheme.typography.bodyMedium)
            }
            if (index < history.take(5).lastIndex) HorizontalDivider()
        }
    }
}

// ─── Quick-add dialog ────────────────────────────────────────────────────────

@Composable
private fun QuickAddDialog(
    name: String,
    price: String,
    unit: String,
    units: List<CatalogUnit>,
    isLoading: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                "Agregar producto rapido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
                Text(
                    "Solo nombre, precio y unidad. Podras agregar mas detalles despues.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PosTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = "Nombre del producto *",
                    modifier = Modifier.fillMaxWidth(),
                )
                PosTextField(
                    value = price,
                    onValueChange = onPriceChange,
                    label = "Precio de venta *",
                    modifier = Modifier.fillMaxWidth(),
                )
                UnitDropdown(
                    units = units,
                    selected = unit,
                    onSelect = onUnitChange,
                    label = "Unidad de venta",
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            PosPrimaryButton(
                text = "Crear",
                onClick = onSubmit,
                enabled = !isLoading,
                isLoading = isLoading,
                modifier = Modifier.width(100.dp),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancelar") }
        },
    )
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    units: List<CatalogUnit>,
    selected: String,
    onSelect: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = units.find { it.code == selected }
        ?.let { "${it.code} \u2013 ${it.name}" }
        ?: selected.ifBlank { "Seleccionar..." }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text("${unit.code} \u2013 ${unit.name}") },
                    onClick = {
                        onSelect(unit.code)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            if (units.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Sin unidades disponibles", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun formatHumanDate(isoString: String): String {
    val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    return try {
        val normalized = isoString.replace(" ", "T")
        val parts = normalized.split("T")
        val dateParts = parts[0].split("-")
        val timeParts = parts.getOrElse(1) { "00:00:00" }.split(":")
        val year = dateParts[0]
        val month = months[dateParts[1].toInt() - 1]
        val day = dateParts[2].toInt()
        val hour = timeParts[0].toInt()
        val minute = timeParts[1]
        val ampm = if (hour < 12) "am" else "pm"
        val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        "$day $month $year, $hour12:$minute $ampm"
    } catch (_: Exception) {
        isoString.take(19).replace("T", " ")
    }
}
