package com.posfab.shared.features.reports.common

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.features.reports.daily.DailyHistoryRepository
import com.posfab.shared.features.reports.daily.DailySaleItem
import com.posfab.shared.features.reports.manager.DebtorItem
import com.posfab.shared.features.reports.manager.IntegrityIssue
import com.posfab.shared.features.reports.manager.IntegrityIssueSample
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
                        id = it.id.toString(),
                        folio = it.folio,
                        total = it.totalAmount.toDouble(),
                        paymentType = it.paymentType,
                        soldAt = it.soldAt,
                        terminalId = it.terminalId.toString(),
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
                        saleDate = it.saleDate,
                        terminalId = it.terminalId.toString(),
                        businessUnitId = it.businessUnitId.toString(),
                        salesCount = it.salesCount.toInt(),
                        total = it.totalAmount.toDouble(),
                    )
                }
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun topDebtors(limit: Int?): AppResult<List<DebtorItem>> {
        return when (val result = executor.execute { reportsApi.topDebtors(it, limit) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.map {
                    DebtorItem(
                        customerId = it.customerId.toString(),
                        customerName = it.customerName,
                        outstandingBalance = it.outstandingBalance.toDouble(),
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
                        wasteDate = it.wasteDate,
                        productId = it.productId.toString(),
                        totalWasteQtyBase = it.totalWasteQtyBase.toDouble(),
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
                            code = it.code,
                            count = it.count,
                            samples = it.samples.map { s ->
                                IntegrityIssueSample(
                                    entityType = s.entityType,
                                    entityId = s.entityId,
                                )
                            },
                        )
                    },
                )
            )
            is AppResult.Failure -> result
        }
    }
}
