package com.posfab.shared.features.sale.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.repository.SaleRepository

class SaleUseCases(private val repository: SaleRepository) {
    suspend fun cashSession(): AppResult<CashSessionCheck> = repository.cashSession()

    suspend fun currentOrNewDraft(): AppResult<SaleDraft> = repository.currentOrNewDraft()

    suspend fun newDraft(): AppResult<SaleDraft> = repository.newDraft()

    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> =
        repository.searchProducts(query, limit)

    suspend fun kitchenProducts(): AppResult<List<SaleProduct>> = repository.kitchenProducts()

    suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> =
        repository.findProductByBarcode(barcode)

    suspend fun addLine(draftId: Long, product: SaleProduct, qty: Double, expectedVersion: Int): SaleMutationResult =
        repository.addLine(draftId, product, qty, expectedVersion)

    suspend fun updateLineQty(draftId: Long, lineId: Long, qty: Double, expectedVersion: Int): SaleMutationResult =
        repository.updateLineQty(draftId, lineId, qty, expectedVersion)

    suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult =
        repository.removeLine(draftId, lineId, expectedVersion)

    suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> = repository.resolveLots(draftId)

    suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit> =
        repository.cancelDraft(draftId, expectedVersion)

    suspend fun checkoutCash(draftId: Long, paidAmount: Double, idempotencyKey: String): AppResult<CashCheckoutResult> =
        repository.checkoutCash(draftId, paidAmount, idempotencyKey)
}
