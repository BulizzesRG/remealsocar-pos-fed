package com.posfab.shared.features.cash

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CashSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val terminalId = 1L
    private val businessUnitId = 2L

    @Test
    fun open_session_success_updates_current_session() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = makeViewModel(repo)
        advanceUntilIdle()

        vm.onOpeningAmountChange("1000")
        vm.openSession()
        advanceUntilIdle()

        assertEquals(CashSessionStatus.OPEN, vm.state.value.currentSession?.status)
        assertEquals(1000.0, vm.state.value.currentSession?.openingAmount)
    }

    @Test
    fun open_session_conflict_refreshes_and_shows_notice() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        repo.openConflict = true
        val vm = makeViewModel(repo)
        advanceUntilIdle()

        vm.onOpeningAmountChange("1000")
        vm.openSession()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice?.contains("ya estaba abierta", ignoreCase = true) == true)
    }

    @Test
    fun close_session_success_sets_closed_status() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = makeViewModel(repo)
        advanceUntilIdle()

        vm.onOpeningAmountChange("1000")
        vm.openSession()
        advanceUntilIdle()

        vm.onClosingAmountChange("950")
        vm.closeSession()
        advanceUntilIdle()

        val session = vm.state.value.currentSession
        assertNotNull(session)
        assertEquals(CashSessionStatus.CLOSED, session.status)
        assertEquals(950.0, session.closingAmount)
    }

    @Test
    fun close_session_without_open_session_shows_notice() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = makeViewModel(repo)
        advanceUntilIdle()

        vm.onClosingAmountChange("950")
        vm.requestCloseSession()
        advanceUntilIdle()

        assertNull(vm.state.value.closeConfirmDialog)
        assertTrue(vm.state.value.notice?.contains("No hay una sesion abierta", ignoreCase = true) == true)
    }

    @Test
    fun daily_report_fetch_by_date() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = makeViewModel(repo)
        advanceUntilIdle()

        vm.onReportDateChange("2026-03-01")
        vm.fetchDailyReport()
        advanceUntilIdle()

        val report = vm.state.value.dailyReport
        assertNotNull(report)
        assertEquals("2026-03-01", report.date)
        assertEquals(terminalId, report.terminalId)
        assertEquals(500.0, report.totalIn)
        assertEquals(90.0, report.totalOut)
        assertEquals(12, report.movements)
    }

    @Test
    fun invalid_opening_amount_shows_error() = runTest(dispatcher) {
        val repo = FakeCashRepository()
        val vm = makeViewModel(repo)
        advanceUntilIdle()

        vm.onOpeningAmountChange("not-a-number")
        vm.openSession()
        advanceUntilIdle()

        assertNotNull(vm.state.value.errorMessage)
        assertNull(vm.state.value.currentSession)
    }

    private fun makeViewModel(repo: CashRepository) = CashSessionViewModel(
        terminalId = terminalId,
        businessUnitId = businessUnitId,
        cashUseCases = CashUseCases(repo),
        dispatcher = dispatcher,
    )
}

private class FakeCashRepository : CashRepository {
    var openConflict = false
    private var session: CashSession? = null

    override suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?> =
        AppResult.Success(session)

    override suspend fun openSession(
        terminalId: Long,
        businessUnitId: Long,
        openingAmount: Double,
    ): AppResult<CashSession> {
        if (openConflict) return AppResult.Failure(AppError.Conflict)
        session = CashSession(
            id = 1L,
            status = CashSessionStatus.OPEN,
            terminalId = terminalId,
            openingAmount = openingAmount,
            closingAmount = null,
            openedAt = "2026-03-01T09:00:00",
            closedAt = null,
        )
        return AppResult.Success(session!!)
    }

    override suspend fun closeSession(terminalId: Long, closingAmount: Double): AppResult<CashSession> {
        val open = session ?: return AppResult.Failure(AppError.Validation("No open session"))
        session = open.copy(
            status = CashSessionStatus.CLOSED,
            closingAmount = closingAmount,
            closedAt = "2026-03-01T18:00:00",
        )
        return AppResult.Success(session!!)
    }

    override suspend fun getDailyReport(date: String, terminalId: Long): AppResult<DailyCashReport> =
        AppResult.Success(
            DailyCashReport(
                date = date,
                terminalId = terminalId,
                totalIn = 500.0,
                totalOut = 90.0,
                movements = 12,
            )
        )
}
