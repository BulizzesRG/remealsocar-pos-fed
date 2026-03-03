package com.posfab.shared.features.reports

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.daily.DailyHistoryRepository
import com.posfab.shared.features.reports.manager.DebtorItem
import com.posfab.shared.features.reports.manager.IntegrityIssue
import com.posfab.shared.features.reports.manager.IntegrityResult
import com.posfab.shared.features.reports.manager.ManagerDailyTotal
import com.posfab.shared.features.reports.manager.ManagerPanelViewModel
import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.reports.manager.WasteItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerPanelViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun manager_sees_integrity_issue_samples() = runTest(dispatcher) {
        val repo = FakeManagerRepository()
        val vm = ManagerPanelViewModel(ReportsUseCases(FakeDailyRepo(), repo), dispatcher)
        advanceUntilIdle()

        vm.refreshIntegrity()
        advanceUntilIdle()

        val integrity = vm.state.value.integrity
        assertNotNull(integrity)
        assertEquals(false, integrity.ok)
        assertEquals(1, integrity.issues.size)
        assertEquals(2, integrity.issues.first().samples.size)
    }

    @Test
    fun integrity_error_path_sets_error_without_crash() = runTest(dispatcher) {
        val repo = FakeManagerRepository()
        repo.failIntegrity = true
        val vm = ManagerPanelViewModel(ReportsUseCases(FakeDailyRepo(), repo), dispatcher)
        advanceUntilIdle()

        vm.refreshIntegrity()
        advanceUntilIdle()

        assertTrue(vm.state.value.errorMessage?.isNotBlank() == true)
    }
}

private class FakeManagerRepository : ManagerRepository {
    var failIntegrity = false

    override suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>> =
        AppResult.Success(listOf(ManagerDailyTotal("POS1", "Store-A", 4, 1234.0)))

    override suspend fun topDebtors(dateFrom: String, dateTo: String): AppResult<List<DebtorItem>> =
        AppResult.Success(listOf(DebtorItem("c1", "Debtor 1", 500.0)))

    override suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>> =
        AppResult.Success(listOf(WasteItem("p1", "Tomate", 3.0, "kg")))

    override suspend fun integrityCheck(): AppResult<IntegrityResult> {
        if (failIntegrity) return AppResult.Failure(AppError.Network("offline"))
        return AppResult.Success(
            IntegrityResult(
                ok = false,
                issues = listOf(
                    IntegrityIssue(
                        key = "negative_stock",
                        count = 2,
                        samples = listOf("lotA -2", "lotB -1"),
                    )
                ),
                checkedAt = "2026-03-02T10:00:00",
            )
        )
    }
}

private class FakeDailyRepo : DailyHistoryRepository {
    override suspend fun salesByDate(date: String, terminalId: String) = AppResult.Success(emptyList<com.posfab.shared.features.reports.daily.DailySaleItem>())
    override suspend fun cashSummary(date: String, terminalId: String) = AppResult.Success(com.posfab.shared.features.cash.domain.DailyCashReport(date, terminalId, 0.0, 0.0, 0.0, 0.0, 0.0, null, null))
}
