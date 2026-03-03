package com.posfab.shared.features.reports.daily

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.DailyCashReport

interface DailyHistoryRepository {
    suspend fun salesByDate(date: String, terminalId: String): AppResult<List<DailySaleItem>>
    suspend fun cashSummary(date: String, terminalId: String): AppResult<DailyCashReport>
}
