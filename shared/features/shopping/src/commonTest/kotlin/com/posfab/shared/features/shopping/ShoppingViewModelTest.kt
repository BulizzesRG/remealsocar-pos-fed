package com.posfab.shared.features.shopping

import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.shopping.domain.ShoppingCurrent
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingListStatus
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.ui.ShoppingConfirm
import com.posfab.shared.features.shopping.ui.ShoppingStrings
import com.posfab.shared.features.shopping.ui.ShoppingView
import com.posfab.shared.features.shopping.ui.ShoppingViewModel
import com.posfab.shared.features.shopping.usecase.ShoppingUseCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = TimeZone.of("America/Mexico_City")
    private var clockMs = Instant.parse("2026-10-18T17:00:00Z").toEpochMilliseconds()

    private val manager = setOf(Permission.SELL, Permission.MANAGE_INVENTORY)
    private val cashier = setOf(Permission.SELL)

    private fun viewModel(repo: FakeShoppingRepository, permissions: Set<Permission> = manager) = ShoppingViewModel(
        ShoppingUseCases(repo),
        permissions,
        now = { Instant.fromEpochMilliseconds(clockMs) },
        timeZone = zone,
        dispatcher = dispatcher,
    )

    private fun TestScope.loaded(repo: FakeShoppingRepository, permissions: Set<Permission> = manager): ShoppingViewModel {
        val vm = viewModel(repo, permissions)
        vm.load()
        advanceUntilIdle()
        return vm
    }

    private fun ShoppingViewModel.typeSlowly(text: String) {
        text.forEach { char ->
            clockMs += 400
            onInputChange(state.value.input + char)
        }
    }

    private fun ShoppingViewModel.scan(code: String) {
        code.forEach { char ->
            clockMs += 10
            onInputChange(state.value.input + char)
        }
        clockMs += 20
        onInputSubmit()
    }

    private val ShoppingViewModel.openItems get() = requireNotNull(state.value.open).items

    private val ShoppingViewModel.toShopItems get() = requireNotNull(state.value.toShop).items

    private fun openWith(vararg ids: Long) = shoppingList(10L, items = ids.map { shoppingItem(it) })

    private fun toShopWith(vararg items: ShoppingItem) =
        shoppingList(9L, ShoppingListStatus.TO_SHOP, items.toList())

    @Test
    fun load_brings_the_open_list_and_the_list_to_shop() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L, 2L), toShop = toShopWith(shoppingItem(5L)))
        val vm = viewModel(repo)

        vm.load()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L), state.open?.items?.map { it.id })
        assertEquals(9L, state.toShop?.id)
        assertNull(state.errorMessage)
    }

    @Test
    fun an_older_load_never_overwrites_a_newer_one() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val older = CompletableDeferred<Unit>()
        val newer = CompletableDeferred<Unit>()
        repo.currentGates += older
        repo.currentGates += newer
        repo.currentResults += AppResult.Success(ShoppingCurrent(open = openWith(1L)))
        repo.currentResults += AppResult.Success(ShoppingCurrent(open = openWith(2L)))
        val vm = viewModel(repo)

        vm.load()
        runCurrent()
        vm.load()
        runCurrent()
        newer.complete(Unit)
        advanceUntilIdle()
        older.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(2L), vm.state.value.open?.items?.map { it.id })
    }

    @Test
    fun load_failure_keeps_the_lists_and_shows_a_message_that_a_good_load_clears() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        val vm = loaded(repo)

        repo.currentResults += AppResult.Failure(AppError.Network("down"))
        vm.load()
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.state.value.open?.items?.map { it.id })
        assertEquals(ShoppingStrings.NETWORK, vm.state.value.errorMessage)

        vm.load()
        advanceUntilIdle()
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun can_close_follows_manage_inventory() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L), toShop = toShopWith(shoppingItem(5L)))

        val boss = loaded(repo)
        val seller = loaded(repo, cashier)

        assertTrue(boss.state.value.canClose)
        assertTrue(boss.state.value.showCloseButton)
        assertTrue(boss.state.value.showFinishButton)
        assertFalse(seller.state.value.canClose)
        assertFalse(seller.state.value.showCloseButton)
        assertFalse(seller.state.value.showFinishButton)
    }

    @Test
    fun enter_with_text_adds_a_free_item_and_clears_the_box() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.typeSlowly("jitomate")
        vm.onQtyChange("2.5")
        vm.onInputSubmit()
        advanceUntilIdle()

        val call = repo.addCalls.single()
        assertEquals("jitomate", call.text)
        assertNull(call.productId)
        assertEquals(2.5, call.qty)
        assertTrue(call.key.startsWith("shop-"))
        val state = vm.state.value
        assertEquals("", state.input)
        assertEquals("", state.qtyInput)
        assertEquals(ShoppingStrings.added("jitomate"), state.notice)
        assertNull(state.pendingAddKey)
        assertEquals(listOf("jitomate"), state.open?.items?.map { it.text })
    }

    @Test
    fun blank_enter_sends_nothing() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.onInputChange("   ")
        vm.onInputSubmit()
        vm.addFromButton()
        advanceUntilIdle()

        assertTrue(repo.addCalls.isEmpty())
        assertTrue(repo.barcodeCalls.isEmpty())
    }

    @Test
    fun text_longer_than_120_is_rejected_before_sending() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.onInputChange("a".repeat(121))
        vm.addFromButton()
        advanceUntilIdle()

        assertTrue(repo.addCalls.isEmpty())
        assertEquals(ShoppingStrings.TEXT_TOO_LONG, vm.state.value.inputError)
    }

    @Test
    fun a_scan_burst_links_the_product_even_after_leftover_text() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.typeSlowly("jit")
        vm.scan("1234567891234")
        advanceUntilIdle()

        assertEquals(listOf("1234567891234"), repo.barcodeCalls)
        val call = repo.addCalls.single()
        assertEquals("Coca cola", call.text)
        assertEquals(COCA.id, call.productId)
        assertEquals("", vm.state.value.input)
        assertEquals(ShoppingStrings.added("Coca cola"), vm.state.value.notice)
    }

    @Test
    fun an_unknown_code_shows_a_message_and_clears_the_box() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.scan("7501000000001")
        advanceUntilIdle()

        assertTrue(repo.addCalls.isEmpty())
        assertEquals(ShoppingStrings.notFoundWithCode("7501000000001"), vm.state.value.errorMessage)
        assertEquals("", vm.state.value.input)
        assertFalse(vm.state.value.isAdding)
    }

    @Test
    fun slowly_typed_digits_between_6_and_80_are_looked_up_as_a_code() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.typeSlowly("20000059")
        vm.onInputSubmit()
        advanceUntilIdle()

        assertEquals(listOf("20000059"), repo.barcodeCalls)
        assertEquals(JAMON.id, repo.addCalls.single().productId)
    }

    @Test
    fun five_digits_are_free_text_not_a_code() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.typeSlowly("12345")
        vm.onInputSubmit()
        advanceUntilIdle()

        assertTrue(repo.barcodeCalls.isEmpty())
        assertEquals("12345", repo.addCalls.single().text)
    }

    @Test
    fun a_lookup_network_failure_puts_the_code_back_for_retry() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        repo.barcodeFailure = AppError.Network("down")
        val vm = loaded(repo)

        vm.scan("1234567891234")
        advanceUntilIdle()

        assertEquals("1234567891234", vm.state.value.input)
        assertEquals(ShoppingStrings.NETWORK, vm.state.value.errorMessage)
        assertTrue(repo.addCalls.isEmpty())
    }

    @Test
    fun suggestions_appear_from_two_letters_after_the_debounce() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.onInputChange("j")
        advanceTimeBy(ShoppingViewModel.SUGGESTION_DEBOUNCE_MS + 10)
        assertTrue(repo.searchCalls.isEmpty())

        vm.onInputChange("ja")
        advanceTimeBy(ShoppingViewModel.SUGGESTION_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(repo.searchCalls.isEmpty())
        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("ja"), repo.searchCalls)
        assertEquals(listOf(JAMON), vm.state.value.suggestions)
    }

    @Test
    fun digits_never_ask_for_suggestions() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.typeSlowly("750")
        advanceUntilIdle()

        assertTrue(repo.searchCalls.isEmpty())
        assertTrue(vm.state.value.suggestions.isEmpty())
    }

    @Test
    fun stale_suggestion_results_are_dropped() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val slow = CompletableDeferred<Unit>()
        repo.searchGates["co"] = slow
        val vm = loaded(repo)

        vm.onInputChange("co")
        advanceTimeBy(ShoppingViewModel.SUGGESTION_DEBOUNCE_MS + 1)
        runCurrent()
        vm.onInputChange("jam")
        advanceUntilIdle()
        slow.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(JAMON), vm.state.value.suggestions)
    }

    @Test
    fun picking_a_suggestion_adds_a_linked_item_with_the_quantity() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)
        vm.onInputChange("jam")
        advanceUntilIdle()

        vm.onQtyChange("3")
        vm.pickSuggestion(JAMON)
        advanceUntilIdle()

        val call = repo.addCalls.single()
        assertEquals("Jamón", call.text)
        assertEquals(JAMON.id, call.productId)
        assertEquals(3.0, call.qty)
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertEquals("", vm.state.value.input)
    }

    @Test
    fun the_idempotency_key_survives_only_a_network_error_and_is_reused() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        repo.addResults += AppResult.Failure(AppError.Network("down"))
        val vm = loaded(repo)

        vm.onInputChange("cebolla")
        vm.onInputSubmit()
        advanceUntilIdle()
        val firstKey = repo.addCalls.single().key
        assertEquals(firstKey, vm.state.value.pendingAddKey)
        assertEquals("cebolla", vm.state.value.input)
        assertEquals(ShoppingStrings.NETWORK, vm.state.value.errorMessage)

        vm.onInputSubmit()
        advanceUntilIdle()

        assertEquals(listOf(firstKey, firstKey), repo.addCalls.map { it.key })
        assertNull(vm.state.value.pendingAddKey)
        assertEquals(1, vm.state.value.open?.items?.size)
    }

    @Test
    fun a_validation_error_drops_the_key() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        repo.addResults += AppResult.Failure(AppError.Validation("Product is inactive"))
        val vm = loaded(repo)

        vm.onInputChange("cebolla")
        vm.onInputSubmit()
        advanceUntilIdle()
        assertNull(vm.state.value.pendingAddKey)
        assertEquals(ShoppingStrings.PRODUCT_GONE, vm.state.value.errorMessage)

        vm.onInputSubmit()
        advanceUntilIdle()

        assertNotEquals(repo.addCalls[0].key, repo.addCalls[1].key)
    }

    @Test
    fun changing_the_text_after_a_network_error_uses_a_new_key() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        repo.addResults += AppResult.Failure(AppError.Network("down"))
        val vm = loaded(repo)

        vm.onInputChange("cebolla")
        vm.onInputSubmit()
        advanceUntilIdle()
        vm.onInputChange("cebolla morada")
        vm.onInputSubmit()
        advanceUntilIdle()

        assertNotEquals(repo.addCalls[0].key, repo.addCalls[1].key)
    }

    @Test
    fun two_fast_enters_create_a_single_item() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val gate = CompletableDeferred<Unit>()
        repo.addGate = gate
        val vm = loaded(repo)

        vm.onInputChange("aceite")
        vm.onInputSubmit()
        runCurrent()
        vm.onInputSubmit()
        vm.addFromButton()
        runCurrent()
        assertTrue(vm.state.value.isAdding)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.addCalls.size)
        assertFalse(vm.state.value.isAdding)
    }

    @Test
    fun a_second_scan_typed_while_the_first_is_being_added_is_not_wiped() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val gate = CompletableDeferred<Unit>()
        repo.addGate = gate
        val vm = loaded(repo)

        vm.scan("1234567891234")
        runCurrent()
        assertTrue(vm.state.value.isAdding)
        vm.scan("20000059")
        runCurrent()
        assertEquals(1, repo.addCalls.size)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("20000059", vm.state.value.input)
        assertFalse(vm.state.value.isAdding)
    }

    @Test
    fun enter_is_ignored_while_a_dialog_is_open() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L, 2L))
        val vm = loaded(repo)

        vm.requestClose()
        assertNotNull(vm.state.value.confirm)
        vm.onInputChange("aceite")
        vm.onInputSubmit()
        advanceUntilIdle()

        assertEquals(0, repo.addCalls.size)
        assertEquals("aceite", vm.state.value.input)
    }

    @Test
    fun quantity_input_is_sanitized() = runTest(dispatcher) {
        val vm = loaded(FakeShoppingRepository())

        vm.onQtyChange("abc")
        assertEquals("", vm.state.value.qtyInput)
        vm.onQtyChange("1.2345")
        assertEquals("1.234", vm.state.value.qtyInput)
        vm.onQtyChange("1.2.3")
        assertEquals("1.23", vm.state.value.qtyInput)
        vm.onQtyChange("2 kg")
        assertEquals("2", vm.state.value.qtyInput)
    }

    @Test
    fun a_zero_quantity_is_rejected_with_a_message() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)

        vm.onInputChange("huevo")
        vm.onQtyChange("0")
        vm.onInputSubmit()
        vm.onQtyChange(".")
        vm.addFromButton()
        advanceUntilIdle()

        assertTrue(repo.addCalls.isEmpty())
        assertEquals(ShoppingStrings.QTY_INVALID, vm.state.value.inputError)
        assertEquals("huevo", vm.state.value.input)
    }

    @Test
    fun editing_saves_the_item_and_shows_saved() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = shoppingList(10L, items = listOf(shoppingItem(1L, "coca", productId = 1L, qty = 2.0))))
        val vm = loaded(repo)

        vm.startEdit(vm.openItems.single())
        assertEquals("2", vm.state.value.edit?.qtyInput)
        vm.updateEditText("coca 600")
        vm.updateEditQty("3")
        vm.saveEdit()
        advanceUntilIdle()

        assertEquals(UpdateCall(1L, "coca 600", 1L, 3.0), repo.updateCalls.single())
        assertNull(vm.state.value.edit)
        assertEquals(ShoppingStrings.SAVED, vm.state.value.notice)
        assertEquals("coca 600", vm.state.value.open?.items?.single()?.text)
    }

    @Test
    fun saving_an_unchanged_edit_just_closes_it() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        val vm = loaded(repo)

        vm.startEdit(vm.openItems.single())
        vm.saveEdit()
        advanceUntilIdle()

        assertTrue(repo.updateCalls.isEmpty())
        assertNull(vm.state.value.edit)
    }

    @Test
    fun editing_validates_text_and_quantity() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        val vm = loaded(repo)
        vm.startEdit(vm.openItems.single())

        vm.updateEditText("   ")
        vm.saveEdit()
        assertEquals(ShoppingStrings.TEXT_REQUIRED, vm.state.value.edit?.error)

        vm.updateEditText("x".repeat(121))
        vm.saveEdit()
        assertEquals(ShoppingStrings.TEXT_TOO_LONG, vm.state.value.edit?.error)

        vm.updateEditText("leche")
        vm.updateEditQty("0")
        vm.saveEdit()
        assertEquals(ShoppingStrings.QTY_INVALID, vm.state.value.edit?.error)
        advanceUntilIdle()
        assertTrue(repo.updateCalls.isEmpty())
    }

    @Test
    fun items_of_the_list_to_shop_cannot_be_edited() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L), toShop = toShopWith(shoppingItem(5L)))
        val vm = loaded(repo)

        vm.startEdit(vm.toShopItems.single())
        vm.requestDelete(5L)

        assertNull(vm.state.value.edit)
        assertNull(vm.state.value.confirm)
    }

    @Test
    fun a_conflict_while_editing_reloads_and_says_the_list_was_closed() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        val vm = loaded(repo)
        vm.startEdit(vm.openItems.single())
        repo.updateResults += AppResult.Failure(AppError.Conflict)
        repo.toShop = repo.open?.copy(status = ShoppingListStatus.TO_SHOP)
        repo.open = null
        val callsBefore = repo.currentCalls

        vm.updateEditText("otro")
        vm.saveEdit()
        advanceUntilIdle()

        assertNull(vm.state.value.edit)
        assertEquals(ShoppingStrings.LIST_CLOSED, vm.state.value.notice)
        assertEquals(callsBefore + 1, repo.currentCalls)
        assertNull(vm.state.value.open)
        assertNotNull(vm.state.value.toShop)
    }

    @Test
    fun removing_asks_first_and_dismiss_keeps_the_item() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L, 2L))
        val vm = loaded(repo)
        vm.startEdit(vm.openItems.first())

        vm.requestDelete(1L)
        assertIs<ShoppingConfirm.DeleteItem>(vm.state.value.confirm)
        vm.dismissConfirm()
        advanceUntilIdle()
        assertTrue(repo.deleteCalls.isEmpty())
        assertNotNull(vm.state.value.edit)

        vm.requestDelete(1L)
        vm.confirm()
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.deleteCalls)
        assertNull(vm.state.value.confirm)
        assertNull(vm.state.value.edit)
        assertEquals(listOf(2L), vm.state.value.open?.items?.map { it.id })
        assertEquals(ShoppingStrings.removed("renglón 1"), vm.state.value.notice)
    }

    @Test
    fun marking_bought_and_tapping_again_returns_to_pending() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L)))
        val vm = loaded(repo)

        vm.setStatus(vm.toShopItems.single(), ShoppingItemStatus.BOUGHT)
        advanceUntilIdle()
        assertEquals(ShoppingItemStatus.BOUGHT, vm.state.value.toShop?.items?.single()?.status)
        assertEquals(1, vm.state.value.toShop?.boughtCount)

        vm.setStatus(vm.toShopItems.single(), ShoppingItemStatus.BOUGHT)
        advanceUntilIdle()

        assertEquals(listOf(5L to ShoppingItemStatus.BOUGHT, 5L to ShoppingItemStatus.PENDING), repo.statusCalls)
        assertEquals(ShoppingItemStatus.PENDING, vm.state.value.toShop?.items?.single()?.status)
    }

    @Test
    fun not_found_then_bought_switches_directly() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L, status = ShoppingItemStatus.NOT_FOUND)))
        val vm = loaded(repo)

        vm.setStatus(vm.toShopItems.single(), ShoppingItemStatus.BOUGHT)
        advanceUntilIdle()

        assertEquals(listOf(5L to ShoppingItemStatus.BOUGHT), repo.statusCalls)
    }

    @Test
    fun the_busy_item_blocks_a_second_tap() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L), shoppingItem(6L)))
        val gate = CompletableDeferred<Unit>()
        repo.statusGate = gate
        val vm = loaded(repo)
        val items = vm.toShopItems

        vm.setStatus(items[0], ShoppingItemStatus.BOUGHT)
        runCurrent()
        assertEquals(5L, vm.state.value.busyItemId)
        vm.setStatus(items[0], ShoppingItemStatus.NOT_FOUND)
        vm.setStatus(items[1], ShoppingItemStatus.BOUGHT)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.statusCalls.size)
        assertNull(vm.state.value.busyItemId)
    }

    @Test
    fun status_of_an_item_outside_the_list_to_shop_is_ignored() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L), toShop = toShopWith(shoppingItem(5L)))
        val vm = loaded(repo)

        vm.setStatus(vm.state.value.open!!.items.single(), ShoppingItemStatus.BOUGHT)
        advanceUntilIdle()

        assertTrue(repo.statusCalls.isEmpty())
        assertNull(vm.state.value.busyItemId)
    }

    @Test
    fun suggestions_are_cleared_after_a_free_text_add() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)
        vm.onInputChange("jam")
        advanceUntilIdle()
        assertTrue(vm.state.value.suggestions.isNotEmpty())

        vm.onInputSubmit()
        advanceUntilIdle()

        assertEquals("jam", repo.addCalls.single().text)
        assertTrue(vm.state.value.suggestions.isEmpty())
    }

    @Test
    fun a_status_conflict_reloads_and_says_the_list_is_finished() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L)))
        val vm = loaded(repo)
        repo.statusResults += AppResult.Failure(AppError.Conflict)
        repo.toShop = null

        vm.setStatus(vm.toShopItems.single(), ShoppingItemStatus.BOUGHT)
        advanceUntilIdle()

        assertEquals(ShoppingStrings.LIST_FINISHED, vm.state.value.notice)
        assertNull(vm.state.value.toShop)
    }

    @Test
    fun close_without_permission_does_nothing() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        val vm = loaded(repo, cashier)

        vm.requestClose()
        vm.confirmClose()
        advanceUntilIdle()

        assertNull(vm.state.value.confirm)
        assertTrue(repo.closeCalls.isEmpty())
    }

    @Test
    fun close_is_not_offered_for_an_empty_or_missing_list() = runTest(dispatcher) {
        val empty = loaded(FakeShoppingRepository(open = shoppingList(10L)))
        val missing = loaded(FakeShoppingRepository())

        empty.requestClose()
        missing.requestClose()

        assertFalse(empty.state.value.showCloseButton)
        assertNull(empty.state.value.confirm)
        assertNull(missing.state.value.confirm)
    }

    @Test
    fun closing_moves_the_list_to_shop_and_reloads() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L, 2L))
        val vm = loaded(repo)

        vm.requestClose()
        assertEquals(ShoppingConfirm.Close, vm.state.value.confirm)
        vm.confirm()
        advanceUntilIdle()

        assertEquals(10L, repo.closeCalls.single().second)
        assertTrue(repo.closeCalls.single().first.startsWith("shop-"))
        val state = vm.state.value
        assertNull(state.confirm)
        assertEquals(ShoppingStrings.CLOSED_NOTICE, state.notice)
        assertNull(state.open)
        assertEquals(10L, state.toShop?.id)
        assertNull(state.pendingCloseKey)
    }

    @Test
    fun a_close_network_error_keeps_the_key_for_the_retry() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        repo.closeResults += AppResult.Failure(AppError.Network("down"))
        val vm = loaded(repo)

        vm.requestClose()
        vm.confirmClose()
        advanceUntilIdle()
        val key = repo.closeCalls.single().first
        assertEquals(key, vm.state.value.pendingCloseKey)
        assertEquals(ShoppingStrings.NETWORK, vm.state.value.errorMessage)
        assertNull(vm.state.value.confirm)

        vm.requestClose()
        vm.confirmClose()
        advanceUntilIdle()

        assertEquals(listOf(key, key), repo.closeCalls.map { it.first })
        assertNull(vm.state.value.pendingCloseKey)
    }

    @Test
    fun a_forbidden_close_shows_the_manager_message() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        repo.closeResults += AppResult.Failure(AppError.Forbidden)
        val vm = loaded(repo)

        vm.requestClose()
        vm.confirmClose()
        advanceUntilIdle()

        assertEquals(ShoppingStrings.FORBIDDEN, vm.state.value.errorMessage)
        assertNull(vm.state.value.pendingCloseKey)
    }

    @Test
    fun a_server_validation_message_survives_the_reload() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        repo.closeResults += AppResult.Failure(AppError.Validation("List is empty"))
        val vm = loaded(repo)

        vm.requestClose()
        vm.confirmClose()
        advanceUntilIdle()

        assertEquals(ShoppingStrings.LIST_EMPTY, vm.state.value.errorMessage)
    }

    @Test
    fun finishing_offers_every_unbought_item_checked_and_sends_only_the_checked_ones() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(
            toShop = toShopWith(
                shoppingItem(5L, status = ShoppingItemStatus.BOUGHT),
                shoppingItem(6L),
                shoppingItem(7L, status = ShoppingItemStatus.NOT_FOUND),
                shoppingItem(8L),
            ),
        )
        val vm = loaded(repo)

        vm.requestFinish()
        val dialog = assertIs<ShoppingConfirm.Finish>(vm.state.value.confirm)
        assertEquals(listOf(6L, 7L, 8L), dialog.candidates.map { it.id })
        assertEquals(setOf(6L, 7L, 8L), dialog.carry)
        vm.toggleCarry(7L)
        vm.toggleCarry(5L)
        vm.confirm()
        advanceUntilIdle()

        val call = repo.finishCalls.single()
        assertEquals(9L, call.id)
        assertEquals(listOf(6L, 8L), call.carry)
        val state = vm.state.value
        assertEquals(ShoppingStrings.FINISHED_NOTICE, state.notice)
        assertNull(state.toShop)
        assertEquals(listOf(6L, 8L), state.open?.items?.map { it.carriedFrom })
    }

    @Test
    fun finishing_with_everything_bought_confirms_with_an_empty_carry_over() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L, status = ShoppingItemStatus.BOUGHT)))
        val vm = loaded(repo)

        vm.requestFinish()
        val dialog = assertIs<ShoppingConfirm.Finish>(vm.state.value.confirm)
        assertFalse(dialog.hasCandidates)
        vm.confirmFinish()
        advanceUntilIdle()

        assertEquals(emptyList(), repo.finishCalls.single().carry)
    }

    @Test
    fun a_finish_retry_reuses_the_key_only_with_the_same_selection() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L), shoppingItem(6L)))
        repo.finishResults += AppResult.Failure(AppError.Network("down"))
        repo.finishResults += AppResult.Failure(AppError.Network("down"))
        val vm = loaded(repo)

        vm.requestFinish()
        vm.confirmFinish()
        advanceUntilIdle()
        vm.requestFinish()
        vm.confirmFinish()
        advanceUntilIdle()
        vm.requestFinish()
        vm.toggleCarry(6L)
        vm.confirmFinish()
        advanceUntilIdle()

        val keys = repo.finishCalls.map { it.key }
        assertEquals(keys[0], keys[1])
        assertNotEquals(keys[1], keys[2])
    }

    @Test
    fun finish_is_not_offered_to_a_cashier() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L)))
        val vm = loaded(repo, cashier)

        vm.requestFinish()

        assertNull(vm.state.value.confirm)
    }

    @Test
    fun history_loads_once_and_its_error_stays_in_its_section() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        repo.historyResult = AppResult.Failure(AppError.Validation("boom"))
        val vm = loaded(repo)

        vm.toggleHistory()
        advanceUntilIdle()
        assertEquals(ShoppingStrings.HISTORY_FAILED, vm.state.value.historyError)
        assertNull(vm.state.value.errorMessage)

        repo.historyResult = AppResult.Success(listOf(ShoppingListSummary(4L, T0, T0, 14, 12, 2)))
        vm.toggleHistory()
        vm.toggleHistory()
        advanceUntilIdle()
        assertEquals(listOf(4L), vm.state.value.history.map { it.id })
        assertNull(vm.state.value.historyError)

        vm.toggleHistory()
        vm.toggleHistory()
        advanceUntilIdle()
        assertEquals(2, repo.historyCalls)
    }

    @Test
    fun finishing_reloads_an_expanded_history() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(toShop = toShopWith(shoppingItem(5L, status = ShoppingItemStatus.BOUGHT)))
        val vm = loaded(repo)
        vm.toggleHistory()
        advanceUntilIdle()

        vm.requestFinish()
        vm.confirmFinish()
        advanceUntilIdle()

        assertEquals(2, repo.historyCalls)
    }

    @Test
    fun opening_a_past_list_shows_its_detail_and_back_returns() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        repo.listResult = AppResult.Success(shoppingList(4L, ShoppingListStatus.DONE, listOf(shoppingItem(40L))))
        val vm = loaded(repo)

        vm.openHistory(4L)
        assertEquals(ShoppingView.HistoryDetail(4L), vm.state.value.view)
        assertTrue(vm.state.value.isLoadingDetail)
        advanceUntilIdle()
        assertEquals(listOf(40L), vm.state.value.historyDetail?.items?.map { it.id })

        assertTrue(vm.onEscape())
        assertEquals(ShoppingView.Lists, vm.state.value.view)
        assertNull(vm.state.value.historyDetail)
    }

    @Test
    fun a_detail_that_arrives_after_going_back_is_ignored() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val gate = CompletableDeferred<Unit>()
        repo.listGate = gate
        val vm = loaded(repo)

        vm.openHistory(4L)
        runCurrent()
        vm.backFromHistory()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(ShoppingView.Lists, vm.state.value.view)
        assertNull(vm.state.value.historyDetail)
    }

    @Test
    fun a_detail_error_stays_in_the_detail() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        repo.listResult = AppResult.Failure(AppError.Validation("List not found"))
        val vm = loaded(repo)

        vm.openHistory(4L)
        advanceUntilIdle()

        assertEquals(ShoppingStrings.DETAIL_FAILED, vm.state.value.detailError)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun escape_closes_the_dialog_then_the_edit_then_clears_the_box() = runTest(dispatcher) {
        val repo = FakeShoppingRepository(open = openWith(1L))
        val vm = loaded(repo)
        vm.onInputChange("leche")
        vm.startEdit(vm.openItems.single())
        vm.requestDelete(1L)

        assertTrue(vm.onEscape())
        assertNull(vm.state.value.confirm)
        assertNotNull(vm.state.value.edit)

        assertTrue(vm.onEscape())
        assertNull(vm.state.value.edit)
        assertEquals("leche", vm.state.value.input)

        assertTrue(vm.onEscape())
        assertEquals("", vm.state.value.input)

        assertFalse(vm.onEscape())
    }

    @Test
    fun refresh_clears_messages_and_reloads() = runTest(dispatcher) {
        val repo = FakeShoppingRepository()
        val vm = loaded(repo)
        vm.scan("7501000000001")
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.state.value.errorMessage)
        assertEquals(2, repo.currentCalls)
    }

    @Test
    fun titles_use_the_business_time_zone() = runTest(dispatcher) {
        val vm = viewModel(FakeShoppingRepository())
        val lateFriday = shoppingList(1L, createdAt = Instant.parse("2026-10-17T05:30:00Z"))

        assertEquals("Lista del vie 16 oct", vm.titleOf(lateFriday))
        assertEquals(
            "vie 16 oct · 3 de 4 comprados",
            vm.historyLabel(ShoppingListSummary(1L, Instant.parse("2026-10-17T05:30:00Z"), null, 4, 3, 1)),
        )
    }
}
