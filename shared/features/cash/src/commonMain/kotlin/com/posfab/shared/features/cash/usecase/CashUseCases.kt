package com.posfab.shared.features.cash.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.features.cash.repository.CashRepository

class CashUseCases(private val repository: CashRepository) {
    suspend fun current(terminalId: String): AppResult<CashSession?> = repository.getCurrentSession(terminalId)
    suspend fun open(terminalId: String, openingCash: Double): AppResult<CashSession> = repository.openSession(terminalId, openingCash)
    suspend fun close(terminalId: String, countedCash: Double): AppResult<CashSession> = repository.closeSession(terminalId, countedCash)
    suspend fun daily(date: String, terminalId: String): AppResult<DailyCashReport> = repository.getDailyReport(date, terminalId)
}
