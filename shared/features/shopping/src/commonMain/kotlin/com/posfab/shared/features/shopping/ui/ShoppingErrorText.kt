package com.posfab.shared.features.shopping.ui

import com.posfab.shared.core.result.AppError

object ShoppingErrorText {
    private val knownMessages: List<Pair<String, String>> = listOf(
        "List is empty" to ShoppingStrings.LIST_EMPTY,
        "List is not open" to ShoppingStrings.LIST_CLOSED,
        "List is not ready to shop" to ShoppingStrings.LIST_FINISHED,
        "List not found" to ShoppingStrings.DETAIL_FAILED,
        "Item not found" to ShoppingStrings.ITEM_NOT_FOUND,
        "text is required" to ShoppingStrings.TEXT_REQUIRED,
        "text too long" to ShoppingStrings.TEXT_TOO_LONG,
        "text is invalid" to ShoppingStrings.TEXT_INVALID,
        "qty is invalid" to ShoppingStrings.QTY_INVALID,
        "Product not found" to ShoppingStrings.PRODUCT_GONE,
        "Product is inactive" to ShoppingStrings.PRODUCT_GONE,
        "carryOver" to ShoppingStrings.RELOAD_AND_RETRY,
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> ShoppingStrings.SIGN_IN_AGAIN
        AppError.Forbidden -> ShoppingStrings.FORBIDDEN
        AppError.Conflict -> ShoppingStrings.CONFLICT
        is AppError.Network -> ShoppingStrings.NETWORK
        is AppError.Validation -> translate(error.message) ?: ShoppingStrings.FAILED
        AppError.RateLimit, is AppError.Unknown -> ShoppingStrings.FAILED
    }

    fun load(error: AppError): String = when (error) {
        is AppError.Validation, AppError.RateLimit, is AppError.Unknown, AppError.Conflict, AppError.Forbidden ->
            ShoppingStrings.LOAD_FAILED
        else -> from(error)
    }

    fun history(error: AppError): String = when (error) {
        is AppError.Network, AppError.Unauthorized -> from(error)
        else -> ShoppingStrings.HISTORY_FAILED
    }

    fun detail(error: AppError): String = when (error) {
        is AppError.Network, AppError.Unauthorized -> from(error)
        else -> ShoppingStrings.DETAIL_FAILED
    }

    fun isItemGone(error: AppError): Boolean =
        error is AppError.Validation && error.message.contains("Item not found", ignoreCase = true)

    private fun translate(message: String): String? =
        knownMessages.firstOrNull { (key, _) -> message.contains(key, ignoreCase = true) }?.second
}
