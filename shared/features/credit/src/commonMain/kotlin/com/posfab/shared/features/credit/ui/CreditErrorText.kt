package com.posfab.shared.features.credit.ui

import com.posfab.shared.core.result.AppError

object CreditErrorText {
    private const val LOAD_FAILED = "No se pudo cargar el fiado, intenta de nuevo"

    private val knownMessages: List<Pair<String, String>> = listOf(
        "Customer not found" to CreditStrings.NOT_FOUND,
    )

    private val libretaMessages: List<Pair<String, String>> = listOf(
        "Customer already has movements" to CreditStrings.HAS_MOVEMENTS,
        "Customer is inactive" to CreditStrings.INACTIVE_CUSTOMER,
        "Customer not found" to CreditStrings.NOT_FOUND,
        "owedSince cannot be in the future" to CreditStrings.DATE_FUTURE,
        "owedSince must be" to CreditStrings.DATE_INVALID,
        "nickname too long" to CreditStrings.NICKNAME_TOO_LONG,
    )

    private val editMessages: List<Pair<String, String>> = listOf(
        "Customer not found" to CreditStrings.NOT_FOUND,
        "phone must have 10 digits" to CreditStrings.PHONE_INVALID,
        "nickname too long" to CreditStrings.NICKNAME_TOO_LONG,
        "address too long" to CreditStrings.ADDRESS_TOO_LONG,
        "name too long" to CreditStrings.NAME_TOO_LONG,
        "name is required" to CreditStrings.NAME_REQUIRED,
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Tu sesión terminó. Vuelve a entrar."
        AppError.Forbidden -> "No tienes permiso para ver el fiado"
        AppError.Conflict -> "Algo cambió mientras mirabas. Toca Actualizar."
        is AppError.Validation -> translate(error.message, knownMessages) ?: LOAD_FAILED
        AppError.RateLimit -> "Demasiados intentos. Espera unos segundos."
        is AppError.Network -> "No hay conexión con el servidor. Intenta de nuevo."
        is AppError.Unknown -> LOAD_FAILED
    }

    fun libreta(error: AppError): String = when (error) {
        is AppError.Network -> CreditStrings.NETWORK
        AppError.Forbidden -> CreditStrings.NO_PERMISSION
        is AppError.Validation -> translate(error.message, libretaMessages) ?: CreditStrings.SAVE_FAILED
        is AppError.Unknown -> translate(error.message, libretaMessages) ?: CreditStrings.SAVE_FAILED
        else -> CreditStrings.SAVE_FAILED
    }

    fun edit(error: AppError): String = when (error) {
        is AppError.Network -> CreditStrings.NETWORK
        AppError.Forbidden -> CreditStrings.NO_EDIT_PERMISSION
        is AppError.Validation -> translate(error.message, editMessages) ?: CreditStrings.SAVE_FAILED
        is AppError.Unknown -> translate(error.message, editMessages) ?: CreditStrings.SAVE_FAILED
        else -> CreditStrings.SAVE_FAILED
    }

    private fun translate(message: String, table: List<Pair<String, String>>): String? =
        table.firstOrNull { (key, _) -> message.contains(key, ignoreCase = true) }?.second
}
