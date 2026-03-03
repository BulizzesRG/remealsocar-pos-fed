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
    suspend fun openDraft(): AppResult<SaleDraft> = repository.openOrCreateCurrentSaleDraft()
    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> = repository.searchProducts(query, limit)
    suspend fun barcodeLookup(barcode: String): AppResult<SaleProduct?> = repository.findProductByBarcode(barcode)

    suspend fun addLine(
        draftId: String,
        product: SaleProduct,
        qty: Double,
        unit: String,
        price: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult {
        return repository.addLine(draftId, product, qty, unit, price, lotId, expectedVersion)
    }

    suspend fun updateLine(draftId: String, lineId: String, qty: Double, unit: String, price: Double, lotId: String?, expectedVersion: Long): SaleMutationResult {
        return repository.updateLine(draftId, lineId, qty, unit, price, lotId, expectedVersion)
    }

    suspend fun removeLine(draftId: String, lineId: String, expectedVersion: Long): SaleMutationResult {
        return repository.removeLine(draftId, lineId, expectedVersion)
    }

    suspend fun validateDraft(draftId: String): AppResult<DraftValidation> = repository.validateDraft(draftId)
    suspend fun resolveLots(draftId: String): AppResult<SaleDraft> = repository.resolveLots(draftId)

    suspend fun checkout(draftId: String, mode: CheckoutMode, expectedVersion: Long, idempotencyKey: String): AppResult<CheckoutResult> {
        return repository.checkout(draftId, mode, expectedVersion, idempotencyKey)
    }

    suspend fun refetchDraft(): AppResult<SaleDraft> = repository.refetchCurrentDraft()
}
