package com.posfab.shared.features.cash.ui

import com.posfab.shared.core.result.AppError

object CashErrorText {
    private const val OPEN_ACCOUNTS = "Open accounts must be charged or emptied first"
    private const val EXCEEDS_DRAWER = "amount exceeds drawer"
    private const val ALREADY_OPEN = "Terminal already has an open session"

    private val sessionClosedMessages: List<String> = listOf(
        "Open cash session is required",
        "No open session for terminal",
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> CashStrings.UNAUTHORIZED
        AppError.Forbidden -> CashStrings.FORBIDDEN
        AppError.Conflict -> CashStrings.CONFLICT
        AppError.RateLimit -> CashStrings.RATE_LIMIT
        is AppError.Network -> CashStrings.NETWORK
        is AppError.Validation -> translate(error.message) ?: "${CashStrings.FAILED_PREFIX}${error.message}"
        is AppError.Unknown -> translate(error.message) ?: "${CashStrings.FAILED_PREFIX}${error.message}"
    }

    fun isSessionClosed(error: AppError): Boolean =
        sessionClosedMessages.any { messageOf(error)?.contains(it, ignoreCase = true) == true }

    fun isAlreadyOpen(error: AppError): Boolean =
        error is AppError.Conflict || messageOf(error)?.contains(ALREADY_OPEN, ignoreCase = true) == true

    fun isOpenAccounts(error: AppError): Boolean =
        messageOf(error)?.contains(OPEN_ACCOUNTS, ignoreCase = true) == true

    private fun translate(message: String): String? = when {
        message.contains(OPEN_ACCOUNTS, ignoreCase = true) -> CashStrings.OPEN_ACCOUNTS_BLOCK
        message.contains(EXCEEDS_DRAWER, ignoreCase = true) -> CashStrings.NOT_ENOUGH
        message.contains(ALREADY_OPEN, ignoreCase = true) -> CashStrings.ALREADY_OPEN
        sessionClosedMessages.any { message.contains(it, ignoreCase = true) } -> CashStrings.CLOSED_NOTICE
        else -> null
    }

    private fun messageOf(error: AppError): String? = when (error) {
        is AppError.Validation -> error.message
        is AppError.Unknown -> error.message
        else -> null
    }
}
