package com.posfab.shared.features.cash.domain

import kotlin.math.round
import kotlin.time.Instant

enum class CashSessionStatus { OPEN, CLOSED, NONE }

data class CashSession(
    val id: Long,
    val status: CashSessionStatus,
    val terminalId: Long,
    val openingAmount: Double,
    val closingAmount: Double?,
    val openedAt: String,
    val closedAt: String?,
)

data class DailyCashReport(
    val date: String,
    val terminalId: Long,
    val totalIn: Double,
    val totalOut: Double,
    val movements: Int,
)

enum class CashDrawerStatus { OPEN, CLOSED }

data class CashSummary(
    val status: CashDrawerStatus,
    val previousClosingAmount: Double?,
    val session: CashSessionSummary?,
)

data class CashSessionSummary(
    val sessionId: Long,
    val openedAt: Instant,
    val openingAmount: Double,
    val cashSales: Double,
    val creditPayments: Double,
    val payouts: Double,
    val otherOut: Double,
    val expectedInDrawer: Double,
    val transferSales: Double,
    val cardSales: Double,
    val creditSales: Double,
    val openAccounts: Int,
    val payoutList: List<CashPayout>,
)

data class CashPayout(
    val id: Long,
    val amount: Double,
    val reason: PayoutReason,
    val notes: String?,
    val createdAt: Instant,
)

enum class PayoutReason {
    SURTIDO,
    PERSONAL,
    OTRO,
    ;

    companion object {
        fun fromRaw(raw: String): PayoutReason? = entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

data class CashCloseResult(
    val openedAt: Instant,
    val closedAt: Instant,
    val summary: CashSessionSummary,
    val countedAmount: Double,
    val delta: Double,
) {
    val difference: DrawerDifference
        get() = DrawerDifference.of(expected = summary.expectedInDrawer, counted = countedAmount)
}

sealed interface DrawerDifference {
    data object Cuadra : DrawerDifference

    data class Sobran(val amount: Double) : DrawerDifference

    data class Faltan(val amount: Double) : DrawerDifference

    companion object {
        fun of(expected: Double, counted: Double): DrawerDifference {
            val cents = toCents(counted) - toCents(expected)
            return when {
                cents == 0L -> Cuadra
                cents > 0L -> Sobran(cents / CENTS)
                else -> Faltan(-cents / CENTS)
            }
        }

        private fun toCents(amount: Double): Long = round(amount * CENTS).toLong()

        private const val CENTS = 100.0
    }
}
