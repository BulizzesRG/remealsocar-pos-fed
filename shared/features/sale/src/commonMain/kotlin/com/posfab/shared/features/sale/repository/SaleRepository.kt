package com.posfab.shared.features.sale.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.CustomerPaymentResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleCustomer
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLineInput
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SaleProduct

interface SaleRepository {
    suspend fun cashSession(): AppResult<CashSessionCheck>
    suspend fun openDrafts(): AppResult<List<SaleDraft>>
    suspend fun draft(draftId: Long): AppResult<SaleDraft>
    suspend fun newDraft(): AppResult<SaleDraft>
    suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>>
    suspend fun kitchenProducts(): AppResult<List<SaleProduct>>
    suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?>
    suspend fun variosProduct(): AppResult<SaleProduct?>
    suspend fun addLine(draftId: Long, product: SaleProduct, input: SaleLineInput, expectedVersion: Int): SaleMutationResult
    suspend fun updateLine(draftId: Long, lineId: Long, input: SaleLineInput, expectedVersion: Int): SaleMutationResult
    suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult
    suspend fun resolveLots(draftId: Long): AppResult<SaleDraft>
    suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit>
    suspend fun setChannel(draftId: Long, channel: SaleChannel, expectedVersion: Int): SaleMutationResult
    suspend fun checkout(
        draftId: Long,
        paymentType: SalePaymentType,
        paidAmount: Double,
        idempotencyKey: String,
        customerId: Long? = null,
    ): AppResult<CheckoutResult>
    suspend fun searchCustomers(query: String, limit: Int): AppResult<List<SaleCustomer>>
    suspend fun customer(customerId: Long): AppResult<SaleCustomer>
    suspend fun createCustomer(name: String, nickname: String?, phone: String?): AppResult<SaleCustomer>
    suspend fun recordCustomerPayment(
        customerId: Long,
        amount: Double,
        idempotencyKey: String,
    ): AppResult<CustomerPaymentResult>
}
