package com.posfab.shared.features.operations.ui

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.operations.common.OperationsErrorText
import com.posfab.shared.features.operations.common.OperationsUseCases
import com.posfab.shared.features.operations.domain.AdjustmentInput
import com.posfab.shared.features.operations.domain.InternalRequisitionInput
import com.posfab.shared.features.operations.domain.PurchaseInput
import com.posfab.shared.features.operations.domain.PurchaseLineInput
import com.posfab.shared.features.operations.domain.RequisitionLineInput
import com.posfab.shared.features.operations.domain.WasteInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class OperationsViewModel(
    session: UserSession,
    private val useCases: OperationsUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val roles = session.user.roles

    private val _state = MutableStateFlow(
        OperationsState(
            canManageOperations = roles.any { it == UserRole.ADMIN || it == UserRole.MANAGER },
            canManageWasteAdjustments = roles.contains(UserRole.MANAGER),
            terminalId = session.terminal.name,
            purchasePaidFromTerminalId = session.terminal.name,
        )
    )
    val state: StateFlow<OperationsState> = _state.asStateFlow()

    init {
        refreshOnHand()
    }

    fun selectTab(tab: OperationsTab) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    fun onBusinessUnitChange(value: String) {
        _state.value = _state.value.copy(businessUnit = value)
    }

    fun onTerminalIdChange(value: String) {
        _state.value = _state.value.copy(terminalId = value)
    }

    fun onPurchaseSupplierChange(value: String) {
        _state.value = _state.value.copy(purchaseSupplier = value)
    }

    fun onPurchasePaidCashChange(value: Boolean) {
        _state.value = _state.value.copy(purchasePaidCash = value)
    }

    fun onPurchasePaidFromTerminalIdChange(value: String) {
        _state.value = _state.value.copy(purchasePaidFromTerminalId = value)
    }

    fun onPurchaseLineProductChange(value: String) {
        _state.value = _state.value.copy(purchaseLineProductId = value)
    }

    fun onPurchaseLineUnitChange(value: String) {
        _state.value = _state.value.copy(purchaseLineUnit = value)
    }

    fun onPurchaseLineQtyChange(value: String) {
        _state.value = _state.value.copy(purchaseLineQtyInput = value)
    }

    fun onPurchaseLineUnitCostChange(value: String) {
        _state.value = _state.value.copy(purchaseLineUnitCostInput = value)
    }

    fun onPurchaseLineLotCodeChange(value: String) {
        _state.value = _state.value.copy(purchaseLineLotCode = value)
    }

    fun onPurchaseLineLotExpiryChange(value: String) {
        _state.value = _state.value.copy(purchaseLineLotExpiry = value)
    }

    fun addPurchaseLine() {
        val snapshot = _state.value
        val qty = snapshot.purchaseLineQtyInput.toDoubleOrNull()
        val unitCost = snapshot.purchaseLineUnitCostInput.toDoubleOrNull()
        if (snapshot.purchaseLineProductId.trim().isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Producto requerido para linea de compra")
            return
        }
        if (snapshot.purchaseLineUnit.trim().isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Unidad requerida para linea de compra")
            return
        }
        if (qty == null || qty <= 0.0 || unitCost == null || unitCost < 0.0) {
            _state.value = snapshot.copy(errorMessage = "Cantidad/costo invalidos en linea de compra")
            return
        }

        val line = PurchaseLineInput(
            productId = snapshot.purchaseLineProductId.trim(),
            unit = snapshot.purchaseLineUnit.trim(),
            qty = qty,
            unitCost = unitCost,
            lotCode = snapshot.purchaseLineLotCode.trim().ifBlank { null },
            lotExpiry = snapshot.purchaseLineLotExpiry.trim().ifBlank { null },
        )
        _state.value = snapshot.copy(
            purchaseLines = snapshot.purchaseLines + line,
            purchaseLineProductId = "",
            purchaseLineQtyInput = "1",
            purchaseLineUnitCostInput = "0",
            purchaseLineLotCode = "",
            purchaseLineLotExpiry = "",
            errorMessage = null,
        )
    }

    fun removePurchaseLine(index: Int) {
        val snapshot = _state.value
        if (index !in snapshot.purchaseLines.indices) return
        _state.value = snapshot.copy(purchaseLines = snapshot.purchaseLines.filterIndexed { i, _ -> i != index })
    }

    fun submitPurchase() {
        val snapshot = _state.value
        if (!snapshot.canManageOperations) {
            _state.value = snapshot.copy(errorMessage = "No autorizado para compras")
            return
        }
        if (snapshot.isSubmittingPurchase) return
        if (snapshot.purchaseSupplier.trim().isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Proveedor requerido")
            return
        }
        if (snapshot.purchaseLines.isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Agrega al menos una linea de compra")
            return
        }

        val key = snapshot.purchaseIdempotencyKey ?: generateIdempotencyKey("purchase")
        val input = PurchaseInput(
            supplier = snapshot.purchaseSupplier.trim(),
            terminalId = snapshot.terminalId.trim(),
            businessUnit = snapshot.businessUnit.trim(),
            paidCash = snapshot.purchasePaidCash,
            paidFromTerminalId = snapshot.purchasePaidFromTerminalId.trim().ifBlank { null },
            lines = snapshot.purchaseLines,
        )

        scope.launch {
            _state.value = _state.value.copy(
                isSubmittingPurchase = true,
                purchaseIdempotencyKey = key,
                errorMessage = null,
                notice = null,
            )
            when (val result = useCases.repository.createPurchase(input, key)) {
                is AppResult.Success -> {
                    val label = result.value.folio ?: result.value.purchaseId ?: "registro generado"
                    _state.value = _state.value.copy(
                        isSubmittingPurchase = false,
                        purchaseLines = emptyList(),
                        purchaseIdempotencyKey = null,
                        lastPurchaseResult = label,
                        notice = "Compra registrada: $label",
                    )
                    refreshOnHand()
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSubmittingPurchase = false,
                        errorMessage = OperationsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onReqSourceBuChange(value: String) {
        _state.value = _state.value.copy(requisitionSourceBu = value)
    }

    fun onReqTargetBuChange(value: String) {
        _state.value = _state.value.copy(requisitionTargetBu = value)
    }

    fun onReqLineProductChange(value: String) {
        _state.value = _state.value.copy(requisitionLineProductId = value)
    }

    fun onReqLineUnitChange(value: String) {
        _state.value = _state.value.copy(requisitionLineUnit = value)
    }

    fun onReqLineQtyChange(value: String) {
        _state.value = _state.value.copy(requisitionLineQtyInput = value)
    }

    fun onReqLineLotIdChange(value: String) {
        _state.value = _state.value.copy(requisitionLineLotId = value)
    }

    fun addRequisitionLine() {
        val snapshot = _state.value
        val qty = snapshot.requisitionLineQtyInput.toDoubleOrNull()
        if (snapshot.requisitionLineProductId.trim().isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Producto requerido para requisicion")
            return
        }
        if (snapshot.requisitionLineUnit.trim().isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Unidad requerida para requisicion")
            return
        }
        if (qty == null || qty <= 0.0) {
            _state.value = snapshot.copy(errorMessage = "Cantidad invalida en requisicion")
            return
        }

        val line = RequisitionLineInput(
            productId = snapshot.requisitionLineProductId.trim(),
            unit = snapshot.requisitionLineUnit.trim(),
            qty = qty,
            lotId = snapshot.requisitionLineLotId.trim().ifBlank { null },
        )
        _state.value = snapshot.copy(
            requisitionLines = snapshot.requisitionLines + line,
            requisitionLineProductId = "",
            requisitionLineQtyInput = "1",
            requisitionLineLotId = "",
            errorMessage = null,
        )
    }

    fun removeRequisitionLine(index: Int) {
        val snapshot = _state.value
        if (index !in snapshot.requisitionLines.indices) return
        _state.value = snapshot.copy(requisitionLines = snapshot.requisitionLines.filterIndexed { i, _ -> i != index })
    }

    fun submitRequisition() {
        val snapshot = _state.value
        if (!snapshot.canManageOperations) {
            _state.value = snapshot.copy(errorMessage = "No autorizado para requisiciones internas")
            return
        }
        if (snapshot.isSubmittingRequisition) return
        if (snapshot.requisitionLines.isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Agrega al menos una linea de requisicion")
            return
        }
        val target = snapshot.requisitionTargetBu.trim().uppercase()
        if (target != "FONDA" && target != "TORTERIA") {
            _state.value = snapshot.copy(errorMessage = "Destino invalido. Usa FONDA o TORTERIA")
            return
        }

        val input = InternalRequisitionInput(
            sourceBusinessUnit = snapshot.requisitionSourceBu.trim(),
            targetBusinessUnit = target,
            terminalId = snapshot.terminalId.trim(),
            lines = snapshot.requisitionLines,
        )

        scope.launch {
            _state.value = _state.value.copy(isSubmittingRequisition = true, errorMessage = null, notice = null)
            when (val result = useCases.repository.createInternalRequisition(input)) {
                is AppResult.Success -> {
                    val label = result.value.folio ?: result.value.requisitionId ?: "requisicion generada"
                    _state.value = _state.value.copy(
                        isSubmittingRequisition = false,
                        requisitionLines = emptyList(),
                        lastRequisitionResult = label,
                        notice = "Requisicion registrada: $label",
                    )
                    refreshOnHand()
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSubmittingRequisition = false,
                        errorMessage = OperationsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onOnHandProductFilterChange(value: String) {
        _state.value = _state.value.copy(onHandProductFilter = value)
    }

    fun onOnHandBusinessUnitFilterChange(value: String) {
        _state.value = _state.value.copy(onHandBusinessUnitFilter = value)
    }

    fun onOnHandQuickSearchChange(value: String) {
        _state.value = _state.value.copy(onHandQuickSearch = value)
    }

    fun refreshOnHand() {
        val snapshot = _state.value
        scope.launch {
            _state.value = _state.value.copy(isLoadingOnHand = true, errorMessage = null)
            when (
                val result = useCases.repository.fetchOnHand(
                    productId = snapshot.onHandProductFilter.trim().ifBlank { null },
                    businessUnit = snapshot.onHandBusinessUnitFilter.trim().ifBlank { null },
                )
            ) {
                is AppResult.Success -> {
                    val current = _state.value
                    _state.value = _state.value.copy(
                        isLoadingOnHand = false,
                        onHandItems = result.value,
                        notice = if (result.value.isEmpty()) "Sin existencias para el filtro actual" else current.notice,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingOnHand = false,
                        errorMessage = OperationsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onLotsProductIdChange(value: String) {
        _state.value = _state.value.copy(lotsProductId = value)
    }

    fun loadLots() {
        val snapshot = _state.value
        val productId = snapshot.lotsProductId.trim()
        if (productId.isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Producto requerido para consultar lotes")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoadingLots = true, errorMessage = null)
            when (val result = useCases.repository.fetchLots(productId)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoadingLots = false,
                        lotItems = result.value,
                        notice = if (result.value.isEmpty()) "Sin lotes para el producto" else null,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingLots = false,
                        errorMessage = OperationsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onWasteProductChange(value: String) {
        _state.value = _state.value.copy(wasteProductId = value)
    }

    fun onWasteBusinessUnitChange(value: String) {
        _state.value = _state.value.copy(wasteBusinessUnit = value)
    }

    fun onWasteUnitChange(value: String) {
        _state.value = _state.value.copy(wasteUnit = value)
    }

    fun onWasteQtyChange(value: String) {
        _state.value = _state.value.copy(wasteQtyInput = value)
    }

    fun onWasteReasonChange(value: String) {
        _state.value = _state.value.copy(wasteReasonCode = value)
    }

    fun onWasteLotIdChange(value: String) {
        _state.value = _state.value.copy(wasteLotId = value)
    }

    fun submitWaste() {
        val snapshot = _state.value
        if (!snapshot.canManageWasteAdjustments) {
            _state.value = snapshot.copy(errorMessage = "Solo MANAGER puede registrar mermas")
            return
        }
        if (snapshot.isSubmittingWaste) return
        val qty = snapshot.wasteQtyInput.toDoubleOrNull()
        if (snapshot.wasteProductId.trim().isEmpty() || snapshot.wasteUnit.trim().isEmpty() || snapshot.wasteReasonCode.trim().isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Completa producto, unidad y motivo de merma")
            return
        }
        if (qty == null || qty <= 0.0) {
            _state.value = snapshot.copy(errorMessage = "La merma requiere qty > 0")
            return
        }

        val key = snapshot.wasteIdempotencyKey ?: generateIdempotencyKey("waste")
        val input = WasteInput(
            productId = snapshot.wasteProductId.trim(),
            businessUnit = snapshot.wasteBusinessUnit.trim(),
            unit = snapshot.wasteUnit.trim(),
            qty = qty,
            reasonCode = snapshot.wasteReasonCode.trim(),
            lotId = snapshot.wasteLotId.trim().ifBlank { null },
        )

        scope.launch {
            _state.value = _state.value.copy(isSubmittingWaste = true, wasteIdempotencyKey = key, errorMessage = null)
            when (val result = useCases.repository.createWaste(input, key)) {
                is AppResult.Success -> {
                    val label = result.value.wasteId ?: result.value.message ?: "merma registrada"
                    _state.value = _state.value.copy(
                        isSubmittingWaste = false,
                        wasteIdempotencyKey = null,
                        wasteQtyInput = "",
                        wasteLotId = "",
                        notice = "Merma registrada: $label",
                    )
                    refreshOnHand()
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSubmittingWaste = false,
                        errorMessage = OperationsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onAdjustmentProductChange(value: String) {
        _state.value = _state.value.copy(adjustmentProductId = value)
    }

    fun onAdjustmentBusinessUnitChange(value: String) {
        _state.value = _state.value.copy(adjustmentBusinessUnit = value)
    }

    fun onAdjustmentUnitChange(value: String) {
        _state.value = _state.value.copy(adjustmentUnit = value)
    }

    fun onAdjustmentQtyDeltaChange(value: String) {
        _state.value = _state.value.copy(adjustmentQtyDeltaInput = value)
    }

    fun onAdjustmentReasonChange(value: String) {
        _state.value = _state.value.copy(adjustmentReasonCode = value)
    }

    fun onAdjustmentLotIdChange(value: String) {
        _state.value = _state.value.copy(adjustmentLotId = value)
    }

    fun submitAdjustment() {
        val snapshot = _state.value
        if (!snapshot.canManageWasteAdjustments) {
            _state.value = snapshot.copy(errorMessage = "Solo MANAGER puede registrar ajustes")
            return
        }
        if (snapshot.isSubmittingAdjustment) return
        val qtyDelta = snapshot.adjustmentQtyDeltaInput.toDoubleOrNull()
        if (snapshot.adjustmentProductId.trim().isEmpty() || snapshot.adjustmentUnit.trim().isEmpty() || snapshot.adjustmentReasonCode.trim().isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Completa producto, unidad y motivo de ajuste")
            return
        }
        if (qtyDelta == null || qtyDelta == 0.0) {
            _state.value = snapshot.copy(errorMessage = "El ajuste requiere qtyDelta distinto de 0")
            return
        }

        val key = snapshot.adjustmentIdempotencyKey ?: generateIdempotencyKey("adjust")
        val input = AdjustmentInput(
            productId = snapshot.adjustmentProductId.trim(),
            businessUnit = snapshot.adjustmentBusinessUnit.trim(),
            unit = snapshot.adjustmentUnit.trim(),
            qtyDelta = qtyDelta,
            reasonCode = snapshot.adjustmentReasonCode.trim(),
            lotId = snapshot.adjustmentLotId.trim().ifBlank { null },
        )

        scope.launch {
            _state.value = _state.value.copy(isSubmittingAdjustment = true, adjustmentIdempotencyKey = key, errorMessage = null)
            when (val result = useCases.repository.createAdjustment(input, key)) {
                is AppResult.Success -> {
                    val label = result.value.adjustmentId ?: result.value.message ?: "ajuste registrado"
                    _state.value = _state.value.copy(
                        isSubmittingAdjustment = false,
                        adjustmentIdempotencyKey = null,
                        adjustmentQtyDeltaInput = "",
                        adjustmentLotId = "",
                        notice = "Ajuste registrado: $label",
                    )
                    refreshOnHand()
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSubmittingAdjustment = false,
                        errorMessage = OperationsErrorText.from(result.error),
                    )
                }
            }
        }
    }

    private fun generateIdempotencyKey(prefix: String): String {
        val randomSuffix = Random.nextInt(100000, 999999)
        return "$prefix-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}-$randomSuffix"
    }
}
