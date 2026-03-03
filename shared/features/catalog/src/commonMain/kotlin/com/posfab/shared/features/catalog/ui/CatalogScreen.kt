package com.posfab.shared.features.catalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CatalogScreen(viewModel: CatalogViewModel) {
    val state by viewModel.state.collectAsState()

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Catalogo de productos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.limit.toString(),
                    onValueChange = viewModel::onLimitChange,
                    label = { Text("Limit") },
                    singleLine = true,
                    modifier = Modifier.width(90.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.onActiveFilterChange(ActiveFilter.ALL) }) { Text("Todos") }
                Button(onClick = { viewModel.onActiveFilterChange(ActiveFilter.ACTIVE_ONLY) }) { Text("Activos") }
                Button(onClick = { viewModel.onActiveFilterChange(ActiveFilter.INACTIVE_ONLY) }) { Text("Inactivos") }
                Button(onClick = { viewModel.refreshList(resetOffset = false) }) { Text("Refrescar") }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Productos (${state.offset + 1}-${state.offset + state.products.size} / ${state.total})", fontWeight = FontWeight.Bold)
                    if (state.isLoadingList) {
                        Text("Cargando productos...")
                    } else if (state.products.isEmpty()) {
                        Text("Sin resultados")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.products) { product ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column {
                                            Text(product.name, fontWeight = FontWeight.Bold)
                                            Text("SKU: ${product.sku ?: "-"} | Barcode: ${product.barcode ?: "-"}")
                                            Text("Base: ${product.baseUnit} | Lote: ${if (product.lotTracked) "SI" else "NO"} | Activo: ${if (product.active) "SI" else "NO"}")
                                        }
                                        Button(onClick = { viewModel.selectProduct(product.id) }) { Text("Editar") }
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::goPrevPage, enabled = state.canGoPrevPage) { Text("Anterior") }
                        Button(onClick = viewModel::goNextPage, enabled = state.canGoNextPage) { Text("Siguiente") }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::startCreateProduct) { Text("Nuevo producto") }
                Button(onClick = viewModel::saveProduct, enabled = !state.isSaving) { Text("Guardar") }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Producto", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = state.form.name, onValueChange = viewModel::onFormNameChange, label = { Text("Nombre*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.form.sku, onValueChange = viewModel::onFormSkuChange, label = { Text("SKU") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.form.barcode, onValueChange = viewModel::onFormBarcodeChange, label = { Text("Barcode") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.form.baseUnit, onValueChange = viewModel::onFormBaseUnitChange, label = { Text("Unidad base*") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Checkbox(checked = state.form.lotTracked, onCheckedChange = viewModel::onFormLotTrackedChange)
                            Text("Lote")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Checkbox(checked = state.form.active, onCheckedChange = viewModel::onFormActiveChange)
                            Text("Activo")
                        }
                    }

                    val selected = state.selectedProduct
                    Text("Precio actual: ${selected?.currentPrice ?: 0.0}")
                    if (state.isLoadingDetail) Text("Cargando detalle...")
                    if (state.hasUnsavedChanges) Text("Hay cambios sin guardar", color = MaterialTheme.colorScheme.primary)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UOM", fontWeight = FontWeight.Bold)
                    state.selectedProduct?.uoms?.forEach { uom ->
                        Text("${uom.unit}: factor ${uom.factorToBase}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.newUomUnit, onValueChange = viewModel::onNewUomUnitChange, label = { Text("Unidad") }, singleLine = true)
                        OutlinedTextField(value = state.newUomFactorInput, onValueChange = viewModel::onNewUomFactorChange, label = { Text("factor_to_base") }, singleLine = true)
                        Button(onClick = viewModel::addUomConversion, enabled = !state.isSaving) { Text("Agregar UOM") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Precios", fontWeight = FontWeight.Bold)
                    Text("Historial reciente")
                    state.selectedProduct?.priceHistory?.take(5)?.forEach { price ->
                        Text("${price.effectiveAt}: ${price.price}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.newPriceInput, onValueChange = viewModel::onNewPriceChange, label = { Text("Nuevo precio") }, singleLine = true)
                        Button(onClick = viewModel::addPriceEntry, enabled = !state.isSaving) { Text("Registrar precio") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Validar barcode", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.barcodeCheckInput, onValueChange = viewModel::onBarcodeCheckInputChange, label = { Text("Barcode") }, singleLine = true)
                        Button(onClick = viewModel::checkBarcode, enabled = !state.isSaving) { Text("Buscar") }
                    }
                    state.barcodeCheckResult?.let {
                        Text("${it.name} (${it.id})")
                    }
                }
            }

            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
