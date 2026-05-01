package com.posfab.shared.features.catalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
    ) {
        Column(
            modifier = Modifier.width(520.dp),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Text(
                "Catalogo de productos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            PosSectionCard(
                title = "Busqueda y filtros",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    PosTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        label = "Buscar producto",
                        modifier = Modifier.weight(1f),
                    )
                    PosSecondaryButton(
                        text = "Refrescar",
                        onClick = { viewModel.refreshList(resetOffset = false) },
                        modifier = Modifier.width(140.dp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    FilterChip(
                        selected = state.activeFilter == ActiveFilter.ALL,
                        onClick = { viewModel.onActiveFilterChange(ActiveFilter.ALL) },
                        label = { Text("Todos") },
                    )
                    FilterChip(
                        selected = state.activeFilter == ActiveFilter.ACTIVE_ONLY,
                        onClick = { viewModel.onActiveFilterChange(ActiveFilter.ACTIVE_ONLY) },
                        label = { Text("Activos") },
                    )
                    FilterChip(
                        selected = state.activeFilter == ActiveFilter.INACTIVE_ONLY,
                        onClick = { viewModel.onActiveFilterChange(ActiveFilter.INACTIVE_ONLY) },
                        label = { Text("Inactivos") },
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tamano de pagina",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(10, 25, 50).forEach { size ->
                        FilterChip(
                            selected = state.limit == size,
                            onClick = { viewModel.onLimitChange(size.toString()) },
                            label = { Text(size.toString()) },
                        )
                    }
                }
            }

            PosSectionCard(
                title = "Productos (${state.offset + 1}-${state.offset + state.products.size} / ${state.total})",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.isLoadingList) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.products.isEmpty()) {
                    EmptyCatalogText("Sin resultados para el filtro actual.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                        items(state.products) { product ->
                            PosSectionCard(
                                modifier = Modifier.fillMaxWidth(),
                                actions = {
                                    PosSecondaryButton(
                                        text = "Editar",
                                        onClick = { viewModel.selectProduct(product.id) },
                                        modifier = Modifier.width(110.dp),
                                    )
                                },
                            ) {
                                Text(
                                    product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "SKU: ${product.sku ?: "-"} · Codigo: ${product.barcode ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Unidad base: ${product.baseUnit} · Lote: ${if (product.lotTracked) "Si" else "No"} · Activo: ${if (product.active) "Si" else "No"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                PosMoneyText(
                                    amount = product.currentPrice ?: 0.0,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    PosSecondaryButton(
                        text = "Anterior",
                        onClick = viewModel::goPrevPage,
                        enabled = state.canGoPrevPage,
                        modifier = Modifier.width(130.dp),
                    )
                    PosSecondaryButton(
                        text = "Siguiente",
                        onClick = viewModel::goNextPage,
                        enabled = state.canGoNextPage,
                        modifier = Modifier.width(130.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosPrimaryButton(
                    text = "Nuevo producto",
                    onClick = viewModel::startCreateProduct,
                    modifier = Modifier.width(180.dp),
                )
                PosPrimaryButton(
                    text = "Guardar",
                    onClick = viewModel::saveProduct,
                    enabled = !state.isSaving,
                    isLoading = state.isSaving,
                    modifier = Modifier.width(160.dp),
                )
            }

            PosSectionCard(
                title = "Producto",
                modifier = Modifier.fillMaxWidth(),
            ) {
                PosTextField(
                    value = state.form.name,
                    onValueChange = viewModel::onFormNameChange,
                    label = "Nombre*",
                    modifier = Modifier.fillMaxWidth(),
                )
                PosTextField(
                    value = state.form.sku,
                    onValueChange = viewModel::onFormSkuChange,
                    label = "SKU",
                    modifier = Modifier.fillMaxWidth(),
                )
                PosTextField(
                    value = state.form.barcode,
                    onValueChange = viewModel::onFormBarcodeChange,
                    label = "Codigo de barras",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    PosTextField(
                        value = state.form.baseUnit,
                        onValueChange = viewModel::onFormBaseUnitChange,
                        label = "Unidad base*",
                        modifier = Modifier.width(160.dp),
                    )
                    FilterChip(
                        selected = state.form.lotTracked,
                        onClick = { viewModel.onFormLotTrackedChange(!state.form.lotTracked) },
                        label = { Text("Lote") },
                    )
                    FilterChip(
                        selected = state.form.active,
                        onClick = { viewModel.onFormActiveChange(!state.form.active) },
                        label = { Text("Activo") },
                    )
                }

                LabeledValue("Precio actual") {
                    PosMoneyText(state.selectedProduct?.currentPrice ?: 0.0)
                }
                if (state.isLoadingDetail) {
                    Text(
                        "Cargando detalle...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.hasUnsavedChanges) {
                    Text(
                        "Hay cambios sin guardar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            PosSectionCard(
                title = "Unidades de medida",
                modifier = Modifier.fillMaxWidth(),
            ) {
                val selectedProduct = state.selectedProduct
                if (selectedProduct?.uoms.isNullOrEmpty()) {
                    EmptyCatalogText("Aun no hay conversiones registradas.")
                } else {
                    selectedProduct?.uoms?.forEachIndexed { index, uom ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(uom.unit, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Factor de conversion: ${uom.factorToBase}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index < selectedProduct.uoms.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    PosTextField(
                        value = state.newUomUnit,
                        onValueChange = viewModel::onNewUomUnitChange,
                        label = "Unidad",
                        modifier = Modifier.width(140.dp),
                    )
                    PosTextField(
                        value = state.newUomFactorInput,
                        onValueChange = viewModel::onNewUomFactorChange,
                        label = "Factor de conversion",
                        modifier = Modifier.width(180.dp),
                    )
                    PosSecondaryButton(
                        text = "Agregar UOM",
                        onClick = viewModel::addUomConversion,
                        enabled = !state.isSaving,
                        modifier = Modifier.width(150.dp),
                    )
                }
            }

            PosSectionCard(
                title = "Precios",
                modifier = Modifier.fillMaxWidth(),
            ) {
                val selectedProduct = state.selectedProduct
                if (selectedProduct?.priceHistory.isNullOrEmpty()) {
                    EmptyCatalogText("Sin historial de precios.")
                } else {
                    val visiblePrices = selectedProduct?.priceHistory?.take(5).orEmpty()
                    visiblePrices.forEachIndexed { index, price ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                price.effectiveAt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PosMoneyText(price.price)
                        }
                        if (index < visiblePrices.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    PosTextField(
                        value = state.newPriceInput,
                        onValueChange = viewModel::onNewPriceChange,
                        label = "Nuevo precio",
                        modifier = Modifier.width(180.dp),
                    )
                    PosSecondaryButton(
                        text = "Registrar precio",
                        onClick = viewModel::addPriceEntry,
                        enabled = !state.isSaving,
                        modifier = Modifier.width(170.dp),
                    )
                }
            }

            PosSectionCard(
                title = "Validar codigo de barras",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    PosTextField(
                        value = state.barcodeCheckInput,
                        onValueChange = viewModel::onBarcodeCheckInputChange,
                        label = "Codigo de barras",
                        modifier = Modifier.width(220.dp),
                    )
                    PosSecondaryButton(
                        text = "Buscar",
                        onClick = viewModel::checkBarcode,
                        enabled = !state.isSaving,
                        modifier = Modifier.width(130.dp),
                    )
                }
                state.barcodeCheckResult?.let {
                    Text(
                        "${it.name} (${it.id})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PosNoticeRow(
                notice = state.notice,
                errorMessage = state.errorMessage,
            )
        }
    }
}

@Composable
private fun LabeledValue(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun EmptyCatalogText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
