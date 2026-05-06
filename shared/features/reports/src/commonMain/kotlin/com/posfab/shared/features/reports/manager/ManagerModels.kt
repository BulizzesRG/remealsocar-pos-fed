package com.posfab.shared.features.reports.manager

data class ManagerDailyTotal(
    val saleDate: String,
    val terminalId: String,
    val businessUnitId: String,
    val salesCount: Int,
    val total: Double,
)

data class DebtorItem(
    val customerId: String,
    val customerName: String,
    val outstandingBalance: Double,
)

data class WasteItem(
    val wasteDate: String,
    val productId: String,
    val totalWasteQtyBase: Double,
)

data class IntegrityIssueSample(
    val entityType: String,
    val entityId: String,
)

data class IntegrityIssue(
    val code: String,
    val count: Int,
    val samples: List<IntegrityIssueSample>,
)

data class IntegrityResult(
    val ok: Boolean,
    val issues: List<IntegrityIssue>,
)

data class ManagerPanelState(
    val dateFrom: String,
    val dateTo: String,
    val isLoadingDashboard: Boolean = false,
    val isLoadingIntegrity: Boolean = false,
    val dailyTotals: List<ManagerDailyTotal> = emptyList(),
    val debtors: List<DebtorItem> = emptyList(),
    val waste: List<WasteItem> = emptyList(),
    val integrity: IntegrityResult? = null,
    val integrityLastCheckedAt: String? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
)
