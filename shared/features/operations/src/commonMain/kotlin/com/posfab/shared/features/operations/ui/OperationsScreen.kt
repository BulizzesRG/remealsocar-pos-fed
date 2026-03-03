package com.posfab.shared.features.operations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
fun OperationsScreen(viewModel: OperationsViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Operations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OperationsTab.entries.forEach { tab ->
                Button(onClick = { viewModel.selectTab(tab) }) { Text(tab.name) }
            }
        }

        when (state.selectedTab) {
            OperationsTab.PURCHASES -> PurchasesTab(state, viewModel)
            OperationsTab.INTERNAL_REQ -> InternalReqTab(state, viewModel)
            OperationsTab.ON_HAND -> OnHandTab(state, viewModel)
            OperationsTab.LOTS -> LotsTab(state, viewModel)
            OperationsTab.WASTE -> WasteTab(state, viewModel)
            OperationsTab.ADJUSTMENTS -> AdjustmentsTab(state, viewModel)
        }

        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PurchasesTab(state: OperationsState, viewModel: OperationsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Purchases", fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.purchaseSupplier, onValueChange = viewModel::onPurchaseSupplierChange, label = { Text("Supplier") }, singleLine = true)
                OutlinedTextField(value = state.terminalId, onValueChange = viewModel::onTerminalIdChange, label = { Text("Terminal") }, singleLine = true)
                OutlinedTextField(value = state.businessUnit, onValueChange = viewModel::onBusinessUnitChange, label = { Text("Business unit") }, singleLine = true)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = state.purchasePaidCash, onCheckedChange = viewModel::onPurchasePaidCashChange)
                    Text("paid_cash")
                }
                OutlinedTextField(value = state.purchasePaidFromTerminalId, onValueChange = viewModel::onPurchasePaidFromTerminalIdChange, label = { Text("paid_from_terminal_id") }, singleLine = true)
            }

            Text("Lineas")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.purchaseLineProductId, onValueChange = viewModel::onPurchaseLineProductChange, label = { Text("product_id") }, singleLine = true)
                OutlinedTextField(value = state.purchaseLineUnit, onValueChange = viewModel::onPurchaseLineUnitChange, label = { Text("unit") }, singleLine = true)
                OutlinedTextField(value = state.purchaseLineQtyInput, onValueChange = viewModel::onPurchaseLineQtyChange, label = { Text("qty") }, singleLine = true)
                OutlinedTextField(value = state.purchaseLineUnitCostInput, onValueChange = viewModel::onPurchaseLineUnitCostChange, label = { Text("unit_cost") }, singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.purchaseLineLotCode, onValueChange = viewModel::onPurchaseLineLotCodeChange, label = { Text("lot_code") }, singleLine = true)
                OutlinedTextField(value = state.purchaseLineLotExpiry, onValueChange = viewModel::onPurchaseLineLotExpiryChange, label = { Text("lot_expiry") }, singleLine = true)
                Button(onClick = viewModel::addPurchaseLine) { Text("Agregar linea") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(state.purchaseLines) { index, line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.productId} ${line.qty} ${line.unit} @ ${line.unitCost}")
                        Button(onClick = { viewModel.removePurchaseLine(index) }) { Text("Quitar") }
                    }
                }
            }

            Button(onClick = viewModel::submitPurchase, enabled = !state.isSubmittingPurchase) { Text("Registrar compra") }
            state.lastPurchaseResult?.let { Text("Ultima compra: $it") }
            state.purchaseIdempotencyKey?.let { Text("Idempotency-Key: $it") }
        }
    }
}

@Composable
private fun InternalReqTab(state: OperationsState, viewModel: OperationsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Internal Requisitions", fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.requisitionSourceBu, onValueChange = viewModel::onReqSourceBuChange, label = { Text("source BU") }, singleLine = true)
                OutlinedTextField(value = state.requisitionTargetBu, onValueChange = viewModel::onReqTargetBuChange, label = { Text("target BU") }, singleLine = true)
                OutlinedTextField(value = state.terminalId, onValueChange = viewModel::onTerminalIdChange, label = { Text("terminal") }, singleLine = true)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.requisitionLineProductId, onValueChange = viewModel::onReqLineProductChange, label = { Text("product_id") }, singleLine = true)
                OutlinedTextField(value = state.requisitionLineUnit, onValueChange = viewModel::onReqLineUnitChange, label = { Text("unit") }, singleLine = true)
                OutlinedTextField(value = state.requisitionLineQtyInput, onValueChange = viewModel::onReqLineQtyChange, label = { Text("qty") }, singleLine = true)
                OutlinedTextField(value = state.requisitionLineLotId, onValueChange = viewModel::onReqLineLotIdChange, label = { Text("lot_id") }, singleLine = true)
                Button(onClick = viewModel::addRequisitionLine) { Text("Agregar linea") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(state.requisitionLines) { index, line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.productId} ${line.qty} ${line.unit} lot=${line.lotId ?: "-"}")
                        Button(onClick = { viewModel.removeRequisitionLine(index) }) { Text("Quitar") }
                    }
                }
            }

            Button(onClick = viewModel::submitRequisition, enabled = !state.isSubmittingRequisition) { Text("Registrar requisicion") }
            state.lastRequisitionResult?.let { Text("Ultima requisicion: $it") }
        }
    }
}

@Composable
private fun OnHandTab(state: OperationsState, viewModel: OperationsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Inventory On-hand", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.onHandProductFilter, onValueChange = viewModel::onOnHandProductFilterChange, label = { Text("product_id") }, singleLine = true)
                OutlinedTextField(value = state.onHandBusinessUnitFilter, onValueChange = viewModel::onOnHandBusinessUnitFilterChange, label = { Text("business_unit") }, singleLine = true)
                Button(onClick = viewModel::refreshOnHand) { Text("Refrescar") }
            }
            OutlinedTextField(value = state.onHandQuickSearch, onValueChange = viewModel::onOnHandQuickSearchChange, label = { Text("Quick search") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            if (state.isLoadingOnHand) {
                Text("Cargando on-hand...")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(state.visibleOnHandItems) { _, item ->
                        Text("${item.productId} | ${item.productName} | ${item.businessUnit} | ${item.onHand} ${item.unit}")
                    }
                }
            }
        }
    }
}

@Composable
private fun LotsTab(state: OperationsState, viewModel: OperationsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Inventory Lots", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.lotsProductId, onValueChange = viewModel::onLotsProductIdChange, label = { Text("product_id") }, singleLine = true)
                Button(onClick = viewModel::loadLots, enabled = !state.isLoadingLots) { Text("Consultar lotes") }
            }
            if (state.isLoadingLots) {
                Text("Cargando lotes...")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(state.lotItems) { _, item ->
                        Text("${item.lotCode} | exp=${item.expiryDate ?: "-"} | on_hand=${item.onHand} ${item.unit}")
                    }
                }
            }
        }
    }
}

@Composable
private fun WasteTab(state: OperationsState, viewModel: OperationsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Waste (Manager)", fontWeight = FontWeight.Bold)
            if (!state.canManageWasteAdjustments) {
                Text("Solo rol MANAGER puede registrar mermas.")
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.wasteProductId, onValueChange = viewModel::onWasteProductChange, label = { Text("product_id") }, singleLine = true)
                OutlinedTextField(value = state.wasteBusinessUnit, onValueChange = viewModel::onWasteBusinessUnitChange, label = { Text("business_unit") }, singleLine = true)
                OutlinedTextField(value = state.wasteUnit, onValueChange = viewModel::onWasteUnitChange, label = { Text("unit") }, singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.wasteQtyInput, onValueChange = viewModel::onWasteQtyChange, label = { Text("qty") }, singleLine = true)
                OutlinedTextField(value = state.wasteReasonCode, onValueChange = viewModel::onWasteReasonChange, label = { Text("reason_code") }, singleLine = true)
                OutlinedTextField(value = state.wasteLotId, onValueChange = viewModel::onWasteLotIdChange, label = { Text("lot_id") }, singleLine = true)
                Button(onClick = viewModel::submitWaste, enabled = !state.isSubmittingWaste) { Text("Registrar merma") }
            }
            state.wasteIdempotencyKey?.let { Text("Idempotency-Key: $it") }
        }
    }
}

@Composable
private fun AdjustmentsTab(state: OperationsState, viewModel: OperationsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adjustments (Manager)", fontWeight = FontWeight.Bold)
            if (!state.canManageWasteAdjustments) {
                Text("Solo rol MANAGER puede registrar ajustes.")
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.adjustmentProductId, onValueChange = viewModel::onAdjustmentProductChange, label = { Text("product_id") }, singleLine = true)
                OutlinedTextField(value = state.adjustmentBusinessUnit, onValueChange = viewModel::onAdjustmentBusinessUnitChange, label = { Text("business_unit") }, singleLine = true)
                OutlinedTextField(value = state.adjustmentUnit, onValueChange = viewModel::onAdjustmentUnitChange, label = { Text("unit") }, singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.adjustmentQtyDeltaInput, onValueChange = viewModel::onAdjustmentQtyDeltaChange, label = { Text("qty_delta") }, singleLine = true)
                OutlinedTextField(value = state.adjustmentReasonCode, onValueChange = viewModel::onAdjustmentReasonChange, label = { Text("reason_code") }, singleLine = true)
                OutlinedTextField(value = state.adjustmentLotId, onValueChange = viewModel::onAdjustmentLotIdChange, label = { Text("lot_id") }, singleLine = true)
                Button(onClick = viewModel::submitAdjustment, enabled = !state.isSubmittingAdjustment) { Text("Registrar ajuste") }
            }
            state.adjustmentIdempotencyKey?.let { Text("Idempotency-Key: $it") }
        }
    }
}
