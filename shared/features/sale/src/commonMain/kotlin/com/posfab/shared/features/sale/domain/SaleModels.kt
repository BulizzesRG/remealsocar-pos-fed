package com.posfab.shared.features.sale.domain

import com.posfab.shared.core.result.AppError
import kotlin.math.round

enum class SalePriceType {
    PIECE,
    WEIGHT,
    OPEN,
    ;

    companion object {
        fun fromRaw(raw: String?): SalePriceType {
            val normalized = raw?.trim()?.uppercase() ?: return PIECE
            return entries.firstOrNull { it.name == normalized } ?: OPEN
        }
    }
}

enum class SaleKitchenKind {
    FONDA,
    TORTERIA,
    ;

    companion object {
        fun fromRaw(raw: String?): SaleKitchenKind? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

enum class SaleChannel {
    TAKEAWAY,
    ONSITE,
    ;

    companion object {
        fun fromRaw(raw: String?): SaleChannel {
            val normalized = raw?.trim()?.uppercase() ?: return TAKEAWAY
            return entries.firstOrNull { it.name == normalized } ?: TAKEAWAY
        }
    }
}

enum class SalePaymentType {
    CASH,
    TRANSFER,
    CARD,
    CREDIT,
}

data class SaleCustomer(
    val id: Long,
    val code: String?,
    val name: String,
    val nickname: String?,
    val phone: String?,
    val balance: Double,
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() }?.let { "$name ($it)" } ?: name
    val hasDebt: Boolean get() = roundMoney(balance) > 0.0
}

data class CustomerPaymentResult(
    val customerId: Long,
    val amount: Double,
    val balance: Double,
)

data class SaleProduct(
    val id: Long,
    val name: String,
    val barcode: String?,
    val unitId: Long,
    val cashPrice: Double?,
    val listPrice: Double?,
    val listOnsitePrice: Double?,
    val cashOnsitePrice: Double? = null,
    val priceType: SalePriceType = SalePriceType.PIECE,
    val kitchenKind: SaleKitchenKind? = null,
    val lotTracked: Boolean = false,
) {
    val isSellable: Boolean get() = priceType == SalePriceType.OPEN || cashPrice != null

    fun cashPriceFor(channel: SaleChannel): Double? = when (channel) {
        SaleChannel.TAKEAWAY -> cashPrice
        SaleChannel.ONSITE -> cashOnsitePrice ?: cashPrice
    }

    fun listPriceFor(channel: SaleChannel): Double? = when (channel) {
        SaleChannel.TAKEAWAY -> listPrice
        SaleChannel.ONSITE -> listOnsitePrice ?: listPrice
    }
}

sealed interface SaleLineInput {
    data class Qty(val qty: Double) : SaleLineInput
    data class Amount(val amount: Double) : SaleLineInput
    data class OpenPrice(val amount: Double) : SaleLineInput
}

data class SaleLine(
    val id: Long,
    val productId: Long,
    val productName: String,
    val qty: Double,
    val unitId: Long,
    val unitPrice: Double,
    val cashUnitPrice: Double,
    val lineTotal: Double,
    val lotId: Long? = null,
    val lotTracked: Boolean = false,
    val priceType: SalePriceType = SalePriceType.PIECE,
)

data class SaleTotals(
    val cashTotal: Double,
    val listTotal: Double,
    val cashDiscount: Double,
)

data class SaleDraft(
    val id: Long,
    val status: String,
    val version: Int,
    val lines: List<SaleLine>,
    val totals: SaleTotals,
    val channel: SaleChannel = SaleChannel.TAKEAWAY,
) {
    val isOpen: Boolean get() = status == "OPEN"
}

data class CheckoutResult(
    val saleId: Long,
    val folio: String,
    val paymentType: SalePaymentType,
    val totalCharged: Double,
    val paidAmount: Double,
    val customerName: String? = null,
    val customerBalanceAfter: Double? = null,
) {
    val change: Double?
        get() = if (paymentType == SalePaymentType.CASH) roundMoney(paidAmount - totalCharged) else null
}

sealed interface SaleMutationResult {
    data class Success(val draft: SaleDraft) : SaleMutationResult
    data class ConflictRefetched(val draft: SaleDraft) : SaleMutationResult
    data class DraftClosed(val draft: SaleDraft) : SaleMutationResult
    data class Failure(val error: AppError) : SaleMutationResult
}

sealed interface CashSessionCheck {
    data object Open : CashSessionCheck
    data object Closed : CashSessionCheck
    data class Unavailable(val error: AppError) : CashSessionCheck
}

fun roundMoney(value: Double): Double = round(value * 100.0) / 100.0
