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

data class SaleProduct(
    val id: Long,
    val name: String,
    val barcode: String?,
    val unitId: Long,
    val cashPrice: Double?,
    val priceType: SalePriceType = SalePriceType.PIECE,
    val kitchenKind: SaleKitchenKind? = null,
    val lotTracked: Boolean = false,
) {
    val isSellable: Boolean get() = priceType == SalePriceType.PIECE && cashPrice != null
}

data class SaleLine(
    val id: Long,
    val productId: Long,
    val productName: String,
    val qty: Double,
    val unitId: Long,
    val cashUnitPrice: Double,
    val lineTotal: Double,
    val lotId: Long? = null,
    val lotTracked: Boolean = false,
)

data class SaleTotals(
    val cashTotal: Double,
    val listTotal: Double = cashTotal,
    val cashDiscount: Double = 0.0,
)

data class SaleDraft(
    val id: Long,
    val status: String,
    val version: Int,
    val lines: List<SaleLine>,
    val totals: SaleTotals,
)

data class CashCheckoutResult(
    val saleId: Long,
    val folio: String,
    val cashTotal: Double,
    val paidAmount: Double,
) {
    val change: Double get() = roundMoney(paidAmount - cashTotal)
}

sealed interface SaleMutationResult {
    data class Success(val draft: SaleDraft) : SaleMutationResult
    data class ConflictRefetched(val draft: SaleDraft) : SaleMutationResult
    data class Failure(val error: AppError) : SaleMutationResult
}

sealed interface CashSessionCheck {
    data object Open : CashSessionCheck
    data object Closed : CashSessionCheck
    data class Unavailable(val error: AppError) : CashSessionCheck
}

fun roundMoney(value: Double): Double = round(value * 100.0) / 100.0
