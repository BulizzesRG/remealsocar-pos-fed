package com.posfab.shared.features.sale.ui

import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleCustomer
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SalePaymentType
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

enum class WeightMode { KILOS, PESOS }

sealed interface SalePrompt {
    val product: SaleProduct
    val lineId: Long?

    data class OpenAmount(override val product: SaleProduct, override val lineId: Long? = null) : SalePrompt

    data class Weight(
        override val product: SaleProduct,
        override val lineId: Long? = null,
        val mode: WeightMode = WeightMode.KILOS,
    ) : SalePrompt
}

data class NewCustomerForm(
    val name: String = "",
    val phone: String = "",
    val isSaving: Boolean = false,
)

data class LastAbono(
    val amount: Double,
    val customerName: String,
    val balanceAfter: Double,
)

data class CreditState(
    val customer: SaleCustomer? = null,
    val pickerQuery: String = "",
    val pickerResults: List<SaleCustomer> = emptyList(),
    val isSearching: Boolean = false,
    val searchedQuery: String? = null,
    val newCustomer: NewCustomerForm? = null,
    val abonoInput: String = "",
    val abonoInputRejected: Boolean = false,
    val isAbonoInFlight: Boolean = false,
    val abonoKey: String? = null,
    val lastAbono: LastAbono? = null,
) {
    val showsPicker: Boolean get() = customer == null && newCustomer == null
    val showsNoMatches: Boolean
        get() = showsPicker && !isSearching && pickerResults.isEmpty() &&
            searchedQuery != null && searchedQuery == pickerQuery.trim()
    val isAbonoLocked: Boolean get() = abonoKey != null
    val isAbonoBusy: Boolean get() = isAbonoLocked || isAbonoInFlight
    val showsPickerHint: Boolean get() = showsPicker && pickerQuery.trim().length < MIN_PICKER_QUERY_LENGTH
    val abonoAmount: Double? get() = abonoInput.toDoubleOrNull()?.takeIf { it > 0.0 }
    val showsAbonoRow: Boolean get() = newCustomer == null && customer?.hasDebt == true
    val canConfirmAbono: Boolean get() = showsAbonoRow && abonoAmount != null && !isAbonoInFlight
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
    val paymentType: SalePaymentType = SalePaymentType.CASH,
    val lastResult: CheckoutResult? = null,
    val confirm: SaleConfirm? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
    val prompt: SalePrompt? = null,
    val promptInput: String = "",
    val promptInputRejected: Boolean = false,
    val paidInputRejected: Boolean = false,
    val variosProduct: SaleProduct? = null,
    val credit: CreditState = CreditState(),
    val abonoOverlayOpen: Boolean = false,
) {
    val draft: SaleDraft? get() = activeDraftId?.let(::accountById)
    val isBusy: Boolean get() = pendingMutations > 0
    val isPaidLocked: Boolean get() = checkoutIdempotencyKey != null
    val channel: SaleChannel get() = draft?.channel ?: SaleChannel.TAKEAWAY
    val listTotal: Double get() = draft?.totals?.listTotal ?: 0.0
    val cashTotal: Double get() = draft?.totals?.cashTotal ?: 0.0
    val cashDiscount: Double get() = draft?.totals?.cashDiscount ?: 0.0
    val amountDue: Double
        get() = when (paymentType) {
            SalePaymentType.CARD, SalePaymentType.CREDIT -> listTotal
            SalePaymentType.CASH, SalePaymentType.TRANSFER -> cashTotal
        }
    val paidAmount: Double?
        get() = if (paymentType == SalePaymentType.CASH) paidInput.toDoubleOrNull() else amountDue
    val change: Double?
        get() = if (paymentType == SalePaymentType.CASH) paidAmount?.let { roundMoney(it - amountDue) } else null
    val canConfirmCharge: Boolean
        get() = !isCheckoutInFlight && when (paymentType) {
            SalePaymentType.CASH -> paidCoversAmountDue
            SalePaymentType.CREDIT -> credit.customer != null && credit.newCustomer == null && !credit.isAbonoBusy
            SalePaymentType.TRANSFER, SalePaymentType.CARD -> true
        }
    val isCreditCharge: Boolean get() = phase == SalePhase.Charging && paymentType == SalePaymentType.CREDIT
    val isCustomerStepActive: Boolean get() = isCreditCharge || abonoOverlayOpen
    private val paidCoversAmountDue: Boolean
        get() = paidAmount?.let { roundMoney(it) >= roundMoney(amountDue) } == true
    val canCharge: Boolean
        get() = phase == SalePhase.Selling && draft?.lines?.isNotEmpty() == true && listTotal > 0 && !isBusy && prompt == null &&
            !abonoOverlayOpen
    val canSetChannel: Boolean
        get() = phase == SalePhase.Selling && !isBusy && !isCheckoutInFlight && confirm == null && prompt == null &&
            !abonoOverlayOpen
    val canOpenAbono: Boolean
        get() = phase == SalePhase.Selling && !isBusy && prompt == null && confirm == null && !isCheckoutInFlight &&
            !abonoOverlayOpen
    val selectedLine: SaleLine? get() = selectedLineId?.let { id -> draft?.lines?.firstOrNull { it.id == id } }
    val canOpenAccount: Boolean
        get() = (phase == SalePhase.Selling || phase == SalePhase.Done) &&
            accounts.size < MAX_ACCOUNTS &&
            draft?.lines?.isNotEmpty() != false &&
            !isCheckoutInFlight &&
            prompt == null &&
            !abonoOverlayOpen
    val canCloseEmptyAccount: Boolean
        get() = phase == SalePhase.Selling && draft?.lines?.isEmpty() == true && accounts.size > 1

    val promptValue: Double? get() = promptInput.toDoubleOrNull()?.takeIf { it > 0.0 }
    val canConfirmPrompt: Boolean
        get() = prompt != null && promptValue != null && phase == SalePhase.Selling
    val promptKilos: Double?
        get() {
            val weight = prompt as? SalePrompt.Weight ?: return null
            val value = promptValue ?: return null
            return when (weight.mode) {
                WeightMode.KILOS -> value
                WeightMode.PESOS -> weight.product.cashPriceFor(channel)?.takeIf { it > 0.0 }?.let { value / it }
            }
        }

    fun accountById(id: Long): SaleDraft? = accounts.firstOrNull { it.id == id }
}

internal const val MAX_ACCOUNTS = 10
internal const val MIN_PICKER_QUERY_LENGTH = 2
