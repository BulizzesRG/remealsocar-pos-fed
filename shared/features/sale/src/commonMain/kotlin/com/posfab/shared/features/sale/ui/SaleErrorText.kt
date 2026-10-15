package com.posfab.shared.features.sale.ui

import com.posfab.shared.core.result.AppError

object SaleErrorText {
    private val knownMessages: List<Pair<String, String>> = listOf(
        "Open cash session is required" to SaleStrings.CASH_SESSION_REQUIRED,
        "has no price" to SaleStrings.NO_PRICE,
        "Draft has no lines" to SaleStrings.NOTHING_TO_CHARGE,
        "Draft version mismatch" to SaleStrings.SALE_CHANGED,
        "Insufficient stock" to SaleStrings.INSUFFICIENT_STOCK,
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> SaleStrings.SESSION_EXPIRED
        AppError.Forbidden -> SaleStrings.NOT_ALLOWED
        AppError.Conflict -> SaleStrings.SALE_CHANGED
        AppError.RateLimit -> SaleStrings.TOO_MANY_REQUESTS
        is AppError.Network -> SaleStrings.NO_CONNECTION
        is AppError.Validation -> fromMessage(error.message)
        is AppError.Unknown -> fromMessage(error.message)
    }

    private fun fromMessage(message: String): String =
        knownMessages.firstOrNull { (needle, _) -> message.contains(needle, ignoreCase = true) }?.second
            ?: SaleStrings.couldNotComplete(message)
}
