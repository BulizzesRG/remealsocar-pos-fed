package com.posfab.shared.features.sale.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLineInput
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.repository.SaleRepository

class SaleUseCases(private val repository: SaleRepository) {
    suspend fun cashSession(): AppResult<CashSessionCheck> = repository.cashSession()

    suspend fun openDrafts(): AppResult<List<SaleDraft>> = repository.openDrafts()

    suspend fun draft(draftId: Long): AppResult<SaleDraft> = repository.draft(draftId)

    suspend fun newDraft(): AppResult<SaleDraft> = repository.newDraft()

    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> =
        repository.searchProducts(query, limit)

    suspend fun kitchenProducts(): AppResult<List<SaleProduct>> = repository.kitchenProducts()

    suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> =
        repository.findProductByBarcode(barcode)

    suspend fun variosProduct(): AppResult<SaleProduct?> = repository.variosProduct()

    suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        input: SaleLineInput,
        expectedVersion: Int,
    ): SaleMutationResult = repository.addLine(draftId, product, input, expectedVersion)

    suspend fun updateLine(
        draftId: Long,
        lineId: Long,
        input: SaleLineInput,
        expectedVersion: Int,
    ): SaleMutationResult = repository.updateLine(draftId, lineId, input, expectedVersion)

    suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult =
        repository.removeLine(draftId, lineId, expectedVersion)

    suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> = repository.resolveLots(draftId)

    suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit> =
        repository.cancelDraft(draftId, expectedVersion)

    suspend fun setChannel(draftId: Long, channel: SaleChannel, expectedVersion: Int): SaleMutationResult =
        repository.setChannel(draftId, channel, expectedVersion)

    suspend fun checkout(
        draftId: Long,
        paymentType: SalePaymentType,
        paidAmount: Double,
        idempotencyKey: String,
    ): AppResult<CheckoutResult> = repository.checkout(draftId, paymentType, paidAmount, idempotencyKey)
}
