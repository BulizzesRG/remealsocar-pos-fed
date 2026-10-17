package com.posfab.shared.features.reports.sales

import kotlin.time.Instant

enum class SalePayment { CASH, TRANSFER, CARD, CREDIT }

enum class SaleChannel { TAKEAWAY, ONSITE }

enum class SalePriceType { PIECE, WEIGHT, OPEN, OTHER }

data class SaleListItem(
    val id: Long,
    val folio: String,
    val soldAt: Instant,
    val payment: SalePayment,
    val channel: SaleChannel,
    val total: Double,
    val customerName: String?,
)

data class SaleDetail(
    val id: Long,
    val folio: String,
    val soldAt: Instant,
    val terminalCode: String,
    val businessUnitCode: String,
    val payment: SalePayment,
    val channel: SaleChannel,
    val subtotal: Double,
    val cashDiscount: Double,
    val total: Double,
    val customerName: String?,
    val lines: List<SaleDetailLine>,
)

data class SaleDetailLine(
    val productName: String,
    val qty: Double,
    val unitCode: String,
    val priceType: SalePriceType,
    val unitPrice: Double,
    val lineTotal: Double,
)

data class DayUnits(
    val tienda: Double,
    val fonda: Double,
    val torteria: Double,
)

internal fun customerDisplayName(name: String, nickname: String?): String {
    val alias = nickname?.trim().orEmpty()
    return if (alias.isEmpty()) name else "$name ($alias)"
}
