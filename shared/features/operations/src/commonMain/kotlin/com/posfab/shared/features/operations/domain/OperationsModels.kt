package com.posfab.shared.features.operations.domain

data class PurchaseLineInput(
    val productId: String,
    val unit: String,
    val qty: Double,
    val unitCost: Double,
    val lotCode: String? = null,
    val lotExpiry: String? = null,
)

data class PurchaseInput(
    val supplier: String,
    val terminalId: String,
    val businessUnit: String,
    val paidCash: Boolean,
    val paidFromTerminalId: String?,
    val lines: List<PurchaseLineInput>,
)

data class PurchaseResult(
    val purchaseId: String?,
    val folio: String?,
    val message: String?,
)

data class RequisitionLineInput(
    val productId: String,
    val unit: String,
    val qty: Double,
    val lotId: String? = null,
)

data class InternalRequisitionInput(
    val sourceBusinessUnit: String,
    val targetBusinessUnit: String,
    val terminalId: String,
    val lines: List<RequisitionLineInput>,
)

data class InternalRequisitionResult(
    val requisitionId: String?,
    val folio: String?,
    val message: String?,
)

data class InventoryOnHandItem(
    val productId: String,
    val productName: String,
    val businessUnit: String,
    val unit: String,
    val onHand: Double,
)

data class InventoryLotItem(
    val productId: String,
    val lotId: String?,
    val lotCode: String,
    val expiryDate: String?,
    val onHand: Double,
    val unit: String,
)

data class WasteInput(
    val productId: String,
    val businessUnit: String,
    val unit: String,
    val qty: Double,
    val reasonCode: String,
    val lotId: String? = null,
)

data class WasteResult(
    val wasteId: String?,
    val message: String?,
)

data class AdjustmentInput(
    val productId: String,
    val businessUnit: String,
    val unit: String,
    val qtyDelta: Double,
    val reasonCode: String,
    val lotId: String? = null,
)

data class AdjustmentResult(
    val adjustmentId: String?,
    val message: String?,
)
