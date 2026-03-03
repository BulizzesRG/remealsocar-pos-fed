package com.posfab.shared.features.cash.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.network.cash.CashApi
import com.posfab.shared.network.cash.CashSessionDto
import com.posfab.shared.network.cash.CloseCashSessionRequestDto
import com.posfab.shared.network.cash.DailyCashReportDto
import com.posfab.shared.network.cash.OpenCashSessionRequestDto

class CashRepositoryImpl(
    private val api: CashApi,
    private val executor: AuthorizedApiExecutor,
) : CashRepository {
    override suspend fun getCurrentSession(terminalId: String): AppResult<CashSession?> {
        return when (val result = executor.execute { api.currentSession(it, terminalId) }) {
            is AppResult.Success -> AppResult.Success(result.value?.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun openSession(terminalId: String, openingCash: Double): AppResult<CashSession> {
        return when (
            val result = executor.execute {
                api.openSession(it, OpenCashSessionRequestDto(terminalId = terminalId, openingCash = openingCash))
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun closeSession(terminalId: String, countedCash: Double): AppResult<CashSession> {
        return when (
            val result = executor.execute {
                api.closeSession(it, CloseCashSessionRequestDto(terminalId = terminalId, countedCash = countedCash))
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun getDailyReport(date: String, terminalId: String): AppResult<DailyCashReport> {
        return when (val result = executor.execute { api.dailyReport(it, date, terminalId) }) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    private fun CashSessionDto.toDomain(): CashSession {
        val statusMapped = when (status.uppercase()) {
            "OPEN" -> CashSessionStatus.OPEN
            "CLOSED" -> CashSessionStatus.CLOSED
            else -> CashSessionStatus.NONE
        }
        return CashSession(
            id = id,
            status = statusMapped,
            terminalId = terminalId,
            openedAt = openedAt,
            openedBy = openedBy,
            openingCash = openingCash,
            movementIn = movementIn,
            movementOut = movementOut,
            expectedClose = expectedClose,
            countedClose = countedClose,
            delta = delta,
        )
    }

    private fun DailyCashReportDto.toDomain(): DailyCashReport = DailyCashReport(
        date = date,
        terminalId = terminalId,
        openingCash = openingCash,
        movementIn = movementIn,
        movementOut = movementOut,
        salesCash = salesCash,
        expectedClose = expectedClose,
        countedClose = countedClose,
        delta = delta,
    )
}
