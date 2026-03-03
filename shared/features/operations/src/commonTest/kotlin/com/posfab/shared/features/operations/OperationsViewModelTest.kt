package com.posfab.shared.features.operations

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.operations.common.OperationsUseCases
import com.posfab.shared.features.operations.domain.AdjustmentInput
import com.posfab.shared.features.operations.domain.AdjustmentResult
import com.posfab.shared.features.operations.domain.InternalRequisitionInput
import com.posfab.shared.features.operations.domain.InternalRequisitionResult
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.InventoryOnHandItem
import com.posfab.shared.features.operations.domain.PurchaseInput
import com.posfab.shared.features.operations.domain.PurchaseResult
import com.posfab.shared.features.operations.domain.WasteInput
import com.posfab.shared.features.operations.domain.WasteResult
import com.posfab.shared.features.operations.repository.OperationsRepository
import com.posfab.shared.features.operations.ui.OperationsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OperationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun purchase_submit_payload_mapping_and_success_error_handling() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = OperationsViewModel(managerSession(), OperationsUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onPurchaseSupplierChange("Proveedor Uno")
        vm.onBusinessUnitChange("TIENDA")
        vm.onTerminalIdChange("POS1")
        vm.onPurchaseLineProductChange("prod-1")
        vm.onPurchaseLineUnitChange("KG")
        vm.onPurchaseLineQtyChange("3")
        vm.onPurchaseLineUnitCostChange("12.5")
        vm.addPurchaseLine()
        vm.submitPurchase()
        advanceUntilIdle()

        assertEquals("Proveedor Uno", repo.lastPurchaseInput?.supplier)
        assertEquals("prod-1", repo.lastPurchaseInput?.lines?.firstOrNull()?.productId)
        assertTrue(vm.state.value.notice?.contains("Compra registrada", ignoreCase = true) == true)

        repo.failPurchase = true
        vm.onPurchaseLineProductChange("prod-2")
        vm.onPurchaseLineUnitChange("EA")
        vm.onPurchaseLineQtyChange("1")
        vm.onPurchaseLineUnitCostChange("1")
        vm.addPurchaseLine()
        vm.submitPurchase()
        advanceUntilIdle()

        assertTrue(vm.state.value.errorMessage?.contains("Datos invalidos", ignoreCase = true) == true)
    }

    @Test
    fun internal_requisition_submit_flow() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = OperationsViewModel(managerSession(), OperationsUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onReqSourceBuChange("TIENDA")
        vm.onReqTargetBuChange("FONDA")
        vm.onReqLineProductChange("prod-3")
        vm.onReqLineUnitChange("EA")
        vm.onReqLineQtyChange("2")
        vm.addRequisitionLine()
        vm.submitRequisition()
        advanceUntilIdle()

        assertEquals("FONDA", repo.lastRequisitionInput?.targetBusinessUnit)
        assertTrue(vm.state.value.notice?.contains("Requisicion registrada", ignoreCase = true) == true)
    }

    @Test
    fun inventory_on_hand_lots_fetch_and_state_rendering() = runTest(dispatcher) {
        val repo = FakeOperationsRepository()
        val vm = OperationsViewModel(managerSession(), OperationsUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onOnHandProductFilterChange("prod-1")
        vm.refreshOnHand()
        advanceUntilIdle()

        assertEquals("prod-1", repo.lastOnHandProductFilter)
        assertTrue(vm.state.value.onHandItems.isNotEmpty())

        vm.onLotsProductIdChange("prod-1")
        vm.loadLots()
        advanceUntilIdle()

        assertEquals("prod-1", repo.lastLotsProductId)
        assertTrue(vm.state.value.lotItems.isNotEmpty())
    }

    @Test
    fun waste_adjustment_role_restrictions_and_validation() = runTest(dispatcher) {
        val cashierRepo = FakeOperationsRepository()
        val cashierVm = OperationsViewModel(cashierSession(), OperationsUseCases(cashierRepo), dispatcher)
        advanceUntilIdle()

        cashierVm.onWasteProductChange("prod-1")
        cashierVm.onWasteQtyChange("1")
        cashierVm.submitWaste()
        advanceUntilIdle()

        assertTrue(cashierVm.state.value.errorMessage?.contains("Solo MANAGER", ignoreCase = true) == true)

        val managerRepo = FakeOperationsRepository()
        val managerVm = OperationsViewModel(managerSession(), OperationsUseCases(managerRepo), dispatcher)
        advanceUntilIdle()

        managerVm.onAdjustmentProductChange("prod-1")
        managerVm.onAdjustmentUnitChange("EA")
        managerVm.onAdjustmentReasonChange("CONTEO")
        managerVm.onAdjustmentQtyDeltaChange("0")
        managerVm.submitAdjustment()
        advanceUntilIdle()

        assertTrue(managerVm.state.value.errorMessage?.contains("qtyDelta", ignoreCase = true) == true)
    }

    @Test
    fun duplicate_submit_protection_reuses_idempotency_key_after_failure() = runTest(dispatcher) {
        val repo = FakeOperationsRepository().apply { failPurchase = true }
        val vm = OperationsViewModel(managerSession(), OperationsUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onPurchaseSupplierChange("Proveedor Reintento")
        vm.onPurchaseLineProductChange("prod-9")
        vm.onPurchaseLineUnitChange("EA")
        vm.onPurchaseLineQtyChange("1")
        vm.onPurchaseLineUnitCostChange("2")
        vm.addPurchaseLine()

        vm.submitPurchase()
        advanceUntilIdle()
        val firstKey = repo.purchaseKeys.firstOrNull()

        vm.submitPurchase()
        advanceUntilIdle()
        val secondKey = repo.purchaseKeys.lastOrNull()

        assertEquals(2, repo.purchaseCalls)
        assertEquals(firstKey, secondKey)
    }

    private fun managerSession() = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("u1", "manager", setOf(UserRole.MANAGER)),
        terminal = TerminalCode.POS1,
    )

    private fun cashierSession() = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("u2", "cashier", setOf(UserRole.CASHIER)),
        terminal = TerminalCode.POS1,
    )
}

private class FakeOperationsRepository : OperationsRepository {
    var failPurchase: Boolean = false
    var purchaseCalls: Int = 0
    val purchaseKeys: MutableList<String> = mutableListOf()

    var lastPurchaseInput: PurchaseInput? = null
    var lastRequisitionInput: InternalRequisitionInput? = null
    var lastOnHandProductFilter: String? = null
    var lastLotsProductId: String? = null

    override suspend fun createPurchase(input: PurchaseInput, idempotencyKey: String): AppResult<PurchaseResult> {
        purchaseCalls++
        purchaseKeys += idempotencyKey
        lastPurchaseInput = input
        return if (failPurchase) {
            AppResult.Failure(AppError.Validation("duplicate payload"))
        } else {
            AppResult.Success(PurchaseResult(purchaseId = "pur-1", folio = "P-001", message = null))
        }
    }

    override suspend fun createInternalRequisition(input: InternalRequisitionInput): AppResult<InternalRequisitionResult> {
        lastRequisitionInput = input
        return AppResult.Success(InternalRequisitionResult(requisitionId = "req-1", folio = "R-001", message = null))
    }

    override suspend fun fetchOnHand(productId: String?, businessUnit: String?): AppResult<List<InventoryOnHandItem>> {
        lastOnHandProductFilter = productId
        return AppResult.Success(
            listOf(
                InventoryOnHandItem(
                    productId = "prod-1",
                    productName = "Harina",
                    businessUnit = businessUnit ?: "TIENDA",
                    unit = "KG",
                    onHand = 20.0,
                )
            )
        )
    }

    override suspend fun fetchLots(productId: String): AppResult<List<InventoryLotItem>> {
        lastLotsProductId = productId
        return AppResult.Success(
            listOf(
                InventoryLotItem(
                    productId = productId,
                    lotId = "lot-1",
                    lotCode = "L001",
                    expiryDate = "2026-12-01",
                    onHand = 5.0,
                    unit = "KG",
                )
            )
        )
    }

    override suspend fun createWaste(input: WasteInput, idempotencyKey: String): AppResult<WasteResult> {
        return AppResult.Success(WasteResult(wasteId = "w1", message = null))
    }

    override suspend fun createAdjustment(input: AdjustmentInput, idempotencyKey: String): AppResult<AdjustmentResult> {
        return AppResult.Success(AdjustmentResult(adjustmentId = "a1", message = null))
    }
}
