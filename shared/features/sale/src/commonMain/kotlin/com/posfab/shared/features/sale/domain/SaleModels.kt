package com.posfab.shared.features.sale.domain

import com.posfab.shared.core.result.AppError

enum class CheckoutMode { CASH, ON_CREDIT }

data class SaleProduct(
    val id: String,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val defaultUnit: String,
    val unitPrice: Double,
    val lotTracked: Boolean,
)

data class SaleLine(
    val id: String,
    val productId: String,
    val productName: String,
    val barcode: String?,
    val qty: Double,
    val unit: String,
    val unitPrice: Double,
    val lineTotal: Double,
    val lotId: String?,
    val lotTracked: Boolean,
)

data class SaleTotals(
    val subtotal: Double,
    val tax: Double,
    val total: Double,
)

data class SaleDraft(
    val id: String,
    val status: String,
    val type: String,
    val terminalCode: String,
    val version: Long,
    val lines: List<SaleLine>,
    val totals: SaleTotals,
)

data class DraftIssue(
    val lineId: String?,
    val code: String,
    val message: String,
)

data class DraftValidation(
    val issues: List<DraftIssue>,
) {
    val hasIssues: Boolean = issues.isNotEmpty()
}

data class CheckoutResult(
    val saleId: String,
    val folio: String,
    val total: Double,
    val mode: CheckoutMode,
)

sealed interface SaleMutationResult {
    data class Success(val draft: SaleDraft) : SaleMutationResult
    data class ConflictRefetched(val draft: SaleDraft) : SaleMutationResult
    data class Failure(val error: AppError) : SaleMutationResult
}
