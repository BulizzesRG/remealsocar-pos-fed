package com.posfab.shared.features.reports.manager

import com.posfab.shared.core.result.AppResult

interface ManagerRepository {
    suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>>
    suspend fun topDebtors(dateFrom: String, dateTo: String): AppResult<List<DebtorItem>>
    suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>>
    suspend fun integrityCheck(): AppResult<IntegrityResult>
}
