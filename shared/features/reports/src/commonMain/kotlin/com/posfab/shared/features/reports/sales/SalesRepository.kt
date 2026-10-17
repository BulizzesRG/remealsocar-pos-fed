package com.posfab.shared.features.reports.sales

import com.posfab.shared.core.result.AppResult
import kotlinx.datetime.LocalDate

interface SalesRepository {
    suspend fun salesOfDay(date: LocalDate): AppResult<List<SaleListItem>>
    suspend fun unitsOfDay(date: LocalDate): AppResult<DayUnits>
    suspend fun saleDetail(id: Long): AppResult<SaleDetail>
}
