package com.posfab.shared.network.reports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SaleHistoryItemDto(
    val id: String,
    val folio: String,
    val total: Double,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("terminal_id") val terminalId: String,
)

@Serializable
data class DailySalesTotalsDto(
    @SerialName("terminal_id") val terminalId: String,
    @SerialName("business_unit") val businessUnit: String? = null,
    @SerialName("sales_count") val salesCount: Int,
    val total: Double,
)

@Serializable
data class DebtorItemDto(
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("debt_total") val debtTotal: Double,
)

@Serializable
data class WasteItemDto(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("waste_qty") val wasteQty: Double,
    val unit: String,
)

@Serializable
data class IntegrityIssueDto(
    val key: String,
    val count: Int,
    val samples: List<String> = emptyList(),
)

@Serializable
data class IntegrityCheckDto(
    val ok: Boolean,
    val issues: List<IntegrityIssueDto> = emptyList(),
    @SerialName("checked_at") val checkedAt: String? = null,
)
