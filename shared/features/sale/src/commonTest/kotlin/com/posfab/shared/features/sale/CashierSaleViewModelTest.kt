package com.posfab.shared.features.sale

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SalePriceType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.features.sale.repository.SaleRepository
import com.posfab.shared.features.sale.ui.CashierSaleViewModel
import com.posfab.shared.features.sale.ui.MAX_ACCOUNTS
import com.posfab.shared.features.sale.ui.SaleConfirm
import com.posfab.shared.features.sale.ui.SalePhase
import com.posfab.shared.features.sale.ui.SaleStrings
import com.posfab.shared.features.sale.usecase.SaleUseCases
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CashierSaleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun TestScope.start(repo: FakeSaleRepository): CashierSaleViewModel {
        val vm = CashierSaleViewModel(SaleUseCases(repo), dispatcher = dispatcher)
        vm.load()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun cash_session_states_drive_the_phase() = runTest(dispatcher) {
        val closed = start(FakeSaleRepository().apply { cashSession = CashSessionCheck.Closed })
        assertEquals(SalePhase.CashClosed, closed.state.value.phase)

        val unavailable = start(
            FakeSaleRepository().apply { cashSession = CashSessionCheck.Unavailable(AppError.Network("down")) }
        )
        assertIs<SalePhase.CashUnavailable>(unavailable.state.value.phase)

        val repo = FakeSaleRepository()
        val open = start(repo)
        assertEquals(SalePhase.Selling, open.state.value.phase)
        assertEquals(repo.kitchenPool.size, open.state.value.kitchenProducts.size)
        assertNotNull(open.state.value.draft)
    }

    @Test
    fun scanning_the_same_product_twice_merges_into_one_line() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        repeat(2) {
            vm.onQueryChange(repo.cigarro.barcode!!)
            vm.onSubmit()
            advanceUntilIdle()
        }

        val lines = vm.state.value.draft!!.lines
        assertEquals(1, lines.size)
        assertEquals(2.0, lines.first().qty)
        assertEquals(1, repo.addLineCalls)
        assertEquals(1, repo.updateQtyCalls)
        assertEquals("", vm.state.value.query)
    }

    @Test
    fun two_quick_adds_are_queued_and_not_lost() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.addProduct(repo.cigarro)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        val lines = vm.state.value.draft!!.lines
        assertEquals(1, lines.size)
        assertEquals(2.0, lines.first().qty)
        assertFalse(vm.state.value.notice.orEmpty().contains("cambi", ignoreCase = true))
    }

    @Test
    fun submit_with_text_searches_immediately_and_tapping_a_result_adds() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onQueryChange("caf")
        vm.onSubmit()
        runCurrent()

        assertEquals(1, repo.searchCalls)
        assertEquals(listOf(repo.cafe), vm.state.value.searchResults)

        vm.addProduct(vm.state.value.searchResults.first())
        advanceUntilIdle()
        assertEquals("Cafe de olla", vm.state.value.draft!!.lines.single().productName)
    }

    @Test
    fun typing_searches_after_the_debounce() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onQueryChange("cig")
        advanceTimeBy(350)
        runCurrent()

        assertEquals(listOf(repo.cigarro), vm.state.value.searchResults)

        vm.onQueryChange("")
        advanceTimeBy(350)
        runCurrent()
        assertTrue(vm.state.value.searchResults.isEmpty())
    }

    @Test
    fun unknown_code_shows_message_with_the_code_and_adds_nothing() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onQueryChange("9999999999999")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(SaleStrings.notFoundWithCode("9999999999999"), vm.state.value.errorMessage)
        assertTrue(vm.state.value.errorMessage!!.contains("9999999999999"))
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
        assertEquals(0, repo.addLineCalls)
    }

    @Test
    fun scanning_a_weight_product_is_not_sellable_yet() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onQueryChange(repo.jamon.barcode!!)
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(SaleStrings.NOT_SELLABLE_YET, vm.state.value.errorMessage)
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
        assertEquals(0, repo.addLineCalls)
    }

    @Test
    fun quantity_controls_increment_decrement_typed_and_remove() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        val lineId = vm.state.value.draft!!.lines.single().id
        vm.selectLine(lineId)

        vm.incrementQty()
        advanceUntilIdle()
        assertEquals(2.0, vm.state.value.draft!!.lines.single().qty)

        vm.decrementQty()
        advanceUntilIdle()
        assertEquals(1.0, vm.state.value.draft!!.lines.single().qty)

        vm.decrementQty()
        advanceUntilIdle()
        assertEquals(1.0, vm.state.value.draft!!.lines.single().qty)

        vm.onQtyInputChange("6")
        vm.applyQty()
        advanceUntilIdle()
        assertEquals(6.0, vm.state.value.draft!!.lines.single().qty)
        assertEquals(126.0, vm.state.value.cashTotal)

        vm.removeSelectedLine()
        advanceUntilIdle()
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
    }

    @Test
    fun start_charge_needs_lines_and_paid_amount_decides_confirmation() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.startCharge()
        advanceUntilIdle()
        assertEquals(SalePhase.Selling, vm.state.value.phase)

        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        assertEquals(63.0, vm.state.value.cashTotal)

        vm.startCharge()
        assertEquals(SalePhase.Charging, vm.state.value.phase)

        vm.payWith(50)
        assertFalse(vm.state.value.canConfirmCharge)

        vm.payWith(100)
        assertEquals(37.0, vm.state.value.change)
        assertTrue(vm.state.value.canConfirmCharge)

        vm.cancelCharge()
        assertEquals(SalePhase.Selling, vm.state.value.phase)
    }

    @Test
    fun pay_exact_charges_immediately_with_zero_change() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()

        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()

        assertEquals(SalePhase.Done, vm.state.value.phase)
        assertEquals(1, repo.checkoutCalls)
        assertEquals(0.0, vm.state.value.lastResult!!.change)
        assertNull(vm.state.value.draft)
    }

    @Test
    fun double_confirm_sends_a_single_checkout() = runTest(dispatcher) {
        val repo = FakeSaleRepository(checkoutDelayMs = 50)
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(1, repo.checkoutCalls)
        assertEquals(SalePhase.Done, vm.state.value.phase)
        assertEquals(100.0, repo.lastPaidAmount)
        assertEquals(37.0, vm.state.value.lastResult!!.change)
    }

    @Test
    fun network_failure_keeps_charging_and_retry_reuses_the_key() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstCheckoutWithNetwork = true }
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()
        assertEquals(SalePhase.Charging, vm.state.value.phase)
        assertNotNull(vm.state.value.errorMessage)

        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(2, repo.checkoutCalls)
        assertEquals(1, repo.checkoutKeys.distinct().size)
        assertEquals(SalePhase.Done, vm.state.value.phase)
    }

    @Test
    fun validation_failure_discards_the_key() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failCheckoutWithValidation = true }
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()
        assertEquals(SalePhase.Charging, vm.state.value.phase)
        assertNotNull(vm.state.value.errorMessage)

        repo.failCheckoutWithValidation = false
        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(2, repo.checkoutCalls)
        assertEquals(2, repo.checkoutKeys.distinct().size)
        assertEquals(SalePhase.Done, vm.state.value.phase)
    }

    @Test
    fun adding_after_done_starts_a_new_sale_with_that_line() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        val firstDraftId = vm.state.value.draft!!.id
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()
        assertEquals(SalePhase.Done, vm.state.value.phase)

        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        assertEquals(SalePhase.Selling, vm.state.value.phase)
        assertNull(vm.state.value.lastResult)
        val draft = vm.state.value.draft!!
        assertNotEquals(firstDraftId, draft.id)
        assertEquals(repo.cigarro.id, draft.lines.single().productId)
    }

    @Test
    fun start_next_sale_returns_to_an_empty_selling_state() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()

        vm.startNextSale()
        advanceUntilIdle()

        assertEquals(SalePhase.Selling, vm.state.value.phase)
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
        assertNull(vm.state.value.lastResult)
        assertEquals("", vm.state.value.query)
    }

    @Test
    fun clearing_asks_for_confirmation_and_cancels_the_draft() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.requestClear()
        assertNull(vm.state.value.confirm)

        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        val oldId = vm.state.value.draft!!.id

        vm.requestClear()
        assertEquals(SaleConfirm.ClearCart, vm.state.value.confirm)
        assertEquals(0, repo.cancelCalls)

        vm.dismissConfirm()
        assertNull(vm.state.value.confirm)
        assertEquals(0, repo.cancelCalls)

        vm.requestClear()
        vm.confirmClear()
        advanceUntilIdle()

        assertEquals(1, repo.cancelCalls)
        assertNull(vm.state.value.confirm)
        val draft = vm.state.value.draft!!
        assertNotEquals(oldId, draft.id)
        assertTrue(draft.lines.isEmpty())
    }

    @Test
    fun conflict_on_update_shows_notice_and_reloads_version() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        repo.forceConflictOnNextUpdate = true
        vm.selectLine(vm.state.value.draft!!.lines.first().id)
        vm.incrementQty()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice?.contains("cambi", ignoreCase = true) == true)
        assertEquals(3, vm.state.value.draft?.version)
    }

    @Test
    fun lot_tracked_line_resolves_lots_before_checkout() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cafe)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(1, repo.resolveLotsCalls)
        assertEquals(listOf("resolveLots", "checkout"), repo.callLog)
        assertEquals(SalePhase.Done, vm.state.value.phase)
    }

    @Test
    fun plain_line_does_not_resolve_lots() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(0, repo.resolveLotsCalls)
        assertEquals(1, repo.checkoutCalls)
    }

    @Test
    fun mutations_are_ignored_while_charging() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.selectLine(vm.state.value.draft!!.lines.single().id)
        vm.startCharge()
        assertEquals(SalePhase.Charging, vm.state.value.phase)

        vm.addProduct(repo.cafe)
        vm.incrementQty()
        vm.decrementQty()
        vm.onQtyInputChange("5")
        vm.applyQty()
        vm.removeSelectedLine()
        advanceUntilIdle()

        assertEquals(SaleStrings.FINISH_CHARGE_FIRST, vm.state.value.notice)
        assertEquals(1, repo.addLineCalls)
        assertEquals(0, repo.updateQtyCalls)
        val line = vm.state.value.draft!!.lines.single()
        assertEquals(1.0, line.qty)
        assertEquals(SalePhase.Charging, vm.state.value.phase)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun checkout_conflict_reloads_the_sale_and_asks_to_charge_again() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failCheckoutWithConflict = true }
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(SaleStrings.CHECKOUT_CONFLICT, state.errorMessage)
        assertNull(state.checkoutIdempotencyKey)
        assertFalse(state.isCheckoutInFlight)
        assertEquals(1, state.draft!!.lines.size)
    }

    @Test
    fun checkout_conflict_with_empty_reloaded_sale_warns_it_may_be_charged() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply {
            failCheckoutWithConflict = true
            conflictAfterServerCharged = true
        }
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(SaleStrings.POSSIBLY_CHARGED, state.notice)
        assertNull(state.errorMessage)
        assertTrue(state.draft!!.lines.isEmpty())
    }

    @Test
    fun paid_amount_is_locked_after_a_network_failure_until_going_back() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstCheckoutWithNetwork = true }
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()
        assertTrue(vm.state.value.isPaidLocked)

        vm.payWith(200)
        vm.onPaidInputChange("5")
        vm.payExact()
        advanceUntilIdle()
        assertEquals("100", vm.state.value.paidInput)
        assertEquals(1, repo.checkoutCalls)

        vm.cancelCharge()
        assertNull(vm.state.value.checkoutIdempotencyKey)
        assertFalse(vm.state.value.isPaidLocked)
        assertEquals(SalePhase.Selling, vm.state.value.phase)
    }

    @Test
    fun load_lists_open_drafts_oldest_first() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(12L, 9L))
        val vm = start(repo)

        val state = vm.state.value
        assertEquals(listOf(9L, 12L), state.accounts.map { it.id })
        assertEquals(9L, state.activeDraftId)
        assertEquals(1, repo.openDraftsCalls)
        assertEquals(0, repo.newDraftCalls)
        assertEquals(SalePhase.Selling, state.phase)
    }

    @Test
    fun load_creates_one_account_when_the_terminal_has_none() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = emptyList())
        val vm = start(repo)

        val state = vm.state.value
        assertEquals(1, state.accounts.size)
        assertEquals(state.accounts.single().id, state.activeDraftId)
        assertEquals(1, repo.newDraftCalls)
        assertEquals(SalePhase.Selling, state.phase)
    }

    @Test
    fun new_account_switches_and_keeps_the_first_one() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        val firstId = vm.state.value.activeDraftId

        assertTrue(vm.state.value.canOpenAccount)
        vm.openAccount()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.accounts.size)
        assertEquals(firstId, state.accounts.first().id)
        assertEquals(1, state.accounts.first().lines.size)
        assertEquals(state.accounts.last().id, state.activeDraftId)
        assertTrue(state.draft!!.lines.isEmpty())
        assertEquals(1, repo.newDraftCalls)
    }

    @Test
    fun new_account_is_ignored_when_the_active_one_is_empty() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        assertFalse(vm.state.value.canOpenAccount)
        vm.openAccount()
        advanceUntilIdle()
        assertEquals(0, repo.newDraftCalls)
        assertEquals(1, vm.state.value.accounts.size)

        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.openAccount()
        vm.openAccount()
        advanceUntilIdle()

        assertEquals(1, repo.newDraftCalls)
        assertEquals(2, vm.state.value.accounts.size)
    }

    @Test
    fun max_accounts_disables_new_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = (1L..MAX_ACCOUNTS.toLong()).toList())
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        assertEquals(MAX_ACCOUNTS, vm.state.value.accounts.size)
        assertFalse(vm.state.value.canOpenAccount)
        vm.openAccount()
        advanceUntilIdle()

        assertEquals(0, repo.newDraftCalls)
        assertEquals(MAX_ACCOUNTS, vm.state.value.accounts.size)
    }

    @Test
    fun switching_accounts_clears_selection_and_notice_but_keeps_search() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.selectLine(vm.state.value.draft!!.lines.single().id)
        vm.onQueryChange("cig")
        advanceTimeBy(350)
        runCurrent()
        assertNotNull(vm.state.value.notice)
        assertNotNull(vm.state.value.selectedLineId)

        vm.selectAccount(99L)
        assertEquals(1L, vm.state.value.activeDraftId)

        vm.selectAccount(2L)

        val state = vm.state.value
        assertEquals(2L, state.activeDraftId)
        assertNull(state.selectedLineId)
        assertEquals("", state.qtyInput)
        assertNull(state.notice)
        assertNull(state.errorMessage)
        assertEquals("cig", state.query)
        assertEquals(listOf(repo.cigarro), state.searchResults)
        assertEquals(1, state.accountById(1L)!!.lines.size)
    }

    @Test
    fun queued_scans_land_on_the_account_that_was_active_when_scanned() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)

        vm.addProduct(repo.cigarro)
        vm.addProduct(repo.cigarro)
        vm.selectAccount(2L)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2L, state.activeDraftId)
        val first = state.accountById(1L)!!.lines.single()
        assertEquals(repo.cigarro.id, first.productId)
        assertEquals(2.0, first.qty)
        assertEquals(repo.kitchenPool.first().id, state.accountById(2L)!!.lines.single().productId)
        assertFalse(state.isBusy)
    }

    @Test
    fun increment_after_switching_updates_the_original_line() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.selectLine(vm.state.value.draft!!.lines.single().id)

        vm.incrementQty()
        vm.selectAccount(2L)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2L, state.activeDraftId)
        assertEquals(2.0, state.accountById(1L)!!.lines.single().qty)
        assertTrue(state.draft!!.lines.isEmpty())
        assertNull(state.selectedLineId)
        assertNull(state.notice)
    }

    @Test
    fun charging_one_account_removes_it_and_activates_the_next() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Done, state.phase)
        assertNotNull(state.lastResult)
        assertEquals(listOf(2L), state.accounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertEquals("CHECKED_OUT", repo.drafts.getValue(1L).status)
    }

    @Test
    fun next_after_charging_with_remaining_accounts_does_not_create_a_draft() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.selectAccount(2L)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        vm.selectAccount(1L)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()

        vm.startNextSale()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(2L, state.activeDraftId)
        assertNull(state.lastResult)
        assertEquals(1, state.draft!!.lines.size)
        assertEquals(0, repo.newDraftCalls)
    }

    @Test
    fun scanning_after_charging_adds_to_the_next_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()

        vm.onQueryChange(repo.cigarro.barcode!!)
        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(2L, state.activeDraftId)
        assertEquals(repo.cigarro.id, state.draft!!.lines.single().productId)
        assertEquals(0, repo.newDraftCalls)
    }

    @Test
    fun charging_the_last_account_then_next_creates_a_new_one() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()

        assertTrue(vm.state.value.accounts.isEmpty())
        assertNull(vm.state.value.activeDraftId)

        vm.startNextSale()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(1, repo.newDraftCalls)
        assertEquals(1, state.accounts.size)
        assertEquals(state.accounts.single().id, state.activeDraftId)
    }

    @Test
    fun clearing_with_other_accounts_closes_it_and_switches() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        vm.requestClear()
        assertEquals(SaleConfirm.ClearCart, vm.state.value.confirm)
        vm.confirmClear()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(2L), state.accounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertEquals("CANCELLED", repo.drafts.getValue(1L).status)
        assertEquals(0, repo.newDraftCalls)
    }

    @Test
    fun clearing_the_only_account_replaces_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        val oldId = vm.state.value.activeDraftId

        vm.requestClear()
        vm.confirmClear()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.accounts.size)
        assertNotEquals(oldId, state.activeDraftId)
        assertEquals(state.accounts.single().id, state.activeDraftId)
        assertTrue(state.draft!!.lines.isEmpty())
        assertEquals(1, repo.newDraftCalls)
    }

    @Test
    fun empty_account_with_others_closes_without_confirmation() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)

        assertTrue(vm.state.value.canCloseEmptyAccount)
        vm.requestClear()
        assertNull(vm.state.value.confirm)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, repo.cancelCalls)
        assertEquals(listOf(2L), state.accounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertFalse(state.canCloseEmptyAccount)
    }

    @Test
    fun account_cancelled_elsewhere_is_removed_on_next_mutation() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        repo.closeElsewhere(1L, "CANCELLED")
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(2L), state.accounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertEquals(SaleStrings.ACCOUNT_CLOSED, state.notice)
        assertEquals(SalePhase.Selling, state.phase)
    }

    @Test
    fun checkout_conflict_on_a_draft_charged_elsewhere_removes_it_and_warns() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L)).apply {
            failCheckoutWithConflict = true
            conflictAfterServerCharged = true
        }
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(listOf(2L), state.accounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertEquals(SaleStrings.POSSIBLY_CHARGED, state.notice)
        assertNull(state.checkoutIdempotencyKey)
        assertFalse(state.isCheckoutInFlight)
        assertEquals("", state.paidInput)
        assertEquals(0, repo.newDraftCalls)
    }

    @Test
    fun switching_is_blocked_while_charging() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.startCharge()

        vm.selectAccount(2L)

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(1L, state.activeDraftId)
        assertEquals(SaleStrings.FINISH_CHARGE_FIRST, state.notice)
    }
    @Test
    fun scan_in_done_then_switching_account_adds_to_the_account_shown_when_scanned() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L, 3L))
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()
        assertEquals(SalePhase.Done, vm.state.value.phase)
        assertEquals(2L, vm.state.value.activeDraftId)

        vm.addProduct(repo.cigarro)
        vm.selectAccount(3L)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(repo.cigarro.id, state.accountById(2L)!!.lines.single().productId)
        assertTrue(state.accountById(3L)!!.lines.isEmpty())
    }

    @Test
    fun scan_with_no_account_after_a_failed_draft_creation_retries_creating_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        repo.failNextNewDraft = true

        vm.requestClear()
        vm.confirmClear()
        advanceUntilIdle()
        assertTrue(vm.state.value.accounts.isEmpty())
        assertNull(vm.state.value.activeDraftId)
        assertNotNull(vm.state.value.errorMessage)
        assertEquals(1, repo.newDraftCalls)

        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, repo.newDraftCalls)
        assertEquals(1, state.accounts.size)
        assertEquals(state.accounts.single().id, state.activeDraftId)
        assertEquals(repo.cigarro.id, state.draft!!.lines.single().productId)
    }

    @Test
    fun checkout_conflict_with_a_network_failure_on_reload_keeps_the_key() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply {
            failCheckoutWithConflict = true
            failNextDraftReadWithNetwork = true
        }
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(100)
        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(repo.checkoutKeys.single(), state.checkoutIdempotencyKey)
        assertTrue(state.isPaidLocked)
        assertFalse(state.isCheckoutInFlight)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun charge_opens_on_cash() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
        assertEquals("", vm.state.value.paidInput)

        vm.selectPaymentType(SalePaymentType.CARD)
        assertEquals(SalePaymentType.CARD, vm.state.value.paymentType)
        vm.cancelCharge()
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)

        vm.startCharge()
        assertEquals(SalePhase.Charging, vm.state.value.phase)
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
        assertFalse(vm.state.value.canConfirmCharge)
    }

    @Test
    fun transfer_confirms_without_paid_input_and_sends_cash_total() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        vm.selectPaymentType(SalePaymentType.TRANSFER)
        val charging = vm.state.value
        assertEquals("", charging.paidInput)
        assertEquals(40.0, charging.amountDue)
        assertEquals(40.0, charging.paidAmount)
        assertNull(charging.change)
        assertTrue(charging.canConfirmCharge)

        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(SalePhase.Done, vm.state.value.phase)
        assertEquals(SalePaymentType.TRANSFER, repo.lastPaymentType)
        assertEquals(40.0, repo.lastPaidAmount)
        val result = vm.state.value.lastResult!!
        assertEquals(SalePaymentType.TRANSFER, result.paymentType)
        assertEquals(40.0, result.totalCharged)
        assertNull(result.change)
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
    }

    @Test
    fun card_sends_list_total_and_has_no_change() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        vm.selectPaymentType(SalePaymentType.CARD)
        assertEquals(42.0, vm.state.value.amountDue)
        assertEquals(42.0, vm.state.value.paidAmount)
        assertNull(vm.state.value.change)

        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(SalePaymentType.CARD, repo.lastPaymentType)
        assertEquals(42.0, repo.lastPaidAmount)
        val result = vm.state.value.lastResult!!
        assertEquals(SalePaymentType.CARD, result.paymentType)
        assertEquals(42.0, result.totalCharged)
        assertNull(result.change)
    }

    @Test
    fun switching_payment_type_clears_the_paid_input() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(50)
        assertEquals("50", vm.state.value.paidInput)

        vm.selectPaymentType(SalePaymentType.CARD)
        assertEquals("", vm.state.value.paidInput)

        vm.selectPaymentType(SalePaymentType.CASH)
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
        assertEquals("", vm.state.value.paidInput)
        assertNull(vm.state.value.paidAmount)
        assertFalse(vm.state.value.canConfirmCharge)
    }

    @Test
    fun switching_payment_type_is_blocked_while_paid_locked() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstCheckoutWithNetwork = true }
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        vm.payWith(50)
        vm.confirmCharge()
        advanceUntilIdle()
        assertTrue(vm.state.value.isPaidLocked)

        vm.selectPaymentType(SalePaymentType.CARD)
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
        assertEquals("50", vm.state.value.paidInput)

        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(2, repo.checkoutCalls)
        assertEquals(1, repo.checkoutKeys.distinct().size)
        assertEquals(SalePaymentType.CASH, repo.lastPaymentType)
        assertEquals(50.0, repo.lastPaidAmount)
        assertEquals(SalePhase.Done, vm.state.value.phase)
    }

    @Test
    fun set_channel_sends_expected_version_and_applies_the_repriced_draft() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        val before = vm.state.value.draft!!
        assertEquals(42.0, before.lines.single().unitPrice)

        vm.setChannel(SaleChannel.ONSITE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, repo.setChannelCalls)
        assertEquals(before.version, repo.lastChannelExpectedVersion)
        assertEquals(SaleChannel.ONSITE, state.channel)
        assertEquals(repo.drafts.getValue(before.id).version, state.draft!!.version)
        assertEquals(44.0, state.draft!!.lines.single().unitPrice)
        assertEquals(44.0, state.listTotal)
        assertEquals(SaleStrings.channelNotice(SaleChannel.ONSITE), state.notice)
        assertEquals(0, state.pendingMutations)
    }

    @Test
    fun set_channel_same_value_is_a_no_op() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        val version = vm.state.value.draft!!.version

        vm.setChannel(SaleChannel.TAKEAWAY)
        advanceUntilIdle()

        assertEquals(0, repo.setChannelCalls)
        assertEquals(0, vm.state.value.pendingMutations)
        assertEquals(version, vm.state.value.draft!!.version)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun set_channel_conflict_refetches_by_id() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        val draftId = vm.state.value.draft!!.id
        repo.forceConflictOnNextSetChannel = true

        vm.setChannel(SaleChannel.ONSITE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, repo.setChannelCalls)
        assertEquals(draftId, state.activeDraftId)
        assertEquals(SaleChannel.TAKEAWAY, state.channel)
        assertEquals(repo.drafts.getValue(draftId).version, state.draft!!.version)
        assertEquals(SaleStrings.SALE_CHANGED, state.notice)
    }

    @Test
    fun back_after_network_failure_clears_the_key_and_reopens_on_cash() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstCheckoutWithNetwork = true }
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        vm.selectPaymentType(SalePaymentType.TRANSFER)
        vm.confirmCharge()
        advanceUntilIdle()
        assertTrue(vm.state.value.isPaidLocked)

        vm.cancelCharge()
        assertEquals(SalePhase.Selling, vm.state.value.phase)
        assertNull(vm.state.value.checkoutIdempotencyKey)

        vm.startCharge()
        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(SalePaymentType.CASH, state.paymentType)
        assertFalse(state.isPaidLocked)
        assertEquals("", state.paidInput)
    }

    @Test
    fun set_channel_on_a_draft_closed_elsewhere_removes_the_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        assertEquals(1L, vm.state.value.activeDraftId)

        repo.closeElsewhere(1L, "CANCELLED")
        vm.setChannel(SaleChannel.ONSITE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, repo.setChannelCalls)
        assertEquals(listOf(2L), state.accounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertEquals(SaleStrings.ACCOUNT_CLOSED, state.notice)
        assertEquals(SalePhase.Selling, state.phase)
    }

    @Test
    fun set_channel_is_ignored_while_charging() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        assertFalse(vm.state.value.canSetChannel)
        vm.setChannel(SaleChannel.ONSITE)
        advanceUntilIdle()
        assertEquals(0, repo.setChannelCalls)
        assertEquals(SaleChannel.TAKEAWAY, vm.state.value.channel)

        vm.cancelCharge()
        vm.requestClear()
        assertFalse(vm.state.value.canSetChannel)
        vm.setChannel(SaleChannel.ONSITE)
        advanceUntilIdle()
        assertEquals(0, repo.setChannelCalls)
    }

    @Test
    fun channel_is_per_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        val firstId = vm.state.value.activeDraftId!!
        vm.setChannel(SaleChannel.ONSITE)
        advanceUntilIdle()

        vm.openAccount()
        advanceUntilIdle()
        val secondId = vm.state.value.activeDraftId!!
        assertNotEquals(firstId, secondId)
        assertEquals(SaleChannel.TAKEAWAY, vm.state.value.channel)

        vm.selectAccount(firstId)
        assertEquals(SaleChannel.ONSITE, vm.state.value.channel)
        assertEquals(SaleChannel.TAKEAWAY, vm.state.value.accountById(secondId)!!.channel)
    }

    @Test
    fun cart_totals_come_from_the_draft() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        assertEquals(64.0, vm.state.value.listTotal)
        assertEquals(61.0, vm.state.value.cashTotal)
        assertEquals(3.0, vm.state.value.cashDiscount)
        assertTrue(vm.state.value.canCharge)

        val suggested = FakeSaleRepository()
        val other = start(suggested)
        other.addProduct(suggested.chicle)
        advanceUntilIdle()

        assertEquals(5.0, other.state.value.listTotal)
        assertEquals(5.0, other.state.value.cashTotal)
        assertEquals(0.0, other.state.value.cashDiscount)
    }

    @Test
    fun cash_checkout_sends_cash_type() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.startCharge()
        assertEquals(40.0, vm.state.value.amountDue)
        vm.payWith(100)
        assertEquals(60.0, vm.state.value.change)
        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(SalePaymentType.CASH, repo.lastPaymentType)
        assertEquals(100.0, repo.lastPaidAmount)
        val result = vm.state.value.lastResult!!
        assertEquals(SalePaymentType.CASH, result.paymentType)
        assertEquals(40.0, result.totalCharged)
        assertEquals(60.0, result.change)
    }
}

private class FakeSaleRepository(
    private val checkoutDelayMs: Long = 0,
    openDraftsSeed: List<Long> = listOf(1L),
) : SaleRepository {
    val cigarro = SaleProduct(
        id = 1L, name = "Cigarro suelto", barcode = "7501000100015", unitId = 1L, cashPrice = 21.0,
        listPrice = 22.0, listOnsitePrice = null,
    )
    val cafe = SaleProduct(
        id = 2L, name = "Cafe de olla", barcode = "7501000100022", unitId = 1L, cashPrice = 30.0, lotTracked = true,
        listPrice = 31.5, listOnsitePrice = null,
    )
    val jamon = SaleProduct(
        id = 3L, name = "Jamon", barcode = "7501000100039", unitId = 2L, cashPrice = 120.0,
        listPrice = 126.0, listOnsitePrice = null,
        priceType = SalePriceType.WEIGHT,
    )
    val chicle = SaleProduct(
        id = 6L, name = "Chicle", barcode = "7501000100060", unitId = 1L, cashPrice = 5.0,
        listPrice = 5.0, listOnsitePrice = null,
    )
    val searchPool = listOf(cigarro, cafe)
    val kitchenPool = listOf(
        SaleProduct(
            id = 4L, name = "Comida", barcode = null, unitId = 1L, cashPrice = 63.0,
            listPrice = 66.0, listOnsitePrice = 66.0,
            kitchenKind = SaleKitchenKind.FONDA,
        ),
        SaleProduct(
            id = 5L, name = "Torta", barcode = null, unitId = 1L, cashPrice = 40.0,
            listPrice = 42.0, listOnsitePrice = 44.0,
            kitchenKind = SaleKitchenKind.TORTERIA,
        ),
    )

    val torta: SaleProduct get() = kitchenPool[1]

    val drafts: MutableMap<Long, SaleDraft> = openDraftsSeed.associateWith { emptyDraft(it) }.toMutableMap()

    var cashSession: CashSessionCheck = CashSessionCheck.Open
    var failFirstCheckoutWithNetwork = false
    var failCheckoutWithValidation = false
    var forceConflictOnNextUpdate = false
    var failCheckoutWithConflict = false
    var conflictAfterServerCharged = false
    var failNextNewDraft = false
    var failNextDraftReadWithNetwork = false
    var forceConflictOnNextSetChannel = false

    var addLineCalls = 0
    var updateQtyCalls = 0
    var searchCalls = 0
    var checkoutCalls = 0
    var cancelCalls = 0
    var resolveLotsCalls = 0
    var openDraftsCalls = 0
    var newDraftCalls = 0
    var setChannelCalls = 0
    var lastChannelExpectedVersion: Int? = null
    var lastPaidAmount: Double? = null
    var lastPaymentType: SalePaymentType? = null
    val checkoutKeys = mutableListOf<String>()
    val callLog = mutableListOf<String>()

    private var draftCounter = openDraftsSeed.maxOrNull() ?: 0L
    private var nextLineId = 1L

    fun closeElsewhere(draftId: Long, status: String) {
        drafts[draftId]?.let { drafts[draftId] = it.copy(status = status, version = it.version + 1) }
    }

    private fun emptyDraft(id: Long) = SaleDraft(
        id = id, status = "OPEN", version = 1, lines = emptyList(), totals = SaleTotals(cashTotal = 0.0, listTotal = 0.0, cashDiscount = 0.0),
    )

    private fun round2(value: Double) = kotlin.math.round(value * 100) / 100

    private fun SaleDraft.withLines(lines: List<SaleLine>): SaleDraft {
        val cashTotal = round2(lines.sumOf { it.qty * it.cashUnitPrice })
        val listTotal = round2(lines.sumOf { it.lineTotal })
        return copy(
            version = version + 1,
            lines = lines,
            totals = SaleTotals(cashTotal = cashTotal, listTotal = listTotal, cashDiscount = round2(listTotal - cashTotal)),
        )
    }

    private fun notFound(): AppError = AppError.Validation("Request rejected: 404")

    private fun mutate(
        draftId: Long,
        expectedVersion: Int,
        change: (SaleDraft) -> SaleDraft,
    ): SaleMutationResult {
        val draft = drafts[draftId] ?: return SaleMutationResult.Failure(notFound())
        if (!draft.isOpen) return SaleMutationResult.DraftClosed(draft)
        if (expectedVersion != draft.version) return SaleMutationResult.ConflictRefetched(draft)
        val updated = change(draft)
        drafts[draftId] = updated
        return SaleMutationResult.Success(updated)
    }

    override suspend fun cashSession(): AppResult<CashSessionCheck> = AppResult.Success(cashSession)

    override suspend fun openDrafts(): AppResult<List<SaleDraft>> {
        openDraftsCalls += 1
        return AppResult.Success(drafts.values.filter { it.isOpen }.sortedBy { it.id })
    }

    override suspend fun draft(draftId: Long): AppResult<SaleDraft> {
        if (failNextDraftReadWithNetwork) {
            failNextDraftReadWithNetwork = false
            return AppResult.Failure(AppError.Network("network down"))
        }
        return drafts[draftId]?.let { AppResult.Success(it) } ?: AppResult.Failure(notFound())
    }

    override suspend fun newDraft(): AppResult<SaleDraft> {
        newDraftCalls += 1
        if (failNextNewDraft) {
            failNextNewDraft = false
            return AppResult.Failure(AppError.Network("network down"))
        }
        draftCounter += 1
        val created = emptyDraft(draftCounter)
        drafts[created.id] = created
        return AppResult.Success(created)
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> {
        searchCalls += 1
        return AppResult.Success(searchPool.filter { it.name.contains(query, ignoreCase = true) }.take(limit))
    }

    override suspend fun kitchenProducts(): AppResult<List<SaleProduct>> = AppResult.Success(kitchenPool)

    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> =
        AppResult.Success((searchPool + jamon + kitchenPool + chicle).firstOrNull { it.barcode == barcode })

    override suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        qty: Double,
        expectedVersion: Int,
    ): SaleMutationResult {
        addLineCalls += 1
        return mutate(draftId, expectedVersion) { draft ->
            val price = product.cashPrice ?: 0.0
            val listPrice = product.listPriceFor(draft.channel) ?: price
            val line = SaleLine(
                id = nextLineId++,
                productId = product.id,
                productName = product.name,
                qty = qty,
                unitId = product.unitId,
                unitPrice = listPrice,
                cashUnitPrice = price,
                lineTotal = round2(qty * listPrice),
                lotTracked = product.lotTracked,
            )
            draft.withLines(draft.lines + line)
        }
    }

    override suspend fun updateLineQty(
        draftId: Long,
        lineId: Long,
        qty: Double,
        expectedVersion: Int,
    ): SaleMutationResult {
        updateQtyCalls += 1
        if (forceConflictOnNextUpdate) {
            forceConflictOnNextUpdate = false
            val draft = drafts[draftId] ?: return SaleMutationResult.Failure(notFound())
            val bumped = draft.copy(version = draft.version + 1)
            drafts[draftId] = bumped
            return SaleMutationResult.ConflictRefetched(bumped)
        }
        return mutate(draftId, expectedVersion) { draft ->
            draft.withLines(
                draft.lines.map {
                    if (it.id == lineId) it.copy(qty = qty, lineTotal = round2(qty * it.unitPrice)) else it
                }
            )
        }
    }

    override suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult =
        mutate(draftId, expectedVersion) { draft -> draft.withLines(draft.lines.filterNot { it.id == lineId }) }

    override suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> {
        resolveLotsCalls += 1
        callLog += "resolveLots"
        val draft = drafts[draftId] ?: return AppResult.Failure(notFound())
        val resolved = draft.withLines(draft.lines.map { if (it.lotTracked) it.copy(lotId = 999L) else it })
        drafts[draftId] = resolved
        return AppResult.Success(resolved)
    }

    override suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit> {
        cancelCalls += 1
        val draft = drafts[draftId] ?: return AppResult.Failure(notFound())
        if (!draft.isOpen || draft.version != expectedVersion) return AppResult.Failure(AppError.Conflict)
        drafts[draftId] = draft.copy(status = "CANCELLED", version = draft.version + 1)
        return AppResult.Success(Unit)
    }

    override suspend fun setChannel(draftId: Long, channel: SaleChannel, expectedVersion: Int): SaleMutationResult {
        setChannelCalls += 1
        lastChannelExpectedVersion = expectedVersion
        if (forceConflictOnNextSetChannel) {
            forceConflictOnNextSetChannel = false
            val draft = drafts[draftId] ?: return SaleMutationResult.Failure(notFound())
            val bumped = draft.copy(version = draft.version + 1)
            drafts[draftId] = bumped
            return SaleMutationResult.ConflictRefetched(bumped)
        }
        return mutate(draftId, expectedVersion) { draft ->
            draft.copy(channel = channel).withLines(
                draft.lines.map { line ->
                    val listPrice = productById(line.productId)?.listPriceFor(channel) ?: line.unitPrice
                    line.copy(unitPrice = listPrice, lineTotal = round2(line.qty * listPrice))
                }
            )
        }
    }

    private fun productById(id: Long): SaleProduct? =
        (searchPool + jamon + kitchenPool + chicle).firstOrNull { it.id == id }

    private fun SaleProduct.listPriceFor(channel: SaleChannel): Double? =
        if (channel == SaleChannel.ONSITE) listOnsitePrice ?: listPrice else listPrice

    override suspend fun checkout(
        draftId: Long,
        paymentType: SalePaymentType,
        paidAmount: Double,
        idempotencyKey: String,
    ): AppResult<CheckoutResult> {
        checkoutCalls += 1
        callLog += "checkout"
        checkoutKeys += idempotencyKey
        lastPaidAmount = paidAmount
        lastPaymentType = paymentType
        if (checkoutDelayMs > 0) delay(checkoutDelayMs)
        if (failFirstCheckoutWithNetwork && checkoutCalls == 1) {
            return AppResult.Failure(AppError.Network("network down"))
        }
        if (failCheckoutWithConflict) {
            failCheckoutWithConflict = false
            if (conflictAfterServerCharged) closeElsewhere(draftId, "CHECKED_OUT")
            return AppResult.Failure(AppError.Conflict)
        }
        if (failCheckoutWithValidation) {
            return AppResult.Failure(AppError.Validation("Draft has no lines"))
        }
        val draft = drafts[draftId] ?: return AppResult.Failure(notFound())
        drafts[draftId] = draft.copy(status = "CHECKED_OUT", version = draft.version + 1)
        return AppResult.Success(
            CheckoutResult(
                saleId = 1L,
                folio = "FOL-0001",
                paymentType = paymentType,
                totalCharged = if (paymentType == SalePaymentType.CARD) draft.totals.listTotal else draft.totals.cashTotal,
                paidAmount = paidAmount,
            ),
        )
    }
}
