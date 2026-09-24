package com.posfab.shared.network.operations

import kotlinx.serialization.Serializable

/*
 * Wire format of the backend (bed) for purchases, internal requisitions, inventory and suppliers.
 * IDs are Long, quantities/money are decimal strings (BigDecimal on the server), field names are camelCase.
 */

// ---- Suppliers ------------------------------------------------------------

@Serializable
data class SupplierDto(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val taxId: String? = null,
    val active: Boolean = true,
)

@Serializable
data class SupplierCreateRequestDto(
    val name: String,
    val phone: String? = null,
    val taxId: String? = null,
    val active: Boolean = true,
)

// ---- Purchases ------------------------------------------------------------

@Serializable
data class PurchaseLineRequestDto(
    val productId: Long,
    val unitId: Long,
    /** Decimal string, e.g. "3.000". */
    val qty: String,
    /** Decimal string, e.g. "18.20". */
    val unitCost: String,
    val lotCode: String? = null,
    /** ISO date (yyyy-MM-dd). */
    val expiryDate: String? = null,
)

@Serializable
data class PurchaseCreateRequestDto(
    val businessUnitId: Long,
    val terminalId: Long? = null,
    val supplierId: Long,
    val supplierDocNo: String? = null,
    val paidCash: Boolean = false,
    /** Required by the backend when [paidCash] is true; that terminal needs an open cash session. */
    val paidFromTerminalId: Long? = null,
    val items: List<PurchaseLineRequestDto>,
)

// ---- Internal requisitions ------------------------------------------------

@Serializable
data class InternalRequisitionLineRequestDto(
    val productId: Long,
    val unitId: Long,
    val qty: String,
    val lotId: Long? = null,
)

@Serializable
data class InternalRequisitionCreateRequestDto(
    /** Business unit that consumes the stock. The backend only accepts FONDA or TORTERIA. */
    val businessUnitId: Long,
    val terminalId: Long? = null,
    val items: List<InternalRequisitionLineRequestDto>,
)

// ---- Inventory ------------------------------------------------------------

@Serializable
data class OnHandResponseDto(
    val productId: Long,
    val businessUnitId: Long,
    /** Quantity in the product's base unit, decimal string. */
    val qtyBase: String,
)

@Serializable
data class InventoryLotResponseDto(
    val lotId: Long,
    val productId: Long,
    val lotCode: String,
    val expiryDate: String? = null,
    val qtyBase: String,
)

@Serializable
data class WasteLineRequestDto(
    val productId: Long,
    val unitId: Long,
    val qty: String,
    val lotId: Long? = null,
    /** One of EXPIRED, DAMAGED, SPOILED, LOST. */
    val reasonCode: String,
    val notes: String? = null,
)

@Serializable
data class WasteRequestDto(
    val terminalId: Long,
    /** Must be the TIENDA business unit. */
    val businessUnitId: Long,
    val lines: List<WasteLineRequestDto>,
)

@Serializable
data class AdjustmentLineRequestDto(
    val productId: Long,
    val unitId: Long,
    /** Non-zero decimal string; positive adds stock, negative removes it. */
    val qtyDelta: String,
    val lotId: Long? = null,
    /** One of COUNT_DIFFERENCE, DATA_FIX, OTHER. */
    val reasonCode: String,
    val notes: String? = null,
)

@Serializable
data class AdjustmentRequestDto(
    val terminalId: Long,
    /** Must be the TIENDA business unit. */
    val businessUnitId: Long,
    val lines: List<AdjustmentLineRequestDto>,
)

// ---- Generic responses ----------------------------------------------------

@Serializable
data class IdResponseDto(val id: Long)

@Serializable
data class IdWithFolioResponseDto(val id: Long, val folio: String? = null)
