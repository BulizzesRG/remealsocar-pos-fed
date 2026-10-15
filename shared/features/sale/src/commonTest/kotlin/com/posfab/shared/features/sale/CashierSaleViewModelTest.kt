package com.posfab.shared.features.sale

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePriceType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.features.sale.repository.SaleRepository
import com.posfab.shared.features.sale.ui.CashierSaleViewModel
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
        assertEquals(listOf("resolveLots", "checkoutCash"), repo.callLog)
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
}

private class FakeSaleRepository(
    private val checkoutDelayMs: Long = 0,
) : SaleRepository {
    val cigarro = SaleProduct(
        id = 1L, name = "Cigarro suelto", barcode = "7501000100015", unitId = 1L, cashPrice = 21.0,
    )
    val cafe = SaleProduct(
        id = 2L, name = "Cafe de olla", barcode = "7501000100022", unitId = 1L, cashPrice = 30.0, lotTracked = true,
    )
    val jamon = SaleProduct(
        id = 3L, name = "Jamon", barcode = "7501000100039", unitId = 2L, cashPrice = 120.0,
        priceType = SalePriceType.WEIGHT,
    )
    val searchPool = listOf(cigarro, cafe)
    val kitchenPool = listOf(
        SaleProduct(
            id = 4L, name = "Comida", barcode = null, unitId = 1L, cashPrice = 63.0,
            kitchenKind = SaleKitchenKind.FONDA,
        ),
        SaleProduct(
            id = 5L, name = "Torta", barcode = null, unitId = 1L, cashPrice = 40.0,
            kitchenKind = SaleKitchenKind.TORTERIA,
        ),
    )

    var cashSession: CashSessionCheck = CashSessionCheck.Open
    var failFirstCheckoutWithNetwork = false
    var failCheckoutWithValidation = false
    var forceConflictOnNextUpdate = false
    var failCheckoutWithConflict = false
    var conflictAfterServerCharged = false

    var addLineCalls = 0
    var updateQtyCalls = 0
    var searchCalls = 0
    var checkoutCalls = 0
    var cancelCalls = 0
    var resolveLotsCalls = 0
    var lastPaidAmount: Double? = null
    val checkoutKeys = mutableListOf<String>()
    val callLog = mutableListOf<String>()

    private var draftCounter = 1L
    private var nextLineId = 1L
    private var needsFreshDraft = false
    private var draft = emptyDraft()

    private fun emptyDraft() = SaleDraft(
        id = draftCounter, status = "OPEN", version = 1, lines = emptyList(), totals = SaleTotals(cashTotal = 0.0),
    )

    private fun freshDraftIfNeeded() {
        if (needsFreshDraft) {
            draftCounter += 1
            draft = emptyDraft()
            needsFreshDraft = false
        }
    }

    private fun round2(value: Double) = kotlin.math.round(value * 100) / 100

    private fun withLines(lines: List<SaleLine>) = draft.copy(
        version = draft.version + 1,
        lines = lines,
        totals = SaleTotals(cashTotal = lines.sumOf { it.lineTotal }),
    )

    override suspend fun cashSession(): AppResult<CashSessionCheck> = AppResult.Success(cashSession)

    override suspend fun currentOrNewDraft(): AppResult<SaleDraft> {
        freshDraftIfNeeded()
        return AppResult.Success(draft)
    }

    override suspend fun newDraft(): AppResult<SaleDraft> {
        draftCounter += 1
        draft = emptyDraft()
        needsFreshDraft = false
        return AppResult.Success(draft)
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> {
        searchCalls += 1
        return AppResult.Success(searchPool.filter { it.name.contains(query, ignoreCase = true) }.take(limit))
    }

    override suspend fun kitchenProducts(): AppResult<List<SaleProduct>> = AppResult.Success(kitchenPool)

    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> =
        AppResult.Success((searchPool + jamon + kitchenPool).firstOrNull { it.barcode == barcode })

    override suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        qty: Double,
        expectedVersion: Int,
    ): SaleMutationResult {
        addLineCalls += 1
        if (expectedVersion != draft.version) return SaleMutationResult.ConflictRefetched(draft)
        val price = product.cashPrice ?: 0.0
        val line = SaleLine(
            id = nextLineId++,
            productId = product.id,
            productName = product.name,
            qty = qty,
            unitId = product.unitId,
            cashUnitPrice = price,
            lineTotal = round2(qty * price),
            lotTracked = product.lotTracked,
        )
        draft = withLines(draft.lines + line)
        return SaleMutationResult.Success(draft)
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
            draft = draft.copy(version = draft.version + 1)
            return SaleMutationResult.ConflictRefetched(draft)
        }
        if (expectedVersion != draft.version) return SaleMutationResult.ConflictRefetched(draft)
        val updated = draft.lines.map {
            if (it.id == lineId) it.copy(qty = qty, lineTotal = round2(qty * it.cashUnitPrice)) else it
        }
        draft = withLines(updated)
        return SaleMutationResult.Success(draft)
    }

    override suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult {
        if (expectedVersion != draft.version) return SaleMutationResult.ConflictRefetched(draft)
        draft = withLines(draft.lines.filterNot { it.id == lineId })
        return SaleMutationResult.Success(draft)
    }

    override suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> {
        resolveLotsCalls += 1
        callLog += "resolveLots"
        draft = withLines(draft.lines.map { if (it.lotTracked) it.copy(lotId = 999L) else it })
        return AppResult.Success(draft)
    }

    override suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit> {
        cancelCalls += 1
        needsFreshDraft = true
        return AppResult.Success(Unit)
    }

    override suspend fun checkoutCash(
        draftId: Long,
        paidAmount: Double,
        idempotencyKey: String,
    ): AppResult<CashCheckoutResult> {
        checkoutCalls += 1
        callLog += "checkoutCash"
        checkoutKeys += idempotencyKey
        lastPaidAmount = paidAmount
        if (checkoutDelayMs > 0) delay(checkoutDelayMs)
        if (failFirstCheckoutWithNetwork && checkoutCalls == 1) {
            return AppResult.Failure(AppError.Network("network down"))
        }
        if (failCheckoutWithConflict) {
            failCheckoutWithConflict = false
            if (conflictAfterServerCharged) needsFreshDraft = true
            return AppResult.Failure(AppError.Conflict)
        }
        if (failCheckoutWithValidation) {
            return AppResult.Failure(AppError.Validation("Draft has no lines"))
        }
        val total = draft.totals.cashTotal
        needsFreshDraft = true
        return AppResult.Success(
            CashCheckoutResult(saleId = 1L, folio = "FOL-0001", cashTotal = total, paidAmount = paidAmount)
        )
    }
}
