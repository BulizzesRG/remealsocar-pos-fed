package com.posfab.shared.features.cash

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionStatus
import com.posfab.shared.features.cash.domain.DailyCashReport
import com.posfab.shared.features.cash.repository.CashRepository
import com.posfab.shared.features.cash.ui.CashSessionViewModel
import com.posfab.shared.features.cash.usecase.CashUseCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CashSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun open_session_success_and_duplicate_conflict() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = CashSessionViewModel(cashierSession(), CashUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onOpeningCashChange("1000")
        vm.openSession()
        advanceUntilIdle()

        assertEquals(CashSessionStatus.OPEN, vm.state.value.currentSession?.status)

        repo.openConflict = true
        vm.openSession()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice?.contains("ya estaba abierta", ignoreCase = true) == true)
    }

    @Test
    fun close_session_success_shows_reconciliation_values() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = CashSessionViewModel(cashierSession(), CashUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onOpeningCashChange("1000")
        vm.openSession()
        advanceUntilIdle()

        vm.onCountedCashChange("1400")
        vm.closeSession()
        advanceUntilIdle()

        val session = vm.state.value.currentSession
        assertNotNull(session)
        assertEquals(1410.0, session.expectedClose)
        assertEquals(1400.0, session.countedClose)
        assertEquals(-10.0, session.delta)
    }

    @Test
    fun terminal_scope_guard_for_cashier() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = CashSessionViewModel(cashierSession(), CashUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onTerminalChange("POS2")
        advanceUntilIdle()

        assertEquals("POS1", vm.state.value.terminalId)
        assertTrue(vm.state.value.notice?.contains("terminal fija", ignoreCase = true) == true)
    }

    @Test
    fun daily_report_fetch_by_date() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = CashSessionViewModel(cashierSession(), CashUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onReportDateChange("2026-03-01")
        vm.fetchDailyReport()
        advanceUntilIdle()

        val report = vm.state.value.dailyReport
        assertNotNull(report)
        assertEquals("2026-03-01", report.date)
        assertEquals("POS1", report.terminalId)
    }

    private fun cashierSession() = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("u1", "cashier", setOf(UserRole.CASHIER)),
        terminal = TerminalCode.POS1,
    )
}

private class FakeCashRepository : CashRepository {
    var openConflict = false
    private var session: CashSession? = null

    override suspend fun getCurrentSession(terminalId: String): AppResult<CashSession?> = AppResult.Success(session)

    override suspend fun openSession(terminalId: String, openingCash: Double): AppResult<CashSession> {
        if (openConflict) return AppResult.Failure(AppError.Conflict)
        session = CashSession(
            id = "cash-1",
            status = CashSessionStatus.OPEN,
            terminalId = terminalId,
            openedAt = "2026-03-01T09:00:00",
            openedBy = "cashier",
            openingCash = openingCash,
            movementIn = 500.0,
            movementOut = 90.0,
            expectedClose = openingCash + 500.0 - 90.0,
            countedClose = null,
            delta = null,
        )
        return AppResult.Success(session!!)
    }

    override suspend fun closeSession(terminalId: String, countedCash: Double): AppResult<CashSession> {
        val open = session ?: return AppResult.Failure(AppError.Validation("No open session"))
        val expected = open.expectedClose
        session = open.copy(
            status = CashSessionStatus.CLOSED,
            countedClose = countedCash,
            delta = countedCash - expected,
        )
        return AppResult.Success(session!!)
    }

    override suspend fun getDailyReport(date: String, terminalId: String): AppResult<DailyCashReport> {
        return AppResult.Success(
            DailyCashReport(
                date = date,
                terminalId = terminalId,
                openingCash = 1000.0,
                movementIn = 500.0,
                movementOut = 90.0,
                salesCash = 750.0,
                expectedClose = 1410.0,
                countedClose = 1400.0,
                delta = -10.0,
            )
        )
    }
}
