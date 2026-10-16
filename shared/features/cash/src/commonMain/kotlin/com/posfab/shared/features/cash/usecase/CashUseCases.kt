package com.posfab.shared.features.cash.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSummary
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.features.cash.repository.CashRepository

class CashUseCases(private val repository: CashRepository) {
    suspend fun current(terminalId: Long): AppResult<CashSession?> = repository.getCurrentSession(terminalId)

    suspend fun summary(terminalId: Long): AppResult<CashSummary> = repository.summary(terminalId)

    suspend fun open(terminalId: Long, businessUnitId: Long, openingAmount: Double): AppResult<Unit> =
        repository.openSession(terminalId, businessUnitId, openingAmount)

    suspend fun close(terminalId: Long, countedAmount: Double): AppResult<CashCloseResult> =
        repository.closeSession(terminalId, countedAmount)

    suspend fun payout(
        terminalId: Long,
        idempotencyKey: String,
        amount: Double,
        reason: PayoutReason,
        notes: String?,
    ): AppResult<CashPayout> = repository.payout(terminalId, idempotencyKey, amount, reason, notes)
}
