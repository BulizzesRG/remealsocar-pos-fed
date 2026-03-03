package com.posfab.shared.features.sale.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun CashierSaleScreen(viewModel: CashierSaleViewModel) {
    val state by viewModel.state.collectAsState()

    if (state.isInitializing) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Cargando borrador...")
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Enter -> {
                    if (state.barcodeInput.isNotBlank()) viewModel.addByBarcode() else viewModel.searchProducts()
                    true
                }
                Key.F5 -> {
                    viewModel.validateDraft()
                    true
                }
                Key.F9 -> {
                    viewModel.checkoutCash()
                    true
                }
                else -> false
            }
        },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Buscar producto (Enter)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
                singleLine = true,
            )
            Button(
                onClick = viewModel::searchProducts,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.isBusy,
            ) {
                Text("Buscar")
            }

            OutlinedTextField(
                value = state.barcodeInput,
                onValueChange = viewModel::onBarcodeChange,
                label = { Text("Codigo de barras") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
                singleLine = true,
            )
            Button(
                onClick = viewModel::addByBarcode,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.isBusy,
            ) {
                Text("Agregar por codigo")
            }

            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Resultados", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.searchResults) { product ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !state.isBusy) { viewModel.addProduct(product) }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(product.name)
                                    Text("${product.sku ?: ""} ${product.barcode ?: ""}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("$${money(product.unitPrice)}")
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Venta en caja", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Draft: ${state.draft?.id ?: "-"} v${state.draft?.version ?: 0}")

            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lineas", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = viewModel::incrementSelectedLineQty, enabled = !state.isBusy) { Text("+ Qty") }
                            Button(onClick = viewModel::decrementSelectedLineQty, enabled = !state.isBusy) { Text("- Qty") }
                            Button(onClick = viewModel::removeSelectedLine, enabled = !state.isBusy) { Text("Eliminar") }
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.draft?.lines.orEmpty()) { line ->
                            val selected = line.id == state.selectedLineId
                            val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
                            Row(
                                modifier = Modifier.fillMaxWidth().background(bg)
                                    .clickable { viewModel.selectLine(line.id) }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.width(280.dp)) {
                                    Text(line.productName, fontWeight = FontWeight.SemiBold)
                                    Text("Lote: ${line.lotId ?: "(pendiente)"} | ${line.unit}")
                                }
                                Text("${line.qty} x ${money(line.unitPrice)}")
                                Text("$${money(line.lineTotal)}")
                            }
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.editQtyInput,
                            onValueChange = viewModel::onEditQtyChange,
                            label = { Text("Qty") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.editUnitInput,
                            onValueChange = viewModel::onEditUnitChange,
                            label = { Text("Unidad") },
                            modifier = Modifier.width(110.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.editPriceInput,
                            onValueChange = viewModel::onEditPriceChange,
                            label = { Text("Precio") },
                            modifier = Modifier.width(120.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.editLotInput,
                            onValueChange = viewModel::onEditLotChange,
                            label = { Text("Lote") },
                            modifier = Modifier.width(140.dp),
                            singleLine = true,
                        )
                        Button(onClick = viewModel::applySelectedLineEdits, enabled = !state.isBusy) { Text("Aplicar") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Subtotal: $${money(state.draft?.totals?.subtotal ?: 0.0)}")
                    Text("Impuesto: $${money(state.draft?.totals?.tax ?: 0.0)}")
                    Text("TOTAL: $${money(state.draft?.totals?.total ?: 0.0)}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::validateDraft, enabled = !state.isBusy) { Text("Validar") }
                Button(onClick = viewModel::resolveLots, enabled = !state.isBusy) { Text("Resolver lotes") }
                Button(onClick = viewModel::checkoutCash, enabled = !state.isCheckoutInFlight) { Text("Cobrar efectivo") }
                Button(onClick = viewModel::checkoutCredit, enabled = !state.isCheckoutInFlight) { Text("Cobrar credito") }
                Button(onClick = viewModel::startNewSale, enabled = !state.isBusy) { Text("Nueva venta") }
            }

            if (state.validationIssues.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("Observaciones de validacion", fontWeight = FontWeight.Bold)
                        state.validationIssues.forEach { issue ->
                            Text("- ${issue.lineId ?: "GLOBAL"}: ${issue.message}")
                        }
                    }
                }
            }

            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.checkoutResult?.let { Text("Folio: ${it.folio}", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun money(value: Double): String {
    val cents = (value * 100.0).roundToLong()
    val whole = cents / 100
    val fraction = abs(cents % 100)
    val suffix = if (fraction < 10) "0$fraction" else "$fraction"
    return "$whole.$suffix"
}
