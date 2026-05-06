package com.posfab.shared.features.reports.daily

import com.posfab.shared.features.cash.domain.DailyCashReport

data class DailySaleItem(
    val id: String,
    val folio: String,
    val total: Double,
    val paymentType: String,
    val soldAt: String,
    val terminalId: String,
)

data class DailyHistoryState(
    val terminalId: String,
    val canSwitchTerminal: Boolean,
    val dateInput: String,
    val isLoadingSales: Boolean = false,
    val isLoadingCashSummary: Boolean = false,
    val sales: List<DailySaleItem> = emptyList(),
    val cashSummary: DailyCashReport? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
)
