package com.posfab.shared.features.sale.ui

import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.roundMoney

sealed interface SalePhase {
    data object Loading : SalePhase
    data object CashClosed : SalePhase
    data class CashUnavailable(val message: String) : SalePhase
    data object Selling : SalePhase
    data object Charging : SalePhase
    data object Done : SalePhase
}

sealed interface SaleConfirm {
    data object ClearCart : SaleConfirm
}

data class CashierSaleState(
    val phase: SalePhase = SalePhase.Loading,
    val accounts: List<SaleDraft> = emptyList(),
    val activeDraftId: Long? = null,
    val query: String = "",
    val searchResults: List<SaleProduct> = emptyList(),
    val kitchenProducts: List<SaleProduct> = emptyList(),
    val selectedLineId: Long? = null,
    val qtyInput: String = "",
    val pendingMutations: Int = 0,
    val isCheckoutInFlight: Boolean = false,
    val checkoutIdempotencyKey: String? = null,
    val paidInput: String = "",
    val lastResult: CashCheckoutResult? = null,
    val confirm: SaleConfirm? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
) {
    val draft: SaleDraft? get() = activeDraftId?.let(::accountById)
    val isBusy: Boolean get() = pendingMutations > 0
    val isPaidLocked: Boolean get() = checkoutIdempotencyKey != null
    val cashTotal: Double get() = draft?.totals?.cashTotal ?: 0.0
    val paidAmount: Double? get() = paidInput.toDoubleOrNull()
    val change: Double? get() = paidAmount?.let { roundMoney(it - cashTotal) }
    val canConfirmCharge: Boolean
        get() = paidAmount?.let { roundMoney(it) >= roundMoney(cashTotal) } == true && !isCheckoutInFlight
    val canCharge: Boolean
        get() = phase == SalePhase.Selling && draft?.lines?.isNotEmpty() == true && cashTotal > 0 && !isBusy
    val selectedLine: SaleLine? get() = selectedLineId?.let { id -> draft?.lines?.firstOrNull { it.id == id } }
    val canOpenAccount: Boolean
        get() = (phase == SalePhase.Selling || phase == SalePhase.Done) &&
            accounts.size < MAX_ACCOUNTS &&
            draft?.lines?.isNotEmpty() != false &&
            !isCheckoutInFlight
    val canCloseEmptyAccount: Boolean
        get() = phase == SalePhase.Selling && draft?.lines?.isEmpty() == true && accounts.size > 1

    fun accountById(id: Long): SaleDraft? = accounts.firstOrNull { it.id == id }
}

internal const val MAX_ACCOUNTS = 10
