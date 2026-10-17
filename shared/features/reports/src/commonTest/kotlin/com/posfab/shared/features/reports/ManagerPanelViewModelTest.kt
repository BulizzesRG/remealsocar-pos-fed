package com.posfab.shared.features.reports

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.manager.DebtorItem
import com.posfab.shared.features.reports.manager.IntegrityIssue
import com.posfab.shared.features.reports.manager.IntegrityIssueSample
import com.posfab.shared.features.reports.manager.IntegrityResult
import com.posfab.shared.features.reports.manager.ManagerDailyTotal
import com.posfab.shared.features.reports.manager.ManagerPanelViewModel
import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.reports.manager.WasteItem
import com.posfab.shared.features.reports.sales.DayUnits
import com.posfab.shared.features.reports.sales.SaleDetail
import com.posfab.shared.features.reports.sales.SaleListItem
import com.posfab.shared.features.reports.sales.SalesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
        val vm = ManagerPanelViewModel(ReportsUseCases(FakeSalesRepo(), repo), dispatcher)
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
        val vm = ManagerPanelViewModel(ReportsUseCases(FakeSalesRepo(), repo), dispatcher)
        advanceUntilIdle()

        vm.refreshIntegrity()
        advanceUntilIdle()

        assertTrue(vm.state.value.errorMessage?.isNotBlank() == true)
    }
}

private class FakeManagerRepository : ManagerRepository {
    var failIntegrity = false

    override suspend fun dailyTotals(dateFrom: String, dateTo: String): AppResult<List<ManagerDailyTotal>> =
        AppResult.Success(listOf(ManagerDailyTotal("2026-05-05", "1", "1", 4, 1234.0)))

    override suspend fun topDebtors(limit: Int?): AppResult<List<DebtorItem>> =
        AppResult.Success(listOf(DebtorItem("1", "Debtor 1", 500.0)))

    override suspend fun wasteTotals(dateFrom: String, dateTo: String): AppResult<List<WasteItem>> =
        AppResult.Success(listOf(WasteItem("2026-05-05", "1", 3.0)))

    override suspend fun integrityCheck(): AppResult<IntegrityResult> {
        if (failIntegrity) return AppResult.Failure(AppError.Network("offline"))
        return AppResult.Success(
            IntegrityResult(
                ok = false,
                issues = listOf(
                    IntegrityIssue(
                        code = "negative_stock",
                        count = 2,
                        samples = listOf(
                            IntegrityIssueSample("LOT", "lotA"),
                            IntegrityIssueSample("LOT", "lotB"),
                        ),
                    )
                ),
            )
        )
    }
}

private class FakeSalesRepo : SalesRepository {
    override suspend fun salesOfDay(date: LocalDate): AppResult<List<SaleListItem>> = AppResult.Success(emptyList())
    override suspend fun unitsOfDay(date: LocalDate): AppResult<DayUnits> = AppResult.Success(DayUnits(0.0, 0.0, 0.0))
    override suspend fun saleDetail(id: Long): AppResult<SaleDetail> = AppResult.Failure(AppError.Validation("Sale not found"))
}
