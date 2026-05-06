package com.posfab.shared.features.sale.repository

import com.posfab.shared.features.sale.domain.CheckoutMode
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.DraftValidation
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.core.result.AppResult

interface SaleRepository {
    suspend fun isCashSessionOpen(): AppResult<Boolean>
    suspend fun openOrCreateCurrentSaleDraft(): AppResult<SaleDraft>
    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>>
    suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?>
    suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        qty: Double,
        unitId: Long,
        unitPrice: Double,
        lotId: Long?,
        expectedVersion: Int,
    ): SaleMutationResult
    suspend fun updateLine(
        draftId: Long,
        lineId: Long,
        qty: Double,
        unitId: Long,
        unitPrice: Double,
        lotId: Long?,
        expectedVersion: Int,
    ): SaleMutationResult
    suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult
    suspend fun validateDraft(draftId: Long): AppResult<DraftValidation>
    suspend fun resolveLots(draftId: Long): AppResult<SaleDraft>
    suspend fun checkout(draftId: Long, mode: CheckoutMode, expectedVersion: Int, idempotencyKey: String): AppResult<CheckoutResult>
    suspend fun refetchCurrentDraft(): AppResult<SaleDraft>
}
