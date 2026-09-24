package com.posfab.shared.features.operations.ui

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.operations.common.OperationsErrorText
import com.posfab.shared.features.operations.common.OperationsUseCases
import com.posfab.shared.features.operations.domain.AdjustmentInput
import com.posfab.shared.features.operations.domain.AdjustmentLineInput
import com.posfab.shared.features.operations.domain.AdjustmentReason
import com.posfab.shared.features.operations.domain.InternalRequisitionInput
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.OperationsBusinessUnit
import com.posfab.shared.features.operations.domain.OperationsProduct
import com.posfab.shared.features.operations.domain.ProductUnitOption
import com.posfab.shared.features.operations.domain.PurchaseInput
import com.posfab.shared.features.operations.domain.PurchaseLineInput
import com.posfab.shared.features.operations.domain.RequisitionLineInput
import com.posfab.shared.features.operations.domain.Supplier
import com.posfab.shared.features.operations.domain.SupplierInput
import com.posfab.shared.features.operations.domain.WasteInput
import com.posfab.shared.features.operations.domain.WasteLineInput
import com.posfab.shared.features.operations.domain.WasteReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class OperationsViewModel(
    session: UserSession,
    terminalId: Long,
    defaultBusinessUnitId: Long,
    private val useCases: OperationsUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val repository get() = useCases.repository

    private val _state = MutableStateFlow(
        OperationsState(
            canManageOperations = session.user.roles.any { it == UserRole.ADMIN || it == UserRole.MANAGER },
            terminalId = terminalId,
            terminalCode = session.terminalCode,
            defaultBusinessUnitId = defaultBusinessUnitId,
            purchaseBusinessUnitId = defaultBusinessUnitId,
            onHandBusinessUnitId = defaultBusinessUnitId,
        )
    )
    val state: StateFlow<OperationsState> = _state.asStateFlow()

    init {
        loadReferenceData()
        refreshOnHand()
    }

    // ---- Navigation / reference data --------------------------------------

    fun selectTab(tab: OperationsTab) {
        if (tab == _state.value.selectedTab) return
        _state.update { it.copy(selectedTab = tab, picker = ProductPickerState(), errorMessage = null, confirmDialog = null) }
    }

    fun loadReferenceData() {
        scope.launch {
            _state.update { it.copy(isLoadingReferenceData = true) }
            val businessUnits = repository.listBusinessUnits()
            val suppliers = repository.listSuppliers()
            _state.update { current ->
                var next = current.copy(isLoadingReferenceData = false)
                when (businessUnits) {
                    is AppResult.Success -> {
                        val units = businessUnits.value
                        next = next.copy(
                            businessUnits = units,
                            purchaseBusinessUnitId = current.purchaseBusinessUnitId?.takeIf { id -> units.any { it.id == id } }
                                ?: units.firstOrNull()?.id,
                            requisitionBusinessUnitId = current.requisitionBusinessUnitId?.takeIf { id -> units.any { it.id == id } }
                                ?: units.firstOrNull { it.code.equals("FONDA", true) || it.code.equals("TORTERIA", true) }?.id,
                            onHandBusinessUnitId = current.onHandBusinessUnitId?.takeIf { id -> units.any { it.id == id } },
                        )
                    }
                    is AppResult.Failure -> next = next.copy(errorMessage = OperationsErrorText.from(businessUnits.error))
                }
                when (suppliers) {
                    is AppResult.Success -> next = next.copy(
                        suppliers = suppliers.value,
                        purchaseSupplierId = current.purchaseSupplierId?.takeIf { id -> suppliers.value.any { it.id == id } },
                    )
                    is AppResult.Failure -> if (next.errorMessage == null) {
                        next = next.copy(errorMessage = OperationsErrorText.from(suppliers.error))
                    }
                }
                next
            }
        }
    }

    // ---- Product picker ---------------------------------------------------

    fun onProductQueryChange(value: String) {
        _state.update { it.copy(picker = it.picker.copy(query = value)) }
    }

    fun searchProducts() {
        val query = _state.value.picker.query.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(errorMessage = "Escribe el nombre, SKU o código del producto") }
            return
        }
        scope.launch {
            _state.update { it.copy(picker = it.picker.copy(isSearching = true), errorMessage = null) }
            when (val result = repository.searchProducts(query)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        picker = it.picker.copy(isSearching = false, results = result.value),
                        notice = if (result.value.isEmpty()) "Sin productos para \"$query\"" else it.notice,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(picker = it.picker.copy(isSearching = false), errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    fun selectProduct(product: OperationsProduct) {
        _state.update {
            it.copy(
                picker = it.picker.copy(
                    selectedProduct = product,
                    results = emptyList(),
                    query = product.name,
                    unitOptions = emptyList(),
                    selectedUnit = null,
                    lots = emptyList(),
                    selectedLot = null,
                    isLoadingUnits = true,
                ),
                errorMessage = null,
            )
        }
        scope.launch {
            when (val result = repository.productUnitOptions(product)) {
                is AppResult.Success -> _state.update {
                    if (it.picker.selectedProduct?.id != product.id) return@update it
                    it.copy(picker = it.picker.copy(isLoadingUnits = false, unitOptions = result.value, selectedUnit = result.value.firstOrNull()))
                }
                is AppResult.Failure -> _state.update {
                    // Fall back to the base unit so the user can still capture the line.
                    val base = ProductUnitOption(product.baseUnitId, product.baseUnitCode, 1.0)
                    it.copy(
                        picker = it.picker.copy(isLoadingUnits = false, unitOptions = listOf(base), selectedUnit = base),
                        errorMessage = OperationsErrorText.from(result.error),
                    )
                }
            }
        }
        val needsLots = product.lotTracked || _state.value.selectedTab == OperationsTab.LOTS
        if (needsLots) loadLotsForSelectedProduct()
    }

    fun clearProduct() {
        _state.update { it.copy(picker = ProductPickerState()) }
    }

    fun selectUnit(option: ProductUnitOption) {
        _state.update { it.copy(picker = it.picker.copy(selectedUnit = option)) }
    }

    fun selectLot(lot: InventoryLotItem?) {
        _state.update { it.copy(picker = it.picker.copy(selectedLot = lot)) }
    }

    fun loadLotsForSelectedProduct() {
        val product = _state.value.picker.selectedProduct ?: run {
            _state.update { it.copy(errorMessage = "Selecciona un producto para consultar lotes") }
            return
        }
        scope.launch {
            _state.update { it.copy(picker = it.picker.copy(isLoadingLots = true)) }
            when (val result = repository.fetchLots(product.id)) {
                is AppResult.Success -> _state.update {
                    if (it.picker.selectedProduct?.id != product.id) return@update it
                    it.copy(
                        picker = it.picker.copy(isLoadingLots = false, lots = result.value, selectedLot = null),
                        notice = if (result.value.isEmpty() && it.selectedTab == OperationsTab.LOTS) "Sin lotes para ${product.name}" else it.notice,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(picker = it.picker.copy(isLoadingLots = false), errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    // ---- Purchases --------------------------------------------------------

    fun selectPurchaseBusinessUnit(unit: OperationsBusinessUnit) {
        _state.update { it.copy(purchaseBusinessUnitId = unit.id) }
    }

    fun selectSupplier(supplier: Supplier) {
        _state.update { it.copy(purchaseSupplierId = supplier.id, showNewSupplierForm = false) }
    }

    fun onPurchaseSupplierDocNoChange(value: String) {
        _state.update { it.copy(purchaseSupplierDocNo = value) }
    }

    fun onPurchasePaidCashChange(value: Boolean) {
        _state.update { it.copy(purchasePaidCash = value) }
    }

    fun toggleNewSupplierForm(show: Boolean) {
        _state.update { it.copy(showNewSupplierForm = show, newSupplierName = "", newSupplierPhone = "", newSupplierTaxId = "") }
    }

    fun onNewSupplierNameChange(value: String) = _state.update { it.copy(newSupplierName = value) }
    fun onNewSupplierPhoneChange(value: String) = _state.update { it.copy(newSupplierPhone = value) }
    fun onNewSupplierTaxIdChange(value: String) = _state.update { it.copy(newSupplierTaxId = value) }

    fun createSupplier() {
        val snapshot = _state.value
        if (snapshot.isCreatingSupplier) return
        if (snapshot.newSupplierName.isBlank()) {
            _state.update { it.copy(errorMessage = "El nombre del proveedor es obligatorio") }
            return
        }
        scope.launch {
            _state.update { it.copy(isCreatingSupplier = true, errorMessage = null) }
            val input = SupplierInput(snapshot.newSupplierName, snapshot.newSupplierPhone, snapshot.newSupplierTaxId)
            when (val result = repository.createSupplier(input)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        isCreatingSupplier = false,
                        suppliers = (it.suppliers + result.value).sortedBy { s -> s.name.lowercase() },
                        purchaseSupplierId = result.value.id,
                        showNewSupplierForm = false,
                        newSupplierName = "",
                        newSupplierPhone = "",
                        newSupplierTaxId = "",
                        notice = "Proveedor \"${result.value.name}\" creado",
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isCreatingSupplier = false, errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    fun onPurchaseLineQtyChange(value: String) = _state.update { it.copy(purchaseLineQtyInput = value) }
    fun onPurchaseLineUnitCostChange(value: String) = _state.update { it.copy(purchaseLineUnitCostInput = value) }
    fun onPurchaseLineLotCodeChange(value: String) = _state.update { it.copy(purchaseLineLotCode = value) }
    fun onPurchaseLineExpiryDateChange(value: String) = _state.update { it.copy(purchaseLineExpiryDate = value) }

    fun addPurchaseLine() {
        val snapshot = _state.value
        val product = snapshot.picker.selectedProduct
        val unit = snapshot.picker.selectedUnit
        val qty = snapshot.purchaseLineQtyInput.trim().toDoubleOrNull()
        val unitCost = snapshot.purchaseLineUnitCostInput.trim().toDoubleOrNull()
        val lotCode = snapshot.purchaseLineLotCode.trim()
        val expiry = snapshot.purchaseLineExpiryDate.trim()

        val error = when {
            product == null -> "Selecciona un producto"
            unit == null -> "Selecciona la unidad de compra"
            qty == null || qty <= 0.0 -> "La cantidad debe ser mayor a 0"
            unitCost == null || unitCost < 0.0 -> "El costo unitario debe ser 0 o mayor"
            product.lotTracked && lotCode.isEmpty() -> "${product.name} maneja lotes: captura el código de lote"
            expiry.isNotEmpty() && !ISO_DATE.matches(expiry) -> "La caducidad debe tener formato AAAA-MM-DD"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(errorMessage = error) }
            return
        }

        val line = PurchaseLineInput(
            productId = product!!.id,
            productName = product.name,
            unitId = unit!!.unitId,
            unitCode = unit.code,
            qty = qty!!,
            unitCost = unitCost!!,
            lotCode = lotCode.ifEmpty { null },
            expiryDate = expiry.ifEmpty { null },
        )
        _state.update {
            it.copy(
                purchaseLines = it.purchaseLines + line,
                picker = ProductPickerState(),
                purchaseLineQtyInput = "1",
                purchaseLineUnitCostInput = "",
                purchaseLineLotCode = "",
                purchaseLineExpiryDate = "",
                errorMessage = null,
            )
        }
    }

    fun removePurchaseLine(index: Int) {
        _state.update {
            if (index !in it.purchaseLines.indices) it
            else it.copy(purchaseLines = it.purchaseLines.filterIndexed { i, _ -> i != index })
        }
    }

    fun requestSubmitPurchase() {
        val snapshot = _state.value
        val validationError = validatePurchase(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val supplier = snapshot.selectedSupplier?.name ?: "proveedor"
        val payment = if (snapshot.purchasePaidCash) "pagada en efectivo desde la caja ${snapshot.terminalCode}" else "sin pago en efectivo (a crédito o pagada fuera de caja)"
        _state.update {
            it.copy(
                confirmDialog = OperationsConfirmDialogState(
                    action = OperationsConfirmAction.PURCHASE,
                    title = "Registrar compra",
                    message = "Se registrará la compra a $supplier con ${snapshot.purchaseLines.size} línea(s) por un total de ${formatMoney(snapshot.purchaseTotal)}, $payment. El inventario se actualizará de inmediato.",
                    confirmLabel = "Registrar compra",
                ),
            )
        }
    }

    fun submitPurchase() {
        val snapshot = _state.value
        val validationError = validatePurchase(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val key = snapshot.purchaseIdempotencyKey ?: generateIdempotencyKey("purchase")
        val input = PurchaseInput(
            businessUnitId = snapshot.purchaseBusinessUnitId!!,
            terminalId = snapshot.terminalId,
            supplierId = snapshot.purchaseSupplierId!!,
            supplierDocNo = snapshot.purchaseSupplierDocNo.trim().ifBlank { null },
            paidCash = snapshot.purchasePaidCash,
            paidFromTerminalId = if (snapshot.purchasePaidCash) snapshot.terminalId else null,
            lines = snapshot.purchaseLines,
        )
        scope.launch {
            _state.update {
                it.copy(isSubmittingPurchase = true, purchaseIdempotencyKey = key, confirmDialog = null, errorMessage = null, notice = null)
            }
            when (val result = repository.createPurchase(input, key)) {
                is AppResult.Success -> {
                    val label = "#${result.value.purchaseId}"
                    _state.update {
                        it.copy(
                            isSubmittingPurchase = false,
                            purchaseLines = emptyList(),
                            purchaseSupplierDocNo = "",
                            purchaseIdempotencyKey = null,
                            lastPurchaseResult = label,
                            notice = "Compra $label registrada",
                        )
                    }
                    refreshOnHand()
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSubmittingPurchase = false, errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    private fun validatePurchase(snapshot: OperationsState): String? = when {
        !snapshot.canManageOperations -> "Solo ADMIN o MANAGER pueden registrar compras"
        snapshot.isSubmittingPurchase -> "Ya se está registrando una compra"
        snapshot.purchaseBusinessUnitId == null -> "Selecciona la unidad de negocio que recibe la compra"
        snapshot.purchaseSupplierId == null -> "Selecciona un proveedor"
        snapshot.purchaseLines.isEmpty() -> "Agrega al menos una línea de compra"
        else -> null
    }

    // ---- Internal requisitions -------------------------------------------

    fun selectRequisitionBusinessUnit(unit: OperationsBusinessUnit) {
        _state.update { it.copy(requisitionBusinessUnitId = unit.id) }
    }

    fun onRequisitionLineQtyChange(value: String) = _state.update { it.copy(requisitionLineQtyInput = value) }

    fun addRequisitionLine() {
        val snapshot = _state.value
        val product = snapshot.picker.selectedProduct
        val unit = snapshot.picker.selectedUnit
        val lot = snapshot.picker.selectedLot
        val qty = snapshot.requisitionLineQtyInput.trim().toDoubleOrNull()
        val error = when {
            product == null -> "Selecciona un producto"
            unit == null -> "Selecciona la unidad"
            qty == null || qty <= 0.0 -> "La cantidad debe ser mayor a 0"
            product.lotTracked && lot == null -> "${product.name} maneja lotes: selecciona el lote a consumir"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(errorMessage = error) }
            return
        }
        val line = RequisitionLineInput(
            productId = product!!.id,
            productName = product.name,
            unitId = unit!!.unitId,
            unitCode = unit.code,
            qty = qty!!,
            lotId = lot?.lotId,
            lotCode = lot?.lotCode,
        )
        _state.update {
            it.copy(
                requisitionLines = it.requisitionLines + line,
                picker = ProductPickerState(),
                requisitionLineQtyInput = "1",
                errorMessage = null,
            )
        }
    }

    fun removeRequisitionLine(index: Int) {
        _state.update {
            if (index !in it.requisitionLines.indices) it
            else it.copy(requisitionLines = it.requisitionLines.filterIndexed { i, _ -> i != index })
        }
    }

    fun requestSubmitRequisition() {
        val snapshot = _state.value
        val validationError = validateRequisition(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val unit = snapshot.selectedRequisitionBusinessUnit?.name ?: "la unidad seleccionada"
        _state.update {
            it.copy(
                confirmDialog = OperationsConfirmDialogState(
                    action = OperationsConfirmAction.REQUISITION,
                    title = "Registrar consumo interno",
                    message = "Se descontarán ${snapshot.requisitionLines.size} línea(s) del inventario de $unit.",
                    confirmLabel = "Registrar consumo",
                ),
            )
        }
    }

    fun submitRequisition() {
        val snapshot = _state.value
        val validationError = validateRequisition(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val key = snapshot.requisitionIdempotencyKey ?: generateIdempotencyKey("req")
        val input = InternalRequisitionInput(
            businessUnitId = snapshot.requisitionBusinessUnitId!!,
            terminalId = snapshot.terminalId,
            lines = snapshot.requisitionLines,
        )
        scope.launch {
            _state.update {
                it.copy(isSubmittingRequisition = true, requisitionIdempotencyKey = key, confirmDialog = null, errorMessage = null, notice = null)
            }
            when (val result = repository.createInternalRequisition(input, key)) {
                is AppResult.Success -> {
                    val label = result.value.folio ?: "#${result.value.requisitionId}"
                    _state.update {
                        it.copy(
                            isSubmittingRequisition = false,
                            requisitionLines = emptyList(),
                            requisitionIdempotencyKey = null,
                            lastRequisitionResult = label,
                            notice = "Consumo interno $label registrado",
                        )
                    }
                    refreshOnHand()
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSubmittingRequisition = false, errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    private fun validateRequisition(snapshot: OperationsState): String? {
        val unit = snapshot.selectedRequisitionBusinessUnit
        return when {
            !snapshot.canManageOperations -> "Solo ADMIN o MANAGER pueden registrar consumos internos"
            snapshot.isSubmittingRequisition -> "Ya se está registrando un consumo interno"
            unit == null -> "Selecciona la unidad que consume (Fonda o Tortería)"
            snapshot.consumptionBusinessUnits.none { it.id == unit.id } -> "El consumo interno solo aplica a Fonda o Tortería"
            snapshot.requisitionLines.isEmpty() -> "Agrega al menos una línea"
            else -> null
        }
    }

    // ---- On hand ----------------------------------------------------------

    fun selectOnHandBusinessUnit(unit: OperationsBusinessUnit?) {
        _state.update { it.copy(onHandBusinessUnitId = unit?.id) }
        refreshOnHand()
    }

    fun onOnHandQuickSearchChange(value: String) {
        _state.update { it.copy(onHandQuickSearch = value) }
    }

    fun refreshOnHand() {
        val businessUnitId = _state.value.onHandBusinessUnitId
        scope.launch {
            _state.update { it.copy(isLoadingOnHand = true) }
            when (val result = repository.fetchOnHand(productId = null, businessUnitId = businessUnitId)) {
                is AppResult.Success -> _state.update {
                    it.copy(isLoadingOnHand = false, onHandItems = result.value.sortedBy { item -> item.productName.lowercase() })
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isLoadingOnHand = false, errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    // ---- Waste ------------------------------------------------------------

    fun onWasteQtyChange(value: String) = _state.update { it.copy(wasteQtyInput = value) }
    fun selectWasteReason(reason: WasteReason) = _state.update { it.copy(wasteReason = reason) }
    fun onWasteNotesChange(value: String) = _state.update { it.copy(wasteNotes = value) }

    fun requestSubmitWaste() {
        val snapshot = _state.value
        val validationError = validateWaste(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val product = snapshot.picker.selectedProduct!!
        val unit = snapshot.picker.selectedUnit!!
        _state.update {
            it.copy(
                confirmDialog = OperationsConfirmDialogState(
                    action = OperationsConfirmAction.WASTE,
                    title = "Registrar merma",
                    message = "Se descontarán ${snapshot.wasteQtyInput.trim()} ${unit.code} de ${product.name} del inventario de Tienda por ${snapshot.wasteReason.label.lowercase()}.",
                    confirmLabel = "Registrar merma",
                ),
            )
        }
    }

    fun submitWaste() {
        val snapshot = _state.value
        val validationError = validateWaste(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val key = snapshot.wasteIdempotencyKey ?: generateIdempotencyKey("waste")
        val input = WasteInput(
            terminalId = snapshot.terminalId,
            businessUnitId = snapshot.tiendaBusinessUnit!!.id,
            lines = listOf(
                WasteLineInput(
                    productId = snapshot.picker.selectedProduct!!.id,
                    unitId = snapshot.picker.selectedUnit!!.unitId,
                    qty = snapshot.wasteQtyInput.trim().toDouble(),
                    reason = snapshot.wasteReason,
                    lotId = snapshot.picker.selectedLot?.lotId,
                    notes = snapshot.wasteNotes.trim().ifBlank { null },
                )
            ),
        )
        scope.launch {
            _state.update { it.copy(isSubmittingWaste = true, wasteIdempotencyKey = key, confirmDialog = null, errorMessage = null, notice = null) }
            when (val result = repository.createWaste(input, key)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isSubmittingWaste = false,
                            wasteIdempotencyKey = null,
                            wasteQtyInput = "",
                            wasteNotes = "",
                            picker = ProductPickerState(),
                            notice = "Merma #${result.value.operationId} registrada",
                        )
                    }
                    refreshOnHand()
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSubmittingWaste = false, errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    private fun validateWaste(snapshot: OperationsState): String? {
        val product = snapshot.picker.selectedProduct
        val qty = snapshot.wasteQtyInput.trim().toDoubleOrNull()
        return when {
            !snapshot.canManageOperations -> "Solo ADMIN o MANAGER pueden registrar mermas"
            snapshot.isSubmittingWaste -> "Ya se está registrando una merma"
            snapshot.tiendaBusinessUnit == null -> "No se encontró la unidad de negocio Tienda"
            product == null -> "Selecciona un producto"
            snapshot.picker.selectedUnit == null -> "Selecciona la unidad"
            qty == null || qty <= 0.0 -> "La cantidad de merma debe ser mayor a 0"
            product.lotTracked && snapshot.picker.selectedLot == null -> "${product.name} maneja lotes: selecciona el lote"
            else -> null
        }
    }

    // ---- Adjustments ------------------------------------------------------

    fun onAdjustmentQtyDeltaChange(value: String) = _state.update { it.copy(adjustmentQtyDeltaInput = value) }
    fun selectAdjustmentReason(reason: AdjustmentReason) = _state.update { it.copy(adjustmentReason = reason) }
    fun onAdjustmentNotesChange(value: String) = _state.update { it.copy(adjustmentNotes = value) }

    fun requestSubmitAdjustment() {
        val snapshot = _state.value
        val validationError = validateAdjustment(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val product = snapshot.picker.selectedProduct!!
        val unit = snapshot.picker.selectedUnit!!
        val delta = snapshot.adjustmentQtyDeltaInput.trim().toDouble()
        val verb = if (delta > 0) "Se sumarán" else "Se restarán"
        _state.update {
            it.copy(
                confirmDialog = OperationsConfirmDialogState(
                    action = OperationsConfirmAction.ADJUSTMENT,
                    title = "Registrar ajuste",
                    message = "$verb ${formatQty(kotlin.math.abs(delta))} ${unit.code} de ${product.name} en el inventario de Tienda (${snapshot.adjustmentReason.label.lowercase()}).",
                    confirmLabel = "Registrar ajuste",
                ),
            )
        }
    }

    fun submitAdjustment() {
        val snapshot = _state.value
        val validationError = validateAdjustment(snapshot)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError, confirmDialog = null) }
            return
        }
        val key = snapshot.adjustmentIdempotencyKey ?: generateIdempotencyKey("adjust")
        val input = AdjustmentInput(
            terminalId = snapshot.terminalId,
            businessUnitId = snapshot.tiendaBusinessUnit!!.id,
            lines = listOf(
                AdjustmentLineInput(
                    productId = snapshot.picker.selectedProduct!!.id,
                    unitId = snapshot.picker.selectedUnit!!.unitId,
                    qtyDelta = snapshot.adjustmentQtyDeltaInput.trim().toDouble(),
                    reason = snapshot.adjustmentReason,
                    lotId = snapshot.picker.selectedLot?.lotId,
                    notes = snapshot.adjustmentNotes.trim().ifBlank { null },
                )
            ),
        )
        scope.launch {
            _state.update { it.copy(isSubmittingAdjustment = true, adjustmentIdempotencyKey = key, confirmDialog = null, errorMessage = null, notice = null) }
            when (val result = repository.createAdjustment(input, key)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isSubmittingAdjustment = false,
                            adjustmentIdempotencyKey = null,
                            adjustmentQtyDeltaInput = "",
                            adjustmentNotes = "",
                            picker = ProductPickerState(),
                            notice = "Ajuste #${result.value.operationId} registrado",
                        )
                    }
                    refreshOnHand()
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSubmittingAdjustment = false, errorMessage = OperationsErrorText.from(result.error))
                }
            }
        }
    }

    private fun validateAdjustment(snapshot: OperationsState): String? {
        val product = snapshot.picker.selectedProduct
        val delta = snapshot.adjustmentQtyDeltaInput.trim().toDoubleOrNull()
        return when {
            !snapshot.canManageOperations -> "Solo ADMIN o MANAGER pueden registrar ajustes"
            snapshot.isSubmittingAdjustment -> "Ya se está registrando un ajuste"
            snapshot.tiendaBusinessUnit == null -> "No se encontró la unidad de negocio Tienda"
            product == null -> "Selecciona un producto"
            snapshot.picker.selectedUnit == null -> "Selecciona la unidad"
            delta == null || delta == 0.0 -> "El ajuste debe ser distinto de 0 (positivo suma, negativo resta)"
            product.lotTracked && snapshot.picker.selectedLot == null -> "${product.name} maneja lotes: selecciona el lote"
            else -> null
        }
    }

    // ---- Dialog / helpers -------------------------------------------------

    fun confirmDialogAction() {
        when (_state.value.confirmDialog?.action) {
            OperationsConfirmAction.PURCHASE -> submitPurchase()
            OperationsConfirmAction.REQUISITION -> submitRequisition()
            OperationsConfirmAction.WASTE -> submitWaste()
            OperationsConfirmAction.ADJUSTMENT -> submitAdjustment()
            null -> Unit
        }
    }

    fun dismissConfirmDialog() {
        _state.update { it.copy(confirmDialog = null) }
    }

    fun dismissMessages() {
        _state.update { it.copy(notice = null, errorMessage = null) }
    }

    private fun generateIdempotencyKey(prefix: String): String {
        val randomSuffix = Random.nextInt(100000, 999999)
        return "$prefix-${kotlin.time.Clock.System.now().toEpochMilliseconds()}-$randomSuffix"
    }

    private companion object {
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        fun formatMoney(value: Double): String {
            val cents = kotlin.math.round(value * 100).toLong()
            val whole = cents / 100
            val frac = (cents % 100).toString().padStart(2, '0')
            return "$$whole.$frac"
        }

        fun formatQty(value: Double): String =
            if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()
    }
}
