package com.posfab.shared.features.sale

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.CustomerPaymentResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleCustomer
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleDraftKind
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleLineInput
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SalePriceType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.features.sale.repository.SaleRepository
import com.posfab.shared.features.sale.ui.CashierSaleViewModel
import com.posfab.shared.features.sale.ui.CreditState
import com.posfab.shared.features.sale.ui.LastAbono
import com.posfab.shared.features.sale.ui.MAX_ACCOUNTS
import com.posfab.shared.features.sale.ui.SaleConfirm
import com.posfab.shared.features.sale.ui.SaleErrorText
import com.posfab.shared.features.sale.ui.SalePhase
import com.posfab.shared.features.sale.ui.SalePrompt
import com.posfab.shared.features.sale.ui.SaleStrings
import com.posfab.shared.features.sale.ui.WeightMode
import com.posfab.shared.features.sale.usecase.SaleUseCases
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    private var clock = 0L

    private fun TestScope.start(repo: FakeSaleRepository): CashierSaleViewModel {
        val vm = CashierSaleViewModel(SaleUseCases(repo), now = { clock }, dispatcher = dispatcher)
        vm.load()
        advanceUntilIdle()
        return vm
    }

    private fun typePrompt(vm: CashierSaleViewModel, text: String, stepMs: Long) {
        text.forEach { char ->
            clock += stepMs
            vm.onPromptInputChange(vm.state.value.promptInput + char)
        }
    }

    private fun typePaid(vm: CashierSaleViewModel, text: String, stepMs: Long) {
        text.forEach { char ->
            clock += stepMs
            vm.onPaidInputChange(vm.state.value.paidInput + char)
        }
    }

    private fun typeQuery(vm: CashierSaleViewModel, text: String, stepMs: Long) {
        text.forEach { char ->
            clock += stepMs
            vm.onQueryChange(vm.state.value.query + char)
        }
    }

    @Test
    fun scan_burst_in_the_weight_prompt_closes_it_and_scans_the_code() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        typePrompt(vm, "0.5", HUMAN_MS)

        clock += PAUSE_MS
        typePrompt(vm, repo.cigarro.barcode!!, SCANNER_MS)
        clock += SCANNER_MS
        vm.confirmPrompt()
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.prompt)
        assertEquals("", state.promptInput)
        assertFalse(state.promptInputRejected)
        assertEquals(listOf(repo.cigarro.id), state.draft!!.lines.map { it.productId })
        assertEquals(1, repo.addLineCalls)
    }

    @Test
    fun scan_burst_of_a_weight_product_reopens_the_prompt_for_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.varios)

        clock += PAUSE_MS
        typePrompt(vm, repo.jamon.barcode!!, SCANNER_MS)
        clock += SCANNER_MS
        vm.confirmPrompt()
        advanceUntilIdle()

        val prompt = assertIs<SalePrompt.Weight>(vm.state.value.prompt)
        assertEquals(repo.jamon, prompt.product)
        assertEquals(WeightMode.KILOS, prompt.mode)
        assertEquals("", vm.state.value.promptInput)
        assertEquals(0, repo.addLineCalls)
    }

    @Test
    fun scan_burst_of_an_unknown_code_shows_not_found() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)

        clock += PAUSE_MS
        typePrompt(vm, "9999999999999", SCANNER_MS)
        clock += SCANNER_MS
        vm.confirmPrompt()
        advanceUntilIdle()

        assertNull(vm.state.value.prompt)
        assertEquals(SaleStrings.notFoundWithCode("9999999999999"), vm.state.value.errorMessage)
        assertEquals(0, repo.addLineCalls)
    }

    @Test
    fun typing_fast_but_short_is_not_a_scan() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        vm.setWeightMode(WeightMode.PESOS)

        clock += PAUSE_MS
        typePrompt(vm, "123", SCANNER_MS)
        assertEquals("123", vm.state.value.promptInput)
        clock += SCANNER_MS
        vm.confirmPrompt()
        advanceUntilIdle()

        assertEquals(SaleLineInput.Amount(123.0), repo.lastLineInput)
        assertEquals(1, repo.addLineCalls)
    }

    @Test
    fun rejected_prompt_input_is_not_added() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)

        typePrompt(vm, "1234567891234", HUMAN_MS)
        assertEquals("12", vm.state.value.promptInput)
        assertTrue(vm.state.value.promptInputRejected)

        clock += SCANNER_MS
        vm.confirmPrompt()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(0, repo.addLineCalls)
        assertEquals(SaleStrings.INPUT_REJECTED, state.errorMessage)
        assertEquals("", state.promptInput)
        assertFalse(state.promptInputRejected)
        assertNotNull(state.prompt)
    }

    @Test
    fun backspace_after_overflow_clears_the_rejection() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)

        typePrompt(vm, "0.3456", HUMAN_MS)
        assertTrue(vm.state.value.promptInputRejected)
        clock += HUMAN_MS
        vm.onPromptInputChange("0.345")
        assertFalse(vm.state.value.promptInputRejected)

        vm.confirmPrompt()
        advanceUntilIdle()

        assertEquals(SaleLineInput.Qty(0.345), repo.lastLineInput)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun scan_burst_in_the_charge_window_restores_the_paid_amount_and_warns() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        vm.startCharge()

        typePaid(vm, "50", HUMAN_MS)
        clock += PAUSE_MS
        typePaid(vm, repo.cigarro.barcode!!, SCANNER_MS)
        clock += SCANNER_MS
        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("50", state.paidInput)
        assertFalse(state.paidInputRejected)
        assertEquals(SaleStrings.FINISH_CHARGE_FIRST, state.notice)
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(0, repo.checkoutCalls)
    }

    @Test
    fun rejected_paid_input_does_not_charge() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        vm.startCharge()

        typePaid(vm, "1234567", HUMAN_MS)
        assertEquals("123456", vm.state.value.paidInput)
        assertTrue(vm.state.value.paidInputRejected)

        clock += SCANNER_MS
        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(0, repo.checkoutCalls)
        assertEquals(SaleStrings.INPUT_REJECTED, state.errorMessage)
        assertEquals("", state.paidInput)
        assertFalse(state.paidInputRejected)
        assertEquals(SalePhase.Charging, state.phase)
    }

    @Test
    fun pay_exact_is_never_treated_as_a_scan() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.kitchenPool.first())
        advanceUntilIdle()
        vm.startCharge()

        clock += PAUSE_MS
        typePaid(vm, "5000000", SCANNER_MS)
        clock += SCANNER_MS
        vm.payExact()
        advanceUntilIdle()

        assertEquals(1, repo.checkoutCalls)
        assertEquals(63.0, repo.lastPaidAmount)
        assertEquals(SalePhase.Done, vm.state.value.phase)
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
        assertEquals(1, repo.updateLineCalls)
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
    fun adding_a_product_clears_the_query_and_the_results() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onQueryChange("caf")
        vm.onSubmit()
        runCurrent()
        vm.addProduct(vm.state.value.searchResults.first())
        advanceUntilIdle()

        assertEquals("", vm.state.value.query)
        assertTrue(vm.state.value.searchResults.isEmpty())
        assertEquals("Cafe de olla", vm.state.value.draft!!.lines.single().productName)
    }

    @Test
    fun scan_burst_after_leftover_text_looks_up_the_code_and_adds() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        typeQuery(vm, "coca", HUMAN_MS)
        clock += PAUSE_MS
        typeQuery(vm, repo.cigarro.barcode!!, SCANNER_MS)
        clock += SCANNER_MS
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals("", vm.state.value.query)
        assertEquals(0, repo.searchCalls)
        assertEquals(repo.cigarro.name, vm.state.value.draft!!.lines.single().productName)
    }

    @Test
    fun digits_typed_slowly_after_text_search_by_name() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        typeQuery(vm, "coca", HUMAN_MS)
        typeQuery(vm, repo.cigarro.barcode!!, HUMAN_MS)
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(1, repo.searchCalls)
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
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
    fun scanning_a_weight_product_opens_the_weight_prompt_in_kilos() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onQueryChange(repo.jamon.barcode!!)
        vm.onSubmit()
        advanceUntilIdle()

        val prompt = assertIs<SalePrompt.Weight>(vm.state.value.prompt)
        assertEquals(repo.jamon, prompt.product)
        assertEquals(WeightMode.KILOS, prompt.mode)
        assertNull(prompt.lineId)
        assertEquals("", vm.state.value.promptInput)
        assertNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
        assertEquals(0, repo.addLineCalls)
    }

    @Test
    fun weight_prompt_in_kilos_adds_the_typed_kilos() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)

        vm.onPromptInputChange("0.35")
        assertEquals(0.35, vm.state.value.promptKilos)
        assertTrue(vm.state.value.canConfirmPrompt)
        vm.confirmPrompt()
        assertNull(vm.state.value.prompt)
        advanceUntilIdle()

        assertEquals(SaleLineInput.Qty(0.35), repo.lastLineInput)
        val line = vm.state.value.draft!!.lines.single()
        assertEquals(0.35, line.qty)
        assertEquals(SalePriceType.WEIGHT, line.priceType)
        assertEquals(line.id, vm.state.value.selectedLineId)
        assertEquals(SaleStrings.added(repo.jamon.name), vm.state.value.notice)
        assertEquals("", vm.state.value.promptInput)
    }

    @Test
    fun weight_prompt_in_pesos_sends_the_amount_not_kilos() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)

        vm.onPromptInputChange("0.5")
        vm.setWeightMode(WeightMode.PESOS)
        assertEquals("", vm.state.value.promptInput)
        assertEquals(WeightMode.PESOS, (vm.state.value.prompt as SalePrompt.Weight).mode)
        vm.onPromptInputChange("20")
        assertEquals(20.0 / 120.0, vm.state.value.promptKilos)
        vm.confirmPrompt()
        advanceUntilIdle()

        assertEquals(SaleLineInput.Amount(20.0), repo.lastLineInput)
        assertEquals(20.0, vm.state.value.cashTotal)
    }

    @Test
    fun quick_weight_buttons_fill_the_field_without_adding() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        vm.setWeightMode(WeightMode.PESOS)

        vm.pickWeight(0.25)
        advanceUntilIdle()

        assertEquals("0.250", vm.state.value.promptInput)
        assertEquals(WeightMode.KILOS, (vm.state.value.prompt as SalePrompt.Weight).mode)
        assertEquals(0, repo.addLineCalls)

        vm.confirmPrompt()
        advanceUntilIdle()
        assertEquals(0.25, vm.state.value.draft!!.lines.single().qty)
    }

    @Test
    fun open_price_product_adds_a_line_with_qty_1_and_the_amount() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.addProduct(repo.varios)
        assertIs<SalePrompt.OpenAmount>(vm.state.value.prompt)
        vm.onPromptInputChange("12.50")
        vm.confirmPrompt()
        advanceUntilIdle()

        assertEquals(SaleLineInput.OpenPrice(12.5), repo.lastLineInput)
        val line = vm.state.value.draft!!.lines.single()
        assertEquals(1.0, line.qty)
        assertEquals(12.5, line.unitPrice)
        assertEquals(SalePriceType.OPEN, line.priceType)
        assertEquals(12.5, vm.state.value.listTotal)
    }

    @Test
    fun quick_open_amounts_fill_the_field() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.pickQuickAmount(5)
        assertEquals("", vm.state.value.promptInput)

        vm.addProduct(repo.varios)
        vm.pickQuickAmount(5)
        advanceUntilIdle()
        assertEquals("5", vm.state.value.promptInput)
        assertEquals(0, repo.addLineCalls)

        vm.confirmPrompt()
        advanceUntilIdle()
        assertEquals(5.0, vm.state.value.draft!!.lines.single().unitPrice)
    }

    @Test
    fun same_open_product_twice_makes_two_lines() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        listOf("5", "12.50").forEach { amount ->
            vm.addProduct(repo.varios)
            vm.onPromptInputChange(amount)
            vm.confirmPrompt()
            advanceUntilIdle()
        }

        val lines = vm.state.value.draft!!.lines
        assertEquals(2, lines.size)
        assertEquals(listOf(5.0, 12.5), lines.map { it.unitPrice })
        assertEquals(2, repo.addLineCalls)
        assertEquals(0, repo.updateLineCalls)
        assertEquals(lines.last().id, vm.state.value.selectedLineId)
    }

    @Test
    fun prompt_input_is_sanitized_per_mode() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onPromptInputChange("12")
        assertEquals("", vm.state.value.promptInput)

        vm.addProduct(repo.jamon)
        vm.onPromptInputChange("0.3456")
        assertEquals("0.345", vm.state.value.promptInput)
        vm.onPromptInputChange("abc")
        assertEquals("", vm.state.value.promptInput)
        vm.onPromptInputChange("123.5")
        assertEquals("12.5", vm.state.value.promptInput)

        vm.setWeightMode(WeightMode.PESOS)
        vm.onPromptInputChange("20.555")
        assertEquals("20.55", vm.state.value.promptInput)

        vm.dismissPrompt()
        vm.addProduct(repo.varios)
        vm.onPromptInputChange("1234567")
        assertEquals("12345", vm.state.value.promptInput)

        vm.dismissPrompt()
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.startCharge()
        vm.onPaidInputChange("1234567")
        assertEquals("123456", vm.state.value.paidInput)
    }

    @Test
    fun invalid_prompt_input_does_not_confirm() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)

        listOf("", ".", "0", "0.000").forEach { input ->
            vm.onPromptInputChange(input)
            assertFalse(vm.state.value.canConfirmPrompt)
            vm.confirmPrompt()
        }
        advanceUntilIdle()

        assertNotNull(vm.state.value.prompt)
        assertEquals(0, repo.addLineCalls)
    }

    @Test
    fun dismissing_the_prompt_adds_nothing() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        vm.onPromptInputChange("0.5")

        vm.dismissPrompt()
        vm.confirmPrompt()
        advanceUntilIdle()

        assertNull(vm.state.value.prompt)
        assertEquals("", vm.state.value.promptInput)
        assertEquals(0, repo.addLineCalls)
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
    }

    @Test
    fun weight_product_without_price_shows_no_price() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.onQueryChange(repo.arrozSuelto.barcode!!)
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(SaleStrings.NO_PRICE, vm.state.value.errorMessage)
        assertNull(vm.state.value.prompt)

        vm.addProduct(repo.arrozSuelto)
        assertEquals(SaleStrings.NO_PRICE, vm.state.value.errorMessage)
        assertNull(vm.state.value.prompt)
        assertEquals(0, repo.addLineCalls)
    }

    @Test
    fun prompt_is_blocked_while_charging() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.startCharge()

        vm.addProduct(repo.jamon)
        vm.addProduct(repo.varios)
        advanceUntilIdle()

        assertNull(vm.state.value.prompt)
        assertEquals(SaleStrings.FINISH_CHARGE_FIRST, vm.state.value.notice)
        assertEquals(1, repo.addLineCalls)
        assertEquals(SalePhase.Charging, vm.state.value.phase)
    }

    @Test
    fun open_prompt_blocks_account_switch_channel_and_clear() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        val newDraftsBefore = repo.newDraftCalls

        vm.addProduct(repo.jamon)
        assertFalse(vm.state.value.canSetChannel)
        assertFalse(vm.state.value.canOpenAccount)
        assertFalse(vm.state.value.canCharge)

        vm.selectAccount(2L)
        vm.setChannel(SaleChannel.ONSITE)
        vm.requestClear()
        vm.openAccount()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1L, state.activeDraftId)
        assertEquals(0, repo.setChannelCalls)
        assertNull(state.confirm)
        assertEquals(newDraftsBefore, repo.newDraftCalls)
        assertEquals(0, repo.cancelCalls)
        assertNotNull(state.prompt)

        vm.dismissPrompt()
        assertTrue(vm.state.value.canSetChannel)
        vm.selectAccount(2L)
        assertEquals(2L, vm.state.value.activeDraftId)
    }

    @Test
    fun changing_a_weight_line_reopens_the_prompt_prefilled_and_updates_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        vm.onPromptInputChange("0.35")
        vm.confirmPrompt()
        advanceUntilIdle()
        val lineId = vm.state.value.draft!!.lines.single().id
        vm.selectLine(lineId)

        vm.changeSelectedLine()

        val prompt = assertIs<SalePrompt.Weight>(vm.state.value.prompt)
        assertEquals(lineId, prompt.lineId)
        assertEquals(WeightMode.KILOS, prompt.mode)
        assertEquals(repo.jamon.id, prompt.product.id)
        assertEquals("0.350", vm.state.value.promptInput)

        vm.onPromptInputChange("0.5")
        vm.confirmPrompt()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleLineInput.Qty(0.5), repo.lastLineInput)
        assertEquals(1, repo.addLineCalls)
        assertEquals(1, repo.updateLineCalls)
        assertEquals(0.5, state.draft!!.lines.single().qty)
        assertEquals(lineId, state.selectedLineId)
        assertEquals("Cambiado: Jamon", state.notice)
        assertNull(state.prompt)
    }

    @Test
    fun changing_an_open_line_updates_its_price() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.varios)
        vm.onPromptInputChange("5")
        vm.confirmPrompt()
        advanceUntilIdle()
        val lineId = vm.state.value.draft!!.lines.single().id

        vm.changeSelectedLine()
        val prompt = assertIs<SalePrompt.OpenAmount>(vm.state.value.prompt)
        assertEquals(lineId, prompt.lineId)
        assertEquals("5.00", vm.state.value.promptInput)

        vm.onPromptInputChange("8")
        vm.confirmPrompt()
        advanceUntilIdle()

        assertEquals(SaleLineInput.OpenPrice(8.0), repo.lastLineInput)
        val line = vm.state.value.draft!!.lines.single()
        assertEquals(8.0, line.unitPrice)
        assertEquals(1.0, line.qty)
        assertEquals(SaleStrings.changed(repo.varios.name), vm.state.value.notice)
    }

    @Test
    fun changing_a_pesos_line_reopens_in_pesos_with_the_cash_amount() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        vm.setWeightMode(WeightMode.PESOS)
        vm.onPromptInputChange("20")
        vm.confirmPrompt()
        advanceUntilIdle()
        val line = vm.state.value.draft!!.lines.single()
        assertEquals(0.166667, line.qty)

        vm.changeSelectedLine()

        val prompt = assertIs<SalePrompt.Weight>(vm.state.value.prompt)
        assertEquals(line.id, prompt.lineId)
        assertEquals(WeightMode.PESOS, prompt.mode)
        assertEquals("20.00", vm.state.value.promptInput)

        vm.confirmPrompt()
        advanceUntilIdle()

        assertEquals(SaleLineInput.Amount(20.0), repo.lastLineInput)
        assertEquals(1, repo.updateLineCalls)
        assertEquals(20.0, vm.state.value.cashTotal)
    }

    @Test
    fun qty_buttons_ignore_weight_and_open_lines() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        vm.onPromptInputChange("0.35")
        vm.confirmPrompt()
        advanceUntilIdle()
        vm.addProduct(repo.varios)
        vm.onPromptInputChange("5")
        vm.confirmPrompt()
        advanceUntilIdle()
        val (weightLine, openLine) = vm.state.value.draft!!.lines

        listOf(weightLine, openLine).forEach { line ->
            vm.selectLine(line.id)
            vm.incrementQty()
            vm.decrementQty()
            vm.onQtyInputChange("3")
            vm.applyQty()
            advanceUntilIdle()
        }

        assertEquals(0, repo.updateLineCalls)
        val lines = vm.state.value.draft!!.lines
        assertEquals(0.35, lines.first { it.id == weightLine.id }.qty)
        assertEquals(1.0, lines.first { it.id == openLine.id }.qty)
    }

    @Test
    fun prompt_does_not_open_while_the_clear_dialog_is_up() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.requestClear()
        assertEquals(SaleConfirm.ClearCart, vm.state.value.confirm)

        vm.addProduct(repo.jamon)
        vm.onQueryChange(repo.jamon.barcode!!)
        vm.onSubmit()
        advanceUntilIdle()

        assertNull(vm.state.value.prompt)
        assertNull(vm.state.value.errorMessage)
        assertEquals(SaleConfirm.ClearCart, vm.state.value.confirm)
        assertEquals(1, repo.addLineCalls)
    }

    @Test
    fun scanning_a_weight_product_from_done_opens_the_prompt_on_the_next_sale() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        val firstDraftId = vm.state.value.draft!!.id
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()
        assertEquals(SalePhase.Done, vm.state.value.phase)

        vm.onQueryChange(repo.jamon.barcode!!)
        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertNull(state.lastResult)
        assertNotEquals(firstDraftId, state.activeDraftId)
        assertIs<SalePrompt.Weight>(state.prompt)

        vm.onPromptInputChange("1")
        vm.confirmPrompt()
        advanceUntilIdle()
        assertEquals(1.0, vm.state.value.draft!!.lines.single().qty)
        assertNotEquals(firstDraftId, vm.state.value.draft!!.id)
    }

    @Test
    fun varios_product_loads_with_the_board_and_is_absent_when_missing() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        assertEquals(repo.varios, vm.state.value.variosProduct)

        val missing = start(FakeSaleRepository().apply { variosMissing = true })
        assertNull(missing.state.value.variosProduct)
        assertNull(missing.state.value.errorMessage)
        assertEquals(SalePhase.Selling, missing.state.value.phase)
    }

    @Test
    fun prompt_confirm_on_a_closed_account_removes_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        repo.closeElsewhere(1L, "CANCELLED")

        vm.onPromptInputChange("0.5")
        vm.confirmPrompt()
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.accountById(1L))
        assertNotEquals(1L, state.activeDraftId)
        assertTrue(state.draft!!.lines.isEmpty())
        assertEquals(SaleStrings.ACCOUNT_CLOSED, state.notice)
        assertNull(state.prompt)
    }

    @Test
    fun prompt_add_retries_once_after_conflict() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.jamon)
        repo.forceConflictOnNextAdd = true

        vm.onPromptInputChange("0.5")
        vm.confirmPrompt()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, repo.addLineCalls)
        assertEquals(0.5, state.draft!!.lines.single().qty)
        assertEquals(repo.drafts.getValue(1L).version, state.draft!!.version)
        assertEquals(SaleStrings.added(repo.jamon.name), state.notice)
    }

    @Test
    fun piece_products_still_merge_quantities() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        vm.addProduct(repo.cigarro)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()

        assertNull(vm.state.value.prompt)
        val line = vm.state.value.draft!!.lines.single()
        assertEquals(2.0, line.qty)
        assertEquals(SaleLineInput.Qty(2.0), repo.lastLineInput)
        assertEquals(1, repo.addLineCalls)
        assertEquals(1, repo.updateLineCalls)

        vm.selectLine(line.id)
        vm.changeSelectedLine()
        assertNull(vm.state.value.prompt)
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
        assertEquals(0, repo.updateLineCalls)
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

    private fun TestScope.startFiar(repo: FakeSaleRepository): CashierSaleViewModel {
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        vm.startCharge()
        vm.selectPaymentType(SalePaymentType.CREDIT)
        return vm
    }

    private fun TestScope.pickJuan(vm: CashierSaleViewModel, repo: FakeSaleRepository) {
        vm.onCustomerQueryChange("ju")
        advanceUntilIdle()
        vm.selectCustomer(vm.state.value.credit.pickerResults.single { it.id == repo.juan.id })
    }

    @Test
    fun selecting_fiar_opens_the_customer_picker() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(SalePaymentType.CREDIT, state.paymentType)
        assertTrue(state.credit.showsPicker)
        assertNull(state.credit.customer)
        assertFalse(state.canConfirmCharge)
        assertEquals(42.0, state.amountDue)
    }

    @Test
    fun typing_a_customer_name_searches_after_the_debounce() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        vm.onCustomerQueryChange("j")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(repo.customerSearchQueries.isEmpty())

        vm.onCustomerQueryChange("ju")
        advanceTimeBy(200)
        runCurrent()
        assertTrue(repo.customerSearchQueries.isEmpty())

        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("ju"), repo.customerSearchQueries)
        assertEquals(listOf(repo.juan), vm.state.value.credit.pickerResults)
        assertNull(vm.state.value.credit.customer)
    }

    @Test
    fun search_matches_the_nickname() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        vm.onCustomerQueryChange("WERO")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("WERO"), repo.customerSearchQueries)
        assertEquals(listOf(repo.juan), vm.state.value.credit.pickerResults)
        assertEquals("Juan Pérez (el Wero)", vm.state.value.credit.pickerResults.single().displayName)
    }

    @Test
    fun selected_customer_shows_name_and_nickname() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        pickJuan(vm, repo)

        val customer = vm.state.value.credit.customer!!
        assertEquals("Juan Pérez (el Wero)", customer.displayName)
        assertEquals("María López", repo.maria.displayName)
        assertEquals(
            "Juan Pérez (el Wero) · debe $350.00",
            SaleStrings.owes(customer.displayName, customer.balance),
        )
    }

    @Test
    fun digits_and_enter_with_a_single_match_select_the_customer() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        vm.onCustomerQueryChange(JUAN_CODE)
        vm.submitCustomerQuery()
        advanceUntilIdle()

        assertEquals(repo.juan, vm.state.value.credit.customer)
        assertEquals(listOf(JUAN_CODE), repo.customerSearchQueries)
        assertTrue(vm.state.value.canConfirmCharge)
    }

    @Test
    fun digits_and_enter_with_no_match_shows_not_found() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        vm.onCustomerQueryChange("99999999")
        vm.submitCustomerQuery()
        advanceUntilIdle()

        val credit = vm.state.value.credit
        assertNull(credit.customer)
        assertTrue(credit.pickerResults.isEmpty())
        assertTrue(credit.showsNoMatches)
        assertFalse(vm.state.value.canConfirmCharge)
    }

    @Test
    fun selecting_a_customer_shows_the_balance_and_enables_fiar() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        pickJuan(vm, repo)

        val state = vm.state.value
        assertEquals(repo.juan, state.credit.customer)
        assertEquals(350.0, state.credit.customer!!.balance)
        assertFalse(state.credit.showsPicker)
        assertTrue(state.credit.showsAbonoRow)
        assertTrue(state.canConfirmCharge)
        assertEquals("Juan Pérez · debe $350.00", SaleStrings.owes(repo.juan.name, repo.juan.balance))
        assertEquals("Fiar $42.00", SaleStrings.confirmLabel(state.paymentType, state.amountDue))
    }

    @Test
    fun fiar_sends_credit_with_customer_id_and_list_total() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(SalePaymentType.CREDIT, repo.lastPaymentType)
        assertEquals(repo.juan.id, repo.lastCheckoutCustomerId)
        assertEquals(42.0, repo.lastPaidAmount)
        assertEquals(1, repo.checkoutCalls)
    }

    @Test
    fun done_after_fiar_shows_fiado_with_the_fetched_balance() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        repo.customers[repo.juan.id] = repo.juan.copy(balance = 500.0)

        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Done, state.phase)
        val result = state.lastResult!!
        assertEquals(SalePaymentType.CREDIT, result.paymentType)
        assertEquals("Juan Pérez (el Wero)", result.customerName)
        assertEquals(42.0, result.totalCharged)
        assertEquals(542.0, result.customerBalanceAfter)
        assertNull(result.change)
        assertEquals(CreditState(), state.credit)
        assertEquals(SaleStrings.CREDIT_DONE, SaleStrings.paymentTypeLabel(SalePaymentType.CREDIT))
    }

    @Test
    fun done_after_fiar_uses_the_display_name() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.confirmCharge()
        advanceUntilIdle()

        val result = vm.state.value.lastResult!!
        assertEquals(repo.juan.displayName, result.customerName)
        assertEquals("Fiado a Juan Pérez (el Wero)", SaleStrings.creditDoneTitle(result.customerName!!))
    }

    @Test
    fun done_after_fiar_falls_back_to_old_balance_plus_total_when_the_fetch_fails() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failCustomerFetchWithNetwork = true }
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        repo.customers[repo.juan.id] = repo.juan.copy(balance = 500.0)

        vm.confirmCharge()
        advanceUntilIdle()

        val result = vm.state.value.lastResult!!
        assertEquals(SalePhase.Done, vm.state.value.phase)
        assertEquals("Juan Pérez (el Wero)", result.customerName)
        assertEquals(392.0, result.customerBalanceAfter)
    }

    @Test
    fun escape_in_the_picker_returns_to_cash() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        assertTrue(vm.dismissCreditStep())

        assertEquals(SalePhase.Charging, vm.state.value.phase)
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
        assertFalse(vm.dismissCreditStep())
    }

    @Test
    fun escape_closes_the_new_customer_form_first() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        vm.startNewCustomer()
        assertNotNull(vm.state.value.credit.newCustomer)

        assertTrue(vm.dismissCreditStep())
        assertNull(vm.state.value.credit.newCustomer)
        assertEquals(SalePaymentType.CREDIT, vm.state.value.paymentType)

        assertTrue(vm.dismissCreditStep())
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
    }

    @Test
    fun new_customer_sends_the_nickname() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        vm.onCustomerQueryChange("Pedro")
        vm.startNewCustomer()
        assertEquals("", vm.state.value.credit.newCustomer!!.nickname)

        vm.onNewCustomerNickname("  el Güero  ")
        assertEquals("  el Güero  ", vm.state.value.credit.newCustomer!!.nickname)
        vm.saveNewCustomer()
        advanceUntilIdle()

        assertEquals("el Güero", repo.lastCreateNickname)
        assertEquals("Pedro (el Güero)", vm.state.value.credit.customer!!.displayName)

        vm.changeCustomer()
        vm.onCustomerQueryChange("Ana")
        vm.startNewCustomer()
        vm.onNewCustomerNickname("   ")
        vm.saveNewCustomer()
        advanceUntilIdle()

        assertEquals(2, repo.createCustomerCalls)
        assertNull(repo.lastCreateNickname)
        assertEquals("Ana", vm.state.value.credit.customer!!.displayName)
    }

    @Test
    fun new_customer_is_saved_and_selected() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        vm.onCustomerQueryChange("Pedro")
        vm.startNewCustomer()
        assertEquals("Pedro", vm.state.value.credit.newCustomer!!.name)
        assertFalse(vm.state.value.canConfirmCharge)

        vm.onNewCustomerPhone("5551239876")
        vm.saveNewCustomer()
        advanceUntilIdle()

        val credit = vm.state.value.credit
        assertNull(credit.newCustomer)
        assertEquals("Pedro", credit.customer!!.name)
        assertEquals("5551239876", credit.customer!!.phone)
        assertFalse(credit.showsAbonoRow)
        assertEquals(1, repo.createCustomerCalls)
        assertTrue(repo.customerSearchQueries.isEmpty())
        assertTrue(vm.state.value.canConfirmCharge)
    }

    @Test
    fun new_customer_phone_keeps_only_ten_digits() = runTest(dispatcher) {
        val vm = startFiar(FakeSaleRepository())
        vm.onCustomerQueryChange("Pedro")
        vm.startNewCustomer()

        vm.onNewCustomerPhone("55-12a34 5678 90")

        assertEquals("5512345678", vm.state.value.credit.newCustomer!!.phone)
    }

    @Test
    fun short_new_customer_phone_is_not_sent() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        vm.onCustomerQueryChange("Pedro")
        vm.startNewCustomer()
        vm.onNewCustomerPhone("5559876")

        vm.saveNewCustomer()
        advanceUntilIdle()

        assertEquals(SaleStrings.CUSTOMER_PHONE_INVALID, vm.state.value.errorMessage)
        assertEquals(0, repo.createCustomerCalls)
        assertNotNull(vm.state.value.credit.newCustomer)
    }

    @Test
    fun blank_new_customer_name_is_not_sent() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        vm.onCustomerQueryChange(JUAN_CODE)
        vm.startNewCustomer()
        assertEquals("", vm.state.value.credit.newCustomer!!.name)

        vm.saveNewCustomer()
        advanceUntilIdle()

        assertEquals(SaleStrings.CUSTOMER_NAME_REQUIRED, vm.state.value.errorMessage)
        assertEquals(0, repo.createCustomerCalls)
    }

    @Test
    fun conflict_on_create_shows_the_save_failed_message() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failNextCreateWithConflict = true }
        val vm = startFiar(repo)
        vm.startNewCustomer()
        vm.onNewCustomerName("Juan Pérez")

        vm.saveNewCustomer()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.CUSTOMER_SAVE_FAILED, state.errorMessage)
        assertNotNull(state.credit.newCustomer)
        assertFalse(state.credit.newCustomer!!.isSaving)
        assertNull(state.credit.customer)
    }

    @Test
    fun abono_todo_fills_the_balance() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.payAllDebt()

        assertEquals("350.00", vm.state.value.credit.abonoInput)
        assertEquals(350.0, vm.state.value.credit.abonoAmount)
        assertEquals(0, repo.paymentCalls)
    }

    @Test
    fun abono_success_updates_the_balance_and_clears_the_field() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.onAbonoInputChange("100")
        vm.confirmAbono()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, repo.paymentCalls)
        assertEquals(100.0, repo.lastPaymentAmount)
        assertEquals(250.0, state.credit.customer!!.balance)
        assertEquals("", state.credit.abonoInput)
        assertFalse(state.credit.isAbonoLocked)
        assertEquals(LastAbono(100.0, "Juan Pérez (el Wero)", 250.0), state.credit.lastAbono)
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(SalePaymentType.CREDIT, state.paymentType)
        assertEquals(
            "Abono de $100.00 registrado · Juan Pérez debe ahora $250.00",
            SaleStrings.abonoRegistered(100.0, "Juan Pérez", 250.0),
        )
        assertEquals(
            "Abono de $250.00 registrado · Juan Pérez ya no debe nada",
            SaleStrings.abonoRegistered(250.0, "Juan Pérez", 0.0),
        )
    }

    @Test
    fun abono_above_the_balance_shows_the_message() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.onAbonoInputChange("400")
        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(SaleStrings.ABONO_EXCEEDS_DEBT, vm.state.value.errorMessage)
        assertEquals(0, repo.paymentCalls)
        assertEquals(350.0, vm.state.value.credit.customer!!.balance)
    }

    @Test
    fun abono_network_failure_keeps_the_key_and_retry_sends_the_same_key() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstPaymentWithNetwork = true }
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")

        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(SaleStrings.NO_CONNECTION, vm.state.value.errorMessage)
        assertTrue(vm.state.value.credit.isAbonoLocked)
        vm.onAbonoInputChange("50")
        vm.payAllDebt()
        vm.changeCustomer()
        assertEquals("100", vm.state.value.credit.abonoInput)
        assertEquals(repo.juan.id, vm.state.value.credit.customer!!.id)

        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(2, repo.paymentCalls)
        assertEquals(1, repo.paymentKeys.distinct().size)
        assertEquals(250.0, vm.state.value.credit.customer!!.balance)
        assertFalse(vm.state.value.credit.isAbonoLocked)
    }

    @Test
    fun abono_with_the_cash_closed_points_to_caja() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply {
            failPaymentWithValidation = "Open cash session is required for terminalId=3"
        }
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")

        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(SaleStrings.ABONO_CASH_CLOSED, vm.state.value.errorMessage)
        assertFalse(vm.state.value.credit.isAbonoLocked)
        assertEquals(350.0, vm.state.value.credit.customer!!.balance)
    }

    @Test
    fun rejected_abono_input_is_not_sent() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.onAbonoInputChange(repo.cigarro.barcode!!)
        assertTrue(vm.state.value.credit.abonoInputRejected)
        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(SaleStrings.INPUT_REJECTED, vm.state.value.errorMessage)
        assertEquals("", vm.state.value.credit.abonoInput)
        assertFalse(vm.state.value.credit.abonoInputRejected)
        assertEquals(0, repo.paymentCalls)
    }

    @Test
    fun abonar_overlay_opens_from_the_cart_with_an_empty_cart() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        assertTrue(vm.state.value.draft!!.lines.isEmpty())
        assertTrue(vm.state.value.canOpenAbono)

        vm.openAbono()
        assertTrue(vm.state.value.abonoOverlayOpen)
        assertTrue(vm.state.value.credit.showsPicker)

        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")
        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(250.0, vm.state.value.credit.customer!!.balance)
        assertEquals(LastAbono(100.0, "Juan Pérez (el Wero)", 250.0), vm.state.value.credit.lastAbono)
        assertEquals(SalePhase.Selling, vm.state.value.phase)

        vm.closeAbono()
        assertFalse(vm.state.value.abonoOverlayOpen)
        assertEquals(CreditState(), vm.state.value.credit)
    }

    @Test
    fun abonar_overlay_blocks_charging_and_channel_changes() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        vm.openAbono()
        val state = vm.state.value
        assertFalse(state.canCharge)
        assertFalse(state.canSetChannel)
        assertFalse(state.canOpenAccount)
        assertFalse(state.canOpenAbono)

        vm.startCharge()
        vm.setChannel(SaleChannel.ONSITE)
        vm.openAccount()
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        assertEquals(SalePhase.Selling, vm.state.value.phase)
        assertEquals(0, repo.setChannelCalls)
        assertEquals(1, repo.addLineCalls + repo.updateLineCalls)
        assertEquals(1, vm.state.value.accounts.size)

        assertTrue(vm.dismissCreditStep())
        assertFalse(vm.state.value.abonoOverlayOpen)
        assertTrue(vm.state.value.canCharge)
    }

    @Test
    fun changing_the_payment_type_resets_the_customer() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        vm.onAbonoInputChange("20")

        vm.selectPaymentType(SalePaymentType.CARD)
        assertEquals(CreditState(), vm.state.value.credit)

        vm.selectPaymentType(SalePaymentType.CREDIT)
        assertTrue(vm.state.value.credit.showsPicker)
        assertNull(vm.state.value.credit.customer)
        assertFalse(vm.state.value.canConfirmCharge)
    }

    @Test
    fun concurrent_confirm_abono_sends_one_payment() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { paymentDelayMs = 50 }
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")

        vm.confirmAbono()
        vm.confirmAbono()
        runCurrent()
        assertTrue(vm.state.value.credit.isAbonoInFlight)
        assertFalse(vm.state.value.canConfirmCharge)
        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(1, repo.paymentCalls)
        assertEquals(250.0, vm.state.value.credit.customer!!.balance)
    }

    @Test
    fun scanned_digits_are_not_saved_as_a_customer_name() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        vm.startNewCustomer()
        vm.onNewCustomerName(repo.cigarro.barcode!!)

        vm.saveNewCustomer()
        advanceUntilIdle()

        assertEquals(SaleStrings.CUSTOMER_NAME_REQUIRED, vm.state.value.errorMessage)
        assertEquals(0, repo.createCustomerCalls)
        assertNotNull(vm.state.value.credit.newCustomer)
    }

    @Test
    fun enter_with_a_single_name_match_selects_the_customer() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)

        vm.onCustomerQueryChange("pérez")
        vm.submitCustomerQuery()
        advanceUntilIdle()

        assertEquals(repo.juan, vm.state.value.credit.customer)
    }

    @Test
    fun picker_hint_shows_until_two_letters() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        assertTrue(vm.state.value.credit.showsPickerHint)

        vm.onCustomerQueryChange("j")
        assertTrue(vm.state.value.credit.showsPickerHint)

        vm.onCustomerQueryChange("ju")
        assertFalse(vm.state.value.credit.showsPickerHint)
    }

    @Test
    fun abono_lock_blocks_payment_type_back_and_fiar() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstPaymentWithNetwork = true }
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")
        vm.confirmAbono()
        advanceUntilIdle()
        assertTrue(vm.state.value.credit.isAbonoLocked)
        assertFalse(vm.state.value.canConfirmCharge)

        vm.selectPaymentType(SalePaymentType.CASH)
        vm.cancelCharge()
        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(SalePaymentType.CREDIT, state.paymentType)
        assertEquals(repo.juan.id, state.credit.customer!!.id)
        assertTrue(state.credit.isAbonoLocked)
        assertEquals(0, repo.checkoutCalls)
    }

    @Test
    fun abono_lock_keeps_the_abonar_window_open() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstPaymentWithNetwork = true }
        val vm = start(repo)
        vm.openAbono()
        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")
        vm.confirmAbono()
        advanceUntilIdle()

        vm.closeAbono()
        assertTrue(vm.dismissCreditStep())

        assertTrue(vm.state.value.abonoOverlayOpen)
        assertTrue(vm.state.value.credit.isAbonoLocked)

        vm.confirmAbono()
        advanceUntilIdle()
        vm.closeAbono()
        assertFalse(vm.state.value.abonoOverlayOpen)
        assertEquals(1, repo.paymentKeys.distinct().size)
    }

    @Test
    fun load_during_an_abono_in_flight_does_not_reset_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { paymentDelayMs = 50 }
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")
        vm.confirmAbono()
        runCurrent()

        vm.load()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(250.0, state.credit.customer!!.balance)
        assertEquals(LastAbono(100.0, "Juan Pérez (el Wero)", 250.0), state.credit.lastAbono)
        assertEquals(1, repo.openDraftsCalls)
    }

    @Test
    fun abono_key_is_cleared_after_a_non_network_failure() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failPaymentWithValidation = "Customer is inactive" }
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        vm.onAbonoInputChange("100")
        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(SaleStrings.CUSTOMER_INACTIVE, vm.state.value.errorMessage)
        assertNull(vm.state.value.credit.abonoKey)
        assertEquals("100", vm.state.value.credit.abonoInput)

        repo.failPaymentWithValidation = null
        vm.confirmAbono()
        advanceUntilIdle()

        assertEquals(2, repo.paymentKeys.distinct().size)
    }

    @Test
    fun escape_clears_the_customer_query_before_leaving_fiar() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        vm.onCustomerQueryChange("ju")
        advanceUntilIdle()

        assertTrue(vm.dismissCreditStep())
        assertEquals("", vm.state.value.credit.pickerQuery)
        assertTrue(vm.state.value.credit.pickerResults.isEmpty())
        assertEquals(SalePaymentType.CREDIT, vm.state.value.paymentType)

        assertTrue(vm.dismissCreditStep())
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
    }

    @Test
    fun keyboard_enter_never_fiar() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = startFiar(repo)
        pickJuan(vm, repo)
        assertTrue(vm.state.value.canConfirmCharge)

        vm.confirmChargeFromKeyboard()
        advanceUntilIdle()
        assertEquals(0, repo.checkoutCalls)
        assertEquals(SalePhase.Charging, vm.state.value.phase)

        vm.selectPaymentType(SalePaymentType.CARD)
        vm.confirmChargeFromKeyboard()
        advanceUntilIdle()
        assertEquals(1, repo.checkoutCalls)
        assertEquals(SalePaymentType.CARD, repo.lastPaymentType)
    }

    @Test
    fun abono_window_lists_only_debtors() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.openAbono()

        vm.onCustomerQueryChange("mar")
        advanceUntilIdle()
        assertTrue(vm.state.value.credit.pickerResults.isEmpty())
        assertTrue(vm.state.value.credit.showsNoMatches)

        vm.onCustomerQueryChange("ju")
        advanceUntilIdle()
        assertEquals(listOf(repo.juan), vm.state.value.credit.pickerResults)
    }

    @Test
    fun late_search_result_for_an_old_query_is_dropped() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { customerSearchDelayMs = 100 }
        val vm = startFiar(repo)

        vm.onCustomerQueryChange("ju")
        advanceTimeBy(260)
        runCurrent()
        assertEquals(listOf("ju"), repo.customerSearchQueries)

        vm.onCustomerQueryChange("mar")
        advanceTimeBy(100)
        runCurrent()
        assertTrue(vm.state.value.credit.pickerResults.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(repo.maria), vm.state.value.credit.pickerResults)
        assertEquals(listOf("ju", "mar"), repo.customerSearchQueries)
    }

    @Test
    fun credit_checkout_network_failure_keeps_the_key_and_the_customer() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failFirstCheckoutWithNetwork = true }
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.confirmCharge()
        advanceUntilIdle()
        assertTrue(vm.state.value.isPaidLocked)

        vm.changeCustomer()
        assertEquals(repo.juan.id, vm.state.value.credit.customer!!.id)

        vm.confirmCharge()
        advanceUntilIdle()
        assertEquals(1, repo.checkoutKeys.distinct().size)
        assertEquals(repo.juan.id, repo.lastCheckoutCustomerId)
        assertEquals(SalePhase.Done, vm.state.value.phase)
    }

    @Test
    fun inactive_customer_on_checkout_allows_choosing_another() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply {
            failCheckoutWithValidation = true
            checkoutValidationMessage = "Customer is inactive"
        }
        val vm = startFiar(repo)
        pickJuan(vm, repo)

        vm.confirmCharge()
        advanceUntilIdle()

        assertEquals(SaleStrings.CUSTOMER_INACTIVE, vm.state.value.errorMessage)
        assertNull(vm.state.value.checkoutIdempotencyKey)
        vm.changeCustomer()
        assertNull(vm.state.value.credit.customer)
        assertTrue(vm.state.value.credit.showsPicker)
    }

    private fun deliveryDraft(
        id: Long,
        paysWith: Double? = 100.0,
        name: String = "Lupe Hernández (Doña Lupe)",
    ) = SaleDraft(
        id = id,
        status = "OPEN",
        version = 3,
        lines = listOf(
            SaleLine(
                id = id * 100,
                productId = 4L,
                productName = "Comida",
                qty = 1.0,
                unitId = 1L,
                unitPrice = 49.35,
                cashUnitPrice = 47.0,
                lineTotal = 49.35,
            ),
        ),
        totals = SaleTotals(cashTotal = 47.0, listTotal = 49.35, cashDiscount = 2.35),
        kind = SaleDraftKind.DELIVERY,
        customerName = name,
        deliveryAddress = "Av. Juárez 5",
        paysWith = paysWith,
    )

    @Test
    fun deliveries_are_listed_after_sale_accounts_and_a_sale_account_starts_active() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L), deliverySeed = listOf(deliveryDraft(51L), deliveryDraft(50L)))
        val vm = start(repo)

        val state = vm.state.value
        assertEquals(listOf(1L, 2L, 50L, 51L), state.accounts.map { it.id })
        assertEquals(listOf(1L, 2L), state.saleAccounts.map { it.id })
        assertEquals(listOf(50L, 51L), state.deliveryAccounts.map { it.id })
        assertEquals(1L, state.activeDraftId)
        assertFalse(state.isDeliveryAccount)
    }

    @Test
    fun only_deliveries_open_creates_a_sale_account_first() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = emptyList(), deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)

        val state = vm.state.value
        assertEquals(1, repo.newDraftCalls)
        assertEquals(1, state.saleAccounts.size)
        assertEquals(state.saleAccounts.single().id, state.activeDraftId)
        assertEquals(listOf(50L), state.deliveryAccounts.map { it.id })
    }

    @Test
    fun an_active_delivery_is_read_only() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        vm.selectAccount(50L)
        val state = vm.state.value
        assertTrue(state.isDeliveryAccount)
        assertFalse(state.canSetChannel)
        assertFalse(state.canOpenAbono)
        assertFalse(state.canCloseEmptyAccount)
        assertTrue(state.canCharge)
        assertEquals(listOf(SalePaymentType.CASH), state.paymentOptions)

        val attempts: List<() -> Unit> = listOf(
            { vm.addProduct(repo.cigarro) },
            { vm.addProduct(repo.jamon) },
            { vm.addProduct(repo.varios) },
            {
                vm.onQueryChange(repo.cigarro.barcode!!)
                vm.onSubmit()
            },
            { vm.selectLine(5000L) },
            { vm.incrementQty() },
            { vm.decrementQty() },
            { vm.applyQty() },
            { vm.changeSelectedLine() },
            { vm.removeSelectedLine() },
            { vm.setChannel(SaleChannel.ONSITE) },
            { vm.requestClear() },
            { vm.confirmClear() },
            { vm.openAbono() },
        )
        attempts.forEach { attempt ->
            vm.selectAccount(1L)
            vm.selectAccount(50L)
            assertNull(vm.state.value.notice)
            attempt()
            advanceUntilIdle()
            val after = vm.state.value
            assertEquals(SaleStrings.DELIVERY_READ_ONLY, after.notice)
            assertNull(after.prompt)
            assertNull(after.confirm)
            assertNull(after.selectedLineId)
            assertFalse(after.abonoOverlayOpen)
            assertEquals(50L, after.activeDraftId)
        }
        assertEquals(0, repo.addLineCalls)
        assertEquals(0, repo.updateLineCalls)
        assertEquals(0, repo.setChannelCalls)
        assertEquals(0, repo.cancelCalls)
        assertEquals(0, repo.barcodeLookups)
        assertEquals("", vm.state.value.query)
        assertEquals(deliveryDraft(50L), repo.drafts[50L])
    }

    @Test
    fun new_account_is_allowed_with_a_delivery_active() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        assertFalse(vm.state.value.canOpenAccount)

        vm.selectAccount(50L)
        assertTrue(vm.state.value.canOpenAccount)
        vm.openAccount()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(1L, 2L, 50L), state.accounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertFalse(state.isDeliveryAccount)
    }

    @Test
    fun account_limit_counts_only_sale_accounts() = runTest(dispatcher) {
        val full = FakeSaleRepository(
            openDraftsSeed = (1L..MAX_ACCOUNTS.toLong()).toList(),
            deliverySeed = listOf(deliveryDraft(50L)),
        )
        val fullVm = start(full)
        fullVm.selectAccount(50L)
        assertFalse(fullVm.state.value.canOpenAccount)

        val repo = FakeSaleRepository(
            openDraftsSeed = (1L until MAX_ACCOUNTS.toLong()).toList(),
            deliverySeed = listOf(deliveryDraft(50L), deliveryDraft(51L), deliveryDraft(52L)),
        )
        val vm = start(repo)
        vm.selectAccount(50L)
        assertTrue(vm.state.value.accounts.size > MAX_ACCOUNTS)
        assertTrue(vm.state.value.canOpenAccount)
    }

    @Test
    fun charging_a_delivery_is_cash_only_with_the_pays_with_prefilled() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        vm.selectAccount(50L)

        vm.startCharge()
        assertEquals(SalePhase.Charging, vm.state.value.phase)
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
        assertEquals("100", vm.state.value.paidInput)
        assertEquals(47.0, vm.state.value.amountDue)

        vm.selectPaymentType(SalePaymentType.CARD)
        vm.selectPaymentType(SalePaymentType.CREDIT)
        assertEquals(SalePaymentType.CASH, vm.state.value.paymentType)
        assertEquals("100", vm.state.value.paidInput)

        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePaymentType.CASH, repo.lastPaymentType)
        assertNull(repo.lastCheckoutCustomerId)
        assertEquals(100.0, repo.lastPaidAmount)
        assertEquals(SalePhase.Done, state.phase)
        val result = assertNotNull(state.lastResult)
        assertTrue(result.isDelivery)
        assertEquals("Lupe Hernández (Doña Lupe)", result.deliveryCustomerName)
        assertEquals(53.0, result.change)
        assertTrue(state.deliveryAccounts.isEmpty())
        assertEquals(1L, state.activeDraftId)
    }

    @Test
    fun charging_a_delivery_without_pays_with_leaves_the_paid_field_empty() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L, paysWith = null)))
        val vm = start(repo)
        vm.selectAccount(50L)

        vm.startCharge()

        assertEquals("", vm.state.value.paidInput)
        assertFalse(vm.state.value.canConfirmCharge)
    }

    @Test
    fun charging_the_last_sale_account_with_deliveries_pending_creates_a_new_sale_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        vm.addProduct(repo.cigarro)
        advanceUntilIdle()
        vm.startCharge()
        vm.payExact()
        advanceUntilIdle()
        assertEquals(SalePhase.Done, vm.state.value.phase)
        assertEquals(0, repo.newDraftCalls)
        val deliveryCallsBefore = repo.openDeliveryCalls

        vm.startNextSale()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(1, repo.newDraftCalls)
        assertEquals(listOf(2L), state.saleAccounts.map { it.id })
        assertEquals(2L, state.activeDraftId)
        assertEquals(listOf(50L), state.deliveryAccounts.map { it.id })
        assertEquals(listOf(2L, 50L), state.accounts.map { it.id })
        assertEquals(deliveryCallsBefore + 1, repo.openDeliveryCalls)
    }

    @Test
    fun refresh_deliveries_drops_a_delivery_charged_elsewhere_and_adds_new_ones() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L), deliveryDraft(51L)))
        val vm = start(repo)
        vm.selectAccount(50L)
        repo.closeElsewhere(50L, "CHECKED_OUT")
        repo.addDelivery(deliveryDraft(52L, name = "Rosa Martínez"))

        vm.refreshDeliveries()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(1L, 51L, 52L), state.accounts.map { it.id })
        assertEquals(1L, state.activeDraftId)
        assertEquals(SaleStrings.DELIVERY_ALREADY_CHARGED, state.notice)
        assertEquals(0, repo.newDraftCalls)
    }

    @Test
    fun refresh_deliveries_keeps_the_active_account_when_it_is_still_there() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L), deliveryDraft(51L)))
        val vm = start(repo)
        vm.selectAccount(51L)
        repo.closeElsewhere(50L, "CHECKED_OUT")

        vm.refreshDeliveries()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(1L, 51L), state.accounts.map { it.id })
        assertEquals(51L, state.activeDraftId)
        assertNull(state.notice)
    }

    @Test
    fun refresh_deliveries_failure_keeps_the_accounts() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        repo.failNextDeliveryList = true

        vm.refreshDeliveries()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(1L, 50L), state.accounts.map { it.id })
        assertEquals(SaleStrings.NO_CONNECTION, state.errorMessage)
    }

    @Test
    fun conflict_charging_a_delivery_already_charged_removes_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L))).apply {
            failCheckoutWithConflict = true
            conflictAfterServerCharged = true
        }
        val vm = start(repo)
        vm.selectAccount(50L)
        vm.startCharge()

        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(listOf(1L), state.accounts.map { it.id })
        assertEquals(1L, state.activeDraftId)
        assertEquals(SaleStrings.DELIVERY_ALREADY_CHARGED, state.notice)
        assertNull(state.checkoutIdempotencyKey)
    }

    @Test
    fun switching_to_a_sale_account_during_the_delivery_fetch_keeps_it_without_notice() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L), deliveryDraft(51L))).apply {
            deliveryListDelayMs = 1_000L
        }
        val vm = start(repo)
        vm.selectAccount(50L)
        repo.closeElsewhere(50L, "CHECKED_OUT")

        vm.refreshDeliveries()
        runCurrent()
        vm.selectAccount(1L)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1L, state.activeDraftId)
        assertNull(state.notice)
        assertEquals(listOf(1L, 51L), state.accounts.map { it.id })
    }

    @Test
    fun switching_to_a_delivery_that_vanished_during_the_fetch_moves_to_a_sale_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L), deliveryDraft(51L))).apply {
            deliveryListDelayMs = 1_000L
        }
        val vm = start(repo)
        repo.closeElsewhere(51L, "CHECKED_OUT")

        vm.refreshDeliveries()
        runCurrent()
        vm.selectAccount(51L)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1L, state.activeDraftId)
        assertNotNull(state.draft)
        assertFalse(state.isDeliveryAccount)
        assertEquals(SaleStrings.DELIVERY_ALREADY_CHARGED, state.notice)
        assertEquals(listOf(1L, 50L), state.accounts.map { it.id })
    }

    @Test
    fun refresh_deliveries_while_charging_changes_nothing() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        vm.selectAccount(50L)
        vm.startCharge()
        repo.closeElsewhere(50L, "CHECKED_OUT")
        val before = vm.state.value
        val callsBefore = repo.openDeliveryCalls

        vm.refreshDeliveries()
        advanceUntilIdle()

        assertEquals(before, vm.state.value)
        assertEquals(callsBefore, repo.openDeliveryCalls)
    }

    @Test
    fun next_sale_after_charging_a_delivery_with_others_pending_lands_on_a_sale_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L), deliveryDraft(51L)))
        val vm = start(repo)
        vm.selectAccount(50L)
        vm.startCharge()
        vm.confirmCharge()
        advanceUntilIdle()
        assertEquals(SalePhase.Done, vm.state.value.phase)

        vm.startNextSale()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(1L, state.activeDraftId)
        assertFalse(state.isDeliveryAccount)
        assertEquals(listOf(1L, 51L), state.accounts.map { it.id })
    }

    @Test
    fun fast_typed_code_in_the_search_field_with_a_delivery_active_adds_nothing() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        vm.selectAccount(50L)

        repo.cigarro.barcode!!.forEach { char ->
            clock += SCANNER_MS
            vm.onQueryChange(vm.state.value.query + char)
        }
        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.DELIVERY_READ_ONLY, state.notice)
        assertEquals("", state.query)
        assertEquals(0, repo.barcodeLookups)
        assertEquals(0, repo.addLineCalls)
        assertEquals(deliveryDraft(50L), repo.drafts[50L])
    }

    @Test
    fun pays_with_above_the_field_limit_leaves_the_paid_field_empty() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L, paysWith = 1_234_567.0)))
        val vm = start(repo)
        vm.selectAccount(50L)

        vm.startCharge()

        assertEquals("", vm.state.value.paidInput)
    }

    @Test
    fun conflict_charging_a_delivery_cancelled_in_pedidos_removes_it() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L))).apply {
            failCheckoutWithConflict = true
        }
        val vm = start(repo)
        vm.selectAccount(50L)
        vm.startCharge()
        repo.closeElsewhere(50L, "CANCELLED")

        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(listOf(1L), state.accounts.map { it.id })
        assertEquals(1L, state.activeDraftId)
        assertEquals(SaleStrings.DELIVERY_CANCELLED, state.notice)
    }

    @Test
    fun conflict_charging_a_delivery_with_a_forbidden_refetch_keeps_it_and_shows_the_error() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L))).apply {
            failCheckoutWithConflict = true
            failNextDraftReadWithForbidden = true
        }
        val vm = start(repo)
        vm.selectAccount(50L)
        vm.startCharge()

        vm.confirmCharge()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertFalse(state.isCheckoutInFlight)
        assertNull(state.checkoutIdempotencyKey)
        assertEquals(SaleStrings.NOT_ALLOWED, state.errorMessage)
        assertEquals(50L, state.activeDraftId)
        assertEquals(listOf(1L, 50L), state.accounts.map { it.id })
    }

    private fun scanInSearch(vm: CashierSaleViewModel, code: String) {
        vm.onQueryChange(code)
        vm.onSubmit()
    }

    private fun TestScope.burstInPaidField(vm: CashierSaleViewModel, code: String) {
        clock += PAUSE_MS
        typePaid(vm, code, SCANNER_MS)
        clock += SCANNER_MS
        vm.confirmCharge()
    }

    @Test
    fun customer_card_with_lines_opens_the_charge_in_fiar_with_the_customer() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        scanInSearch(vm, JUAN_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(SalePaymentType.CREDIT, state.paymentType)
        assertEquals(repo.juan, state.credit.customer)
        assertEquals("", state.paidInput)
        assertNull(state.errorMessage)
        assertNull(state.notice)
        assertEquals(0, repo.checkoutCalls)
        assertEquals(1, repo.barcodeLookups)
        assertEquals(1, repo.customerCodeLookups)
    }

    @Test
    fun customer_card_after_a_slow_add_opens_the_charge_once_the_line_is_in() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        repo.addLineDelayMs = 500

        vm.addProduct(repo.torta)
        scanInSearch(vm, JUAN_CODE)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(SalePhase.Selling, vm.state.value.phase)

        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(SalePaymentType.CREDIT, state.paymentType)
        assertEquals(repo.juan, state.credit.customer)
        assertEquals(listOf(repo.torta.id), state.draft!!.lines.map { it.productId })
        assertEquals(0, state.pendingMutations)
        assertEquals(0, repo.checkoutCalls)
    }

    @Test
    fun customer_card_is_dropped_when_the_cashier_switches_account_during_the_lookup() = runTest(dispatcher) {
        val repo = FakeSaleRepository(openDraftsSeed = listOf(1L, 2L))
        val vm = start(repo)
        vm.selectAccount(1L)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        vm.selectAccount(2L)
        vm.addProduct(repo.chicle)
        advanceUntilIdle()
        vm.selectAccount(1L)
        repo.customerCodeLookupDelayMs = 500

        scanInSearch(vm, JUAN_CODE)
        advanceTimeBy(100)
        runCurrent()
        vm.selectAccount(2L)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, repo.customerCodeLookups)
        assertEquals(SalePhase.Selling, state.phase)
        assertEquals(2L, state.activeDraftId)
        assertEquals(SalePaymentType.CASH, state.paymentType)
        assertNull(state.credit.customer)
        assertFalse(state.abonoOverlayOpen)
        assertEquals(0, repo.checkoutCalls)
    }

    @Test
    fun customer_card_with_an_empty_cart_opens_abonar_with_the_customer() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        scanInSearch(vm, JUAN_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertTrue(state.abonoOverlayOpen)
        assertEquals(repo.juan, state.credit.customer)
        assertNull(state.errorMessage)
        assertEquals(0, repo.paymentCalls)
    }

    @Test
    fun customer_card_without_debt_and_an_empty_cart_only_tells_so() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        scanInSearch(vm, MARIA_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.noDebt(repo.maria.displayName), state.notice)
        assertFalse(state.abonoOverlayOpen)
        assertEquals(SalePhase.Selling, state.phase)
        assertNull(state.credit.customer)
    }

    @Test
    fun inactive_customer_card_shows_the_message_and_opens_nothing() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()

        scanInSearch(vm, INACTIVE_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.CUSTOMER_INACTIVE, state.errorMessage)
        assertEquals(SalePhase.Selling, state.phase)
        assertFalse(state.abonoOverlayOpen)
        assertNull(state.credit.customer)
    }

    @Test
    fun unknown_code_looks_up_the_customer_once_and_shows_not_found() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)

        scanInSearch(vm, "39999999")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.notFoundWithCode("39999999"), state.errorMessage)
        assertEquals("No hay ningún producto ni cliente con el código 39999999", state.errorMessage)
        assertEquals(1, repo.customerCodeLookups)
        assertFalse(state.abonoOverlayOpen)
        assertEquals(SalePhase.Selling, state.phase)
    }

    @Test
    fun customer_lookup_network_failure_shows_the_error_and_opens_nothing() = runTest(dispatcher) {
        val repo = FakeSaleRepository().apply { failCustomerCodeLookupWithNetwork = true }
        val vm = start(repo)

        scanInSearch(vm, JUAN_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleErrorText.from(AppError.Network("network down")), state.errorMessage)
        assertFalse(state.abonoOverlayOpen)
        assertEquals(SalePhase.Selling, state.phase)
        assertNull(state.credit.customer)
    }

    @Test
    fun customer_card_burst_in_the_cash_field_switches_to_fiar() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        vm.startCharge()
        typePaid(vm, "50", HUMAN_MS)

        burstInPaidField(vm, JUAN_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Charging, state.phase)
        assertEquals(SalePaymentType.CREDIT, state.paymentType)
        assertEquals(repo.juan, state.credit.customer)
        assertEquals("", state.paidInput)
        assertFalse(state.paidInputRejected)
        assertNull(state.notice)
        assertEquals(0, repo.checkoutCalls)
    }

    @Test
    fun customer_card_burst_while_the_charge_is_in_flight_only_warns() = runTest(dispatcher) {
        val repo = FakeSaleRepository(checkoutDelayMs = 5_000)
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        vm.startCharge()
        typePaid(vm, "50", HUMAN_MS)

        burstInPaidField(vm, JUAN_CODE)
        assertEquals("50", vm.state.value.paidInput)
        vm.confirmCharge()
        runCurrent()

        val state = vm.state.value
        assertTrue(state.isPaidLocked)
        assertEquals(SaleStrings.FINISH_CHARGE_FIRST, state.notice)
        assertEquals(SalePaymentType.CASH, state.paymentType)
        assertNull(state.credit.customer)
        assertEquals(1, repo.customerCodeLookups)
        advanceUntilIdle()
    }

    @Test
    fun product_burst_in_the_cash_field_still_warns_and_skips_the_customer_lookup() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        vm.startCharge()

        burstInPaidField(vm, repo.cigarro.barcode!!)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.FINISH_CHARGE_FIRST, state.notice)
        assertEquals(SalePaymentType.CASH, state.paymentType)
        assertEquals(0, repo.customerCodeLookups)
    }

    @Test
    fun customer_card_burst_while_charging_a_delivery_is_read_only() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L, paysWith = null)))
        val vm = start(repo)
        vm.selectAccount(50L)
        vm.startCharge()

        burstInPaidField(vm, JUAN_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.DELIVERY_READ_ONLY, state.notice)
        assertEquals(SalePaymentType.CASH, state.paymentType)
        assertNull(state.credit.customer)
    }

    @Test
    fun customer_card_with_a_delivery_active_looks_nothing_up() = runTest(dispatcher) {
        val repo = FakeSaleRepository(deliverySeed = listOf(deliveryDraft(50L)))
        val vm = start(repo)
        vm.selectAccount(50L)

        scanInSearch(vm, JUAN_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SaleStrings.DELIVERY_READ_ONLY, state.notice)
        assertEquals(0, repo.customerCodeLookups)
        assertEquals(0, repo.barcodeLookups)
        assertFalse(state.abonoOverlayOpen)
        assertEquals(SalePhase.Selling, state.phase)
    }

    @Test
    fun customer_card_after_a_sale_opens_abonar_on_the_next_empty_account() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = start(repo)
        vm.addProduct(repo.torta)
        advanceUntilIdle()
        vm.startCharge()
        vm.payExact()
        vm.confirmCharge()
        advanceUntilIdle()
        assertEquals(SalePhase.Done, vm.state.value.phase)

        scanInSearch(vm, JUAN_CODE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SalePhase.Selling, state.phase)
        assertTrue(state.draft!!.lines.isEmpty())
        assertTrue(state.abonoOverlayOpen)
        assertEquals(repo.juan, state.credit.customer)
    }

    private companion object {
        const val HUMAN_MS = 120L
        const val SCANNER_MS = 5L
        const val PAUSE_MS = 1_000L
    }
}

private const val JUAN_CODE = "30000011"
private const val MARIA_CODE = "30000028"
private const val INACTIVE_CODE = "30000035"

private class FakeSaleRepository(
    private val checkoutDelayMs: Long = 0,
    openDraftsSeed: List<Long> = listOf(1L),
    deliverySeed: List<SaleDraft> = emptyList(),
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
        listPrice = 126.0, listOnsitePrice = 137.0, cashOnsitePrice = 130.0,
        priceType = SalePriceType.WEIGHT,
    )
    val varios = SaleProduct(
        id = 7L, name = "Varios", barcode = null, unitId = 1L, cashPrice = null,
        listPrice = null, listOnsitePrice = null,
        priceType = SalePriceType.OPEN,
    )
    val arrozSuelto = SaleProduct(
        id = 8L, name = "Arroz suelto", barcode = "20000028", unitId = 2L, cashPrice = null,
        listPrice = null, listOnsitePrice = null,
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

    val juan = SaleCustomer(
        id = 10L,
        code = JUAN_CODE,
        name = "Juan Pérez",
        nickname = "el Wero",
        phone = "5551234",
        balance = 350.0,
    )
    val maria = SaleCustomer(id = 11L, code = MARIA_CODE, name = "María López", nickname = null, phone = null, balance = 0.0)
    val customers: MutableMap<Long, SaleCustomer> = mutableMapOf(juan.id to juan, maria.id to maria)
    val inactiveCustomer = SaleCustomer(
        id = 12L,
        code = INACTIVE_CODE,
        name = "Juana Baja",
        nickname = null,
        phone = null,
        balance = 20.0,
        active = false,
    )
    var customerCodeLookups = 0
    var failCustomerCodeLookupWithNetwork = false
    var addLineDelayMs = 0L
    var customerCodeLookupDelayMs = 0L
        val customerSearchQueries = mutableListOf<String>()
    var createCustomerCalls = 0
    var lastCreateNickname: String? = null
    var paymentCalls = 0
    val paymentKeys = mutableListOf<String>()
    var lastPaymentAmount: Double? = null
    var lastCheckoutCustomerId: Long? = null
    var failNextCreateWithConflict = false
    var failFirstPaymentWithNetwork = false
    var failPaymentWithValidation: String? = null
    var failCustomerFetchWithNetwork = false
    var paymentDelayMs = 0L
    var customerSearchDelayMs = 0L

    val drafts: MutableMap<Long, SaleDraft> =
        (openDraftsSeed.associateWith { emptyDraft(it) } + deliverySeed.associateBy { it.id }).toMutableMap()
    var openDeliveryCalls = 0
    var barcodeLookups = 0
    var failNextDeliveryList = false
    var deliveryListDelayMs = 0L
    var failNextDraftReadWithForbidden = false

    fun addDelivery(draft: SaleDraft) {
        drafts[draft.id] = draft
    }

    var cashSession: CashSessionCheck = CashSessionCheck.Open
    var failFirstCheckoutWithNetwork = false
    var failCheckoutWithValidation = false
    var checkoutValidationMessage = "Draft has no lines"
    var forceConflictOnNextUpdate = false
    var failCheckoutWithConflict = false
    var conflictAfterServerCharged = false
    var failNextNewDraft = false
    var failNextDraftReadWithNetwork = false
    var forceConflictOnNextSetChannel = false
    var forceConflictOnNextAdd = false
    var variosMissing = false
    var lastLineInput: SaleLineInput? = null

    var addLineCalls = 0
    var updateLineCalls = 0
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
        val open = drafts.values.filter { it.isOpen }.sortedBy { it.id }
        return AppResult.Success(open.filterNot { it.isDelivery } + open.filter { it.isDelivery })
    }

    override suspend fun openDeliveryDrafts(): AppResult<List<SaleDraft>> {
        openDeliveryCalls += 1
        if (deliveryListDelayMs > 0) delay(deliveryListDelayMs)
        if (failNextDeliveryList) {
            failNextDeliveryList = false
            return AppResult.Failure(AppError.Network("network down"))
        }
        return AppResult.Success(drafts.values.filter { it.isOpen && it.isDelivery }.sortedBy { it.id })
    }

    override suspend fun draft(draftId: Long): AppResult<SaleDraft> {
        if (failNextDraftReadWithForbidden) {
            failNextDraftReadWithForbidden = false
            return AppResult.Failure(AppError.Forbidden)
        }
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

    private val allProducts: List<SaleProduct> get() = searchPool + jamon + kitchenPool + chicle + varios + arrozSuelto

    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> {
        barcodeLookups += 1
        return AppResult.Success(allProducts.firstOrNull { it.barcode == barcode })
    }

    override suspend fun variosProduct(): AppResult<SaleProduct?> = AppResult.Success(varios.takeUnless { variosMissing })

    override suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        input: SaleLineInput,
        expectedVersion: Int,
    ): SaleMutationResult {
        addLineCalls += 1
        lastLineInput = input
        if (addLineDelayMs > 0) delay(addLineDelayMs)
        if (forceConflictOnNextAdd) {
            forceConflictOnNextAdd = false
            val draft = drafts[draftId] ?: return SaleMutationResult.Failure(notFound())
            val bumped = draft.copy(version = draft.version + 1)
            drafts[draftId] = bumped
            return SaleMutationResult.ConflictRefetched(bumped)
        }
        return mutate(draftId, expectedVersion) { draft ->
            val openAmount = (input as? SaleLineInput.OpenPrice)?.amount
            val price = openAmount ?: product.cashPriceFor(draft.channel) ?: 0.0
            val listPrice = openAmount ?: product.listPriceFor(draft.channel) ?: price
            val qty = input.qtyFor(price)
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
                priceType = product.priceType,
            )
            draft.withLines(draft.lines + line)
        }
    }

    override suspend fun updateLine(
        draftId: Long,
        lineId: Long,
        input: SaleLineInput,
        expectedVersion: Int,
    ): SaleMutationResult {
        updateLineCalls += 1
        lastLineInput = input
        if (forceConflictOnNextUpdate) {
            forceConflictOnNextUpdate = false
            val draft = drafts[draftId] ?: return SaleMutationResult.Failure(notFound())
            val bumped = draft.copy(version = draft.version + 1)
            drafts[draftId] = bumped
            return SaleMutationResult.ConflictRefetched(bumped)
        }
        return mutate(draftId, expectedVersion) { draft ->
            draft.withLines(
                draft.lines.map { line ->
                    if (line.id == lineId) line.updatedWith(input) else line
                }
            )
        }
    }

    private fun SaleLineInput.qtyFor(cashPrice: Double): Double = when (this) {
        is SaleLineInput.Qty -> qty
        is SaleLineInput.Amount -> round6(amount / cashPrice)
        is SaleLineInput.OpenPrice -> 1.0
    }

    private fun SaleLine.updatedWith(input: SaleLineInput): SaleLine = when (input) {
        is SaleLineInput.OpenPrice -> copy(
            qty = 1.0,
            unitPrice = input.amount,
            cashUnitPrice = input.amount,
            lineTotal = round2(input.amount),
        )
        else -> {
            val qty = input.qtyFor(cashUnitPrice)
            copy(qty = qty, lineTotal = round2(qty * unitPrice))
        }
    }

    private fun round6(value: Double) = kotlin.math.round(value * 1_000_000) / 1_000_000

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

    private fun productById(id: Long): SaleProduct? = allProducts.firstOrNull { it.id == id }

    override suspend fun checkout(
        draftId: Long,
        paymentType: SalePaymentType,
        paidAmount: Double,
        idempotencyKey: String,
        customerId: Long?,
    ): AppResult<CheckoutResult> {
        checkoutCalls += 1
        callLog += "checkout"
        checkoutKeys += idempotencyKey
        lastPaidAmount = paidAmount
        lastPaymentType = paymentType
        lastCheckoutCustomerId = customerId
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
            return AppResult.Failure(AppError.Validation(checkoutValidationMessage))
        }
        val draft = drafts[draftId] ?: return AppResult.Failure(notFound())
        drafts[draftId] = draft.copy(status = "CHECKED_OUT", version = draft.version + 1)
        val total = when (paymentType) {
            SalePaymentType.CARD, SalePaymentType.CREDIT -> draft.totals.listTotal
            SalePaymentType.CASH, SalePaymentType.TRANSFER -> draft.totals.cashTotal
        }
        if (paymentType == SalePaymentType.CREDIT && customerId != null) {
            customers[customerId]?.let { customers[customerId] = it.copy(balance = round2(it.balance + total)) }
        }
        return AppResult.Success(
            CheckoutResult(
                saleId = 1L,
                folio = "FOL-0001",
                paymentType = paymentType,
                totalCharged = total,
                paidAmount = paidAmount,
            ),
        )
    }

    override suspend fun searchCustomers(query: String, limit: Int): AppResult<List<SaleCustomer>> {
        customerSearchQueries += query
        if (customerSearchDelayMs > 0) withContext(NonCancellable) { delay(customerSearchDelayMs) }
        val trimmed = query.trim()
        val matches = if (trimmed.all(Char::isDigit)) {
            customers.values.filter { it.code == trimmed }
        } else {
            customers.values.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.nickname?.contains(trimmed, ignoreCase = true) == true
            }
        }
        return AppResult.Success(matches.sortedBy { it.name }.take(limit))
    }

    override suspend fun customer(customerId: Long): AppResult<SaleCustomer> {
        if (failCustomerFetchWithNetwork) return AppResult.Failure(AppError.Network("network down"))
        return customers[customerId]?.let { AppResult.Success(it) } ?: AppResult.Failure(notFound())
    }

    override suspend fun findCustomerByCode(code: String): AppResult<SaleCustomer?> {
        customerCodeLookups += 1
        if (customerCodeLookupDelayMs > 0) delay(customerCodeLookupDelayMs)
        if (failCustomerCodeLookupWithNetwork) return AppResult.Failure(AppError.Network("network down"))
        val match = (customers.values + inactiveCustomer).firstOrNull { it.code == code }
        return AppResult.Success(match)
    }

    override suspend fun createCustomer(name: String, nickname: String?, phone: String?): AppResult<SaleCustomer> {
        createCustomerCalls += 1
        lastCreateNickname = nickname
        if (failNextCreateWithConflict) {
            failNextCreateWithConflict = false
            return AppResult.Failure(AppError.Conflict)
        }
        val created = SaleCustomer(
            id = 100L + createCustomerCalls,
            code = null,
            name = name,
            nickname = nickname,
            phone = phone,
            balance = 0.0,
        )
        customers[created.id] = created
        return AppResult.Success(created)
    }

    override suspend fun recordCustomerPayment(
        customerId: Long,
        amount: Double,
        idempotencyKey: String,
    ): AppResult<CustomerPaymentResult> {
        paymentCalls += 1
        paymentKeys += idempotencyKey
        lastPaymentAmount = amount
        if (paymentDelayMs > 0) delay(paymentDelayMs)
        if (failFirstPaymentWithNetwork && paymentCalls == 1) {
            return AppResult.Failure(AppError.Network("network down"))
        }
        failPaymentWithValidation?.let { return AppResult.Failure(AppError.Validation(it)) }
        val customer = customers[customerId] ?: return AppResult.Failure(notFound())
        val updated = customer.copy(balance = round2(customer.balance - amount))
        customers[customerId] = updated
        return AppResult.Success(CustomerPaymentResult(customerId, amount, updated.balance))
    }
}
