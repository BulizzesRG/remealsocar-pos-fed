package com.posfab.shared.features.catalog

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.PriceHistory
import com.posfab.shared.features.catalog.domain.PriceHistoryEntry
import com.posfab.shared.features.catalog.domain.PriceType
import com.posfab.shared.features.catalog.domain.PricesInput
import com.posfab.shared.features.catalog.domain.ProductInput
import com.posfab.shared.features.catalog.domain.ProductPage
import com.posfab.shared.features.catalog.domain.ProductPrices
import com.posfab.shared.features.catalog.repository.CatalogRepository
import com.posfab.shared.features.catalog.ui.CatalogConfirm
import com.posfab.shared.features.catalog.ui.CatalogFormMode
import com.posfab.shared.features.catalog.ui.CatalogViewModel
import com.posfab.shared.features.catalog.ui.PendingAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun detail(
        id: String,
        name: String,
        barcode: String? = null,
        priceType: PriceType = PriceType.PIECE,
        sku: String? = null,
        lotTracked: Boolean = false,
    ) = CatalogProductDetail(
        id = id,
        name = name,
        sku = sku,
        barcode = barcode,
        lotTracked = lotTracked,
        active = true,
        priceType = priceType,
        counted = false,
        piecesPerPack = null,
        kitchenKind = null,
        prices = null,
    )

    private class FakeRepo : CatalogRepository {
        val products = linkedMapOf<String, CatalogProductDetail>()
        val createdInputs = mutableListOf<ProductInput>()
        val updatedInputs = mutableListOf<Pair<String, ProductInput>>()
        val pricesSet = mutableListOf<Pair<String, PricesInput>>()
        val internalCodeCalls = mutableListOf<String>()
        val callOrder = mutableListOf<String>()
        var lastListArgs: Pair<String?, Boolean>? = null
        val listOffsets = mutableListOf<Int>()
        var listCalls = 0
        var failSetPricesOnce = false
        var failAssignCodeOnce = false
        var cashDiscountPct = 5.0
        val priceHistoryCalls = mutableListOf<String>()
        val priceHistories = mutableMapOf<String, PriceHistory>()
        var priceHistoryFailure: AppError? = null
        val priceHistoryGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        private var nextId = 100

        override suspend fun listProducts(query: String?, activeOnly: Boolean, offset: Int): AppResult<ProductPage> {
            lastListArgs = query to activeOnly
            listOffsets += offset
            listCalls++
            val all = products.values.map {
                CatalogProductSummary(it.id, it.name, it.barcode, it.priceType, it.kitchenKind, it.active, it.prices?.cashTakeaway)
            }
            return AppResult.Success(ProductPage(all.drop(offset).take(50), all.size))
        }

        override suspend fun getProduct(productId: String): AppResult<CatalogProductDetail> =
            products[productId]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Unknown("missing"))

        override suspend fun createProduct(input: ProductInput): AppResult<CatalogProductDetail> {
            callOrder += "create"
            createdInputs += input
            val id = (nextId++).toString()
            val created = CatalogProductDetail(
                id = id,
                name = input.name,
                sku = input.sku,
                barcode = input.barcode,
                lotTracked = input.lotTracked,
                active = input.active,
                priceType = input.priceType,
                counted = input.counted,
                piecesPerPack = input.piecesPerPack,
                kitchenKind = input.kitchenKind,
                prices = null,
            )
            products[id] = created
            return AppResult.Success(created)
        }

        override suspend fun updateProduct(productId: String, input: ProductInput): AppResult<CatalogProductDetail> {
            callOrder += "update"
            updatedInputs += productId to input
            val current = products.getValue(productId)
            val updated = current.copy(
                name = input.name,
                barcode = input.barcode,
                priceType = input.priceType,
                counted = input.counted,
                piecesPerPack = input.piecesPerPack,
                kitchenKind = input.kitchenKind,
                active = input.active,
            )
            products[productId] = updated
            return AppResult.Success(updated)
        }

        override suspend fun setPrices(productId: String, input: PricesInput): AppResult<CatalogProductDetail> {
            callOrder += "prices"
            pricesSet += productId to input
            if (failSetPricesOnce) {
                failSetPricesOnce = false
                return AppResult.Failure(AppError.Validation("listTakeaway must be >= cashTakeaway"))
            }
            val updated = products.getValue(productId).copy(
                prices = ProductPrices(input.cashTakeaway, input.listTakeaway, input.cashOnsite, input.listOnsite, input.suggestedPrice),
            )
            products[productId] = updated
            return AppResult.Success(updated)
        }

        override suspend fun getPriceHistory(productId: String): AppResult<PriceHistory> {
            priceHistoryCalls += productId
            priceHistoryGates[productId]?.await()
            priceHistoryFailure?.let { return AppResult.Failure(it) }
            return AppResult.Success(priceHistories[productId] ?: PriceHistory(currentSince = null, entries = emptyList()))
        }

        override suspend fun assignInternalCode(productId: String): AppResult<CatalogProductDetail> {
            callOrder += "code"
            internalCodeCalls += productId
            if (failAssignCodeOnce) {
                failAssignCodeOnce = false
                return AppResult.Failure(AppError.Network("timeout"))
            }
            val updated = products.getValue(productId).copy(barcode = "2000000000017")
            products[productId] = updated
            return AppResult.Success(updated)
        }

        override suspend fun findByBarcode(barcode: String): AppResult<CatalogProductDetail?> =
            AppResult.Success(products.values.firstOrNull { it.barcode == barcode })

        override suspend fun cashDiscountPct(): AppResult<Double> = AppResult.Success(cashDiscountPct)
    }

    private fun newVm(repo: FakeRepo) = CatalogViewModel(CatalogUseCases(repo), dispatcher)

    @Test
    fun search_debounce_sends_query_and_active_filter() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.onQueryChange("milk")
        advanceTimeBy(350)
        advanceUntilIdle()
        assertEquals("milk" to true, repo.lastListArgs)

        vm.onActiveOnlyChange(false)
        advanceUntilIdle()
        assertEquals("milk" to false, repo.lastListArgs)
    }

    @Test
    fun enter_with_known_barcode_opens_product_and_clears_query() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Coca", barcode = "7501055300000")
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.onQueryChange("7501055300000")
        vm.onSearchSubmit()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(CatalogFormMode.EDITING, state.mode)
        assertEquals("1", state.selected?.id)
        assertEquals("", state.query)
    }

    @Test
    fun enter_with_unknown_barcode_shows_message_and_keeps_mode() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.onQueryChange("7509999999999")
        vm.onSearchSubmit()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(CatalogFormMode.IDLE, state.mode)
        assertTrue(state.errorMessage.orEmpty().contains("7509999999999"))
        assertEquals("", state.query)
        assertTrue(repo.createdInputs.isEmpty())
    }

    @Test
    fun enter_with_text_refreshes_immediately_without_changing_mode() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()
        val before = repo.listCalls

        vm.onQueryChange("pan")
        vm.onSearchSubmit()
        advanceUntilIdle()

        assertEquals(before + 1, repo.listCalls)
        assertEquals("pan" to true, repo.lastListArgs)
        assertEquals(CatalogFormMode.IDLE, vm.state.value.mode)
    }

    @Test
    fun create_piece_product_sets_proposed_card_price_and_ends_editing() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Torta")
        vm.onCashTakeawayChange("17")
        assertEquals("18.00", vm.state.value.form.listTakeaway)
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertEquals(PriceType.PIECE, repo.createdInputs.single().priceType)
        val (_, prices) = repo.pricesSet.single()
        assertEquals(17.0, prices.cashTakeaway)
        assertEquals(18.0, prices.listTakeaway)
        val state = vm.state.value
        assertEquals(CatalogFormMode.EDITING, state.mode)
        assertEquals("Guardado", state.notice)
        assertNotNull(state.selected)
    }

    @Test
    fun open_price_product_skips_prices_and_forces_not_counted() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Varios")
        vm.onCountedChange(true)
        vm.onPriceTypeChange(PriceType.OPEN)
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertTrue(repo.pricesSet.isEmpty())
        val input = repo.createdInputs.single()
        assertEquals(PriceType.OPEN, input.priceType)
        assertEquals(false, input.counted)
    }

    @Test
    fun weight_product_reaches_repository_as_weight() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Jamón")
        vm.onPriceTypeChange(PriceType.WEIGHT)
        vm.onCashTakeawayChange("120")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertEquals(PriceType.WEIGHT, repo.createdInputs.single().priceType)
    }

    @Test
    fun pieces_per_pack_is_normalized_and_validated() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Galletas")
        vm.onCashTakeawayChange("10")
        vm.onPiecesPerPackChange("1")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()
        assertNull(repo.createdInputs.single().piecesPerPack)

        vm.startCreate()
        vm.onNameChange("Galletas 20")
        vm.onCashTakeawayChange("10")
        vm.onPiecesPerPackChange("20")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()
        assertEquals(20, repo.createdInputs.last().piecesPerPack)

        vm.startCreate()
        vm.onNameChange("Galletas malas")
        vm.onCashTakeawayChange("10")
        vm.onPiecesPerPackChange("-3")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()
        assertEquals(2, repo.createdInputs.size)
        assertEquals("Piezas por paquete debe ser 2 o más", vm.state.value.errorMessage)
    }

    @Test
    fun card_price_follows_proposal_until_edited_and_suggested_toggles() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onCashTakeawayChange("20")
        assertEquals("21.00", vm.state.value.form.listTakeaway)
        vm.onCashTakeawayChange("30")
        assertEquals("32.00", vm.state.value.form.listTakeaway)

        vm.onListTakeawayChange("40")
        vm.onCashTakeawayChange("50")
        assertEquals("40", vm.state.value.form.listTakeaway)

        vm.onSuggestedPriceChange(true)
        assertEquals("50", vm.state.value.form.listTakeaway)

        vm.onSuggestedPriceChange(false)
        assertEquals("53.00", vm.state.value.form.listTakeaway)
    }

    @Test
    fun generate_code_while_editing_assigns_immediately_and_keeps_typed_name() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Coca")
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.selectProduct("1")
        advanceUntilIdle()
        vm.onNameChange("Coca Cola")
        vm.requestGenerateCode()
        advanceUntilIdle()

        assertEquals(listOf("1"), repo.internalCodeCalls)
        val state = vm.state.value
        assertEquals("2000000000017", state.form.barcode)
        assertEquals("Coca Cola", state.form.name)
    }

    @Test
    fun generate_code_while_creating_runs_after_create_and_prices() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Pan")
        vm.onCashTakeawayChange("8")
        vm.requestGenerateCode()
        assertTrue(repo.internalCodeCalls.isEmpty())
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertEquals(listOf("create", "prices", "code"), repo.callOrder)
        assertEquals("2000000000017", vm.state.value.form.barcode)
    }

    @Test
    fun failed_set_prices_keeps_product_open_and_retry_updates_instead_of_creating() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.failSetPricesOnce = true
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Torta")
        vm.onCashTakeawayChange("17")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        val failed = vm.state.value
        assertEquals(CatalogFormMode.EDITING, failed.mode)
        assertNotNull(failed.selected)
        assertEquals("El precio con tarjeta no puede ser menor que el de efectivo", failed.errorMessage)
        assertEquals("Torta", failed.form.name)

        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertEquals(1, repo.createdInputs.size)
        assertEquals(1, repo.updatedInputs.size)
        assertEquals(2, repo.pricesSet.size)
        assertNull(vm.state.value.errorMessage)
        assertEquals("Guardado", vm.state.value.notice)
    }

    @Test
    fun update_sends_back_original_sku_and_lot_tracked() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Aceite", sku = "ACE-1", lotTracked = true)
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.selectProduct("1")
        advanceUntilIdle()
        vm.onNameChange("Aceite 1L")
        vm.onCashTakeawayChange("30")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        val (id, input) = repo.updatedInputs.single()
        assertEquals("1", id)
        assertEquals("ACE-1", input.sku)
        assertTrue(input.lotTracked)
        assertEquals("Aceite 1L", input.name)
    }

    @Test
    fun local_validation_blocks_save_without_calling_repository() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()
        assertEquals("Escribe el nombre", vm.state.value.errorMessage)

        vm.onNameChange("Torta")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()
        assertEquals("Escribe el precio en efectivo para llevar", vm.state.value.errorMessage)

        vm.onCashTakeawayChange("20")
        vm.onListTakeawayChange("15")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()
        assertEquals("El precio con tarjeta no puede ser menor que el de efectivo", vm.state.value.errorMessage)

        assertTrue(repo.createdInputs.isEmpty())
    }

    @Test
    fun opening_product_with_hand_set_card_price_keeps_it_when_cash_changes() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Refresco").copy(prices = ProductPrices(20.0, 25.0, null, null, false))
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.selectProduct("1")
        advanceUntilIdle()
        assertTrue(vm.state.value.form.listTakeawayEdited)
        vm.onCashTakeawayChange("22")

        assertEquals("25.00", vm.state.value.form.listTakeaway)
    }

    @Test
    fun opening_product_with_proposed_card_price_keeps_following_proposal() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Refresco").copy(prices = ProductPrices(20.0, 21.0, 30.0, 32.0, false))
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.selectProduct("1")
        advanceUntilIdle()
        val form = vm.state.value.form
        assertEquals(false, form.listTakeawayEdited)
        assertEquals(false, form.listOnsiteEdited)
        vm.onCashTakeawayChange("30")

        assertEquals("32.00", vm.state.value.form.listTakeaway)
    }

    @Test
    fun double_save_creates_only_one_product() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Torta")
        vm.onCashTakeawayChange("17")
        vm.save()
        vm.confirmSave()
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertEquals(1, repo.createdInputs.size)
        assertEquals(1, repo.pricesSet.size)
    }

    @Test
    fun text_typed_after_scan_is_not_wiped_by_lookup() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.onQueryChange("7509999999999")
        vm.onSearchSubmit()
        assertEquals("", vm.state.value.query)
        vm.onQueryChange("pan")
        advanceUntilIdle()

        assertEquals("pan", vm.state.value.query)
        assertEquals(CatalogFormMode.IDLE, vm.state.value.mode)
    }

    @Test
    fun generate_code_on_create_toggles_and_typed_barcode_clears_it() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.requestGenerateCode()
        assertTrue(vm.state.value.form.generateCodeOnSave)
        vm.requestGenerateCode()
        assertEquals(false, vm.state.value.form.generateCodeOnSave)

        vm.requestGenerateCode()
        vm.onBarcodeChange("7501234567890")
        assertEquals(false, vm.state.value.form.generateCodeOnSave)
        assertEquals("7501234567890", vm.state.value.form.barcode)
    }

    @Test
    fun money_input_allows_one_dot_and_two_decimals() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onCashTakeawayChange("12.345.6")

        assertEquals("12.34", vm.state.value.form.cashTakeaway)
    }

    @Test
    fun switching_to_open_price_clears_pieces_per_pack() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onPiecesPerPackChange("12")
        vm.onPriceTypeChange(PriceType.OPEN)

        assertEquals("", vm.state.value.form.piecesPerPack)
    }

    @Test
    fun created_product_appears_in_list_even_if_prices_fail() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.failSetPricesOnce = true
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Torta")
        vm.onCashTakeawayChange("17")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertNotNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.products.any { it.name == "Torta" })
    }

    @Test
    fun save_asks_for_confirmation_before_calling_repository() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Torta")
        vm.onCashTakeawayChange("17")
        vm.save()
        advanceUntilIdle()

        assertEquals(CatalogConfirm.Save, vm.state.value.confirm)
        assertTrue(repo.callOrder.isEmpty())

        vm.confirmSave()
        advanceUntilIdle()

        assertNull(vm.state.value.confirm)
        assertEquals(listOf("create", "prices"), repo.callOrder)
    }

    @Test
    fun dismissing_save_confirmation_keeps_form_intact() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Torta")
        vm.onCashTakeawayChange("17")
        vm.save()
        vm.dismissConfirm()
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.confirm)
        assertEquals(CatalogFormMode.CREATING, state.mode)
        assertEquals("Torta", state.form.name)
        assertEquals("17", state.form.cashTakeaway)
        assertTrue(state.hasUnsavedChanges)
        assertTrue(repo.callOrder.isEmpty())
    }

    @Test
    fun selecting_another_product_with_unsaved_changes_asks_to_discard() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Coca")
        repo.products["2"] = detail("2", "Pepsi")
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.selectProduct("1")
        advanceUntilIdle()
        vm.onNameChange("Coca Cola")
        vm.selectProduct("2")
        advanceUntilIdle()

        assertEquals(CatalogConfirm.Discard(PendingAction.Select("2")), vm.state.value.confirm)
        assertEquals("1", vm.state.value.selected?.id)
        assertEquals("Coca Cola", vm.state.value.form.name)

        vm.confirmDiscard()
        advanceUntilIdle()

        assertNull(vm.state.value.confirm)
        assertEquals("2", vm.state.value.selected?.id)
        assertEquals("Pepsi", vm.state.value.form.name)
    }

    @Test
    fun cancel_without_changes_does_not_ask() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Coca")
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.selectProduct("1")
        advanceUntilIdle()
        vm.cancel()

        assertNull(vm.state.value.confirm)
        assertEquals(CatalogFormMode.IDLE, vm.state.value.mode)
    }

    @Test
    fun cancel_with_changes_asks_and_dismiss_keeps_editing() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Torta")
        vm.cancel()
        assertEquals(CatalogConfirm.Discard(PendingAction.Cancel), vm.state.value.confirm)

        vm.dismissConfirm()
        assertEquals(CatalogFormMode.CREATING, vm.state.value.mode)
        assertEquals("Torta", vm.state.value.form.name)

        vm.cancel()
        vm.confirmDiscard()
        assertEquals(CatalogFormMode.IDLE, vm.state.value.mode)
    }

    @Test
    fun load_more_appends_next_page_with_offset() = runTest(dispatcher) {
        val repo = FakeRepo()
        (1..120).forEach { repo.products[it.toString()] = detail(it.toString(), "Producto $it") }
        val vm = newVm(repo)
        advanceUntilIdle()

        assertEquals(50, vm.state.value.products.size)
        assertEquals(120, vm.state.value.total)
        assertTrue(vm.state.value.canLoadMore)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(50, repo.listOffsets.last())
        assertEquals(100, vm.state.value.products.size)
        assertEquals("51", vm.state.value.products[50].id)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(100, repo.listOffsets.last())
        assertEquals(120, vm.state.value.products.size)
        assertEquals(false, vm.state.value.canLoadMore)
    }

    @Test
    fun load_more_is_hidden_when_everything_is_loaded() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.products["1"] = detail("1", "Coca")
        val vm = newVm(repo)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.canLoadMore)
        val calls = repo.listCalls
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(calls, repo.listCalls)
    }

    private fun history(changedBy: String, cash: Double = 17.0) = PriceHistory(
        currentSince = "2026-10-17T16:48:47.123456Z",
        entries = listOf(
            PriceHistoryEntry(
                prices = ProductPrices(cash, cash + 1, null, null, false),
                validFrom = "2026-10-01T12:00:00Z",
                validUntil = "2026-10-17T16:48:47.123456Z",
                changedBy = changedBy,
            ),
        ),
    )

    private fun TestScope.openedProduct(repo: FakeRepo, id: String = "1"): CatalogViewModel {
        repo.products[id] = detail(id, "Coca").copy(prices = ProductPrices(17.0, 18.0, null, null, false))
        val vm = newVm(repo)
        advanceUntilIdle()
        vm.selectProduct(id)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun opening_a_product_does_not_load_price_history() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = openedProduct(repo)

        val state = vm.state.value
        assertEquals(CatalogFormMode.EDITING, state.mode)
        assertTrue(repo.priceHistoryCalls.isEmpty())
        assertFalse(state.showPriceHistory)
        assertNull(state.priceHistory)
    }

    @Test
    fun first_toggle_opens_and_loads_price_history() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistories["1"] = history("gerente")
        val vm = openedProduct(repo)

        vm.togglePriceHistory()
        assertTrue(vm.state.value.isLoadingPriceHistory)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.showPriceHistory)
        assertFalse(state.isLoadingPriceHistory)
        assertEquals("gerente", state.priceHistory?.entries?.single()?.changedBy)
        assertEquals(listOf("1"), repo.priceHistoryCalls)
    }

    @Test
    fun closing_and_reopening_price_history_does_not_reload() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = openedProduct(repo)

        vm.togglePriceHistory()
        advanceUntilIdle()
        vm.togglePriceHistory()
        assertFalse(vm.state.value.showPriceHistory)
        vm.togglePriceHistory()
        advanceUntilIdle()

        assertTrue(vm.state.value.showPriceHistory)
        assertEquals(listOf("1"), repo.priceHistoryCalls)
    }

    @Test
    fun price_history_error_stays_out_of_the_general_banner() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistoryFailure = AppError.Network("timeout")
        val vm = openedProduct(repo)

        vm.togglePriceHistory()
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.priceHistoryError)
        assertNull(state.errorMessage)
        assertNull(state.priceHistory)
        assertFalse(state.isLoadingPriceHistory)
    }

    @Test
    fun price_history_retries_after_an_error_when_reopened() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistoryFailure = AppError.Network("timeout")
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()

        repo.priceHistoryFailure = null
        vm.togglePriceHistory()
        vm.togglePriceHistory()
        advanceUntilIdle()

        assertEquals(2, repo.priceHistoryCalls.size)
        assertNull(vm.state.value.priceHistoryError)
        assertNotNull(vm.state.value.priceHistory)
    }

    @Test
    fun opening_another_product_clears_price_history() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistories["1"] = history("gerente")
        repo.products["2"] = detail("2", "Jamón")
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()

        vm.selectProduct("2")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("2", state.selected?.id)
        assertNull(state.priceHistory)
        assertFalse(state.showPriceHistory)
        assertFalse(state.isLoadingPriceHistory)
        assertNull(state.priceHistoryError)
        assertEquals(listOf("1"), repo.priceHistoryCalls)
    }

    @Test
    fun cancel_and_new_clear_price_history() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistories["1"] = history("gerente")
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()

        vm.startCreate()
        assertNull(vm.state.value.priceHistory)
        assertFalse(vm.state.value.showPriceHistory)

        vm.selectProduct("1")
        advanceUntilIdle()
        vm.togglePriceHistory()
        advanceUntilIdle()
        vm.cancel()

        assertEquals(CatalogFormMode.IDLE, vm.state.value.mode)
        assertNull(vm.state.value.priceHistory)
        assertFalse(vm.state.value.showPriceHistory)
    }

    @Test
    fun saving_prices_with_history_open_reloads_it() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistories["1"] = history("gerente")
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()

        repo.priceHistories["1"] = history("admin", cash = 18.0)
        vm.onCashTakeawayChange("19")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Guardado", state.notice)
        assertTrue(state.showPriceHistory)
        assertEquals(listOf("1", "1"), repo.priceHistoryCalls)
        assertEquals("admin", state.priceHistory?.entries?.single()?.changedBy)
    }

    @Test
    fun saving_prices_with_history_closed_reloads_on_next_toggle() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistories["1"] = history("gerente")
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()
        vm.togglePriceHistory()

        vm.onCashTakeawayChange("19")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertFalse(vm.state.value.showPriceHistory)
        assertNull(vm.state.value.priceHistory)
        assertEquals(1, repo.priceHistoryCalls.size)

        vm.togglePriceHistory()
        advanceUntilIdle()
        assertEquals(2, repo.priceHistoryCalls.size)
        assertNotNull(vm.state.value.priceHistory)
    }

    @Test
    fun saving_an_open_price_product_keeps_loaded_history() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()

        vm.onPriceTypeChange(PriceType.OPEN)
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        assertTrue(repo.pricesSet.isEmpty())
        assertEquals(1, repo.priceHistoryCalls.size)
        assertNotNull(vm.state.value.priceHistory)
    }

    @Test
    fun toggle_in_creating_mode_never_calls_repository() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.togglePriceHistory()
        advanceUntilIdle()
        vm.togglePriceHistory()
        advanceUntilIdle()

        assertTrue(repo.priceHistoryCalls.isEmpty())
        assertFalse(vm.state.value.showPriceHistory)
        assertFalse(vm.state.value.isLoadingPriceHistory)
    }

    @Test
    fun toggle_with_no_product_open_does_nothing() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.togglePriceHistory()
        advanceUntilIdle()

        assertTrue(repo.priceHistoryCalls.isEmpty())
        assertFalse(vm.state.value.showPriceHistory)
    }

    @Test
    fun history_in_flight_for_a_previous_product_is_ignored() = runTest(dispatcher) {
        val repo = FakeRepo()
        val gate = CompletableDeferred<Unit>()
        repo.priceHistoryGates["1"] = gate
        repo.priceHistories["1"] = history("gerente")
        repo.products["2"] = detail("2", "Jamón")
        val vm = openedProduct(repo)

        vm.togglePriceHistory()
        advanceUntilIdle()
        assertTrue(vm.state.value.isLoadingPriceHistory)

        vm.selectProduct("2")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("2", state.selected?.id)
        assertNull(state.priceHistory)
        assertFalse(state.isLoadingPriceHistory)
        assertFalse(state.showPriceHistory)
    }

    @Test
    fun switching_product_then_opening_history_shows_only_the_new_product() = runTest(dispatcher) {
        val repo = FakeRepo()
        val gate = CompletableDeferred<Unit>()
        repo.priceHistoryGates["1"] = gate
        repo.priceHistories["1"] = history("gerente")
        repo.products["2"] = detail("2", "Jamón")
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()

        vm.selectProduct("2")
        advanceUntilIdle()
        vm.togglePriceHistory()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("2", state.selected?.id)
        assertTrue(state.showPriceHistory)
        assertTrue(state.priceHistory?.entries.orEmpty().isEmpty())
        assertEquals(listOf("1", "2"), repo.priceHistoryCalls)
    }

    @Test
    fun failed_set_prices_keeps_loaded_history_untouched() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistories["1"] = history("gerente")
        val vm = openedProduct(repo)
        vm.togglePriceHistory()
        advanceUntilIdle()

        repo.failSetPricesOnce = true
        vm.onCashTakeawayChange("19")
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, repo.priceHistoryCalls.size)
        assertEquals("gerente", state.priceHistory?.entries?.single()?.changedBy)
        assertTrue(state.showPriceHistory)
        assertNull(state.priceHistoryError)
    }

    @Test
    fun history_opens_fresh_after_prices_were_saved_but_the_code_step_failed() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.failAssignCodeOnce = true
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.startCreate()
        vm.onNameChange("Pan")
        vm.onCashTakeawayChange("8")
        vm.requestGenerateCode()
        vm.save()
        vm.confirmSave()
        advanceUntilIdle()

        val afterSave = vm.state.value
        assertEquals(listOf("create", "prices", "code"), repo.callOrder)
        assertEquals(CatalogFormMode.EDITING, afterSave.mode)
        assertNotNull(afterSave.errorMessage)
        assertNull(afterSave.priceHistory)
        assertTrue(repo.priceHistoryCalls.isEmpty())

        val newId = afterSave.selected?.id ?: error("product was not created")
        repo.priceHistories[newId] = history("admin", cash = 8.0)
        vm.togglePriceHistory()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(newId), repo.priceHistoryCalls)
        assertEquals("admin", state.priceHistory?.entries?.single()?.changedBy)
        assertNull(state.priceHistoryError)
    }

    @Test
    fun history_with_entries_but_no_current_since_is_kept() = runTest(dispatcher) {
        val repo = FakeRepo()
        repo.priceHistories["1"] = history("gerente").copy(currentSince = null)
        val vm = openedProduct(repo)

        vm.togglePriceHistory()
        advanceUntilIdle()

        val loaded = vm.state.value.priceHistory
        assertNull(loaded?.currentSince)
        assertEquals(1, loaded?.entries?.size)
        assertNull(vm.state.value.priceHistoryError)
    }

    @Test
    fun toggling_twice_while_loading_does_not_start_a_second_request() = runTest(dispatcher) {
        val repo = FakeRepo()
        val gate = CompletableDeferred<Unit>()
        repo.priceHistoryGates["1"] = gate
        val vm = openedProduct(repo)

        vm.togglePriceHistory()
        advanceUntilIdle()
        vm.togglePriceHistory()
        vm.togglePriceHistory()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("1"), repo.priceHistoryCalls)
        assertTrue(vm.state.value.showPriceHistory)
        assertNotNull(vm.state.value.priceHistory)
    }
}
