package com.posfab.shared.features.credit.ui

import com.posfab.shared.core.result.AppError

object CreditErrorText {
    private const val LOAD_FAILED = "No se pudo cargar el fiado, intenta de nuevo"

    private val knownMessages: List<Pair<String, String>> = listOf(
        "Customer not found" to "No se encontró el cliente",
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Tu sesión terminó. Vuelve a entrar."
        AppError.Forbidden -> "No tienes permiso para ver el fiado"
        AppError.Conflict -> "Algo cambió mientras mirabas. Toca Actualizar."
        is AppError.Validation -> translate(error.message)
        AppError.RateLimit -> "Demasiados intentos. Espera unos segundos."
        is AppError.Network -> "No hay conexión con el servidor. Intenta de nuevo."
        is AppError.Unknown -> LOAD_FAILED
    }

    private fun translate(message: String): String =
        knownMessages.firstOrNull { (key, _) -> message.contains(key, ignoreCase = true) }?.second
            ?: LOAD_FAILED
}
