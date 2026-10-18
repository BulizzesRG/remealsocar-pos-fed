package com.posfab.shared.features.catalog.common

import com.posfab.shared.core.result.AppError

object CatalogErrorText {
    private val knownMessages: List<Pair<String, String>> = listOf(
        "OPEN products cannot be counted" to "Un producto de precio abierto no se cuenta",
        "WEIGHT products cannot have piecesPerPack" to "Por kilo no lleva piezas por paquete",
        "13-digit codes starting with 2" to "Ese código está reservado para la báscula",
        "invalid check digit" to "El código interno no es válido",
        "cashTakeaway must be > 0" to "Escribe el precio en efectivo",
        "cashOnsite must be > 0" to "El precio en efectivo en corral debe ser mayor que cero",
        "listTakeaway must be >= cashTakeaway" to "El precio con tarjeta no puede ser menor que el de efectivo",
        "listOnsite must be >= cashOnsite" to "El precio con tarjeta no puede ser menor que el de efectivo",
        "must equal" to "Con precio sugerido, el precio con tarjeta es igual al de efectivo",
        "cashOnsite is required" to "Escribe el precio en efectivo en corral",
        "at most 2 decimals" to "Los precios llevan a lo más dos decimales",
        "is too large" to "El precio es demasiado grande",
        "piecesPerPack must be" to "Piezas por paquete debe ser 2 o más",
        "name is required" to "Escribe el nombre",
    )

    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Tu sesión terminó. Vuelve a entrar."
        AppError.Forbidden -> "No tienes permiso para cambiar productos"
        AppError.Conflict -> "Ese código ya lo tiene otro producto"
        is AppError.Validation -> translate(error.message)
        AppError.RateLimit -> "Demasiados intentos. Espera unos segundos."
        is AppError.Network -> "No hay conexión con el servidor. Intenta de nuevo."
        is AppError.Unknown -> "Algo salió mal. Intenta de nuevo."
    }

    private fun translate(message: String): String =
        knownMessages.firstOrNull { (key, _) -> message.contains(key, ignoreCase = true) }?.second
            ?: "No se pudo guardar: $message"
}
