package com.posfab.shared.features.operations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.components.PosSectionCard
import com.posfab.shared.ui.components.PosTextField
import com.posfab.shared.ui.theme.PosSpacing

@Composable
fun OperationsScreen(viewModel: OperationsViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Text(
            "Operaciones",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        TabRow(
            selectedTabIndex = OperationsTab.entries.indexOf(state.selectedTab),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OperationsTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.displayName) },
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (state.selectedTab) {
                OperationsTab.PURCHASES -> PurchasesTab(state, viewModel)
                OperationsTab.INTERNAL_REQ -> InternalReqTab(state, viewModel)
                OperationsTab.ON_HAND -> OnHandTab(state, viewModel)
                OperationsTab.LOTS -> LotsTab(state, viewModel)
                OperationsTab.WASTE -> WasteTab(state, viewModel)
                OperationsTab.ADJUSTMENTS -> AdjustmentsTab(state, viewModel)
            }
        }

        PosNoticeRow(
            notice = state.notice,
            errorMessage = state.errorMessage,
        )
    }

    state.confirmDialog?.let { dialog ->
        PosConfirmDialog(
            title = dialog.title,
            message = dialog.message,
            confirmLabel = dialog.confirmLabel,
            cancelLabel = dialog.cancelLabel,
            onConfirm = viewModel::confirmDialogAction,
            onDismiss = viewModel::dismissConfirmDialog,
        )
    }
}

@Composable
private fun PurchasesTab(state: OperationsState, viewModel: OperationsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(
            title = "Compra",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.purchaseSupplier,
                    onValueChange = viewModel::onPurchaseSupplierChange,
                    label = "Proveedor",
                    modifier = Modifier.weight(1f),
                )
                PosTextField(
                    value = state.terminalId,
                    onValueChange = viewModel::onTerminalIdChange,
                    label = "Terminal",
                    modifier = Modifier.width(140.dp),
                )
                PosTextField(
                    value = state.businessUnit,
                    onValueChange = viewModel::onBusinessUnitChange,
                    label = "Unidad de negocio",
                    modifier = Modifier.width(180.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                ) {
                    Checkbox(
                        checked = state.purchasePaidCash,
                        onCheckedChange = viewModel::onPurchasePaidCashChange,
                    )
                    Text("Pagada en efectivo")
                }
                PosTextField(
                    value = state.purchasePaidFromTerminalId,
                    onValueChange = viewModel::onPurchasePaidFromTerminalIdChange,
                    label = "Terminal de cobro",
                    modifier = Modifier.width(180.dp),
                )
            }
        }

        PosSectionCard(
            title = "Linea de compra",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.purchaseLineProductId,
                    onValueChange = viewModel::onPurchaseLineProductChange,
                    label = "Producto",
                    modifier = Modifier.weight(1f),
                )
                PosTextField(
                    value = state.purchaseLineUnit,
                    onValueChange = viewModel::onPurchaseLineUnitChange,
                    label = "Unidad",
                    modifier = Modifier.width(110.dp),
                )
                PosTextField(
                    value = state.purchaseLineQtyInput,
                    onValueChange = viewModel::onPurchaseLineQtyChange,
                    label = "Cantidad",
                    modifier = Modifier.width(110.dp),
                )
                PosTextField(
                    value = state.purchaseLineUnitCostInput,
                    onValueChange = viewModel::onPurchaseLineUnitCostChange,
                    label = "Costo unitario",
                    modifier = Modifier.width(140.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.purchaseLineLotCode,
                    onValueChange = viewModel::onPurchaseLineLotCodeChange,
                    label = "Codigo de lote",
                    modifier = Modifier.width(180.dp),
                )
                PosTextField(
                    value = state.purchaseLineLotExpiry,
                    onValueChange = viewModel::onPurchaseLineLotExpiryChange,
                    label = "Caducidad",
                    modifier = Modifier.width(180.dp),
                )
                PosSecondaryButton(
                    text = "Agregar linea",
                    onClick = viewModel::addPurchaseLine,
                    modifier = Modifier.width(160.dp),
                )
            }
        }

        PosSectionCard(
            title = "Lineas capturadas",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.purchaseLines.isEmpty()) {
                EmptyText("Aun no hay lineas de compra.")
            } else {
                state.purchaseLines.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${line.productId} · ${line.qty} ${line.unit} · ${line.unitCost}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                        PosSecondaryButton(
                            text = "Quitar",
                            onClick = { viewModel.removePurchaseLine(index) },
                            modifier = Modifier.width(120.dp),
                        )
                    }
                    if (index < state.purchaseLines.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            PosPrimaryButton(
                text = "Registrar compra",
                onClick = viewModel::requestSubmitPurchase,
                enabled = !state.isSubmittingPurchase,
                isLoading = state.isSubmittingPurchase,
                modifier = Modifier.width(200.dp),
            )
            state.lastPurchaseResult?.let {
                Text("Ultima compra: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InternalReqTab(state: OperationsState, viewModel: OperationsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(
            title = "Requisicion interna",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.requisitionSourceBu,
                    onValueChange = viewModel::onReqSourceBuChange,
                    label = "Origen",
                    modifier = Modifier.width(160.dp),
                )
                PosTextField(
                    value = state.requisitionTargetBu,
                    onValueChange = viewModel::onReqTargetBuChange,
                    label = "Destino",
                    modifier = Modifier.width(160.dp),
                )
                PosTextField(
                    value = state.terminalId,
                    onValueChange = viewModel::onTerminalIdChange,
                    label = "Terminal",
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        PosSectionCard(
            title = "Linea de requisicion",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.requisitionLineProductId,
                    onValueChange = viewModel::onReqLineProductChange,
                    label = "Producto",
                    modifier = Modifier.weight(1f),
                )
                PosTextField(
                    value = state.requisitionLineUnit,
                    onValueChange = viewModel::onReqLineUnitChange,
                    label = "Unidad",
                    modifier = Modifier.width(110.dp),
                )
                PosTextField(
                    value = state.requisitionLineQtyInput,
                    onValueChange = viewModel::onReqLineQtyChange,
                    label = "Cantidad",
                    modifier = Modifier.width(110.dp),
                )
                PosTextField(
                    value = state.requisitionLineLotId,
                    onValueChange = viewModel::onReqLineLotIdChange,
                    label = "Lote",
                    modifier = Modifier.width(140.dp),
                )
                PosSecondaryButton(
                    text = "Agregar linea",
                    onClick = viewModel::addRequisitionLine,
                    modifier = Modifier.width(160.dp),
                )
            }
        }

        PosSectionCard(
            title = "Lineas capturadas",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.requisitionLines.isEmpty()) {
                EmptyText("Aun no hay lineas de requisicion.")
            } else {
                state.requisitionLines.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${line.productId} · ${line.qty} ${line.unit} · lote ${line.lotId ?: "-"}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                        PosSecondaryButton(
                            text = "Quitar",
                            onClick = { viewModel.removeRequisitionLine(index) },
                            modifier = Modifier.width(120.dp),
                        )
                    }
                    if (index < state.requisitionLines.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            PosPrimaryButton(
                text = "Registrar requisicion",
                onClick = viewModel::requestSubmitRequisition,
                enabled = !state.isSubmittingRequisition,
                isLoading = state.isSubmittingRequisition,
                modifier = Modifier.width(220.dp),
            )
            state.lastRequisitionResult?.let {
                Text("Ultima requisicion: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun OnHandTab(state: OperationsState, viewModel: OperationsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(
            title = "Existencias",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.onHandProductFilter,
                    onValueChange = viewModel::onOnHandProductFilterChange,
                    label = "Producto",
                    modifier = Modifier.width(160.dp),
                )
                PosTextField(
                    value = state.onHandBusinessUnitFilter,
                    onValueChange = viewModel::onOnHandBusinessUnitFilterChange,
                    label = "Unidad de negocio",
                    modifier = Modifier.width(180.dp),
                )
                PosSecondaryButton(
                    text = "Refrescar",
                    onClick = viewModel::refreshOnHand,
                    modifier = Modifier.width(140.dp),
                )
            }
            PosTextField(
                value = state.onHandQuickSearch,
                onValueChange = viewModel::onOnHandQuickSearchChange,
                label = "Busqueda rapida",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PosSectionCard(
            title = "Resultados",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (state.isLoadingOnHand) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.visibleOnHandItems.isEmpty()) {
                EmptyText("Sin existencias para el filtro actual.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    itemsIndexed(state.visibleOnHandItems) { _, item ->
                        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                            Text(item.productName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.productId} · ${item.businessUnit} · ${item.onHand} ${item.unit}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LotsTab(state: OperationsState, viewModel: OperationsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(
            title = "Lotes",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.lotsProductId,
                    onValueChange = viewModel::onLotsProductIdChange,
                    label = "Producto",
                    modifier = Modifier.width(180.dp),
                )
                PosSecondaryButton(
                    text = "Consultar lotes",
                    onClick = viewModel::loadLots,
                    enabled = !state.isLoadingLots,
                    modifier = Modifier.width(160.dp),
                )
            }
        }

        PosSectionCard(
            title = "Resultados",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (state.isLoadingLots) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.lotItems.isEmpty()) {
                EmptyText("Sin lotes para el producto consultado.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                    itemsIndexed(state.lotItems) { _, item ->
                        Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                            Text(item.lotCode, fontWeight = FontWeight.SemiBold)
                            Text(
                                "ID ${item.lotId ?: "-"} · exp ${item.expiryDate ?: "-"} · ${item.onHand} ${item.unit}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WasteTab(state: OperationsState, viewModel: OperationsViewModel) {
    PosSectionCard(
        title = "Merma",
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!state.canManageWasteAdjustments) {
            EmptyText("Solo rol MANAGER puede registrar mermas.")
            return@PosSectionCard
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.wasteProductId,
                    onValueChange = viewModel::onWasteProductChange,
                    label = "Producto",
                    modifier = Modifier.weight(1f),
                )
                PosTextField(
                    value = state.wasteBusinessUnit,
                    onValueChange = viewModel::onWasteBusinessUnitChange,
                    label = "Unidad de negocio",
                    modifier = Modifier.width(180.dp),
                )
                PosTextField(
                    value = state.wasteUnit,
                    onValueChange = viewModel::onWasteUnitChange,
                    label = "Unidad",
                    modifier = Modifier.width(110.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.wasteQtyInput,
                    onValueChange = viewModel::onWasteQtyChange,
                    label = "Cantidad",
                    modifier = Modifier.width(140.dp),
                )
                PosTextField(
                    value = state.wasteReasonCode,
                    onValueChange = viewModel::onWasteReasonChange,
                    label = "Motivo",
                    modifier = Modifier.width(180.dp),
                )
                PosTextField(
                    value = state.wasteLotId,
                    onValueChange = viewModel::onWasteLotIdChange,
                    label = "Lote",
                    modifier = Modifier.width(140.dp),
                )
            }
            PosPrimaryButton(
                text = "Registrar merma",
                onClick = viewModel::requestSubmitWaste,
                enabled = !state.isSubmittingWaste,
                isLoading = state.isSubmittingWaste,
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun AdjustmentsTab(state: OperationsState, viewModel: OperationsViewModel) {
    PosSectionCard(
        title = "Ajuste",
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!state.canManageWasteAdjustments) {
            EmptyText("Solo rol MANAGER puede registrar ajustes.")
            return@PosSectionCard
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.adjustmentProductId,
                    onValueChange = viewModel::onAdjustmentProductChange,
                    label = "Producto",
                    modifier = Modifier.weight(1f),
                )
                PosTextField(
                    value = state.adjustmentBusinessUnit,
                    onValueChange = viewModel::onAdjustmentBusinessUnitChange,
                    label = "Unidad de negocio",
                    modifier = Modifier.width(180.dp),
                )
                PosTextField(
                    value = state.adjustmentUnit,
                    onValueChange = viewModel::onAdjustmentUnitChange,
                    label = "Unidad",
                    modifier = Modifier.width(110.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                PosTextField(
                    value = state.adjustmentQtyDeltaInput,
                    onValueChange = viewModel::onAdjustmentQtyDeltaChange,
                    label = "Delta de cantidad",
                    modifier = Modifier.width(160.dp),
                )
                PosTextField(
                    value = state.adjustmentReasonCode,
                    onValueChange = viewModel::onAdjustmentReasonChange,
                    label = "Motivo",
                    modifier = Modifier.width(180.dp),
                )
                PosTextField(
                    value = state.adjustmentLotId,
                    onValueChange = viewModel::onAdjustmentLotIdChange,
                    label = "Lote",
                    modifier = Modifier.width(140.dp),
                )
            }
            PosPrimaryButton(
                text = "Registrar ajuste",
                onClick = viewModel::requestSubmitAdjustment,
                enabled = !state.isSubmittingAdjustment,
                isLoading = state.isSubmittingAdjustment,
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val OperationsTab.displayName: String
    get() = when (this) {
        OperationsTab.PURCHASES -> "Compras"
        OperationsTab.INTERNAL_REQ -> "Requisiciones"
        OperationsTab.ON_HAND -> "Inventario"
        OperationsTab.LOTS -> "Lotes"
        OperationsTab.WASTE -> "Mermas"
        OperationsTab.ADJUSTMENTS -> "Ajustes"
    }
