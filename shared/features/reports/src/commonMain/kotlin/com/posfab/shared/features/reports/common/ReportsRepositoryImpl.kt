package com.posfab.shared.features.reports.common

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.features.reports.daily.DailyHistoryRepository
import com.posfab.shared.features.reports.daily.DailySaleItem
import com.posfab.shared.features.reports.manager.DebtorItem
import com.posfab.shared.features.reports.manager.IntegrityIssue
import com.posfab.shared.features.reports.manager.IntegrityResult
import com.posfab.shared.features.reports.manager.ManagerDailyTotal
import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.reports.manager.WasteItem
import com.posfab.shared.network.cash.CashApi
import com.posfab.shared.network.reports.ReportsApi

class ReportsRepositoryImpl(
    private val reportsApi: ReportsApi,
    private val cashApi: CashApi,
    private val executor: AuthorizedApiExecutor,
) : DailyHistoryRepository, ManagerRepository {
    override suspend fun salesByDate(date: String, terminalId: String): AppResult<List<DailySaleItem>> {
        return when (val result = executor.execute { reportsApi.dailySales(it, date, terminalId) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    DailySaleItem(
                        id = it.id,
                        folio = it.folio,
                        total = it.total,
                        paymentStatus = it.paymentStatus,
                        createdAt = it.createdAt,
                        terminalId = it.terminalId,
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun cashSummary(date: String, terminalId: String): AppResult<DailyCashReport> {
        return when (val result = executor.execute { cashApi.dailyReport(it, date, terminalId) }) {
            is AppResult.Success -> AppResult.Success(
                DailyCashReport(
                    date = result.value.date,
                    terminalId = result.value.terminalId,
                    openingCash = result.value.openingCash,
                    movementIn = result.value.movementIn,
                    movementOut = result.value.movementOut,
                    salesCash = result.value.salesCash,
                    expectedClose = result.value.expectedClose,
                    countedClose = result.value.countedClose,
                    delta = result.value.delta,
                )
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>> {
        return when (val result = executor.execute { reportsApi.dailyTotals(it, dateFrom, dateTo) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    ManagerDailyTotal(
                        terminalId = it.terminalId,
                        businessUnit = it.businessUnit,
                        salesCount = it.salesCount,
                        total = it.total,
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun topDebtors(dateFrom: String, dateTo: String): AppResult<List<DebtorItem>> {
        return when (val result = executor.execute { reportsApi.topDebtors(it, dateFrom, dateTo) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    DebtorItem(
                        customerId = it.customerId,
                        customerName = it.customerName,
                        debtTotal = it.debtTotal,
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>> {
        return when (val result = executor.execute { reportsApi.wasteTotals(it, dateFrom, dateTo) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    WasteItem(
                        productId = it.productId,
                        productName = it.productName,
                        wasteQty = it.wasteQty,
                        unit = it.unit,
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun integrityCheck(): AppResult<IntegrityResult> {
        return when (val result = executor.execute { reportsApi.integrityCheck(it) }) {
            is AppResult.Success -> AppResult.Success(
                IntegrityResult(
                    ok = result.value.ok,
                    issues = result.value.issues.map {
                        IntegrityIssue(
                            key = it.key,
                            count = it.count,
                            samples = it.samples.take(5),
                        )
                    },
                    checkedAt = result.value.checkedAt,
                )
            )
            is AppResult.Failure -> result
        }
    }
}
