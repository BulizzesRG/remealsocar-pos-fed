package com.posfab.shared.network.reports

import kotlinx.serialization.Serializable

@Serializable
data class SaleHistoryItemDto(
    val id: Long,
    val folio: String,
    val terminalId: Long,
    val paymentType: String,
    val soldAt: String,
    val totalAmount: String,
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
