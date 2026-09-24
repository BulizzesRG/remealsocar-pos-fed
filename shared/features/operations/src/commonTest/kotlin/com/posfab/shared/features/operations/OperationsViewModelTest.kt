package com.posfab.shared.features.operations

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.operations.common.OperationsUseCases
import com.posfab.shared.features.operations.domain.AdjustmentInput
import com.posfab.shared.features.operations.domain.AdjustmentReason
import com.posfab.shared.features.operations.domain.InternalRequisitionInput
import com.posfab.shared.features.operations.domain.InternalRequisitionResult
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.InventoryOnHandItem
import com.posfab.shared.features.operations.domain.InventoryOperationResult
import com.posfab.shared.features.operations.domain.OperationsBusinessUnit
import com.posfab.shared.features.operations.domain.OperationsProduct
import com.posfab.shared.features.operations.domain.OperationsUnit
import com.posfab.shared.features.operations.domain.ProductUnitOption
import com.posfab.shared.features.operations.domain.PurchaseInput
import com.posfab.shared.features.operations.domain.PurchaseResult
import com.posfab.shared.features.operations.domain.Supplier
import com.posfab.shared.features.operations.domain.SupplierInput
import com.posfab.shared.features.operations.domain.WasteInput
import com.posfab.shared.features.operations.domain.WasteReason
import com.posfab.shared.features.operations.repository.OperationsRepository
import com.posfab.shared.features.operations.ui.OperationsTab
import com.posfab.shared.features.operations.ui.OperationsViewModel
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
class OperationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private companion object {
        const val TERMINAL_ID = 1L
        const val TIENDA_ID = 10L
        const val FONDA_ID = 11L
        const val TORTERIA_ID = 12L
        const val PZA_ID = 1L
        const val CAJA_ID = 6L
    }

    private fun vm(repo: FakeOperationsRepository, session: UserSession = managerSession()) =
        OperationsViewModel(session, TERMINAL_ID, TIENDA_ID, OperationsUseCases(repo), dispatcher)

    @Test
    fun loads_reference_data_and_defaults_to_device_business_unit() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(3, state.businessUnits.size)
        assertEquals(TIENDA_ID, state.purchaseBusinessUnitId)
        assertEquals(FONDA_ID, state.requisitionBusinessUnitId)
        assertEquals(TIENDA_ID, state.onHandBusinessUnitId)
        assertEquals(listOf("Proveedor Uno"), state.suppliers.map { it.name })
        assertNull(state.purchaseSupplierId)
        assertEquals(TIENDA_ID, repo.lastOnHandBusinessUnitId)
        assertTrue(state.onHandItems.isNotEmpty())
    }

    @Test
    fun purchase_maps_numeric_ids_units_and_payment_into_backend_payload() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.selectSupplier(repo.suppliers.first())
        vm.onPurchaseSupplierDocNoChange(" FAC-1001 ")
        vm.onPurchasePaidCashChange(true)

        vm.onProductQueryChange("harina")
        vm.searchProducts()
        advanceUntilIdle()
        assertEquals("harina", repo.lastProductQuery)
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 100L })
        advanceUntilIdle()

        val units = vm.state.value.picker.unitOptions
        assertEquals(listOf(PZA_ID, CAJA_ID), units.map { it.unitId })
        vm.selectUnit(units.first { it.unitId == CAJA_ID })
        vm.onPurchaseLineQtyChange("3")
        vm.onPurchaseLineUnitCostChange("12.5")
        vm.addPurchaseLine()
        assertNull(vm.state.value.errorMessage)
        assertEquals(1, vm.state.value.purchaseLines.size)
        assertEquals(37.5, vm.state.value.purchaseTotal)
        // Picker is reset for the next line.
        assertNull(vm.state.value.picker.selectedProduct)

        vm.submitPurchase()
        advanceUntilIdle()

        val input = assertNotNull(repo.lastPurchaseInput)
        assertEquals(TIENDA_ID, input.businessUnitId)
        assertEquals(TERMINAL_ID, input.terminalId)
        assertEquals(1L, input.supplierId)
        assertEquals("FAC-1001", input.supplierDocNo)
        assertTrue(input.paidCash)
        assertEquals(TERMINAL_ID, input.paidFromTerminalId)
        val line = input.lines.single()
        assertEquals(100L, line.productId)
        assertEquals(CAJA_ID, line.unitId)
        assertEquals(3.0, line.qty)
        assertEquals(12.5, line.unitCost)
        assertNull(line.lotCode)

        val state = vm.state.value
        assertTrue(state.purchaseLines.isEmpty())
        assertEquals("#501", state.lastPurchaseResult)
        assertTrue(state.notice?.contains("Compra #501") == true)
        assertNull(state.purchaseIdempotencyKey)
    }

    @Test
    fun purchase_line_requires_lot_code_for_lot_tracked_products() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onProductQueryChange("queso")
        vm.searchProducts()
        advanceUntilIdle()
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 200L })
        advanceUntilIdle()
        vm.onPurchaseLineQtyChange("2")
        vm.onPurchaseLineUnitCostChange("80")
        vm.addPurchaseLine()
        assertTrue(vm.state.value.errorMessage?.contains("maneja lotes") == true)
        assertTrue(vm.state.value.purchaseLines.isEmpty())

        vm.onPurchaseLineLotCodeChange("L-2026-01")
        vm.onPurchaseLineExpiryDateChange("31/12/2026")
        vm.addPurchaseLine()
        assertTrue(vm.state.value.errorMessage?.contains("AAAA-MM-DD") == true)

        vm.onPurchaseLineExpiryDateChange("2026-12-31")
        vm.addPurchaseLine()
        assertNull(vm.state.value.errorMessage)
        val line = vm.state.value.purchaseLines.single()
        assertEquals("L-2026-01", line.lotCode)
        assertEquals("2026-12-31", line.expiryDate)
    }

    @Test
    fun purchase_validation_requires_supplier_and_lines_before_confirm_dialog() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.requestSubmitPurchase()
        assertTrue(vm.state.value.errorMessage?.contains("proveedor", ignoreCase = true) == true)
        assertNull(vm.state.value.confirmDialog)

        vm.selectSupplier(repo.suppliers.first())
        vm.requestSubmitPurchase()
        assertTrue(vm.state.value.errorMessage?.contains("línea") == true)
        assertEquals(0, repo.purchaseCalls)
    }

    @Test
    fun creating_a_supplier_adds_it_and_selects_it() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.toggleNewSupplierForm(true)
        vm.createSupplier()
        assertTrue(vm.state.value.errorMessage?.contains("nombre", ignoreCase = true) == true)

        vm.onNewSupplierNameChange("Abarrotes del Norte")
        vm.onNewSupplierPhoneChange("555-0199")
        vm.createSupplier()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Abarrotes del Norte", repo.lastSupplierInput?.name)
        assertEquals(2, state.suppliers.size)
        assertEquals(2L, state.purchaseSupplierId)
        assertEquals("Abarrotes del Norte", state.selectedSupplier?.name)
        assertTrue(!state.showNewSupplierForm)
    }

    @Test
    fun requisition_sends_consuming_business_unit_and_lot_for_lot_tracked_products() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.selectTab(OperationsTab.INTERNAL_REQ)
        vm.selectRequisitionBusinessUnit(vm.state.value.businessUnits.first { it.id == TORTERIA_ID })

        vm.onProductQueryChange("queso")
        vm.searchProducts()
        advanceUntilIdle()
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 200L })
        advanceUntilIdle()
        // Lots are loaded automatically for lot-tracked products.
        assertEquals(200L, repo.lastLotsProductId)
        assertEquals(1, vm.state.value.picker.lots.size)

        vm.onRequisitionLineQtyChange("2")
        vm.addRequisitionLine()
        assertTrue(vm.state.value.errorMessage?.contains("selecciona el lote") == true)

        vm.selectLot(vm.state.value.picker.lots.single())
        vm.addRequisitionLine()
        assertNull(vm.state.value.errorMessage)

        vm.submitRequisition()
        advanceUntilIdle()

        val input = assertNotNull(repo.lastRequisitionInput)
        assertEquals(TORTERIA_ID, input.businessUnitId)
        assertEquals(TERMINAL_ID, input.terminalId)
        assertEquals(900L, input.lines.single().lotId)
        assertEquals(PZA_ID, input.lines.single().unitId)
        assertTrue(vm.state.value.notice?.contains("REQ-TOR-0001") == true)
    }

    @Test
    fun requisition_rejects_tienda_as_consuming_unit() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.selectTab(OperationsTab.INTERNAL_REQ)
        vm.selectRequisitionBusinessUnit(vm.state.value.businessUnits.first { it.id == TIENDA_ID })
        vm.onProductQueryChange("harina")
        vm.searchProducts()
        advanceUntilIdle()
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 100L })
        advanceUntilIdle()
        vm.addRequisitionLine()
        vm.requestSubmitRequisition()

        assertTrue(vm.state.value.errorMessage?.contains("Fonda o Tortería") == true)
        assertNull(vm.state.value.confirmDialog)
    }

    @Test
    fun waste_is_admin_or_manager_only_and_posts_to_tienda_with_reason_code() = runTest(dispatcher) {
        val cashierRepo = FakeOperationsRepository()
        val cashierVm = vm(cashierRepo, cashierSession())
        advanceUntilIdle()
        cashierVm.submitWaste()
        assertTrue(cashierVm.state.value.errorMessage?.contains("ADMIN o MANAGER") == true)

        val repo = FakeOperationsRepository()
        val vm = vm(repo, adminSession())
        advanceUntilIdle()
        vm.selectTab(OperationsTab.WASTE)
        vm.onProductQueryChange("harina")
        vm.searchProducts()
        advanceUntilIdle()
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 100L })
        advanceUntilIdle()
        vm.onWasteQtyChange("1.5")
        vm.selectWasteReason(WasteReason.DAMAGED)
        vm.onWasteNotesChange("Caja rota")
        vm.requestSubmitWaste()
        assertNotNull(vm.state.value.confirmDialog)
        vm.confirmDialogAction()
        advanceUntilIdle()

        val input = assertNotNull(repo.lastWasteInput)
        assertEquals(TIENDA_ID, input.businessUnitId)
        assertEquals(TERMINAL_ID, input.terminalId)
        val line = input.lines.single()
        assertEquals(100L, line.productId)
        assertEquals(1.5, line.qty)
        assertEquals("DAMAGED", line.reason.code)
        assertEquals("Caja rota", line.notes)
        assertTrue(vm.state.value.notice?.contains("Merma #700") == true)
        assertNull(vm.state.value.picker.selectedProduct)
    }

    @Test
    fun adjustment_rejects_zero_delta_and_sends_negative_deltas() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.selectTab(OperationsTab.ADJUSTMENTS)
        vm.onProductQueryChange("harina")
        vm.searchProducts()
        advanceUntilIdle()
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 100L })
        advanceUntilIdle()

        vm.onAdjustmentQtyDeltaChange("0")
        vm.submitAdjustment()
        advanceUntilIdle()
        assertTrue(vm.state.value.errorMessage?.contains("distinto de 0") == true)
        assertNull(repo.lastAdjustmentInput)

        vm.onAdjustmentQtyDeltaChange("-2")
        vm.selectAdjustmentReason(AdjustmentReason.DATA_FIX)
        vm.submitAdjustment()
        advanceUntilIdle()

        val line = assertNotNull(repo.lastAdjustmentInput).lines.single()
        assertEquals(-2.0, line.qtyDelta)
        assertEquals("DATA_FIX", line.reason.code)
    }

    @Test
    fun failed_purchase_keeps_lines_and_reuses_idempotency_key_and_shows_server_message() = runTest(dispatcher) {
        val repo = FakeOperationsRepository().apply { failPurchase = true }
        val vm = vm(repo)
        advanceUntilIdle()

        vm.selectSupplier(repo.suppliers.first())
        vm.onProductQueryChange("harina")
        vm.searchProducts()
        advanceUntilIdle()
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 100L })
        advanceUntilIdle()
        vm.onPurchaseLineQtyChange("1")
        vm.onPurchaseLineUnitCostChange("2")
        vm.addPurchaseLine()

        vm.submitPurchase()
        advanceUntilIdle()
        assertTrue(vm.state.value.errorMessage?.contains("Open cash session is required") == true)
        assertEquals(1, vm.state.value.purchaseLines.size)

        vm.submitPurchase()
        advanceUntilIdle()

        assertEquals(2, repo.purchaseCalls)
        assertEquals(repo.purchaseKeys.first(), repo.purchaseKeys.last())
    }

    @Test
    fun on_hand_filters_by_business_unit_and_quick_search() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.selectOnHandBusinessUnit(null)
        advanceUntilIdle()
        assertNull(repo.lastOnHandBusinessUnitId)
        assertEquals(2, vm.state.value.onHandItems.size)

        vm.onOnHandQuickSearchChange("ques")
        assertEquals(listOf(200L), vm.state.value.visibleOnHandItems.map { it.productId })

        vm.onOnHandQuickSearchChange("100")
        assertEquals(listOf(100L), vm.state.value.visibleOnHandItems.map { it.productId })
    }

    @Test
    fun lots_tab_loads_lots_when_a_product_is_selected_even_if_not_lot_tracked() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.selectTab(OperationsTab.LOTS)
        vm.onProductQueryChange("harina")
        vm.searchProducts()
        advanceUntilIdle()
        vm.selectProduct(vm.state.value.picker.results.first { it.id == 100L })
        advanceUntilIdle()

        assertEquals(100L, repo.lastLotsProductId)
        assertTrue(vm.state.value.notice?.contains("Sin lotes") == true)

        vm.selectTab(OperationsTab.PURCHASES)
        assertNull(vm.state.value.picker.selectedProduct)
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun managerSession() = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("u1", "manager", setOf(UserRole.MANAGER)),
        terminalCode = "POS1",
    )

    private fun adminSession() = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("u0", "admin", setOf(UserRole.ADMIN)),
        terminalCode = "POS1",
    )

    private fun cashierSession() = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("u2", "cashier", setOf(UserRole.CASHIER)),
        terminalCode = "POS1",
    )

    private class FakeOperationsRepository : OperationsRepository {
        val harina = OperationsProduct(100L, "Harina de trigo", "HAR-1", null, "PZA", PZA_ID, lotTracked = false)
        val queso = OperationsProduct(200L, "Queso Oaxaca", "QUE-1", "750", "PZA", PZA_ID, lotTracked = true)
        val suppliers = mutableListOf(Supplier(1L, "Proveedor Uno", null, null, true))

        var failPurchase = false
        var purchaseCalls = 0
        val purchaseKeys = mutableListOf<String>()

        var lastProductQuery: String? = null
        var lastPurchaseInput: PurchaseInput? = null
        var lastRequisitionInput: InternalRequisitionInput? = null
        var lastWasteInput: WasteInput? = null
        var lastAdjustmentInput: AdjustmentInput? = null
        var lastSupplierInput: SupplierInput? = null
        var lastOnHandBusinessUnitId: Long? = null
        var lastLotsProductId: Long? = null

        override suspend fun listBusinessUnits() = AppResult.Success(
            listOf(
                OperationsBusinessUnit(TIENDA_ID, "TIENDA", "Tienda"),
                OperationsBusinessUnit(FONDA_ID, "FONDA", "Fonda"),
                OperationsBusinessUnit(TORTERIA_ID, "TORTERIA", "Tortería"),
            )
        )

        override suspend fun listUnits() = AppResult.Success(
            listOf(OperationsUnit(PZA_ID, "PZA", "Pieza"), OperationsUnit(CAJA_ID, "CAJA", "Caja"))
        )

        override suspend fun listSuppliers() = AppResult.Success(suppliers.toList())

        override suspend fun createSupplier(input: SupplierInput): AppResult<Supplier> {
            lastSupplierInput = input
            val created = Supplier(2L, input.name, input.phone, input.taxId, true)
            suppliers += created
            return AppResult.Success(created)
        }

        override suspend fun searchProducts(query: String, limit: Int): AppResult<List<OperationsProduct>> {
            lastProductQuery = query
            return AppResult.Success(listOf(harina, queso).filter { it.name.contains(query, ignoreCase = true) })
        }

        override suspend fun productUnitOptions(product: OperationsProduct): AppResult<List<ProductUnitOption>> {
            val base = ProductUnitOption(product.baseUnitId, product.baseUnitCode, 1.0)
            return AppResult.Success(if (product.id == harina.id) listOf(base, ProductUnitOption(CAJA_ID, "CAJA", 12.0)) else listOf(base))
        }

        override suspend fun createPurchase(input: PurchaseInput, idempotencyKey: String): AppResult<PurchaseResult> {
            purchaseCalls++
            purchaseKeys += idempotencyKey
            lastPurchaseInput = input
            return if (failPurchase) {
                AppResult.Failure(AppError.Validation("Open cash session is required for paidFromTerminalId=1"))
            } else {
                AppResult.Success(PurchaseResult(501L))
            }
        }

        override suspend fun createInternalRequisition(input: InternalRequisitionInput, idempotencyKey: String): AppResult<InternalRequisitionResult> {
            lastRequisitionInput = input
            return AppResult.Success(InternalRequisitionResult(601L, "REQ-TOR-0001"))
        }

        override suspend fun fetchOnHand(productId: Long?, businessUnitId: Long?): AppResult<List<InventoryOnHandItem>> {
            lastOnHandBusinessUnitId = businessUnitId
            val all = listOf(
                InventoryOnHandItem(100L, "Harina de trigo", TIENDA_ID, "TIENDA", 20.0, "PZA"),
                InventoryOnHandItem(200L, "Queso Oaxaca", FONDA_ID, "FONDA", 5.0, "PZA"),
            )
            return AppResult.Success(all.filter { businessUnitId == null || it.businessUnitId == businessUnitId })
        }

        override suspend fun fetchLots(productId: Long): AppResult<List<InventoryLotItem>> {
            lastLotsProductId = productId
            return AppResult.Success(
                if (productId == queso.id) listOf(InventoryLotItem(900L, productId, "L001", "2026-12-01", 5.0)) else emptyList()
            )
        }

        override suspend fun createWaste(input: WasteInput, idempotencyKey: String): AppResult<InventoryOperationResult> {
            lastWasteInput = input
            return AppResult.Success(InventoryOperationResult(700L))
        }

        override suspend fun createAdjustment(input: AdjustmentInput, idempotencyKey: String): AppResult<InventoryOperationResult> {
            lastAdjustmentInput = input
            return AppResult.Success(InventoryOperationResult(701L))
        }
    }
}
