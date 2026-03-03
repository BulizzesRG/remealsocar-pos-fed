package com.posfab.shared.features.sale

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CheckoutMode
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.DraftIssue
import com.posfab.shared.features.sale.domain.DraftValidation
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.features.sale.repository.SaleRepository
import com.posfab.shared.features.sale.ui.CashierSaleViewModel
import com.posfab.shared.features.sale.usecase.SaleUseCases
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CashierSaleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun add_edit_remove_line_updates_state() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = CashierSaleViewModel(SaleUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.addProduct(repo.searchPool.first())
        advanceUntilIdle()
        assertEquals(1, vm.state.value.draft?.lines?.size)

        val lineId = vm.state.value.draft?.lines?.first()?.id ?: error("line missing")
        vm.selectLine(lineId)
        vm.incrementSelectedLineQty()
        advanceUntilIdle()
        assertEquals(2.0, vm.state.value.draft?.lines?.first()?.qty)

        vm.removeSelectedLine()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.draft?.lines?.size)
    }

    @Test
    fun conflict_update_triggers_refetch_notice() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = CashierSaleViewModel(SaleUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.addProduct(repo.searchPool.first())
        advanceUntilIdle()

        repo.forceConflictOnNextUpdate = true
        vm.selectLine(vm.state.value.draft!!.lines.first().id)
        vm.incrementSelectedLineQty()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice?.contains("recargo", ignoreCase = true) == true)
        assertEquals(3L, vm.state.value.draft?.version)
    }

    @Test
    fun checkout_double_click_sends_one_effective_finalize() = runTest(dispatcher) {
        val repo = FakeSaleRepository(checkoutDelayMs = 50)
        val vm = CashierSaleViewModel(SaleUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.addProduct(repo.searchPool.first())
        advanceUntilIdle()

        vm.checkoutCash()
        vm.checkoutCash()
        advanceUntilIdle()

        assertEquals(1, repo.checkoutCalls)
        assertEquals(1, repo.checkoutKeys.distinct().size)
        assertNotNull(vm.state.value.checkoutResult)
    }

    @Test
    fun barcode_lookup_populates_line() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = CashierSaleViewModel(SaleUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.onBarcodeChange("7501000100015")
        vm.addByBarcode()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.draft?.lines?.size)
        assertEquals("Cafe 500g", vm.state.value.draft?.lines?.first()?.productName)
    }

    @Test
    fun validate_and_resolve_lots_updates_issue_state() = runTest(dispatcher) {
        val repo = FakeSaleRepository()
        val vm = CashierSaleViewModel(SaleUseCases(repo), dispatcher)
        advanceUntilIdle()

        vm.addProduct(repo.searchPool.first())
        advanceUntilIdle()

        vm.validateDraft()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.validationIssues.size)

        vm.resolveLots()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.validationIssues.size)
        assertEquals("LOT-AUTO", vm.state.value.draft?.lines?.first()?.lotId)
    }
}

private class FakeSaleRepository(
    private val checkoutDelayMs: Long = 0,
) : SaleRepository {
    val searchPool = listOf(
        SaleProduct(
            id = "p1",
            name = "Cafe 500g",
            sku = "CAF-500",
            barcode = "7501000100015",
            defaultUnit = "PZA",
            unitPrice = 120.0,
            lotTracked = true,
        )
    )

    private var draft = SaleDraft(
        id = "draft-1",
        status = "OPEN",
        type = "SALE",
        terminalCode = "POS1",
        version = 1,
        lines = emptyList(),
        totals = SaleTotals(subtotal = 0.0, tax = 0.0, total = 0.0),
    )

    var forceConflictOnNextUpdate = false
    var checkoutCalls = 0
    val checkoutKeys = mutableListOf<String>()

    override suspend fun openOrCreateCurrentSaleDraft(): AppResult<SaleDraft> = AppResult.Success(draft)

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> = AppResult.Success(searchPool)

    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> {
        return AppResult.Success(searchPool.firstOrNull { it.barcode == barcode })
    }

    override suspend fun addLine(
        draftId: String,
        product: SaleProduct,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult {
        val nextLine = SaleLine(
            id = "line-${draft.lines.size + 1}",
            productId = product.id,
            productName = product.name,
            barcode = product.barcode,
            qty = qty,
            unit = unit,
            unitPrice = unitPrice,
            lineTotal = qty * unitPrice,
            lotId = lotId,
            lotTracked = product.lotTracked,
        )
        draft = draft.copy(
            version = draft.version + 1,
            lines = draft.lines + nextLine,
            totals = draftTotals((draft.lines + nextLine)),
        )
        return SaleMutationResult.Success(draft)
    }

    override suspend fun updateLine(
        draftId: String,
        lineId: String,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult {
        if (forceConflictOnNextUpdate) {
            forceConflictOnNextUpdate = false
            draft = draft.copy(version = draft.version + 1)
            return SaleMutationResult.ConflictRefetched(draft)
        }

        val updated = draft.lines.map { line ->
            if (line.id == lineId) {
                line.copy(qty = qty, unit = unit, unitPrice = unitPrice, lineTotal = qty * unitPrice, lotId = lotId)
            } else line
        }
        draft = draft.copy(version = draft.version + 1, lines = updated, totals = draftTotals(updated))
        return SaleMutationResult.Success(draft)
    }

    override suspend fun removeLine(draftId: String, lineId: String, expectedVersion: Long): SaleMutationResult {
        val updated = draft.lines.filterNot { it.id == lineId }
        draft = draft.copy(version = draft.version + 1, lines = updated, totals = draftTotals(updated))
        return SaleMutationResult.Success(draft)
    }

    override suspend fun validateDraft(draftId: String): AppResult<DraftValidation> {
        val issues = draft.lines.filter { it.lotTracked && it.lotId == null }
            .map { DraftIssue(lineId = it.id, code = "LOT_REQUIRED", message = "Falta lote para ${it.productName}") }
        return AppResult.Success(DraftValidation(issues))
    }

    override suspend fun resolveLots(draftId: String): AppResult<SaleDraft> {
        val updated = draft.lines.map { if (it.lotTracked) it.copy(lotId = "LOT-AUTO") else it }
        draft = draft.copy(version = draft.version + 1, lines = updated, totals = draftTotals(updated))
        return AppResult.Success(draft)
    }

    override suspend fun checkout(
        draftId: String,
        mode: CheckoutMode,
        expectedVersion: Long,
        idempotencyKey: String,
    ): AppResult<CheckoutResult> {
        checkoutCalls += 1
        checkoutKeys += idempotencyKey
        if (checkoutDelayMs > 0) delay(checkoutDelayMs)
        draft = draft.copy(status = "COMPLETED")
        return AppResult.Success(
            CheckoutResult(
                saleId = "sale-1",
                folio = "FOL-0001",
                total = draft.totals.total,
                mode = mode,
            )
        )
    }

    override suspend fun refetchCurrentDraft(): AppResult<SaleDraft> = AppResult.Success(draft)

    private fun draftTotals(lines: List<SaleLine>): SaleTotals {
        val subtotal = lines.sumOf { it.lineTotal }
        val tax = subtotal * 0.16
        return SaleTotals(subtotal = subtotal, tax = tax, total = subtotal + tax)
    }
}
