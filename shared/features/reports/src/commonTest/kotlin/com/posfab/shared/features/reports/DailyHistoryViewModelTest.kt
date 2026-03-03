package com.posfab.shared.features.reports

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.features.reports.common.ReportsUseCases
import com.posfab.shared.features.reports.daily.DailyHistoryRepository
import com.posfab.shared.features.reports.daily.DailyHistoryViewModel
import com.posfab.shared.features.reports.daily.DailySaleItem
import com.posfab.shared.features.reports.manager.ManagerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DailyHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun daily_sales_filter_by_date_works() = runTest(dispatcher) {
        val repo = FakeDailyHistoryRepo()
        val vm = DailyHistoryViewModel(cashierSession(), ReportsUseCases(repo, FakeManagerRepo()), dispatcher)
        advanceUntilIdle()

        vm.onDateChange("2026-03-01")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals("2026-03-01", repo.lastSalesDate)
        assertEquals(1, vm.state.value.sales.size)
    }

    @Test
    fun terminal_scoping_enforced_for_cashier() = runTest(dispatcher) {
        val repo = FakeDailyHistoryRepo()
        val vm = DailyHistoryViewModel(cashierSession(), ReportsUseCases(repo, FakeManagerRepo()), dispatcher)
        advanceUntilIdle()

        vm.onTerminalChange("POS2")
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals("POS1", vm.state.value.terminalId)
        assertTrue(vm.state.value.notice?.contains("terminal fija", ignoreCase = true) == true)
    }

    private fun cashierSession() = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("u1", "cashier", setOf(UserRole.CASHIER)),
        terminal = TerminalCode.POS1,
    )
}

private class FakeDailyHistoryRepo : DailyHistoryRepository {
    var lastSalesDate: String = ""

    override suspend fun salesByDate(date: String, terminalId: String): AppResult<List<DailySaleItem>> {
        lastSalesDate = date
        return AppResult.Success(
            listOf(
                DailySaleItem(
                    id = "s1",
                    folio = "F-1",
                    total = 123.0,
                    paymentStatus = "PAID",
                    createdAt = "2026-03-01T10:00:00",
                    terminalId = terminalId,
                )
            )
        )
    }

    override suspend fun cashSummary(date: String, terminalId: String): AppResult<DailyCashReport> {
        return AppResult.Success(
            DailyCashReport(
                date = date,
                terminalId = terminalId,
                openingCash = 1000.0,
                movementIn = 200.0,
                movementOut = 50.0,
                salesCash = 300.0,
                expectedClose = 1150.0,
                countedClose = 1140.0,
                delta = -10.0,
            )
        )
    }
}

private class FakeManagerRepo : ManagerRepository {
    override suspend fun dailyTotals(dateFrom: String, dateTo: String) = AppResult.Success(emptyList<com.posfab.shared.features.reports.manager.ManagerDailyTotal>())
    override suspend fun topDebtors(dateFrom: String, dateTo: String) = AppResult.Success(emptyList<com.posfab.shared.features.reports.manager.DebtorItem>())
    override suspend fun wasteTotals(dateFrom: String, dateTo: String) = AppResult.Success(emptyList<com.posfab.shared.features.reports.manager.WasteItem>())
    override suspend fun integrityCheck() = AppResult.Success(com.posfab.shared.features.reports.manager.IntegrityResult(ok = true, issues = emptyList(), checkedAt = null))
}
