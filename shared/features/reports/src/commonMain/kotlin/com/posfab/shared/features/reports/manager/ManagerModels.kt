package com.posfab.shared.features.reports.manager

data class ManagerDailyTotal(
    val terminalId: String,
    val businessUnit: String?,
    val salesCount: Int,
    val total: Double,
)

data class DebtorItem(
    val customerId: String,
    val customerName: String,
    val debtTotal: Double,
)

data class WasteItem(
    val productId: String,
    val productName: String,
    val wasteQty: Double,
    val unit: String,
)

data class IntegrityIssue(
    val key: String,
    val count: Int,
    val samples: List<String>,
)

data class IntegrityResult(
    val ok: Boolean,
    val issues: List<IntegrityIssue>,
    val checkedAt: String?,
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
