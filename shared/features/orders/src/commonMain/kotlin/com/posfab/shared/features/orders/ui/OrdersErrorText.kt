package com.posfab.shared.features.orders.ui

import com.posfab.shared.core.result.AppError

object OrdersErrorText {
    private val closedMessages: List<String> = listOf(
        "Order not found",
        "Order is not open",
        "is not OPEN",
        "Only OPEN drafts can be cancelled",
    )

    private val knownMessages: List<Pair<String, String>> = listOf(
        "Order not found" to OrdersStrings.ORDER_NOT_FOUND,
        "Order is not open" to OrdersStrings.ORDER_CLOSED,
        "is not OPEN" to OrdersStrings.ORDER_CLOSED,
        "Only OPEN drafts can be cancelled" to OrdersStrings.ORDER_CLOSED,
        "Draft line" to OrdersStrings.LINE_NOT_FOUND,
        "notes are required" to OrdersStrings.NOTES_REQUIRED,
        "Customer is inactive" to OrdersStrings.CUSTOMER_INACTIVE,
        "Customer not found" to OrdersStrings.CUSTOMER_NOT_FOUND,
        "phone must have 10 digits" to OrdersStrings.PHONE_INCOMPLETE,
        "paysWith is invalid" to OrdersStrings.PAYS_WITH_INVALID,
        "Use /orders/{id}/cancel" to OrdersStrings.HAS_LINES,
        "too long" to OrdersStrings.TEXT_TOO_LONG,
        "name is required" to OrdersStrings.NAME_INVALID,
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> OrdersStrings.SIGN_IN_AGAIN
        AppError.Forbidden -> OrdersStrings.FORBIDDEN
        AppError.Conflict -> OrdersStrings.CONFLICT
        is AppError.Network -> OrdersStrings.NETWORK
        is AppError.Validation -> translate(error.message) ?: OrdersStrings.FAILED
        AppError.RateLimit, is AppError.Unknown -> OrdersStrings.FAILED
    }

    fun customer(error: AppError): String =
        if (error is AppError.Conflict) OrdersStrings.CUSTOMER_SAVE_CONFLICT else from(error)

    fun load(error: AppError): String = when (error) {
        is AppError.Validation, AppError.RateLimit, is AppError.Unknown, AppError.Conflict -> OrdersStrings.LOAD_FAILED
        else -> from(error)
    }

    fun isClosed(error: AppError): Boolean =
        error is AppError.Validation && closedMessages.any { error.message.contains(it, ignoreCase = true) }

    private fun translate(message: String): String? =
        knownMessages.firstOrNull { (key, _) -> message.contains(key, ignoreCase = true) }?.second
}
