package com.posfab.shared.features.cash.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSummary
import com.posfab.shared.features.cash.domain.PayoutReason

interface CashRepository {
    suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?>
    suspend fun summary(terminalId: Long): AppResult<CashSummary>
    suspend fun openSession(terminalId: Long, businessUnitId: Long, openingAmount: Double): AppResult<Unit>
    suspend fun closeSession(terminalId: Long, countedAmount: Double): AppResult<CashCloseResult>
    suspend fun payout(
        terminalId: Long,
        idempotencyKey: String,
        amount: Double,
        reason: PayoutReason,
        notes: String?,
    ): AppResult<CashPayout>
}
