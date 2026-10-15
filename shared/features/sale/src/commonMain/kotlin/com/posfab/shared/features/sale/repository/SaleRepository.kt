package com.posfab.shared.features.sale.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct

interface SaleRepository {
    suspend fun cashSession(): AppResult<CashSessionCheck>
    suspend fun currentOrNewDraft(): AppResult<SaleDraft>
    suspend fun newDraft(): AppResult<SaleDraft>
    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>>
    suspend fun kitchenProducts(): AppResult<List<SaleProduct>>
    suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?>
    suspend fun addLine(draftId: Long, product: SaleProduct, qty: Double, expectedVersion: Int): SaleMutationResult
    suspend fun updateLineQty(draftId: Long, lineId: Long, qty: Double, expectedVersion: Int): SaleMutationResult
    suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult
    suspend fun resolveLots(draftId: Long): AppResult<SaleDraft>
    suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit>
    suspend fun checkoutCash(draftId: Long, paidAmount: Double, idempotencyKey: String): AppResult<CashCheckoutResult>
}
