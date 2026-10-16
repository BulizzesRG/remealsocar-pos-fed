package com.posfab.shared.features.cash

import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashCloseResult
import com.posfab.shared.features.cash.domain.CashDrawerStatus
import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.CashSession
import com.posfab.shared.features.cash.domain.CashSessionSummary
import com.posfab.shared.features.cash.domain.CashSummary
import com.posfab.shared.features.cash.domain.DrawerDifference
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.features.cash.repository.CashRepository
import com.posfab.shared.features.cash.ui.CashPhase
import com.posfab.shared.features.cash.ui.CashStrings
import com.posfab.shared.features.cash.ui.CashViewModel
import com.posfab.shared.features.cash.usecase.CashUseCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TERMINAL_ID = 1L
private const val BUSINESS_UNIT_ID = 2L
private val OPENED_AT = Instant.parse("2026-10-16T14:15:00Z")
private val NOW = Instant.parse("2026-10-16T20:00:00Z")
private val MANAGER = setOf(Permission.OPEN_CASH, Permission.MANAGE_CASH)

@OptIn(ExperimentalCoroutinesApi::class)
class CashViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun closed_drawer_proposes_previous_closing_and_accepts_zero() = runTest(dispatcher) {
        val repo = FakeCashRepository(closedSummary(1250.0))
        val vm = loaded(repo)

        val phase = assertIs<CashPhase.Closed>(vm.state.value.phase)
        assertEquals(1250.0, phase.previousClosingAmount)
        assertEquals("1250", vm.state.value.openingInput)
        assertTrue(vm.state.value.canOpenDay)

        vm.onOpeningAmountChange("0")
        assertTrue(vm.state.value.canOpenDay)
    }

    @Test
    fun previous_closing_with_cents_is_proposed_with_two_decimals() = runTest(dispatcher) {
        val vm = loaded(FakeCashRepository(closedSummary(1250.5)))

        assertEquals("1250.50", vm.state.value.openingInput)
    }

    @Test
    fun first_time_leaves_the_field_blank_and_the_button_disabled() = runTest(dispatcher) {
        val vm = loaded(FakeCashRepository(closedSummary(null)))

        val phase = assertIs<CashPhase.Closed>(vm.state.value.phase)
        assertNull(phase.previousClosingAmount)
        assertEquals("", vm.state.value.openingInput)
        assertFalse(vm.state.value.canOpenDay)
    }

    @Test
    fun open_success_reloads_into_open() = runTest(dispatcher) {
        val repo = FakeCashRepository(closedSummary(1250.0))
        val vm = loaded(repo)
        repo.summary = openSummary()

        vm.openDay()
        advanceUntilIdle()

        assertEquals(listOf(Triple(TERMINAL_ID, BUSINESS_UNIT_ID, 1250.0)), repo.openCalls)
        assertIs<CashPhase.Open>(vm.state.value.phase)
        assertFalse(vm.state.value.isOpening)
    }

    @Test
    fun open_conflict_reloads_and_shows_notice() = runTest(dispatcher) {
        val repo = FakeCashRepository(closedSummary(1250.0))
        repo.openResult = AppResult.Failure(AppError.Conflict)
        val vm = loaded(repo)
        repo.summary = openSummary()

        vm.openDay()
        advanceUntilIdle()

        assertIs<CashPhase.Open>(vm.state.value.phase)
        assertEquals(CashStrings.ALREADY_OPEN, vm.state.value.notice)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun open_is_sent_once_while_in_flight() = runTest(dispatcher) {
        val repo = FakeCashRepository(closedSummary(100.0))
        val gate = CompletableDeferred<Unit>()
        repo.openGate = gate
        val vm = loaded(repo)

        vm.openDay()
        vm.openDay()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.openCalls.size)
    }

    @Test
    fun without_manage_cash_no_actions_open() = runTest(dispatcher) {
        val vm = loaded(FakeCashRepository(openSummary()), permissions = setOf(Permission.OPEN_CASH))

        assertFalse(vm.state.value.canManage)
        vm.openPayout()
        vm.openClose()

        assertNull(vm.state.value.payout)
        assertNull(vm.state.value.closing)
    }

    @Test
    fun payout_above_expected_is_rejected_without_calling_the_server() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 100.0))
        val vm = loaded(repo)

        vm.openPayout()
        vm.onPayoutAmountChange("150")
        vm.confirmPayout()
        advanceUntilIdle()

        assertEquals(CashStrings.NOT_ENOUGH, vm.state.value.payout?.errorMessage)
        assertTrue(repo.payoutCalls.isEmpty())
    }

    @Test
    fun payout_success_closes_overlay_reloads_and_notifies() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        val vm = loaded(repo)
        val summaryCallsBefore = repo.summaryCalls

        vm.openPayout()
        vm.onPayoutAmountChange("500")
        vm.confirmPayout()
        advanceUntilIdle()

        assertNull(vm.state.value.payout)
        assertEquals(summaryCallsBefore + 1, repo.summaryCalls)
        assertEquals("Retiro guardado: Surtido · $500.00", vm.state.value.notice)
        val call = repo.payoutCalls.single()
        assertEquals(500.0, call.amount)
        assertEquals(PayoutReason.SURTIDO, call.reason)
        assertNull(call.notes)
        assertTrue(call.key.startsWith("payout-${NOW.toEpochMilliseconds()}-1-"))
    }

    @Test
    fun payout_network_failure_keeps_the_key_and_locks_the_form() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        repo.payoutResults.add(AppResult.Failure(AppError.Network("timeout")))
        val vm = loaded(repo)

        vm.openPayout()
        vm.onPayoutAmountChange("200")
        vm.confirmPayout()
        advanceUntilIdle()

        val locked = assertNotNull(vm.state.value.payout)
        assertEquals(CashStrings.PAYOUT_RETRY, locked.errorMessage)
        assertTrue(locked.isLocked)

        vm.onPayoutAmountChange("300")
        vm.onPayoutReasonChange(PayoutReason.PERSONAL)
        vm.onPayoutEscape()
        vm.dismissPayout()
        assertEquals("200", vm.state.value.payout?.amount)
        assertEquals(PayoutReason.SURTIDO, vm.state.value.payout?.reason)

        vm.confirmPayout()
        advanceUntilIdle()

        assertEquals(2, repo.payoutCalls.size)
        assertEquals(repo.payoutCalls[0].key, repo.payoutCalls[1].key)
        assertNull(vm.state.value.payout)
    }

    @Test
    fun payout_validation_failure_clears_the_key() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        repo.payoutResults.add(AppResult.Failure(AppError.Validation("amount exceeds drawer")))
        val vm = loaded(repo)

        vm.openPayout()
        vm.onPayoutAmountChange("200")
        vm.confirmPayout()
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.payout)
        assertNull(form.pendingKey)
        assertFalse(form.isLocked)
        assertEquals(CashStrings.NOT_ENOUGH, form.errorMessage)

        vm.confirmPayout()
        advanceUntilIdle()

        assertNotEquals(repo.payoutCalls[0].key, repo.payoutCalls[1].key)
    }

    @Test
    fun payout_is_sent_once_while_in_flight() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        val gate = CompletableDeferred<Unit>()
        repo.payoutGate = gate
        val vm = loaded(repo)

        vm.openPayout()
        vm.onPayoutAmountChange("10")
        vm.confirmPayout()
        vm.confirmPayout()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.payoutCalls.size)
    }

    @Test
    fun payout_otro_requires_trimmed_notes() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        val vm = loaded(repo)

        vm.openPayout()
        vm.onPayoutAmountChange("50")
        vm.onPayoutReasonChange(PayoutReason.OTRO)
        assertFalse(assertNotNull(vm.state.value.payout).canConfirm)

        vm.onPayoutNotesChange("  Gas  ")
        assertTrue(assertNotNull(vm.state.value.payout).canConfirm)
        vm.confirmPayout()
        advanceUntilIdle()

        assertEquals("Gas", repo.payoutCalls.single().notes)
        assertEquals(PayoutReason.OTRO, repo.payoutCalls.single().reason)
    }

    @Test
    fun payout_escape_clears_first_then_closes() = runTest(dispatcher) {
        val vm = loaded(FakeCashRepository(openSummary()))

        vm.openPayout()
        vm.onPayoutAmountChange("80")
        vm.onPayoutEscape()

        assertEquals("", vm.state.value.payout?.amount)

        vm.onPayoutEscape()
        assertNull(vm.state.value.payout)
    }

    @Test
    fun close_is_blocked_while_accounts_are_open() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(openAccounts = 2))
        val vm = loaded(repo)

        vm.openClose()
        vm.onCountedChange("1500")
        assertFalse(vm.state.value.canConfirmClose)
        vm.confirmClose()
        advanceUntilIdle()

        assertTrue(repo.closeCalls.isEmpty())
        assertNotNull(vm.state.value.closing)
    }

    @Test
    fun close_shows_the_live_difference() = runTest(dispatcher) {
        val vm = loaded(FakeCashRepository(openSummary(expected = 1500.0)))

        vm.openClose()
        assertNull(vm.state.value.closeDifference)

        vm.onCountedChange("1500")
        assertEquals(DrawerDifference.Cuadra, vm.state.value.closeDifference)

        vm.onCountedChange("1600")
        assertEquals(DrawerDifference.Sobran(100.0), vm.state.value.closeDifference)

        vm.onCountedChange("1450.5")
        assertEquals(DrawerDifference.Faltan(49.5), vm.state.value.closeDifference)

        vm.onCountedChange("0")
        assertTrue(vm.state.value.canConfirmClose)
    }

    @Test
    fun close_success_shows_summary_and_listo_reloads_closed() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        val vm = loaded(repo)

        vm.openClose()
        vm.onCountedChange("1480")
        vm.confirmClose()
        advanceUntilIdle()

        assertEquals(listOf(TERMINAL_ID to 1480.0), repo.closeCalls)
        val closed = assertIs<CashPhase.DayClosed>(vm.state.value.phase)
        assertEquals(DrawerDifference.Faltan(20.0), closed.result.difference)
        assertNull(vm.state.value.closing)

        repo.summary = closedSummary(1480.0)
        vm.finishDay()
        advanceUntilIdle()

        assertEquals(CashPhase.Closed(1480.0), vm.state.value.phase)
        assertEquals("1480", vm.state.value.openingInput)
    }

    @Test
    fun close_on_an_already_closed_session_reloads() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary())
        repo.closeResult = AppResult.Failure(AppError.Validation("No open session for terminalId=1"))
        val vm = loaded(repo)
        repo.summary = closedSummary(900.0)

        vm.openClose()
        vm.onCountedChange("900")
        vm.confirmClose()
        advanceUntilIdle()

        assertEquals(CashPhase.Closed(900.0), vm.state.value.phase)
        assertEquals(CashStrings.CLOSED_NOTICE, vm.state.value.notice)
        assertNull(vm.state.value.closing)
    }

    @Test
    fun close_with_open_accounts_from_server_reloads_and_shows_message() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary())
        repo.closeResult = AppResult.Failure(AppError.Validation("Open accounts must be charged or emptied first"))
        val vm = loaded(repo)
        repo.summary = openSummary(openAccounts = 1)

        vm.openClose()
        vm.onCountedChange("1500")
        vm.confirmClose()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.openSession?.openAccounts)
        assertEquals(CashStrings.OPEN_ACCOUNTS_BLOCK, vm.state.value.closing?.errorMessage)
        assertFalse(vm.state.value.canConfirmClose)
    }

    @Test
    fun close_escape_clears_first_then_closes() = runTest(dispatcher) {
        val vm = loaded(FakeCashRepository(openSummary()))

        vm.openClose()
        vm.onCountedChange("10")
        vm.onCloseEscape()
        assertEquals("", vm.state.value.closing?.counted)

        vm.onCloseEscape()
        assertNull(vm.state.value.closing)
    }

    @Test
    fun amount_filter_rejects_letters_and_long_numbers() = runTest(dispatcher) {
        val repo = FakeCashRepository(closedSummary(null))
        val vm = loaded(repo)

        vm.onOpeningAmountChange("12ab3")
        assertEquals("123", vm.state.value.openingInput)

        vm.onOpeningAmountChange("12.345")
        assertEquals("12.34", vm.state.value.openingInput)

        vm.onOpeningAmountChange("1234567")
        assertEquals("123456", vm.state.value.openingInput)

        vm.openDay()
        advanceUntilIdle()

        assertEquals(CashStrings.INPUT_REJECTED, vm.state.value.errorMessage)
        assertEquals("", vm.state.value.openingInput)
        assertTrue(repo.openCalls.isEmpty())
    }

    @Test
    fun load_failure_shows_unavailable_and_retry_recovers() = runTest(dispatcher) {
        val repo = FakeCashRepository(AppResult.Failure(AppError.Network("down")))
        val vm = loaded(repo)

        assertEquals(CashPhase.Unavailable(CashStrings.NETWORK), vm.state.value.phase)

        repo.summary = closedSummary(10.0)
        vm.retry()
        advanceUntilIdle()

        assertEquals(CashPhase.Closed(10.0), vm.state.value.phase)
    }

    @Test
    fun forbidden_maps_to_manager_only_message() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary())
        repo.payoutResults.add(AppResult.Failure(AppError.Forbidden))
        val vm = loaded(repo)

        vm.openPayout()
        vm.onPayoutAmountChange("10")
        vm.confirmPayout()
        advanceUntilIdle()

        assertEquals(CashStrings.FORBIDDEN, vm.state.value.payout?.errorMessage)
    }

    @Test
    fun stale_load_does_not_replace_the_summary_after_a_payout() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        val vm = loaded(repo)
        val gate = CompletableDeferred<Unit>()
        repo.nextSummaryGate = gate

        vm.load()
        runCurrent()
        repo.summary = openSummary(expected = 1000.0)
        vm.openPayout()
        vm.onPayoutAmountChange("500")
        vm.confirmPayout()
        advanceUntilIdle()

        assertEquals(1000.0, vm.state.value.openSession?.expectedInDrawer)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1000.0, vm.state.value.openSession?.expectedInDrawer)
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun stale_load_does_not_replace_the_closing_summary() = runTest(dispatcher) {
        val repo = FakeCashRepository(openSummary(expected = 1500.0))
        val vm = loaded(repo)
        val gate = CompletableDeferred<Unit>()
        repo.nextSummaryGate = gate

        vm.load()
        runCurrent()
        vm.openClose()
        vm.onCountedChange("1500")
        vm.confirmClose()
        advanceUntilIdle()
        assertIs<CashPhase.DayClosed>(vm.state.value.phase)

        repo.summary = closedSummary(1500.0)
        gate.complete(Unit)
        advanceUntilIdle()
        assertIs<CashPhase.DayClosed>(vm.state.value.phase)

        vm.load()
        advanceUntilIdle()
        assertIs<CashPhase.DayClosed>(vm.state.value.phase)

        vm.finishDay()
        advanceUntilIdle()
        assertEquals(CashPhase.Closed(1500.0), vm.state.value.phase)
    }

    private fun TestScope.loaded(
        repo: FakeCashRepository,
        permissions: Set<Permission> = MANAGER,
    ): CashViewModel {
        val vm = CashViewModel(
            cashUseCases = CashUseCases(repo),
            terminalId = TERMINAL_ID,
            businessUnitId = BUSINESS_UNIT_ID,
            permissions = permissions,
            now = { NOW },
            dispatcher = dispatcher,
        )
        vm.load()
        advanceUntilIdle()
        return vm
    }
}

private fun closedSummary(previous: Double?): AppResult<CashSummary> =
    AppResult.Success(CashSummary(CashDrawerStatus.CLOSED, previous, null))

private fun openSummary(expected: Double = 1500.0, openAccounts: Int = 0): AppResult<CashSummary> =
    AppResult.Success(CashSummary(CashDrawerStatus.OPEN, 1000.0, sessionSummary(expected, openAccounts)))

private fun sessionSummary(expected: Double, openAccounts: Int = 0) = CashSessionSummary(
    sessionId = 7L,
    openedAt = OPENED_AT,
    openingAmount = 1000.0,
    cashSales = 600.0,
    creditPayments = 0.0,
    payouts = 100.0,
    otherOut = 0.0,
    expectedInDrawer = expected,
    transferSales = 50.0,
    cardSales = 30.0,
    creditSales = 20.0,
    openAccounts = openAccounts,
    payoutList = emptyList(),
)

private data class PayoutCall(val key: String, val amount: Double, val reason: PayoutReason, val notes: String?)

private class FakeCashRepository(var summary: AppResult<CashSummary>) : CashRepository {
    var summaryCalls = 0
        private set
    var nextSummaryGate: CompletableDeferred<Unit>? = null
    var openResult: AppResult<Unit> = AppResult.Success(Unit)
    var openGate: CompletableDeferred<Unit>? = null
    val openCalls = mutableListOf<Triple<Long, Long, Double>>()
    val payoutResults = ArrayDeque<AppResult<CashPayout>>()
    var payoutGate: CompletableDeferred<Unit>? = null
    val payoutCalls = mutableListOf<PayoutCall>()
    var closeResult: AppResult<CashCloseResult>? = null
    val closeCalls = mutableListOf<Pair<Long, Double>>()

    override suspend fun getCurrentSession(terminalId: Long): AppResult<CashSession?> = AppResult.Success(null)

    override suspend fun summary(terminalId: Long): AppResult<CashSummary> {
        summaryCalls++
        val captured = summary
        nextSummaryGate?.let {
            nextSummaryGate = null
            it.await()
        }
        return captured
    }

    override suspend fun openSession(terminalId: Long, businessUnitId: Long, openingAmount: Double): AppResult<Unit> {
        openCalls += Triple(terminalId, businessUnitId, openingAmount)
        openGate?.await()
        return openResult
    }

    override suspend fun closeSession(terminalId: Long, countedAmount: Double): AppResult<CashCloseResult> {
        closeCalls += terminalId to countedAmount
        closeResult?.let { return it }
        val session = (summary as AppResult.Success).value.session ?: sessionSummary(0.0)
        return AppResult.Success(
            CashCloseResult(
                openedAt = OPENED_AT,
                closedAt = NOW,
                summary = session,
                countedAmount = countedAmount,
                delta = countedAmount - session.expectedInDrawer,
            ),
        )
    }

    override suspend fun payout(
        terminalId: Long,
        idempotencyKey: String,
        amount: Double,
        reason: PayoutReason,
        notes: String?,
    ): AppResult<CashPayout> {
        payoutCalls += PayoutCall(idempotencyKey, amount, reason, notes)
        payoutGate?.await()
        return payoutResults.removeFirstOrNull()
            ?: AppResult.Success(CashPayout(payoutCalls.size.toLong(), amount, reason, notes, NOW))
    }
}
