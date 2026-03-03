package com.posfab.shared.network.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PurchaseRequestDto(
    val supplier: String,
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("business_unit") val businessUnit: String,
    @SerialName("paid_cash") val paidCash: Boolean,
    @SerialName("paid_from_terminal_id") val paidFromTerminalId: String? = null,
    val lines: List<PurchaseLineDto>,
)

@Serializable
data class PurchaseLineDto(
    @SerialName("product_id") val productId: String,
    val unit: String,
    val qty: Double,
    @SerialName("unit_cost") val unitCost: Double,
    @SerialName("lot_code") val lotCode: String? = null,
    @SerialName("lot_expiry") val lotExpiry: String? = null,
)

@Serializable
data class PurchaseResponseDto(
    @SerialName("purchase_id") val purchaseId: String? = null,
    val folio: String? = null,
    val message: String? = null,
)

@Serializable
data class InternalRequisitionRequestDto(
    @SerialName("source_business_unit") val sourceBusinessUnit: String,
    @SerialName("target_business_unit") val targetBusinessUnit: String,
    @SerialName("terminal_id") val terminalId: String,
    val lines: List<InternalRequisitionLineDto>,
)

@Serializable
data class InternalRequisitionLineDto(
    @SerialName("product_id") val productId: String,
    val unit: String,
    val qty: Double,
    @SerialName("lot_id") val lotId: String? = null,
)

@Serializable
data class InternalRequisitionResponseDto(
    @SerialName("requisition_id") val requisitionId: String? = null,
    val folio: String? = null,
    val message: String? = null,
)

@Serializable
data class OnHandItemDto(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("business_unit") val businessUnit: String,
    val unit: String,
    @SerialName("on_hand") val onHand: Double,
)

@Serializable
data class InventoryLotDto(
    @SerialName("product_id") val productId: String,
    @SerialName("lot_id") val lotId: String? = null,
    @SerialName("lot_code") val lotCode: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("on_hand") val onHand: Double,
    val unit: String? = null,
)

@Serializable
data class WasteRequestDto(
    @SerialName("product_id") val productId: String,
    @SerialName("business_unit") val businessUnit: String,
    val unit: String,
    val qty: Double,
    @SerialName("reason_code") val reasonCode: String,
    @SerialName("lot_id") val lotId: String? = null,
)

@Serializable
data class WasteResponseDto(
    @SerialName("waste_id") val wasteId: String? = null,
    val message: String? = null,
)

@Serializable
data class AdjustmentRequestDto(
    @SerialName("product_id") val productId: String,
    @SerialName("business_unit") val businessUnit: String,
    val unit: String,
    @SerialName("qty_delta") val qtyDelta: Double,
    @SerialName("reason_code") val reasonCode: String,
    @SerialName("lot_id") val lotId: String? = null,
)

@Serializable
data class AdjustmentResponseDto(
    @SerialName("adjustment_id") val adjustmentId: String? = null,
    val message: String? = null,
)
