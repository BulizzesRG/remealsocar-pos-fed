package com.posfab.shared.network.pos

import com.posfab.shared.network.catalog.ProductPricesDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DraftDto(
    val id: Long,
    val type: String,
    val status: String,
    @SerialName("terminalId") val terminalId: Long,
    @SerialName("userId") val userId: Long? = null,
    @SerialName("businessUnitId") val businessUnitId: Long? = null,
    val version: Int,
    val lines: List<DraftLineDto>,
    val totals: DraftTotalsDto,
    @SerialName("channel") val channel: String = "TAKEAWAY",
)

@Serializable
data class DraftListDto(val items: List<DraftDto>)

@Serializable
data class DraftLineDto(
    val id: Long,
    @SerialName("lineNo") val lineNo: Int,
    @SerialName("productId") val productId: Long,
    @SerialName("unitId") val unitId: Long,
    val qty: String,
    @SerialName("unitPrice") val unitPrice: String? = null,
    @SerialName("unitCost") val unitCost: String? = null,
    @SerialName("cashUnitPrice") val cashUnitPrice: String? = null,
    @SerialName("productName") val productName: String,
    @SerialName("productBarcode") val productBarcode: String? = null,
    @SerialName("unitCode") val unitCode: String,
    @SerialName("lineTotal") val lineTotal: String,
    @SerialName("lotId") val lotId: Long? = null,
    @SerialName("lotTracked") val lotTracked: Boolean = false,
    val notes: String? = null,
    @SerialName("priceType") val priceType: String = "PIECE",
)

@Serializable
data class DraftTotalsDto(
    val amount: String,
    val subtotal: String,
    val tax: String,
    val total: String,
    @SerialName("cashDiscount") val cashDiscount: String = "0",
    @SerialName("cashTotal") val cashTotal: String? = null,
)

@Serializable
data class DraftCreateRequestDto(
    val type: String,
    @SerialName("terminalId") val terminalId: Long,
    @SerialName("businessUnitId") val businessUnitId: Long? = null,
    @SerialName("customerId") val customerId: Long? = null,
    @SerialName("supplierId") val supplierId: Long? = null,
    val notes: String? = null,
)

@Serializable
data class DraftCreateResponseDto(
    val id: Long,
    val version: Int,
)

@Serializable
data class AddLineRequestDto(
    @SerialName("productId") val productId: Long,
    @SerialName("unitId") val unitId: Long,
    val qty: String? = null,
    @SerialName("amount") val amount: String? = null,
    @SerialName("unitPrice") val unitPrice: String? = null,
    @SerialName("unitCost") val unitCost: String? = null,
    @SerialName("lotId") val lotId: Long? = null,
    @SerialName("expectedVersion") val expectedVersion: Int,
)

@Serializable
data class UpdateLineRequestDto(
    @SerialName("productId") val productId: Long? = null,
    @SerialName("unitId") val unitId: Long? = null,
    val qty: String? = null,
    @SerialName("amount") val amount: String? = null,
    @SerialName("unitPrice") val unitPrice: String? = null,
    @SerialName("unitCost") val unitCost: String? = null,
    @SerialName("lotId") val lotId: Long? = null,
    val notes: String? = null,
    @SerialName("expectedVersion") val expectedVersion: Int,
)

@Serializable
data class CheckoutRequestDto(
    @SerialName("paymentStatus") val paymentStatus: String? = null,
    @SerialName("paymentType") val paymentType: String? = null,
    @SerialName("paidAmount") val paidAmount: String? = null,
    @SerialName("customerId") val customerId: Long? = null,
    @SerialName("idempotencyKey") val idempotencyKey: String? = null,
)

@Serializable
data class CheckoutResponseDto(
    val id: Long,
    val folio: String? = null,
    val subtotal: String? = null,
    @SerialName("cashDiscount") val cashDiscount: String? = null,
    @SerialName("totalAmount") val totalAmount: String? = null,
)

@Serializable
data class DraftChannelRequestDto(
    val channel: String,
    @SerialName("expectedVersion") val expectedVersion: Int,
)

@Serializable
data class CancelDraftRequestDto(
    @SerialName("expectedVersion") val expectedVersion: Int,
)

@Serializable
data class DraftIssueDto(
    val code: String,
    val message: String,
    @SerialName("lineId") val lineId: Long? = null,
)

@Serializable
data class ValidateSummaryDto(
    @SerialName("lineCount") val lineCount: Int,
    @SerialName("cashSessionOpen") val cashSessionOpen: Boolean? = null,
)

@Serializable
data class ValidateDraftResponseDto(
    val ok: Boolean,
    val issues: List<DraftIssueDto> = emptyList(),
    val summary: ValidateSummaryDto? = null,
)

@Serializable
data class ResolveLotsIssueDto(
    @SerialName("lineId") val lineId: Long,
    @SerialName("productId") val productId: Long,
    @SerialName("requiredQtyBase") val requiredQtyBase: String? = null,
    @SerialName("availableQtyBase") val availableQtyBase: String? = null,
    @SerialName("missingQtyBase") val missingQtyBase: String? = null,
    val message: String,
)

@Serializable
data class ResolveLotsResponseDto(
    val ok: Boolean,
    val id: Long,
    val version: Int,
    @SerialName("linesResolved") val linesResolved: Int,
    val issues: List<ResolveLotsIssueDto> = emptyList(),
)

@Serializable
data class BarcodeLookupProductDto(
    val id: Long,
    val sku: String,
    val name: String,
    @SerialName("baseUnitId") val baseUnitId: Long,
    @SerialName("requiresLotTracking") val requiresLotTracking: Boolean,
    val active: Boolean,
    @SerialName("currentPrice") val currentPrice: String? = null,
    @SerialName("priceType") val priceType: String = "PIECE",
    @SerialName("kitchenKind") val kitchenKind: String? = null,
    val prices: ProductPricesDto? = null,
)

@Serializable
data class BarcodeLookupUomDto(
    val id: Long,
    @SerialName("unitId") val unitId: Long,
    @SerialName("factorToBase") val factorToBase: String,
    val barcode: String,
)

@Serializable
data class BarcodeLookupResponseDto(
    val product: BarcodeLookupProductDto,
    @SerialName("matchedOn") val matchedOn: String,
    @SerialName("matchedUom") val matchedUom: BarcodeLookupUomDto? = null,
)
