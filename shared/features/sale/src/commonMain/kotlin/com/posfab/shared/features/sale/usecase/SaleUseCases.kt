package com.posfab.shared.features.sale.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CheckoutMode
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.DraftValidation
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.repository.SaleRepository

class SaleUseCases(private val repository: SaleRepository) {
    suspend fun isCashSessionOpen(): AppResult<Boolean> = repository.isCashSessionOpen()
    suspend fun openDraft(): AppResult<SaleDraft> = repository.openOrCreateCurrentSaleDraft()
    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> = repository.searchProducts(query, limit)
    suspend fun barcodeLookup(barcode: String): AppResult<SaleProduct?> = repository.findProductByBarcode(barcode)

    suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        qty: Double,
        unitId: Long,
        price: Double,
        lotId: Long?,
        expectedVersion: Int,
    ): SaleMutationResult {
        return repository.addLine(draftId, product, qty, unitId, price, lotId, expectedVersion)
    }

    suspend fun updateLine(
        draftId: Long,
        lineId: Long,
        qty: Double,
        unitId: Long,
        price: Double,
        lotId: Long?,
        expectedVersion: Int,
    ): SaleMutationResult {
        return repository.updateLine(draftId, lineId, qty, unitId, price, lotId, expectedVersion)
    }

    suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult {
        return repository.removeLine(draftId, lineId, expectedVersion)
    }

    suspend fun validateDraft(draftId: Long): AppResult<DraftValidation> = repository.validateDraft(draftId)
    suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> = repository.resolveLots(draftId)

    suspend fun checkout(draftId: Long, mode: CheckoutMode, expectedVersion: Int, idempotencyKey: String): AppResult<CheckoutResult> {
        return repository.checkout(draftId, mode, expectedVersion, idempotencyKey)
    }

    suspend fun refetchDraft(): AppResult<SaleDraft> = repository.refetchCurrentDraft()
}
