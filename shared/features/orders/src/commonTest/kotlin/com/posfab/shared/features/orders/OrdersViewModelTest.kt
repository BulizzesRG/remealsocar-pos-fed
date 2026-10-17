package com.posfab.shared.features.orders

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.OrderCustomer
import com.posfab.shared.features.orders.domain.OrderLine
import com.posfab.shared.features.orders.domain.OrderLineInput
import com.posfab.shared.features.orders.domain.OrderMutationResult
import com.posfab.shared.features.orders.domain.OrderPriceType
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.domain.OrderStatus
import com.posfab.shared.features.orders.ui.OrderFocus
import com.posfab.shared.features.orders.ui.OrderPrompt
import com.posfab.shared.features.orders.ui.OrdersStrings
import com.posfab.shared.features.orders.ui.OrdersView
import com.posfab.shared.features.orders.ui.OrdersViewModel
import com.posfab.shared.features.orders.usecase.OrdersUseCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = TimeZone.of("America/Mexico_City")
    private val now = Instant.parse("2026-10-17T01:00:00Z")
    private val today = LocalDate(2026, 10, 16)

    private fun viewModel(repo: FakeOrdersRepository) =
        OrdersViewModel(OrdersUseCases(repo), now = { now }, timeZone = zone, dispatcher = dispatcher)

    private fun pieceLine(id: Long, product: Long, qty: Double, price: Double) =
        OrderLine(id, product, "P$product", qty, "PZA", OrderPriceType.PIECE, price, qty * price)

    private fun TestScope.editingNew(repo: FakeOrdersRepository = FakeOrdersRepository()): OrdersViewModel {
        val vm = viewModel(repo)
        vm.newOrder()
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.readyToSave(repo: FakeOrdersRepository): OrdersViewModel {
        val vm = editingNew(repo)
        vm.onCustomerQuery("lupe")
        advanceUntilIdle()
        vm.pickCustomer(LUPE.id)
        vm.addProduct(TORTA)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun load_fetches_open_paid_and_cancelled_of_the_business_day() = runTest(dispatcher) {
        val open = listOf(sampleOrder(id = 1L), sampleOrder(id = 2L))
        val repo = FakeOrdersRepository(
            openResult = AppResult.Success(open),
            paidResult = AppResult.Success(listOf(sampleOrder(id = 3L, status = OrderStatus.PAID))),
            cancelledResult = AppResult.Success(listOf(sampleOrder(id = 4L, status = OrderStatus.CANCELLED))),
        )
        val vm = viewModel(repo)

        vm.load()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(open, state.open)
        assertEquals(listOf(3L), state.paidToday.map { it.id })
        assertEquals(listOf(4L), state.cancelledToday.map { it.id })
        assertEquals(listOf(OrderStatus.PAID to today, OrderStatus.CANCELLED to today), repo.dayCalls.sortedBy { it.first })
    }

    @Test
    fun load_failure_keeps_previous_lists_and_shows_a_message() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(openResult = AppResult.Success(listOf(sampleOrder(id = 1L))))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        repo.openResult = AppResult.Failure(AppError.Network("down"))
        vm.load()
        advanceUntilIdle()

        assertEquals(listOf(1L), vm.state.value.open.map { it.id })
        assertEquals(OrdersStrings.NETWORK, vm.state.value.errorMessage)
    }

    @Test
    fun an_older_load_never_overwrites_a_newer_one() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val olderGate = CompletableDeferred<Unit>()
        val newerGate = CompletableDeferred<Unit>()
        repo.openResults += AppResult.Success(listOf(sampleOrder(id = 1L)))
        repo.openResults += AppResult.Success(listOf(sampleOrder(id = 2L)))
        repo.openGates += olderGate
        repo.openGates += newerGate
        val vm = viewModel(repo)

        vm.load()
        runCurrent()
        vm.load()
        runCurrent()
        newerGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.state.value.open.map { it.id })
        assertFalse(vm.state.value.isLoading)

        olderGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.state.value.open.map { it.id })
    }

    @Test
    fun sections_toggle() = runTest(dispatcher) {
        val vm = viewModel(FakeOrdersRepository())
        vm.togglePaid()
        vm.toggleCancelled()
        vm.toggleCancelled()
        assertTrue(vm.state.value.showPaid)
        assertFalse(vm.state.value.showCancelled)
    }

    @Test
    fun new_order_creates_an_empty_order_and_opens_the_editor() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)

        val state = vm.state.value
        assertEquals(OrdersView.Editing(7L), state.view)
        val form = assertNotNull(state.editing)
        assertTrue(form.isNew)
        assertEquals(OrdersStrings.NEW_ORDER_TITLE, form.title)
        assertEquals(OrderFocus.CUSTOMER, form.focus)
        assertEquals(1, repo.createKeys.size)
        assertNull(state.pendingCreateKey)
        assertEquals(listOf(COMIDA, TORTA), state.kitchenProducts)
    }

    @Test
    fun new_order_keeps_the_key_only_after_a_network_failure() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(
            createResults = mutableListOf(
                AppResult.Failure(AppError.Network("down")),
                AppResult.Failure(AppError.Forbidden),
                AppResult.Success(sampleOrder()),
            ),
        )
        val vm = viewModel(repo)

        vm.newOrder()
        advanceUntilIdle()
        val kept = assertNotNull(vm.state.value.pendingCreateKey)
        assertEquals(OrdersStrings.NETWORK, vm.state.value.errorMessage)

        vm.newOrder()
        advanceUntilIdle()
        assertNull(vm.state.value.pendingCreateKey)
        assertEquals(OrdersStrings.FORBIDDEN, vm.state.value.errorMessage)

        vm.newOrder()
        advanceUntilIdle()
        assertEquals(kept, repo.createKeys[0])
        assertEquals(kept, repo.createKeys[1])
        assertTrue(repo.createKeys[2] != kept)
        assertIs<OrdersView.Editing>(vm.state.value.view)
    }

    @Test
    fun new_order_twice_creates_only_one() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = viewModel(repo)

        vm.newOrder()
        vm.newOrder()
        advanceUntilIdle()

        assertEquals(1, repo.createKeys.size)
    }

    @Test
    fun customer_suggestions_wait_for_two_characters_and_the_debounce() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)

        vm.onCustomerQuery("l")
        advanceUntilIdle()
        assertTrue(repo.customerQueries.isEmpty())

        vm.onCustomerQuery("lu")
        advanceTimeBy(OrdersViewModel.CUSTOMER_SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(repo.customerQueries.isEmpty())
        vm.onCustomerQuery("lup")
        advanceUntilIdle()

        assertEquals(listOf("lup"), repo.customerQueries)
        assertEquals(listOf(LUPE), vm.state.value.editing?.suggestions)
    }

    @Test
    fun digits_search_by_phone() = runTest(dispatcher) {
        val vm = editingNew()

        vm.onCustomerQuery("55500")
        advanceUntilIdle()

        assertEquals(listOf(ROSA), vm.state.value.editing?.suggestions)
    }

    @Test
    fun picking_a_customer_proposes_the_saved_address() = runTest(dispatcher) {
        val vm = editingNew()
        vm.onCustomerQuery("lupe")
        advanceUntilIdle()

        vm.pickCustomer(LUPE.id)

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(LUPE, form.selected)
        assertEquals("Calle 5 #12", form.address)
        assertEquals("", form.customerQuery)
        assertTrue(form.suggestions.isEmpty())
        assertEquals(OrderFocus.ADDRESS, form.focus)
        assertEquals("Pedido de Lupe Hernández (Doña Lupe)", form.title)
    }

    @Test
    fun picking_a_customer_without_address_keeps_the_typed_one() = runTest(dispatcher) {
        val vm = editingNew()
        vm.onAddress("Av. Juárez 3")
        vm.onCustomerQuery("rosa")
        advanceUntilIdle()

        vm.pickCustomer(ROSA.id)

        assertEquals("Av. Juárez 3", vm.state.value.editing?.address)
    }

    @Test
    fun enter_selects_only_a_single_suggestion() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(customers = listOf(LUPE, LUPE.copy(id = 99L, name = "Lupe Dos")))
        val vm = editingNew(repo)
        vm.onCustomerQuery("lupe")
        advanceUntilIdle()

        vm.onCustomerQueryEnter()
        assertNull(vm.state.value.editing?.selected)

        vm.onCustomerQuery("lupe dos")
        advanceUntilIdle()
        vm.onCustomerQueryEnter()
        assertEquals(99L, vm.state.value.editing?.selected?.id)
    }

    @Test
    fun other_customer_clears_the_selection() = runTest(dispatcher) {
        val vm = editingNew()
        vm.onCustomerQuery("lupe")
        advanceUntilIdle()
        vm.pickCustomer(LUPE.id)

        vm.clearCustomer()

        assertNull(vm.state.value.editing?.selected)
        assertEquals(OrderFocus.CUSTOMER, vm.state.value.editing?.focus)
    }

    @Test
    fun new_customer_starts_from_the_query_and_digits_go_to_the_phone() = runTest(dispatcher) {
        val vm = editingNew()
        vm.onCustomerQuery("Ana López")
        vm.startNewCustomer()
        assertEquals("Ana López", vm.state.value.editing?.newCustomer?.name)

        vm.cancelNewCustomer()
        vm.onCustomerQuery("5512345678")
        vm.startNewCustomer()
        val form = assertNotNull(vm.state.value.editing)
        assertEquals("", form.newCustomer?.name)
        assertEquals("5512345678", form.newCustomer?.phone)
        assertEquals(OrderFocus.NEW_CUSTOMER_NAME, form.focus)
    }

    @Test
    fun new_customer_phone_keeps_ten_digits_only() = runTest(dispatcher) {
        val vm = editingNew()
        vm.startNewCustomer()

        vm.onNewCustomerPhone("55-1234-5678-99")

        assertEquals("5512345678", vm.state.value.editing?.newCustomer?.phone)
    }

    @Test
    fun save_is_disabled_without_customer_address_or_lines() = runTest(dispatcher) {
        val vm = editingNew()
        assertFalse(vm.state.value.editing!!.canSave)

        vm.onCustomerQuery("rosa")
        advanceUntilIdle()
        vm.pickCustomer(ROSA.id)
        assertFalse(vm.state.value.editing!!.canSave)

        vm.onAddress("Av. 3")
        assertFalse(vm.state.value.editing!!.canSave)

        vm.addProduct(TORTA)
        assertFalse(vm.state.value.editing!!.canSave)
        advanceUntilIdle()
        assertTrue(vm.state.value.editing!!.canSave)

        vm.onAddress("   ")
        assertFalse(vm.state.value.editing!!.canSave)
    }

    @Test
    fun change_is_live_and_negative_means_le_falta() = runTest(dispatcher) {
        val vm = readyToSave(FakeOrdersRepository())

        vm.onPaysWith("100")
        assertEquals(50.0, vm.state.value.editing?.change)
        assertEquals("Cambio $50.00", OrdersStrings.changeOrMissing(vm.state.value.editing!!.change!!))

        vm.onPaysWith("20")
        assertEquals(-30.0, vm.state.value.editing?.change)
        assertEquals("Le falta $30.00", OrdersStrings.changeOrMissing(vm.state.value.editing!!.change!!))

        vm.onPaysWith("")
        assertNull(vm.state.value.editing?.change)
    }

    @Test
    fun pays_with_keeps_two_decimals() = runTest(dispatcher) {
        val vm = editingNew()
        vm.onPaysWith("1a2.345")
        assertEquals("12.34", vm.state.value.editing?.paysWithInput)
    }

    @Test
    fun piece_products_merge_quantities_and_each_mutation_uses_the_last_version() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)

        vm.addProduct(TORTA)
        vm.addProduct(TORTA)
        vm.addProduct(COMIDA)
        assertEquals(3, vm.state.value.editing?.pendingMutations)
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(0, form.pendingMutations)
        assertEquals(listOf(2.0, 1.0), form.lines.map { it.qty })
        assertEquals(175.0, form.total)
        assertEquals(4, form.version)
        assertEquals(
            listOf("add:50:${OrderLineInput.Qty(1.0)}:v1", "qty:100:2.0:v2", "add:75:${OrderLineInput.Qty(1.0)}:v3"),
            repo.mutationLog,
        )
    }

    @Test
    fun mutations_run_one_at_a_time() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        val gate = CompletableDeferred<Unit>()
        repo.mutationGate = gate

        vm.addProduct(TORTA)
        vm.addProduct(COMIDA)
        runCurrent()
        assertEquals(1, repo.mutationLog.size)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, repo.mutationLog.size)
    }

    @Test
    fun increment_decrement_and_remove_lines() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        vm.addProduct(TORTA)
        advanceUntilIdle()
        val lineId = vm.state.value.editing!!.lines.single().id

        vm.incrementLine(lineId)
        advanceUntilIdle()
        assertEquals(2.0, vm.state.value.editing!!.lines.single().qty)

        vm.decrementLine(lineId)
        advanceUntilIdle()
        assertEquals(1.0, vm.state.value.editing!!.lines.single().qty)

        vm.decrementLine(lineId)
        advanceUntilIdle()
        assertTrue(vm.state.value.editing!!.lines.isEmpty())

        vm.addProduct(COMIDA)
        advanceUntilIdle()
        vm.removeLine(vm.state.value.editing!!.lines.single().id)
        advanceUntilIdle()
        assertTrue(vm.state.value.editing!!.lines.isEmpty())
    }

    @Test
    fun weight_prompt_asks_kilos_with_quick_buttons() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)

        vm.addProduct(JAMON)
        assertIs<OrderPrompt.Weight>(vm.state.value.editing?.prompt)
        assertTrue(repo.mutationLog.isEmpty())

        vm.pickWeight(0.25)
        assertEquals("0.250", vm.state.value.editing?.prompt?.input)
        assertEquals(31.5, vm.state.value.editing?.prompt?.previewTotal)
        vm.confirmPrompt()
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertNull(form.prompt)
        assertEquals(0.25, form.lines.single().qty)
        assertEquals(31.5, form.total)
    }

    @Test
    fun weight_input_keeps_three_decimals_and_rejects_zero() = runTest(dispatcher) {
        val vm = editingNew()
        vm.addProduct(JAMON)

        vm.onPromptInput("0.12345")
        assertEquals("0.123", vm.state.value.editing?.prompt?.input)

        vm.onPromptInput("0")
        vm.confirmPrompt()
        assertEquals(OrdersStrings.PROMPT_INVALID, vm.state.value.editing?.errorMessage)
        assertNotNull(vm.state.value.editing?.prompt)
    }

    @Test
    fun open_price_prompt_asks_pesos_and_never_merges() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)

        repeat(2) {
            vm.addProduct(CARGO)
            assertIs<OrderPrompt.OpenAmount>(vm.state.value.editing?.prompt)
            vm.onPromptInput("20")
            vm.confirmPrompt()
            advanceUntilIdle()
        }

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(2, form.lines.size)
        assertEquals(40.0, form.total)
        assertTrue(repo.mutationLog.all { it.startsWith("add:90:${OrderLineInput.OpenPrice(20.0)}") })
    }

    @Test
    fun a_prompt_blocks_other_cart_changes_and_escape_closes_it() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        vm.addProduct(JAMON)

        vm.addProduct(TORTA)
        runCurrent()
        assertTrue(repo.mutationLog.isEmpty())

        assertTrue(vm.onEscape())
        assertNull(vm.state.value.editing?.prompt)
        assertIs<OrdersView.Editing>(vm.state.value.view)
    }

    @Test
    fun product_search_waits_for_two_characters_and_debounce() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)

        vm.onProductQuery("t")
        advanceUntilIdle()
        assertTrue(repo.productQueries.isEmpty())

        vm.onProductQuery("to")
        advanceTimeBy(OrdersViewModel.PRODUCT_SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(repo.productQueries.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("to"), repo.productQueries)
        assertEquals(listOf(TORTA), vm.state.value.editing?.productResults)

        vm.addProduct(TORTA)
        assertEquals("", vm.state.value.editing?.productQuery)
        assertTrue(vm.state.value.editing!!.productResults.isEmpty())
    }

    @Test
    fun a_refetched_conflict_shows_the_server_cart() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        val serverLines = listOf(pieceLine(5L, 75L, 3.0, 75.0))
        repo.nextMutation = OrderMutationResult.ConflictRefetched(
            sampleOrder(version = 9, lines = serverLines).cart,
        )

        vm.addProduct(TORTA)
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(serverLines, form.lines)
        assertEquals(9, form.version)
        assertEquals(OrdersStrings.CONFLICT, form.errorMessage)
    }

    @Test
    fun a_closed_order_leaves_the_editor() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        repo.nextMutation = OrderMutationResult.Closed(sampleOrder(status = OrderStatus.CANCELLED).cart)

        vm.addProduct(TORTA)
        advanceUntilIdle()

        assertEquals(OrdersView.List, vm.state.value.view)
        assertNull(vm.state.value.editing)
        assertEquals(OrdersStrings.ORDER_CLOSED, vm.state.value.notice)
    }

    @Test
    fun back_on_an_empty_order_cancels_it_quietly() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)

        vm.back()
        advanceUntilIdle()

        assertEquals(listOf(7L to 1), repo.cancelEmpties)
        assertEquals(OrdersView.List, vm.state.value.view)
        assertTrue(repo.cancels.isEmpty())
    }

    @Test
    fun back_with_unsaved_changes_asks_first() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = readyToSave(repo)

        vm.back()
        assertTrue(vm.state.value.editing!!.confirmLeave)

        assertTrue(vm.onEscape())
        assertFalse(vm.state.value.editing!!.confirmLeave)

        vm.back()
        vm.confirmLeave()
        advanceUntilIdle()

        assertEquals(OrdersView.List, vm.state.value.view)
        assertTrue(repo.cancelEmpties.isEmpty())
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun back_waits_for_pending_lines() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        repo.mutationGate = CompletableDeferred()

        vm.addProduct(TORTA)
        runCurrent()
        vm.back()

        assertEquals(OrdersStrings.WAIT_FOR_LINES, vm.state.value.editing?.saveHint)
        assertNull(vm.state.value.editing?.errorMessage)
        assertIs<OrdersView.Editing>(vm.state.value.view)
        repo.mutationGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun opening_a_saved_order_fills_the_form() = runTest(dispatcher) {
        val saved = sampleOrder(
            id = 8L,
            version = 4,
            customer = LUPE,
            address = "Otra calle 9",
            lines = listOf(pieceLine(1L, 50L, 2.0, 50.0)),
            paysWith = 200.0,
        )
        val repo = FakeOrdersRepository(orders = mutableMapOf(8L to saved))
        val vm = viewModel(repo)

        vm.open(8L)
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertFalse(form.isNew)
        assertEquals(LUPE.id, form.selected?.id)
        assertEquals("Otra calle 9", form.address)
        assertEquals("200.00", form.paysWithInput)
        assertEquals(100.0, form.change)
        assertTrue(form.canMarkSent)
        assertFalse(form.hasUnsavedChanges)
        assertEquals(OrderFocus.PRODUCT, form.focus)

        vm.back()
        advanceUntilIdle()
        assertEquals(OrdersView.List, vm.state.value.view)
        assertTrue(repo.cancelEmpties.isEmpty())
    }

    @Test
    fun mark_sent_from_the_card_uses_the_listed_version() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(openResult = AppResult.Success(listOf(sampleOrder(id = 3L, version = 5))))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.markSent(3L)
        advanceUntilIdle()

        assertEquals(listOf(Triple(3L, OrderStage.ENVIADO, 5)), repo.stages)
        assertEquals(OrdersStrings.SENT, vm.state.value.notice)
        assertNull(vm.state.value.busyOrderId)
    }

    @Test
    fun mark_sent_is_ignored_for_an_order_already_sent() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(openResult = AppResult.Success(listOf(sampleOrder(id = 3L, stage = OrderStage.ENVIADO))))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.markSent(3L)
        advanceUntilIdle()

        assertTrue(repo.stages.isEmpty())
    }

    @Test
    fun mark_sent_from_the_editor_needs_a_saved_unchanged_order() = runTest(dispatcher) {
        val saved = sampleOrder(id = 8L, version = 4, customer = LUPE, address = "Calle", lines = listOf(pieceLine(1L, 50L, 1.0, 50.0)))
        val repo = FakeOrdersRepository(orders = mutableMapOf(8L to saved))
        val vm = viewModel(repo)
        vm.open(8L)
        advanceUntilIdle()

        vm.onNotes("sin cebolla")
        assertFalse(vm.state.value.editing!!.canMarkSent)
        vm.markSent(8L)
        assertTrue(repo.stages.isEmpty())

        vm.onNotes("")
        vm.save()
        advanceUntilIdle()
        vm.open(8L)
        advanceUntilIdle()
        vm.markSent(8L)
        advanceUntilIdle()

        assertEquals(listOf(Triple(8L, OrderStage.ENVIADO, 4)), repo.stages)
        assertEquals(OrdersView.List, vm.state.value.view)
    }

    @Test
    fun new_orders_cannot_be_marked_sent() = runTest(dispatcher) {
        val vm = readyToSave(FakeOrdersRepository())
        assertFalse(vm.state.value.editing!!.canMarkSent)
    }

    @Test
    fun cancel_other_needs_a_note() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(openResult = AppResult.Success(listOf(sampleOrder(id = 3L, version = 2))))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.startCancel(3L)
        vm.pickCancelReason(CancelReason.OTRO)
        assertTrue(vm.state.value.cancelDialog!!.needsNotes)
        assertFalse(vm.state.value.cancelDialog!!.canConfirm)
        vm.confirmCancel()
        advanceUntilIdle()
        assertTrue(repo.cancels.isEmpty())

        vm.onCancelNotes("  Se fue  ")
        vm.confirmCancel()
        advanceUntilIdle()

        assertEquals(listOf<Pair<CancelReason, String?>>(CancelReason.OTRO to "Se fue"), repo.cancels)
        assertNull(vm.state.value.cancelDialog)
        assertEquals(OrdersStrings.CANCELLED, vm.state.value.notice)
    }

    @Test
    fun cancel_from_the_editor_leaves_to_the_list() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = readyToSave(repo)

        vm.startCancel(7L)
        vm.pickCancelReason(CancelReason.NO_CONTESTO)
        vm.confirmCancel()
        advanceUntilIdle()

        assertEquals(listOf<Pair<CancelReason, String?>>(CancelReason.NO_CONTESTO to null), repo.cancels)
        assertEquals(OrdersView.List, vm.state.value.view)
        assertNull(vm.state.value.editing)
    }

    @Test
    fun cancel_failure_stays_in_the_dialog_and_escape_closes_it() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(openResult = AppResult.Success(listOf(sampleOrder(id = 3L))))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()
        repo.cancelResult = AppResult.Failure(AppError.Network("down"))

        vm.startCancel(3L)
        vm.pickCancelReason(CancelReason.SIN_DINERO)
        vm.confirmCancel()
        advanceUntilIdle()

        val dialog = assertNotNull(vm.state.value.cancelDialog)
        assertFalse(dialog.isSubmitting)
        assertEquals(OrdersStrings.NETWORK, dialog.errorMessage)
        assertTrue(vm.onEscape())
        assertNull(vm.state.value.cancelDialog)
    }

    @Test
    fun save_on_a_closed_order_returns_to_the_list() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = readyToSave(repo)
        repo.updateResult = AppResult.Failure(AppError.Validation("Order is not open"))

        vm.save()
        advanceUntilIdle()

        assertEquals(OrdersView.List, vm.state.value.view)
        assertEquals(OrdersStrings.ORDER_CLOSED, vm.state.value.notice)
    }

    @Test
    fun fields_are_locked_while_saving() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = readyToSave(repo)

        vm.save()
        vm.onAddress("cambio")
        vm.onPaysWith("1")

        assertEquals("Calle 5 #12", vm.state.value.editing?.address)
        assertEquals("", vm.state.value.editing?.paysWithInput)
        advanceUntilIdle()
    }

    @Test
    fun escape_on_the_list_is_not_consumed() = runTest(dispatcher) {
        val vm = viewModel(FakeOrdersRepository())
        assertFalse(vm.onEscape())
    }

    @Test
    fun save_hint_names_the_first_missing_piece() = runTest(dispatcher) {
        val vm = editingNew()
        assertEquals(OrdersStrings.MISSING_CUSTOMER, vm.state.value.editing?.saveHint)

        vm.onCustomerQuery("rosa")
        advanceUntilIdle()
        vm.pickCustomer(ROSA.id)
        assertEquals(OrdersStrings.MISSING_ADDRESS, vm.state.value.editing?.saveHint)

        vm.onAddress("Av. 3")
        assertEquals(OrdersStrings.MISSING_LINES, vm.state.value.editing?.saveHint)

        vm.addProduct(TORTA)
        advanceUntilIdle()
        assertNull(vm.state.value.editing?.saveHint)
    }

    @Test
    fun clearing_pays_with_on_a_saved_order_sends_zero() = runTest(dispatcher) {
        val saved = sampleOrder(id = 8L, customer = LUPE, address = "Calle", lines = listOf(pieceLine(1L, 50L, 1.0, 50.0)), paysWith = 200.0)
        val repo = FakeOrdersRepository(orders = mutableMapOf(8L to saved))
        val vm = viewModel(repo)
        vm.open(8L)
        advanceUntilIdle()

        vm.onPaysWith("")
        vm.save()
        advanceUntilIdle()

        assertEquals(0.0, repo.updates.single().third.paysWith)
    }

    @Test
    fun an_empty_pays_with_on_a_new_order_is_not_sent() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = readyToSave(repo)

        vm.save()
        advanceUntilIdle()

        assertNull(repo.updates.single().third.paysWith)
    }

    @Test
    fun zero_pays_with_counts_as_not_captured() = runTest(dispatcher) {
        val zero = sampleOrder(id = 8L, customer = LUPE, address = "Calle", lines = listOf(pieceLine(1L, 50L, 1.0, 50.0)), paysWith = 0.0)
        assertNull(zero.capturedPaysWith)
        assertNull(zero.copy(change = -50.0).shownChange)
        assertEquals(10.0, zero.copy(paysWith = 60.0, change = 10.0).shownChange)
        val repo = FakeOrdersRepository(orders = mutableMapOf(8L to zero))
        val vm = viewModel(repo)
        vm.open(8L)
        advanceUntilIdle()

        assertEquals("", vm.state.value.editing?.paysWithInput)
        assertNull(vm.state.value.editing?.change)
        vm.onPaysWith("0")
        assertNull(vm.state.value.editing?.change)
    }

    @Test
    fun leaving_with_a_picked_but_unsaved_customer_and_no_lines_discards_the_order() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        vm.onCustomerQuery("lupe")
        advanceUntilIdle()
        vm.pickCustomer(LUPE.id)

        vm.back()
        assertTrue(vm.state.value.editing!!.confirmLeave)
        vm.confirmLeave()
        advanceUntilIdle()

        assertEquals(listOf(7L to 1), repo.cancelEmpties)
        assertEquals(OrdersView.List, vm.state.value.view)
    }

    @Test
    fun a_saved_order_with_customer_is_never_discarded_even_without_lines() = runTest(dispatcher) {
        val saved = sampleOrder(id = 8L, version = 4, customer = LUPE, address = "Calle", lines = listOf(pieceLine(1L, 50L, 1.0, 50.0)))
        val repo = FakeOrdersRepository(orders = mutableMapOf(8L to saved))
        val vm = viewModel(repo)
        vm.open(8L)
        advanceUntilIdle()

        vm.removeLine(1L)
        advanceUntilIdle()
        vm.clearCustomer()
        vm.back()
        vm.confirmLeave()
        advanceUntilIdle()

        assertTrue(repo.cancelEmpties.isEmpty())
        assertEquals(OrdersView.List, vm.state.value.view)
    }

    @Test
    fun a_failed_discard_says_so_and_reloads() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        repo.cancelEmptyResult = AppResult.Failure(AppError.Network("down"))
        val vm = editingNew(repo)
        val callsBefore = repo.openCalls

        vm.back()
        advanceUntilIdle()

        assertEquals(OrdersStrings.DISCARD_FAILED, vm.state.value.notice)
        assertTrue(repo.openCalls > callsBefore)
    }

    @Test
    fun customer_search_failure_is_shown_instead_of_an_empty_list() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        repo.customerSearchResult = AppResult.Failure(AppError.Network("down"))
        val vm = editingNew(repo)

        vm.onCustomerQuery("lupe")
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(OrdersStrings.CUSTOMER_SEARCH_FAILED, form.customerSearchError)
        assertFalse(form.showsNoCustomerMatch)

        vm.onCustomerQuery("lup")
        assertNull(vm.state.value.editing?.customerSearchError)
    }

    @Test
    fun no_match_shows_only_after_the_search_finished() = runTest(dispatcher) {
        val vm = editingNew()

        vm.onCustomerQuery("zzz")
        assertFalse(vm.state.value.editing!!.showsNoCustomerMatch)
        advanceUntilIdle()

        assertTrue(vm.state.value.editing!!.showsNoCustomerMatch)
    }

    @Test
    fun an_open_cancel_dialog_locks_the_cart_and_fields() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = readyToSave(repo)
        val logBefore = repo.mutationLog.size

        vm.startCancel(7L)
        vm.addProduct(COMIDA)
        vm.onAddress("otra")
        runCurrent()

        assertEquals(logBefore, repo.mutationLog.size)
        assertEquals("Calle 5 #12", vm.state.value.editing?.address)
        assertTrue(vm.state.value.editorLocked)
    }

    @Test
    fun a_prompt_locks_the_form_fields() = runTest(dispatcher) {
        val vm = editingNew()
        vm.addProduct(JAMON)

        vm.onAddress("otra")

        assertEquals("", vm.state.value.editing?.address)
        assertTrue(vm.state.value.editorLocked)
    }

    @Test
    fun cancel_from_a_prompt_closes_the_prompt_and_escape_closes_the_dialog_first() = runTest(dispatcher) {
        val vm = readyToSave(FakeOrdersRepository())
        vm.addProduct(JAMON)

        vm.startCancel(7L)
        assertNull(vm.state.value.editing?.prompt)
        assertNotNull(vm.state.value.cancelDialog)

        assertTrue(vm.onEscape())
        assertNull(vm.state.value.cancelDialog)
        assertIs<OrdersView.Editing>(vm.state.value.view)
        assertFalse(vm.state.value.editing!!.confirmLeave)
    }

    @Test
    fun a_second_open_while_loading_is_ignored() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(
            orders = mutableMapOf(
                8L to sampleOrder(id = 8L, customer = LUPE),
                9L to sampleOrder(id = 9L, customer = ROSA),
            ),
        )
        val gate = CompletableDeferred<Unit>()
        repo.orderGate = gate
        val vm = viewModel(repo)

        vm.open(8L)
        runCurrent()
        vm.open(9L)
        vm.newOrder()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(8L, vm.state.value.editing?.orderId)
        assertTrue(repo.createKeys.isEmpty())
        assertNull(vm.state.value.openingId)
    }

    @Test
    fun opening_a_closed_order_stays_on_the_list() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(orders = mutableMapOf(8L to sampleOrder(id = 8L, status = OrderStatus.PAID)))
        val vm = viewModel(repo)

        vm.open(8L)
        advanceUntilIdle()

        assertEquals(OrdersView.List, vm.state.value.view)
        assertEquals(OrdersStrings.ORDER_CLOSED, vm.state.value.notice)
    }

    @Test
    fun queued_mutations_are_dropped_when_the_editor_closes() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        repo.nextMutation = OrderMutationResult.Closed(sampleOrder(status = OrderStatus.CANCELLED).cart)

        vm.addProduct(TORTA)
        vm.addProduct(COMIDA)
        vm.addProduct(TORTA)
        advanceUntilIdle()

        assertEquals(1, repo.mutationLog.size)
        assertNull(vm.state.value.editing)
        assertEquals(OrdersView.List, vm.state.value.view)
    }

    @Test
    fun a_late_result_for_another_order_never_touches_the_open_editor() = runTest(dispatcher) {
        val repo = FakeOrdersRepository(orders = mutableMapOf(8L to sampleOrder(id = 8L, version = 3, customer = LUPE)))
        val vm = editingNew(repo)
        val gate = CompletableDeferred<Unit>()
        repo.mutationGate = gate
        repo.nextMutation = OrderMutationResult.Closed(sampleOrder(status = OrderStatus.CANCELLED).cart)

        vm.addProduct(TORTA)
        vm.addProduct(COMIDA)
        runCurrent()
        val stale = OrderMutationResult.ConflictRefetched(sampleOrder(version = 20, lines = listOf(pieceLine(5L, 1L, 9.0, 1.0))).cart)
        gate.complete(Unit)
        repo.mutationGate = null
        runCurrent()
        repo.nextMutation = stale
        vm.open(8L)
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(8L, form.orderId)
        assertEquals(3, form.version)
        assertTrue(form.lines.isEmpty())
        assertNull(form.errorMessage)
        assertEquals(1, repo.mutationLog.size)
        assertEquals(stale, repo.nextMutation)
    }

    private fun TestScope.withNewCustomer(repo: FakeOrdersRepository, phone: String = "5512345678"): OrdersViewModel {
        val vm = editingNew(repo)
        vm.startNewCustomer()
        vm.onNewCustomerName(" Ana ")
        vm.onNewCustomerPhone(phone)
        vm.onAddress("Av. 3")
        return vm
    }

    @Test
    fun guardar_cliente_creates_and_selects_the_customer() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = withNewCustomer(repo)
        assertFalse(vm.state.value.editing!!.canSave)

        vm.saveNewCustomer()
        assertTrue(vm.state.value.editing!!.isSavingCustomer)
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(listOf<Triple<String, String?, String?>>(Triple("Ana", "5512345678", "Av. 3")), repo.createdCustomers)
        assertEquals(30L, form.selected?.id)
        assertNull(form.newCustomer)
        assertEquals("Av. 3", form.address)
        assertFalse(form.isSavingCustomer)
        assertEquals(OrdersStrings.MISSING_LINES, form.saveHint)
    }

    @Test
    fun order_save_needs_a_selected_customer_and_never_creates_one() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = withNewCustomer(repo)
        vm.addProduct(TORTA)
        advanceUntilIdle()

        assertFalse(vm.state.value.editing!!.canSave)
        assertEquals(OrdersStrings.MISSING_CUSTOMER, vm.state.value.editing!!.saveHint)
        vm.save()
        advanceUntilIdle()
        assertTrue(repo.createdCustomers.isEmpty())
        assertTrue(repo.updates.isEmpty())

        vm.saveNewCustomer()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(30L, repo.updates.single().third.customerId)
        assertEquals(1, repo.createdCustomers.size)
    }

    @Test
    fun guardar_cliente_with_an_incomplete_phone_is_blocked() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = withNewCustomer(repo, phone = "55123")

        vm.saveNewCustomer()
        advanceUntilIdle()

        assertEquals(OrdersStrings.PHONE_INCOMPLETE, vm.state.value.editing?.customerError)
        assertTrue(repo.createdCustomers.isEmpty())
        assertNotNull(vm.state.value.editing?.newCustomer)
    }

    @Test
    fun guardar_cliente_with_a_digits_only_name_is_blocked() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = editingNew(repo)
        vm.startNewCustomer()
        vm.onNewCustomerName("12345")

        vm.saveNewCustomer()

        assertEquals(OrdersStrings.NAME_INVALID, vm.state.value.editing?.customerError)
        assertTrue(repo.createdCustomers.isEmpty())
    }

    @Test
    fun guardar_cliente_network_failure_keeps_the_form_for_another_tap() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = withNewCustomer(repo)
        repo.createCustomerResults += AppResult.Failure(AppError.Network("down"))

        vm.saveNewCustomer()
        advanceUntilIdle()

        val form = assertNotNull(vm.state.value.editing)
        assertEquals(OrdersStrings.NETWORK, form.customerError)
        assertEquals("Ana", form.newCustomer?.name?.trim())
        assertEquals("5512345678", form.newCustomer?.phone)
        assertNull(form.selected)
        assertFalse(form.isSavingCustomer)

        vm.saveNewCustomer()
        advanceUntilIdle()
        assertEquals(30L, vm.state.value.editing?.selected?.id)
        assertEquals(2, repo.createdCustomers.size)
    }

    @Test
    fun guardar_cliente_conflict_asks_to_check_the_phone() = runTest(dispatcher) {
        val repo = FakeOrdersRepository()
        val vm = withNewCustomer(repo)
        repo.createCustomerResults += AppResult.Failure(AppError.Conflict)

        vm.saveNewCustomer()
        advanceUntilIdle()

        assertEquals(OrdersStrings.CUSTOMER_SAVE_CONFLICT, vm.state.value.editing?.customerError)
        assertNotNull(vm.state.value.editing?.newCustomer)
    }

    @Test
    fun editing_the_new_customer_clears_its_error() = runTest(dispatcher) {
        val vm = withNewCustomer(FakeOrdersRepository(), phone = "55")
        vm.saveNewCustomer()
        assertNotNull(vm.state.value.editing?.customerError)

        vm.onNewCustomerPhone("5512345678")

        assertNull(vm.state.value.editing?.customerError)
    }

    @Test
    fun weight_preview_uses_the_cash_price() = runTest(dispatcher) {
        val vm = editingNew()
        vm.addProduct(JAMON.copy(cashPrice = 120.0))

        vm.pickWeight(0.5)

        assertEquals(60.0, vm.state.value.editing?.prompt?.previewTotal)
    }
}
