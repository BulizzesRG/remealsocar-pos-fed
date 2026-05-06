package com.posfab.shared.features.cash.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.network.cash.CashApi
import com.posfab.shared.network.cash.CashSessionResponseDto
import com.posfab.shared.network.cash.CloseCashSessionRequestDto
import com.posfab.shared.network.cash.DailyCashReportResponseDto
import com.posfab.shared.network.cash.OpenCashSessionRequestDto

class CashRepositoryImpl(
    private val api: CashApi,
    private val executor: AuthorizedApiExecutor,
) : CashRepository {
    override suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?> =
        when (val result = executor.execute { api.currentSession(it, terminalId) }) {
            is AppResult.Success -> AppResult.Success(result.value?.toDomain())
            is AppResult.Failure -> result
        }

    override suspend fun openSession(
        terminalId: Long,
        businessUnitId: Long,
        openingAmount: Double,
    ): AppResult<CashSession> {
        val openResult = executor.execute {
            api.openSession(
                it,
                OpenCashSessionRequestDto(
                    terminalId = terminalId,
                    businessUnitId = businessUnitId,
                    openingAmount = openingAmount.toBigDecimal().toPlainString(),
                ),
            )
        }
        if (openResult is AppResult.Failure) return openResult

        return when (val sessionResult = executor.execute { api.currentSession(it, terminalId) }) {
            is AppResult.Success -> {
                val session = sessionResult.value
                if (session != null) {
                    AppResult.Success(session.toDomain())
                } else {
                    AppResult.Failure(AppError.Network("Session not found after open"))
                }
            }
            is AppResult.Failure -> sessionResult
        }
    }

    override suspend fun closeSession(terminalId: Long, closingAmount: Double): AppResult<CashSession> =
        when (
            val result = executor.execute {
                api.closeSession(
                    it,
                    CloseCashSessionRequestDto(
                        terminalId = terminalId,
                        closingAmount = closingAmount.toBigDecimal().toPlainString(),
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }

    override suspend fun getDailyReport(date: String, terminalId: Long): AppResult<DailyCashReport> =
        when (val result = executor.execute { api.dailyReport(it, date, terminalId) }) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }

    private fun CashSessionResponseDto.toDomain(): CashSession {
        val statusMapped = when (status.uppercase()) {
            "OPEN" -> CashSessionStatus.OPEN
            "CLOSED" -> CashSessionStatus.CLOSED
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

    private fun DailyCashReportResponseDto.toDomain(): DailyCashReport = DailyCashReport(
        date = date,
        terminalId = terminalId,
        totalIn = totalIn.toDoubleOrNull() ?: 0.0,
        totalOut = totalOut.toDoubleOrNull() ?: 0.0,
        movements = movements,
    )
}
