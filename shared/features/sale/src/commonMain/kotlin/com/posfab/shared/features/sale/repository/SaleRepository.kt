package com.posfab.shared.features.sale.repository

import com.posfab.shared.features.sale.domain.CheckoutMode
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.DraftValidation
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.core.result.AppResult

interface SaleRepository {
    suspend fun openOrCreateCurrentSaleDraft(): AppResult<SaleDraft>
    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>>
    suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?>
    suspend fun addLine(
        draftId: String,
        product: SaleProduct,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult
    suspend fun updateLine(
        draftId: String,
        lineId: String,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult

    suspend fun removeLine(draftId: String, lineId: String, expectedVersion: Long): SaleMutationResult
    suspend fun validateDraft(draftId: String): AppResult<DraftValidation>
    suspend fun resolveLots(draftId: String): AppResult<SaleDraft>
    suspend fun checkout(draftId: String, mode: CheckoutMode, expectedVersion: Long, idempotencyKey: String): AppResult<CheckoutResult>
    suspend fun refetchCurrentDraft(): AppResult<SaleDraft>
}
