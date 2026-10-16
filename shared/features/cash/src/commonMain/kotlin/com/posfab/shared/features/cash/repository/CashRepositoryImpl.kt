package com.posfab.shared.features.cash.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashDrawerStatus
import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.domain.CashSessionSummary
import com.posfab.shared.features.cash.domain.CashSummary
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.network.cash.CashApi
import com.posfab.shared.network.cash.CashCloseResponseDto
import com.posfab.shared.network.cash.CashPayoutItemDto
import com.posfab.shared.network.cash.CashPayoutRequestDto
import com.posfab.shared.network.cash.CashPayoutResponseDto
import com.posfab.shared.network.cash.CashSessionResponseDto
import com.posfab.shared.network.cash.CashSessionSummaryDto
import com.posfab.shared.network.cash.CashSummaryResponseDto
import com.posfab.shared.network.cash.CloseCashSessionRequestDto
import com.posfab.shared.network.cash.OpenCashSessionRequestDto
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

class CashRepositoryImpl(
    private val api: CashApi,
    private val executor: AuthorizedApiExecutor,
) : CashRepository {
    override suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?> =
        when (val result = executor.execute { api.currentSession(it, terminalId) }) {
            is AppResult.Success -> AppResult.Success(result.value?.toDomain())
            is AppResult.Failure -> result
        }

    override suspend fun summary(terminalId: Long): AppResult<CashSummary> =
        when (val result = executor.execute { api.summary(it, terminalId) }) {
            is AppResult.Success -> result.value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }

    override suspend fun openSession(
        terminalId: Long,
        businessUnitId: Long,
        openingAmount: Double,
    ): AppResult<Unit> {
        val request = OpenCashSessionRequestDto(
            terminalId = terminalId,
            businessUnitId = businessUnitId,
            openingAmount = formatMoney(openingAmount),
        )
        return when (val result = executor.execute { api.openSession(it, request) }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    override suspend fun closeSession(terminalId: Long, countedAmount: Double): AppResult<CashCloseResult> {
        val request = CloseCashSessionRequestDto(terminalId = terminalId, closingAmount = formatMoney(countedAmount))
        return when (val result = executor.execute { api.closeSession(it, request) }) {
            is AppResult.Success -> result.value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }
    }

    override suspend fun payout(
        terminalId: Long,
        idempotencyKey: String,
        amount: Double,
        reason: PayoutReason,
        notes: String?,
    ): AppResult<CashPayout> {
        val request = CashPayoutRequestDto(
            terminalId = terminalId,
            amount = formatMoney(amount),
            reason = reason.name,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        return when (val result = executor.execute { api.payout(it, idempotencyKey, request) }) {
            is AppResult.Success -> result.value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }
    }

    private fun malformed(): AppResult.Failure = AppResult.Failure(AppError.Unknown(MALFORMED))

    private fun CashSessionResponseDto.toDomain(): CashSession {
        val statusMapped = when (status.uppercase()) {
            OPEN -> CashSessionStatus.OPEN
            CLOSED -> CashSessionStatus.CLOSED
            else -> CashSessionStatus.NONE
        }
        return CashSession(
            id = id,
            status = statusMapped,
            terminalId = terminalId,
            openingAmount = openingAmount.toDoubleOrNull() ?: 0.0,
            closingAmount = closingAmount?.toDoubleOrNull(),
            openedAt = openedAt,
            closedAt = closedAt,
        )
    }

    private fun CashSummaryResponseDto.toDomain(): CashSummary? {
        val drawerStatus = when (status.uppercase()) {
            OPEN -> CashDrawerStatus.OPEN
            CLOSED -> CashDrawerStatus.CLOSED
            else -> return null
        }
        val previous = previousClosingAmount?.let { it.toDoubleOrNull() ?: return null }
        val mappedSession = session?.let { it.toDomain() ?: return null }
        if (drawerStatus == CashDrawerStatus.OPEN && mappedSession == null) return null
        return CashSummary(status = drawerStatus, previousClosingAmount = previous, session = mappedSession)
    }

    private fun CashSessionSummaryDto.toDomain(): CashSessionSummary? = CashSessionSummary(
        sessionId = sessionId,
        openedAt = parseInstant(openedAt) ?: return null,
        openingAmount = openingAmount.toDoubleOrNull() ?: return null,
        cashSales = cashSales.toDoubleOrNull() ?: return null,
        creditPayments = creditPayments.toDoubleOrNull() ?: return null,
        payouts = payouts.toDoubleOrNull() ?: return null,
        otherOut = otherOut.toDoubleOrNull() ?: return null,
        expectedInDrawer = expectedInDrawer.toDoubleOrNull() ?: return null,
        transferSales = transferSales.toDoubleOrNull() ?: return null,
        cardSales = cardSales.toDoubleOrNull() ?: return null,
        creditSales = creditSales.toDoubleOrNull() ?: return null,
        openAccounts = openAccounts,
        payoutList = payoutList.map { it.toDomain() ?: return null },
    )

    private fun CashPayoutItemDto.toDomain(): CashPayout? = CashPayout(
        id = id,
        amount = amount.toDoubleOrNull() ?: return null,
        reason = PayoutReason.fromRaw(reason) ?: PayoutReason.OTRO,
        notes = notes?.takeIf { it.isNotBlank() },
        createdAt = parseInstant(createdAt) ?: return null,
    )

    private fun CashPayoutResponseDto.toDomain(): CashPayout? = CashPayout(
        id = id,
        amount = amount.toDoubleOrNull() ?: return null,
        reason = PayoutReason.fromRaw(reason) ?: PayoutReason.OTRO,
        notes = notes?.takeIf { it.isNotBlank() },
        createdAt = parseInstant(createdAt) ?: return null,
    )

    private fun CashCloseResponseDto.toDomain(): CashCloseResult? = CashCloseResult(
        openedAt = parseInstant(openedAt) ?: return null,
        closedAt = parseInstant(closedAt) ?: return null,
        summary = summary.toDomain() ?: return null,
        countedAmount = countedAmount.toDoubleOrNull() ?: return null,
        delta = delta.toDoubleOrNull() ?: return null,
    )

    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

    private fun formatMoney(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private companion object {
        const val OPEN = "OPEN"
        const val CLOSED = "CLOSED"
        const val MALFORMED = "cash summary"
    }
}
