package com.posfab.shared.features.operations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.posfab.shared.features.operations.domain.AdjustmentReason
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.OperationsBusinessUnit
import com.posfab.shared.features.operations.domain.Supplier
import com.posfab.shared.features.operations.domain.WasteReason
import com.posfab.shared.ui.components.PosConfirmDialog
import com.posfab.shared.ui.components.PosDropdownField
import com.posfab.shared.ui.components.PosMoneyText
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
        modifier = Modifier.fillMaxSize().padding(PosSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Operaciones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Terminal ${state.terminalCode.ifBlank { "#${state.terminalId}" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isLoadingReferenceData) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp))
            }
        }

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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.selectedTab) {
                OperationsTab.PURCHASES -> PurchasesTab(state, viewModel)
                OperationsTab.INTERNAL_REQ -> InternalReqTab(state, viewModel)
                OperationsTab.ON_HAND -> OnHandTab(state, viewModel)
                OperationsTab.LOTS -> LotsTab(state, viewModel)
                OperationsTab.WASTE -> WasteTab(state, viewModel)
                OperationsTab.ADJUSTMENTS -> AdjustmentsTab(state, viewModel)
            }
        }

        PosNoticeRow(notice = state.notice, errorMessage = state.errorMessage)
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

// ---- Shared product picker ----------------------------------------------------

/**
 * Search + select a product, choose the capture unit and, when the product tracks lots
 * (or [alwaysShowLots]), the lot. Used by every tab that captures a line.
 */
@Composable
private fun ProductPicker(
    state: OperationsState,
    viewModel: OperationsViewModel,
    alwaysShowLots: Boolean = false,
    showUnit: Boolean = true,
) {
    val picker = state.picker
    val product = picker.selectedProduct

    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosTextField(
                value = picker.query,
                onValueChange = viewModel::onProductQueryChange,
                label = "Producto (nombre, SKU o código)",
                modifier = Modifier.weight(1f),
                enabled = product == null,
            )
            if (product == null) {
                PosSecondaryButton(
                    text = "Buscar",
                    onClick = viewModel::searchProducts,
                    enabled = !picker.isSearching,
                    icon = Icons.Filled.Search,
                    modifier = Modifier.width(130.dp),
                )
            } else {
                PosSecondaryButton(
                    text = "Cambiar",
                    onClick = viewModel::clearProduct,
                    modifier = Modifier.width(130.dp),
                )
            }
        }

        if (picker.isSearching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp))
                Text("Buscando…", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (product == null && picker.results.isNotEmpty()) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    picker.results.take(8).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectProduct(item) }
                                .padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(item.sku?.let { "SKU $it" }, item.barcode, "base ${item.baseUnitCode}")
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.lotTracked) {
                                Text("Lotes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (index < picker.results.take(8).lastIndex) HorizontalDivider()
                    }
                }
            }
        }

        if (product != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull("ID ${product.id}", product.sku?.let { "SKU $it" }, if (product.lotTracked) "maneja lotes" else null)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showUnit) {
                    PosDropdownField(
                        options = picker.unitOptions,
                        selected = picker.selectedUnit,
                        onSelect = viewModel::selectUnit,
                        label = if (picker.isLoadingUnits) "Unidad (cargando…)" else "Unidad",
                        optionLabel = { it.code },
                        enabled = !picker.isLoadingUnits,
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
            if (product.lotTracked || alwaysShowLots) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosDropdownField(
                        options = picker.lots,
                        selected = picker.selectedLot,
                        onSelect = { viewModel.selectLot(it) },
                        label = if (picker.isLoadingLots) "Lote (cargando…)" else "Lote",
                        optionLabel = { it.summary() },
                        emptyText = "Sin lotes con existencia",
                        enabled = !picker.isLoadingLots,
                        modifier = Modifier.weight(1f),
                    )
                    PosSecondaryButton(
                        text = "Actualizar",
                        onClick = viewModel::loadLotsForSelectedProduct,
                        icon = Icons.Filled.Refresh,
                        enabled = !picker.isLoadingLots,
                        modifier = Modifier.width(150.dp),
                    )
                }
            }
        }
    }
}

// ---- Purchases ---------------------------------------------------------------

@Composable
private fun PurchasesTab(state: OperationsState, viewModel: OperationsViewModel) {
    if (!state.canManageOperations) {
        RestrictedNotice("Solo ADMIN o MANAGER pueden registrar compras.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(title = "Datos de la compra", modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PosDropdownField(
                    options = state.suppliers,
                    selected = state.selectedSupplier,
                    onSelect = viewModel::selectSupplier,
                    label = "Proveedor",
                    optionLabel = { it.label() },
                    emptyText = "Sin proveedores. Crea uno con «Nuevo».",
                    modifier = Modifier.weight(1f),
                )
                PosSecondaryButton(
                    text = if (state.showNewSupplierForm) "Cancelar" else "Nuevo",
                    onClick = { viewModel.toggleNewSupplierForm(!state.showNewSupplierForm) },
                    icon = if (state.showNewSupplierForm) null else Icons.Filled.Add,
                    modifier = Modifier.width(130.dp),
                )
                PosDropdownField(
                    options = state.businessUnits,
                    selected = state.selectedPurchaseBusinessUnit,
                    onSelect = viewModel::selectPurchaseBusinessUnit,
                    label = "Recibe en",
                    optionLabel = { it.name },
                    modifier = Modifier.width(180.dp),
                )
            }
            if (state.showNewSupplierForm) {
                NewSupplierForm(state, viewModel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                PosTextField(
                    value = state.purchaseSupplierDocNo,
                    onValueChange = viewModel::onPurchaseSupplierDocNoChange,
                    label = "Factura / nota (opcional)",
                    modifier = Modifier.width(240.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.purchasePaidCash, onCheckedChange = viewModel::onPurchasePaidCashChange)
                    Column {
                        Text("Pagada en efectivo desde esta caja")
                        Text(
                            "Requiere sesión de caja abierta en ${state.terminalCode.ifBlank { "la terminal" }}; registra la salida de efectivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        PosSectionCard(title = "Agregar línea", modifier = Modifier.fillMaxWidth()) {
            ProductPicker(state, viewModel)
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PosTextField(
                    value = state.purchaseLineQtyInput,
                    onValueChange = viewModel::onPurchaseLineQtyChange,
                    label = "Cantidad",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(120.dp),
                )
                PosTextField(
                    value = state.purchaseLineUnitCostInput,
                    onValueChange = viewModel::onPurchaseLineUnitCostChange,
                    label = "Costo unitario",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(150.dp),
                )
                PosTextField(
                    value = state.purchaseLineLotCode,
                    onValueChange = viewModel::onPurchaseLineLotCodeChange,
                    label = if (state.picker.selectedProduct?.lotTracked == true) "Lote (obligatorio)" else "Lote (opcional)",
                    modifier = Modifier.width(170.dp),
                )
                PosTextField(
                    value = state.purchaseLineExpiryDate,
                    onValueChange = viewModel::onPurchaseLineExpiryDateChange,
                    label = "Caducidad (AAAA-MM-DD)",
                    modifier = Modifier.width(230.dp),
                )
                PosSecondaryButton(
                    text = "Agregar",
                    onClick = viewModel::addPurchaseLine,
                    icon = Icons.Filled.Add,
                    enabled = state.picker.selectedProduct != null,
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        PosSectionCard(title = "Líneas de la compra", modifier = Modifier.fillMaxWidth()) {
            if (state.purchaseLines.isEmpty()) {
                EmptyText("Aún no hay líneas. Busca un producto y agrégalo.")
            } else {
                state.purchaseLines.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName, fontWeight = FontWeight.SemiBold)
                            Text(
                                buildString {
                                    append("${line.qty.trimZeros()} ${line.unitCode} × ")
                                    append(line.unitCost.money())
                                    line.lotCode?.let { append(" · lote $it") }
                                    line.expiryDate?.let { append(" · cad. $it") }
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PosMoneyText(amount = line.lineTotal, style = MaterialTheme.typography.bodyMedium)
                        PosSecondaryButton(
                            text = "Quitar",
                            onClick = { viewModel.removePurchaseLine(index) },
                            modifier = Modifier.padding(start = PosSpacing.sm).width(110.dp),
                        )
                    }
                    if (index < state.purchaseLines.lastIndex) HorizontalDivider()
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Total de la compra", fontWeight = FontWeight.SemiBold)
                    PosMoneyText(amount = state.purchaseTotal, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            PosPrimaryButton(
                text = "Registrar compra",
                onClick = viewModel::requestSubmitPurchase,
                enabled = !state.isSubmittingPurchase && state.purchaseLines.isNotEmpty(),
                isLoading = state.isSubmittingPurchase,
                modifier = Modifier.width(220.dp),
            )
            state.lastPurchaseResult?.let {
                Text("Última compra: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NewSupplierForm(state: OperationsState, viewModel: OperationsViewModel) {
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(PosSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Text("Nuevo proveedor", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PosTextField(
                    value = state.newSupplierName,
                    onValueChange = viewModel::onNewSupplierNameChange,
                    label = "Nombre",
                    modifier = Modifier.weight(1f),
                )
                PosTextField(
                    value = state.newSupplierPhone,
                    onValueChange = viewModel::onNewSupplierPhoneChange,
                    label = "Teléfono (opcional)",
                    modifier = Modifier.width(200.dp),
                )
                PosTextField(
                    value = state.newSupplierTaxId,
                    onValueChange = viewModel::onNewSupplierTaxIdChange,
                    label = "RFC (opcional)",
                    modifier = Modifier.width(170.dp),
                )
                PosPrimaryButton(
                    text = "Guardar",
                    onClick = viewModel::createSupplier,
                    enabled = !state.isCreatingSupplier && state.newSupplierName.isNotBlank(),
                    isLoading = state.isCreatingSupplier,
                    modifier = Modifier.width(130.dp),
                )
            }
        }
    }
}

// ---- Internal requisitions ---------------------------------------------------

@Composable
private fun InternalReqTab(state: OperationsState, viewModel: OperationsViewModel) {
    if (!state.canManageOperations) {
        RestrictedNotice("Solo ADMIN o MANAGER pueden registrar consumos internos.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(title = "Consumo interno", modifier = Modifier.fillMaxWidth()) {
            Text(
                "Descuenta del inventario de la Fonda o la Tortería lo que consumen en su operación. " +
                    "El stock debe existir en esa unidad (se recibe con una compra dirigida a ella).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PosDropdownField(
                options = state.consumptionBusinessUnits,
                selected = state.selectedRequisitionBusinessUnit,
                onSelect = viewModel::selectRequisitionBusinessUnit,
                label = "Unidad que consume",
                optionLabel = { it.name },
                emptyText = "No hay unidades Fonda/Tortería configuradas",
                modifier = Modifier.width(260.dp),
            )
        }

        PosSectionCard(title = "Agregar línea", modifier = Modifier.fillMaxWidth()) {
            ProductPicker(state, viewModel)
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PosTextField(
                    value = state.requisitionLineQtyInput,
                    onValueChange = viewModel::onRequisitionLineQtyChange,
                    label = "Cantidad",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(120.dp),
                )
                PosSecondaryButton(
                    text = "Agregar",
                    onClick = viewModel::addRequisitionLine,
                    icon = Icons.Filled.Add,
                    enabled = state.picker.selectedProduct != null,
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        PosSectionCard(title = "Líneas del consumo", modifier = Modifier.fillMaxWidth()) {
            if (state.requisitionLines.isEmpty()) {
                EmptyText("Aún no hay líneas.")
            } else {
                state.requisitionLines.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${line.qty.trimZeros()} ${line.unitCode}" + (line.lotCode?.let { " · lote $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PosSecondaryButton(
                            text = "Quitar",
                            onClick = { viewModel.removeRequisitionLine(index) },
                            modifier = Modifier.width(110.dp),
                        )
                    }
                    if (index < state.requisitionLines.lastIndex) HorizontalDivider()
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            PosPrimaryButton(
                text = "Registrar consumo",
                onClick = viewModel::requestSubmitRequisition,
                enabled = !state.isSubmittingRequisition && state.requisitionLines.isNotEmpty(),
                isLoading = state.isSubmittingRequisition,
                modifier = Modifier.width(220.dp),
            )
            state.lastRequisitionResult?.let {
                Text("Último consumo: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---- On hand -----------------------------------------------------------------

@Composable
private fun OnHandTab(state: OperationsState, viewModel: OperationsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(title = "Existencias", modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PosDropdownField(
                    options = listOf<OperationsBusinessUnit?>(null) + state.businessUnits,
                    selected = state.selectedOnHandBusinessUnit,
                    onSelect = { viewModel.selectOnHandBusinessUnit(it) },
                    label = "Unidad de negocio",
                    optionLabel = { it?.name ?: "Todas" },
                    placeholder = "Todas",
                    modifier = Modifier.width(220.dp),
                )
                PosTextField(
                    value = state.onHandQuickSearch,
                    onValueChange = viewModel::onOnHandQuickSearchChange,
                    label = "Filtrar por producto",
                    modifier = Modifier.weight(1f),
                )
                PosSecondaryButton(
                    text = "Actualizar",
                    onClick = viewModel::refreshOnHand,
                    icon = Icons.Filled.Refresh,
                    enabled = !state.isLoadingOnHand,
                    modifier = Modifier.width(150.dp),
                )
            }
        }

        PosSectionCard(
            title = "Resultados (${state.visibleOnHandItems.size})",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when {
                state.isLoadingOnHand -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.visibleOnHandItems.isEmpty() -> EmptyText("Sin existencias para el filtro actual.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    itemsIndexed(state.visibleOnHandItems) { _, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "ID ${item.productId} · ${item.businessUnitCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${item.qtyBase.trimZeros()} ${item.baseUnitCode}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.qtyBase <= 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---- Lots --------------------------------------------------------------------

@Composable
private fun LotsTab(state: OperationsState, viewModel: OperationsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(title = "Lotes por producto", modifier = Modifier.fillMaxWidth()) {
            ProductPicker(state, viewModel, showUnit = false)
        }

        PosSectionCard(
            title = "Lotes (${state.picker.lots.size})",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when {
                state.picker.selectedProduct == null -> EmptyText("Busca y selecciona un producto para ver sus lotes.")
                state.picker.isLoadingLots -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.picker.lots.isEmpty() -> EmptyText("Este producto no tiene lotes registrados.")
                else -> LazyColumn(modifier = Modifier.heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    itemsIndexed(state.picker.lots) { _, lot ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lot.lotCode, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "ID ${lot.lotId} · caduca ${lot.expiryDate ?: "sin fecha"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${lot.qtyBase.trimZeros()} ${state.picker.selectedProduct?.baseUnitCode.orEmpty()}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.SemiBold,
                                color = if (lot.qtyBase <= 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---- Waste -------------------------------------------------------------------

@Composable
private fun WasteTab(state: OperationsState, viewModel: OperationsViewModel) {
    if (!state.canManageOperations) {
        RestrictedNotice("Solo ADMIN o MANAGER pueden registrar mermas.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(title = "Merma (inventario de Tienda)", modifier = Modifier.fillMaxWidth()) {
            ProductPicker(state, viewModel)
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PosTextField(
                    value = state.wasteQtyInput,
                    onValueChange = viewModel::onWasteQtyChange,
                    label = "Cantidad",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(120.dp),
                )
                PosDropdownField(
                    options = WasteReason.entries,
                    selected = state.wasteReason,
                    onSelect = viewModel::selectWasteReason,
                    label = "Motivo",
                    optionLabel = { it.label },
                    modifier = Modifier.width(220.dp),
                )
                PosTextField(
                    value = state.wasteNotes,
                    onValueChange = viewModel::onWasteNotesChange,
                    label = "Notas (opcional)",
                    modifier = Modifier.weight(1f),
                )
            }
            PosPrimaryButton(
                text = "Registrar merma",
                onClick = viewModel::requestSubmitWaste,
                enabled = !state.isSubmittingWaste && state.picker.selectedProduct != null,
                isLoading = state.isSubmittingWaste,
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

// ---- Adjustments -------------------------------------------------------------

@Composable
private fun AdjustmentsTab(state: OperationsState, viewModel: OperationsViewModel) {
    if (!state.canManageOperations) {
        RestrictedNotice("Solo ADMIN o MANAGER pueden registrar ajustes.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
    ) {
        PosSectionCard(title = "Ajuste de inventario (Tienda)", modifier = Modifier.fillMaxWidth()) {
            Text(
                "Positivo suma existencias, negativo las resta. Úsalo para diferencias de conteo o correcciones de captura.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProductPicker(state, viewModel)
            Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PosTextField(
                    value = state.adjustmentQtyDeltaInput,
                    onValueChange = viewModel::onAdjustmentQtyDeltaChange,
                    label = "Diferencia (+/-)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(150.dp),
                )
                PosDropdownField(
                    options = AdjustmentReason.entries,
                    selected = state.adjustmentReason,
                    onSelect = viewModel::selectAdjustmentReason,
                    label = "Motivo",
                    optionLabel = { it.label },
                    modifier = Modifier.width(240.dp),
                )
                PosTextField(
                    value = state.adjustmentNotes,
                    onValueChange = viewModel::onAdjustmentNotesChange,
                    label = "Notas (opcional)",
                    modifier = Modifier.weight(1f),
                )
            }
            PosPrimaryButton(
                text = "Registrar ajuste",
                onClick = viewModel::requestSubmitAdjustment,
                enabled = !state.isSubmittingAdjustment && state.picker.selectedProduct != null,
                isLoading = state.isSubmittingAdjustment,
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

// ---- Small helpers -----------------------------------------------------------

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RestrictedNotice(text: String) {
    PosSectionCard(modifier = Modifier.fillMaxWidth()) {
        EmptyText(text)
    }
}

private fun Supplier.label(): String = if (phone.isNullOrBlank()) name else "$name · $phone"

private fun InventoryLotItem.summary(): String =
    "$lotCode · ${qtyBase.trimZeros()} disp." + (expiryDate?.let { " · cad. $it" } ?: "")

private fun Double.trimZeros(): String {
    if (this == kotlin.math.floor(this) && kotlin.math.abs(this) < 1e15) return toLong().toString()
    return toString()
}

private fun Double.money(): String {
    val cents = kotlin.math.round(this * 100).toLong()
    return "$${cents / 100}.${(kotlin.math.abs(cents) % 100).toString().padStart(2, '0')}"
}

private val OperationsTab.displayName: String
    get() = when (this) {
        OperationsTab.PURCHASES -> "Compras"
        OperationsTab.INTERNAL_REQ -> "Consumo interno"
        OperationsTab.ON_HAND -> "Inventario"
        OperationsTab.LOTS -> "Lotes"
        OperationsTab.WASTE -> "Mermas"
        OperationsTab.ADJUSTMENTS -> "Ajustes"
    }
