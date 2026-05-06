package com.posfab.shared.features.cash.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.DailyCashReport

interface CashRepository {
    suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?>
    suspend fun openSession(terminalId: Long, businessUnitId: Long, openingAmount: Double): AppResult<CashSession>
    suspend fun closeSession(terminalId: Long, closingAmount: Double): AppResult<CashSession>
    suspend fun getDailyReport(date: String, terminalId: Long): AppResult<DailyCashReport>
}
