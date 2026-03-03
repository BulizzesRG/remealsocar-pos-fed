package com.posfab.shared.features.cash.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.DailyCashReport

interface CashRepository {
    suspend fun getCurrentSession(terminalId: String): AppResult<CashSession?>
    suspend fun openSession(terminalId: String, openingCash: Double): AppResult<CashSession>
    suspend fun closeSession(terminalId: String, countedCash: Double): AppResult<CashSession>
    suspend fun getDailyReport(date: String, terminalId: String): AppResult<DailyCashReport>
}
