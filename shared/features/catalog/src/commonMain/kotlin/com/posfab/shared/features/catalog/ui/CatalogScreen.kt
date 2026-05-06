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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
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

    Row(modifier = Modifier.fillMaxSize()) {
        // LEFT — Product list
        ProductListPanel(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onActiveFilterChange,
            onSelectProduct = viewModel::selectProduct,
            onNewProduct = viewModel::startCreateProduct,
            onPrevPage = viewModel::goPrevPage,
            onNextPage = viewModel::goNextPage,
            modifier = Modifier.width(380.dp).fillMaxHeight(),
        )

        VerticalDivider()

        // RIGHT — Detail / Form
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (state.formMode) {
                CatalogFormMode.IDLE -> EmptyDetailState(onNewProduct = viewModel::startCreateProduct)
                CatalogFormMode.CREATING, CatalogFormMode.EDITING -> ProductFormPanel(
                    state = state,
                    onNameChange = viewModel::onFormNameChange,
                    onSkuChange = viewModel::onFormSkuChange,
                    onBarcodeChange = viewModel::onFormBarcodeChange,
                    onBaseUnitChange = viewModel::onFormBaseUnitChange,
                    onLotTrackedChange = viewModel::onFormLotTrackedChange,
                    onActiveChange = viewModel::onFormActiveChange,
                    onNewPriceChange = viewModel::onNewPriceChange,
                    onAddPrice = viewModel::addPriceEntry,
                    onNewUomUnitChange = viewModel::onNewUomUnitChange,
                    onNewUomFactorChange = viewModel::onNewUomFactorChange,
                    onAddUom = viewModel::addUomConversion,
                    onSave = viewModel::saveProduct,
                    onCancel = viewModel::cancelForm,
                )
            }
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
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(PosSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Productos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            PosPrimaryButton(
                text = "Nuevo",
                onClick = onNewProduct,
                icon = Icons.Filled.Add,
                modifier = Modifier.width(110.dp),
            )
        }

        // Search
        PosTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = "Buscar...",
            modifier = Modifier.fillMaxWidth(),
        )

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

        // Count
        Text(
            text = if (state.total == 0) "Sin resultados" else "${state.total} producto${if (state.total != 1) "s" else ""}",
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

        // Pagination
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PosSecondaryButton(
                text = "← Anterior",
                onClick = onPrevPage,
                enabled = state.canGoPrevPage,
                modifier = Modifier.width(130.dp),
            )
            PosSecondaryButton(
                text = "Siguiente →",
                onClick = onNextPage,
                enabled = state.canGoNextPage,
                modifier = Modifier.width(130.dp),
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

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = bgColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
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
                        if (!product.sku.isNullOrBlank()) append("SKU: ${product.sku}  ")
                        append(product.baseUnit)
                        if (!product.active) append("  • Inactivo")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(PosSpacing.sm))
            PosMoneyText(
                amount = product.currentPrice ?: 0.0,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyDetailState(onNewProduct: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            Text(
                text = "Selecciona un producto para editarlo",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "o crea uno nuevo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(PosSpacing.sm))
            PosPrimaryButton(
                text = "+ Nuevo producto",
                onClick = onNewProduct,
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
    onNewPriceChange: (String) -> Unit,
    onAddPrice: () -> Unit,
    onNewUomUnitChange: (String) -> Unit,
    onNewUomFactorChange: (String) -> Unit,
    onAddUom: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val isEditing = state.formMode == CatalogFormMode.EDITING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PosSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.lg),
    ) {
        // ── Form header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.hasUnsavedChanges) {
                    Text(
                        text = "Cambios sin guardar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                PosSecondaryButton(
                    text = "Cancelar",
                    onClick = onCancel,
                    modifier = Modifier.width(120.dp),
                )
                PosPrimaryButton(
                    text = if (isEditing) "Guardar" else "Crear producto",
                    onClick = onSave,
                    enabled = !state.isSaving,
                    isLoading = state.isSaving,
                    modifier = Modifier.width(160.dp),
                )
            }
        }

        HorizontalDivider()

        // ── Sección 1: Información básica ──
        PosSectionCard(
            title = "Información básica",
            modifier = Modifier.fillMaxWidth(),
        ) {
            PosTextField(
                value = state.form.name,
                onValueChange = onNameChange,
                label = "Nombre *",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) {
                PosTextField(
                    value = state.form.sku,
                    onValueChange = onSkuChange,
                    label = "SKU (opcional)",
                    modifier = Modifier.weight(1f),
                )
                PosTextField(
                    value = state.form.barcode,
                    onValueChange = onBarcodeChange,
                    label = "Código de barras (opcional)",
                    modifier = Modifier.weight(1f),
                )
            }

            // Unit dropdown
            UnitDropdown(
                units = state.units,
                selected = state.form.baseUnit,
                onSelect = onBaseUnitChange,
                label = "Unidad base *",
                modifier = Modifier.fillMaxWidth(),
            )

            // Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.xl),
            ) {
                LabeledSwitch(
                    label = "Control de lote",
                    description = "Requiere número de lote en ventas",
                    checked = state.form.lotTracked,
                    onCheckedChange = onLotTrackedChange,
                    modifier = Modifier.weight(1f),
                )
                LabeledSwitch(
                    label = "Producto activo",
                    description = "Disponible para venta",
                    checked = state.form.active,
                    onCheckedChange = onActiveChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Sección 2: Precio (solo en edición) ──
        if (isEditing) {
            PosSectionCard(
                title = "Precio",
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Historial de precios
                val history = state.selectedProduct?.priceHistory.orEmpty()
                if (history.isEmpty()) {
                    Text(
                        "Sin historial de precios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Últimos precios registrados",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    history.take(5).forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.xxs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.effectiveAt.take(19).replace("T", "  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PosMoneyText(entry.price, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (index < history.take(5).lastIndex) HorizontalDivider()
                    }
                }

                HorizontalDivider()

                // Registrar nuevo precio
                Text(
                    "Registrar nuevo precio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosTextField(
                        value = state.newPriceInput,
                        onValueChange = onNewPriceChange,
                        label = "Precio",
                        modifier = Modifier.width(200.dp),
                    )
                    PosSecondaryButton(
                        text = "Registrar",
                        onClick = onAddPrice,
                        enabled = !state.isSaving && state.newPriceInput.isNotBlank(),
                        modifier = Modifier.width(140.dp),
                    )
                }
            }

            // ── Sección 3: Unidades de medida (solo en edición) ──
            PosSectionCard(
                title = "Unidades de medida",
                modifier = Modifier.fillMaxWidth(),
            ) {
                val uoms = state.selectedProduct?.uoms.orEmpty()
                if (uoms.isEmpty()) {
                    Text(
                        "Sin conversiones registradas. La unidad base es ${state.selectedProduct?.baseUnit}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small,
                        ).padding(horizontal = PosSpacing.md, vertical = PosSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Unidad", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Factor a base", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    uoms.forEachIndexed { index, uom ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(uom.unit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "× ${uom.factorToBase}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index < uoms.lastIndex) HorizontalDivider()
                    }
                }

                HorizontalDivider()

                Text(
                    "Agregar conversión",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UnitDropdown(
                        units = state.units,
                        selected = state.newUomUnit,
                        onSelect = onNewUomUnitChange,
                        label = "Unidad",
                        modifier = Modifier.width(200.dp),
                    )
                    PosTextField(
                        value = state.newUomFactorInput,
                        onValueChange = onNewUomFactorChange,
                        label = "Factor (ej: 6 para sixpack)",
                        modifier = Modifier.width(220.dp),
                    )
                    PosSecondaryButton(
                        text = "Agregar",
                        onClick = onAddUom,
                        enabled = !state.isSaving,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }

        // ── Mensajes ──
        PosNoticeRow(
            notice = state.notice,
            errorMessage = state.errorMessage,
        )

        if (state.isLoadingDetail) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Cargando...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    units: List<com.posfab.shared.features.catalog.domain.CatalogUnit>,
    selected: String,
    onSelect: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = units.find { it.code == selected }
        ?.let { "${it.code} – ${it.name}" }
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
                    text = { Text("${unit.code} – ${unit.name}") },
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
