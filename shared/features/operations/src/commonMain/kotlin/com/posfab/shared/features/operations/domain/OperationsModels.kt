package com.posfab.shared.features.operations.domain

/*
 * Domain models of the Operations feature. They mirror the backend contract: numeric IDs,
 * quantities in the unit the user captured (the backend converts to base units) and the
 * business unit / terminal identified by id.
 */

data class OperationsBusinessUnit(
    val id: Long,
    val code: String,
    val name: String,
)

data class OperationsUnit(
    val id: Long,
    val code: String,
    val name: String,
)

data class Supplier(
    val id: Long,
    val name: String,
    val phone: String?,
    val taxId: String?,
    val active: Boolean,
)

data class SupplierInput(
    val name: String,
    val phone: String?,
    val taxId: String?,
)

/** Product as needed to capture an operation line. */
data class OperationsProduct(
    val id: Long,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val baseUnitCode: String,
    val baseUnitId: Long,
    val lotTracked: Boolean,
)

/** A unit the product can be captured in (base unit plus its UOM conversions). */
data class ProductUnitOption(
    val unitId: Long,
    val code: String,
    val factorToBase: Double,
)

data class PurchaseLineInput(
    val productId: Long,
    val productName: String,
    val unitId: Long,
    val unitCode: String,
    val qty: Double,
    val unitCost: Double,
    val lotCode: String? = null,
    val expiryDate: String? = null,
) {
    val lineTotal: Double get() = qty * unitCost
}

data class PurchaseInput(
    val businessUnitId: Long,
    val terminalId: Long?,
    val supplierId: Long,
    val supplierDocNo: String?,
    val paidCash: Boolean,
    val paidFromTerminalId: Long?,
    val lines: List<PurchaseLineInput>,
)

data class PurchaseResult(val purchaseId: Long)

data class RequisitionLineInput(
    val productId: Long,
    val productName: String,
    val unitId: Long,
    val unitCode: String,
    val qty: Double,
    val lotId: Long? = null,
    val lotCode: String? = null,
)

data class InternalRequisitionInput(
    /** Consuming business unit (FONDA or TORTERIA). */
    val businessUnitId: Long,
    val terminalId: Long?,
    val lines: List<RequisitionLineInput>,
)

data class InternalRequisitionResult(
    val requisitionId: Long,
    val folio: String?,
)

data class InventoryOnHandItem(
    val productId: Long,
    val productName: String,
    val businessUnitId: Long,
    val businessUnitCode: String,
    /** Quantity in the product's base unit. */
    val qtyBase: Double,
    val baseUnitCode: String,
)

data class InventoryLotItem(
    val lotId: Long,
    val productId: Long,
    val lotCode: String,
    val expiryDate: String?,
    val qtyBase: Double,
)

enum class WasteReason(val code: String, val label: String) {
    EXPIRED("EXPIRED", "Caducado"),
    DAMAGED("DAMAGED", "Dañado"),
    SPOILED("SPOILED", "Echado a perder"),
    LOST("LOST", "Extraviado"),
}

enum class AdjustmentReason(val code: String, val label: String) {
    COUNT_DIFFERENCE("COUNT_DIFFERENCE", "Diferencia de conteo"),
    DATA_FIX("DATA_FIX", "Corrección de datos"),
    OTHER("OTHER", "Otro"),
}

data class WasteLineInput(
    val productId: Long,
    val unitId: Long,
    val qty: Double,
    val reason: WasteReason,
    val lotId: Long? = null,
    val notes: String? = null,
)

data class WasteInput(
    val terminalId: Long,
    /** Must be TIENDA. */
    val businessUnitId: Long,
    val lines: List<WasteLineInput>,
)

data class AdjustmentLineInput(
    val productId: Long,
    val unitId: Long,
    /** Non-zero. Positive adds stock, negative removes it. */
    val qtyDelta: Double,
    val reason: AdjustmentReason,
    val lotId: Long? = null,
    val notes: String? = null,
)

data class AdjustmentInput(
    val terminalId: Long,
    /** Must be TIENDA. */
    val businessUnitId: Long,
    val lines: List<AdjustmentLineInput>,
)

data class InventoryOperationResult(val operationId: Long)
