package com.posfab.shared.network.reports

import kotlinx.serialization.Serializable

@Serializable
data class SaleHistoryItemDto(
    val id: Long,
    val folio: String,
    val terminalId: Long,
    val businessUnitId: Long,
    val paymentType: String,
    val paymentStatus: String,
    val soldAt: String,
    val totalAmount: String,
    val cashDiscount: String,
    val channel: String,
    val customerName: String? = null,
    val customerNickname: String? = null,
)

@Serializable
data class SaleDetailDto(
    val id: Long,
    val folio: String,
    val soldAt: String,
    val terminalId: Long,
    val terminalCode: String,
    val businessUnitId: Long,
    val businessUnitCode: String,
    val paymentType: String,
    val paymentStatus: String,
    val channel: String,
    val subtotal: String,
    val cashDiscount: String,
    val totalAmount: String,
    val customerId: Long? = null,
    val customerName: String? = null,
    val customerNickname: String? = null,
    val lines: List<SaleDetailLineDto>,
)

@Serializable
data class SaleDetailLineDto(
    val id: Long,
    val productId: Long,
    val productName: String,
    val unitCode: String,
    val priceType: String,
    val qty: String,
    val unitPrice: String,
    val lineTotal: String,
)

@Serializable
data class DailyBreakdownDto(
    val byPayment: List<PaymentBreakdownRowDto>,
    val byKitchen: List<KitchenBreakdownRowDto>,
)

@Serializable
data class PaymentBreakdownRowDto(
    val saleDate: String,
    val businessUnitId: Long,
    val businessUnitCode: String,
    val paymentType: String,
    val salesCount: Long,
    val subtotal: String,
    val cashDiscount: String,
    val totalAmount: String,
)

@Serializable
data class KitchenBreakdownRowDto(
    val saleDate: String,
    val businessUnitId: Long,
    val businessUnitCode: String,
    val kitchenKind: String? = null,
    val lineCount: Long,
    val amount: String,
)

@Serializable
data class DailySalesTotalsDto(
    val saleDate: String,
    val terminalId: Long,
    val businessUnitId: Long,
    val salesCount: Long,
    val totalAmount: String,
)

@Serializable
data class DebtorItemDto(
    val customerId: Long,
    val customerName: String,
    val outstandingBalance: String,
)

@Serializable
data class WasteItemDto(
    val wasteDate: String,
    val productId: Long,
    val totalWasteQtyBase: String,
)

@Serializable
data class IntegrityIssueSampleDto(
    val entityType: String,
    val entityId: String,
)

@Serializable
data class IntegrityIssueDto(
    val code: String,
    val count: Int,
    val samples: List<IntegrityIssueSampleDto> = emptyList(),
)

@Serializable
data class IntegrityCheckDto(
    val ok: Boolean,
    val issues: List<IntegrityIssueDto> = emptyList(),
)
