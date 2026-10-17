package com.posfab.shared.features.reports.sales

import com.posfab.shared.core.result.AppError

object SalesErrorText {
    private val knownMessages: List<Pair<String, String>> = listOf(
        "Sale not found" to SalesStrings.SALE_NOT_FOUND,
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> SalesStrings.SIGN_IN_AGAIN
        AppError.Forbidden -> SalesStrings.FORBIDDEN
        is AppError.Network -> SalesStrings.NETWORK
        is AppError.Validation -> translate(error.message) ?: SalesStrings.LOAD_FAILED
        AppError.Conflict, AppError.RateLimit, is AppError.Unknown -> SalesStrings.LOAD_FAILED
    }

    private fun translate(message: String): String? =
        knownMessages.firstOrNull { (key, _) -> message.contains(key, ignoreCase = true) }?.second
}
