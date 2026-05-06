package com.posfab.shared.features.cash.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.features.cash.repository.CashRepository

class CashUseCases(private val repository: CashRepository) {
    suspend fun current(terminalId: Long): AppResult<CashSession?> = repository.getCurrentSession(terminalId)
    suspend fun open(terminalId: Long, businessUnitId: Long, openingAmount: Double): AppResult<CashSession> =
        repository.openSession(terminalId, businessUnitId, openingAmount)
    suspend fun close(terminalId: Long, closingAmount: Double): AppResult<CashSession> =
        repository.closeSession(terminalId, closingAmount)
    suspend fun daily(date: String, terminalId: Long): AppResult<DailyCashReport> =
        repository.getDailyReport(date, terminalId)
}
