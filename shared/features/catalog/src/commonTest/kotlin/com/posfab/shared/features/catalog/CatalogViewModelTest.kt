package com.posfab.shared.features.catalog

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.domain.BusinessUnit
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.CatalogUnit
import com.posfab.shared.features.catalog.domain.CreateProductInput
import com.posfab.shared.features.catalog.domain.PriceHistoryEntry
import com.posfab.shared.features.catalog.domain.ProductPage
import com.posfab.shared.features.catalog.domain.ProductUomConversion
import com.posfab.shared.features.catalog.domain.UpdateProductInput
import com.posfab.shared.features.catalog.repository.CatalogRepository
import com.posfab.shared.features.catalog.ui.CatalogFormMode
import com.posfab.shared.features.catalog.ui.CatalogViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Test
    fun product_list_search_and_pagination_behavior() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onQueryChange("milk")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals("milk", repo.lastQuery)
        assertEquals(0, repo.lastOffset)

        vm.goNextPage()
        advanceUntilIdle()

        assertEquals(20, repo.lastOffset)
    }

    @Test
    fun create_edit_product_success_and_validation_errors() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.startCreateProduct()
        vm.onFormNameChange("")
        vm.saveProduct()
        assertTrue(vm.state.value.errorMessage?.contains("Nombre", ignoreCase = true) == true)

        vm.onFormNameChange("Azucar")
        vm.onFormBaseUnitChange("KG")
        vm.saveProduct()
        advanceUntilIdle()

        assertEquals(1, repo.createdProducts)
        val selected = vm.state.value.selectedProduct
        assertNotNull(selected)

        vm.onFormNameChange("Azucar refinada")
        vm.saveProduct()
        advanceUntilIdle()

        assertEquals(1, repo.updatedProducts)
        assertEquals("Azucar refinada", vm.state.value.selectedProduct?.name)
    }

    @Test
    fun add_uom_conversion_conflict_error_handling() = runTest(dispatcher) {
        val repo = FakeCatalogRepository(conflictOnUom = true)
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.selectProduct("p1")
        advanceUntilIdle()

        vm.onNewUomUnitChange("BOX")
        vm.onNewUomFactorChange("12")
        vm.addUomConversion()
        advanceUntilIdle()

        assertTrue(vm.state.value.errorMessage?.contains("Conflicto", ignoreCase = true) == true)
    }

    @Test
    fun add_price_history_refreshes_current_price_display() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.selectProduct("p1")
        advanceUntilIdle()

        vm.onNewPriceChange("39.9")
        vm.addPriceEntry()
        advanceUntilIdle()

        assertEquals(39.9, vm.state.value.selectedProduct?.currentPrice)
        assertTrue((vm.state.value.selectedProduct?.priceHistory?.size ?: 0) >= 1)
    }

    // ── New tests ──

    @Test
    fun create_product_with_initial_price_chains_price_entry() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.startCreateProduct()
        vm.onFormNameChange("Coca Cola 600ml")
        vm.onFormBaseUnitChange("EA")
        vm.onFormInitialPriceChange("15.50")
        vm.saveProduct()
        advanceUntilIdle()

        assertEquals(1, repo.createdProducts)
        assertEquals(1, repo.addPriceCalls)
        assertTrue(vm.state.value.notice?.contains("precio") == true)
    }

    @Test
    fun quick_add_creates_product_with_price() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.openQuickAdd()
        assertTrue(vm.state.value.showQuickAddDialog)

        vm.onQuickAddNameChange("Sabritas")
        vm.onQuickAddPriceChange("18.00")
        vm.onQuickAddUnitChange("EA")
        vm.submitQuickAdd()
        advanceUntilIdle()

        assertEquals(1, repo.createdProducts)
        assertEquals(1, repo.addPriceCalls)
        assertFalse(vm.state.value.showQuickAddDialog)
        assertTrue(vm.state.value.notice?.contains("Sabritas") == true)
    }

    @Test
    fun quick_add_validates_empty_name() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.openQuickAdd()
        vm.onQuickAddPriceChange("10.00")
        vm.submitQuickAdd()

        assertTrue(vm.state.value.errorMessage?.contains("Nombre", ignoreCase = true) == true)
        assertEquals(0, repo.createdProducts)
    }

    @Test
    fun quick_add_validates_invalid_price() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.openQuickAdd()
        vm.onQuickAddNameChange("Test")
        vm.submitQuickAdd()

        assertTrue(vm.state.value.errorMessage?.contains("Precio", ignoreCase = true) == true)
        assertEquals(0, repo.createdProducts)
    }

    @Test
    fun lot_tracking_warning_shown_when_enabling() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.startCreateProduct()
        assertFalse(vm.state.value.form.lotTracked)

        // Turning ON should show warning, NOT toggle immediately
        vm.onFormLotTrackedChange(true)
        assertTrue(vm.state.value.showLotTrackingWarning)
        assertFalse(vm.state.value.form.lotTracked)

        // Confirming should apply the change
        vm.confirmLotTracking()
        assertFalse(vm.state.value.showLotTrackingWarning)
        assertTrue(vm.state.value.form.lotTracked)

        // Turning OFF should NOT show warning
        vm.onFormLotTrackedChange(false)
        assertFalse(vm.state.value.showLotTrackingWarning)
        assertFalse(vm.state.value.form.lotTracked)
    }

    @Test
    fun barcode_check_auto_selects_product() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onBarcodeCheckInputChange("750123")
        vm.checkBarcode()
        advanceUntilIdle()

        assertEquals(CatalogFormMode.EDITING, vm.state.value.formMode)
        assertEquals("p1", vm.state.value.selectedProduct?.id)
        assertEquals("", vm.state.value.barcodeCheckInput)
        assertTrue(vm.state.value.notice?.contains("encontrado") == true)
    }

    @Test
    fun price_uses_terminal_business_unit_by_default() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher, defaultBusinessUnitId = 2L)
        advanceUntilIdle()

        assertEquals(2L, vm.state.value.selectedBusinessUnitId)
        vm.selectProduct("p1")
        advanceUntilIdle()
        vm.onNewPriceChange("12.5")
        vm.addPriceEntry()
        advanceUntilIdle()

        assertEquals(2L, repo.lastPriceBusinessUnitId)
        assertEquals(1, repo.addPriceCalls)
    }

    @Test
    fun price_falls_back_to_first_business_unit_and_can_be_changed() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        assertEquals(1L, vm.state.value.selectedBusinessUnitId)
        vm.onBusinessUnitChange(2L)
        vm.selectProduct("p1")
        advanceUntilIdle()
        vm.onNewPriceChange("9.99")
        vm.addPriceEntry()
        advanceUntilIdle()

        assertEquals(2L, repo.lastPriceBusinessUnitId)
    }

    @Test
    fun price_is_blocked_without_business_unit() = runTest(dispatcher) {
        val repo = FakeCatalogRepository(businessUnits = emptyList())
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.selectProduct("p1")
        advanceUntilIdle()
        vm.onNewPriceChange("9.99")
        vm.addPriceEntry()
        advanceUntilIdle()

        assertTrue(vm.state.value.errorMessage?.contains("unidad de negocio", ignoreCase = true) == true)
        assertEquals(0, repo.addPriceCalls)
    }

    @Test
    fun pagination_text_displays_range() = runTest(dispatcher) {
        val repo = FakeCatalogRepository()
        val vm = CatalogViewModel(CatalogUseCases(repo), dispatcher)
        advanceUntilIdle()

        val text = vm.state.value.paginationText
        assertTrue(text.contains("Mostrando"))
        assertTrue(text.contains("1"))
        assertTrue(text.contains("40"))
    }
}

private class FakeCatalogRepository(
    private val conflictOnUom: Boolean = false,
    private val businessUnits: List<BusinessUnit> = listOf(
        BusinessUnit(1L, "TIENDA", "Tienda"),
        BusinessUnit(2L, "FONDA", "Fonda"),
    ),
) : CatalogRepository {
    var lastQuery: String? = null
    var lastOffset: Int = 0
    var createdProducts: Int = 0
    var updatedProducts: Int = 0
    var addPriceCalls: Int = 0
    var lastPriceBusinessUnitId: Long? = null

    private val products = mutableMapOf(
        "p1" to CatalogProductDetail(
            id = "p1",
            name = "Leche",
            sku = "LEC-01",
            barcode = "750123",
            baseUnit = "LT",
            lotTracked = false,
            active = true,
            currentPrice = 25.0,
            uoms = listOf(ProductUomConversion("LT", 1.0)),
            priceHistory = mutableListOf<PriceHistoryEntry>().toList(),
        )
    )

    override suspend fun listProducts(query: String?, limit: Int, offset: Int, active: Boolean?): AppResult<ProductPage> {
        lastQuery = query
        lastOffset = offset
        val name = if (query.isNullOrBlank()) "Leche" else "Leche $query"
        val items = listOf(
            CatalogProductSummary(
                id = "p1",
                name = name,
                sku = "LEC-01",
                barcode = "750123",
                baseUnit = "LT",
                lotTracked = false,
                active = true,
                currentPrice = products["p1"]?.currentPrice,
            )
        )
        return AppResult.Success(ProductPage(items = items, total = 40, limit = limit, offset = offset))
    }

    override suspend fun createProduct(input: CreateProductInput): AppResult<CatalogProductDetail> {
        createdProducts++
        val id = "p${newId++}"
        val detail = CatalogProductDetail(
            id = id,
            name = input.name,
            sku = input.sku,
            barcode = input.barcode,
            baseUnit = input.baseUnit,
            lotTracked = input.lotTracked,
            active = input.active,
            currentPrice = null,
            uoms = listOf(ProductUomConversion(input.baseUnit, 1.0)),
            priceHistory = emptyList(),
        )
        products[detail.id] = detail
        return AppResult.Success(detail)
    }

    override suspend fun getProduct(productId: String): AppResult<CatalogProductDetail> {
        val detail = products[productId] ?: return AppResult.Failure(AppError.Validation("not found"))
        return AppResult.Success(detail)
    }

    override suspend fun updateProduct(productId: String, input: UpdateProductInput): AppResult<CatalogProductDetail> {
        updatedProducts++
        val current = products[productId] ?: return AppResult.Failure(AppError.Validation("not found"))
        val updated = current.copy(
            name = input.name,
            sku = input.sku,
            barcode = input.barcode,
            baseUnit = input.baseUnit,
            lotTracked = input.lotTracked,
            active = input.active,
        )
        products[productId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun addUom(productId: String, unit: String, factorToBase: Double): AppResult<Unit> {
        if (conflictOnUom) return AppResult.Failure(AppError.Conflict)
        val current = products[productId] ?: return AppResult.Failure(AppError.Validation("not found"))
        products[productId] = current.copy(
            uoms = current.uoms + ProductUomConversion(unit, factorToBase)
        )
        return AppResult.Success(Unit)
    }

    override suspend fun listUnits(): AppResult<List<CatalogUnit>> = AppResult.Success(
        listOf(
            CatalogUnit("EA", "Each"),
            CatalogUnit("KG", "Kilogram"),
            CatalogUnit("LT", "Liter"),
        )
    )

    override suspend fun listBusinessUnits(): AppResult<List<BusinessUnit>> = AppResult.Success(businessUnits)

    override suspend fun addPrice(productId: String, businessUnitId: Long, price: Double): AppResult<Unit> {
        addPriceCalls++
        lastPriceBusinessUnitId = businessUnitId
        val current = products[productId] ?: return AppResult.Failure(AppError.Validation("not found"))
        products[productId] = current.copy(
            currentPrice = price,
            priceHistory = listOf(PriceHistoryEntry(productId, price, "2026-03-02T10:00:00")) + current.priceHistory,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun findByBarcode(barcode: String): AppResult<CatalogProductDetail?> {
        return AppResult.Success(products.values.firstOrNull { it.barcode == barcode })
    }

    private companion object {
        var newId: Int = 2
    }
}
