package com.posfab.shared.features.login

import com.posfab.shared.core.result.AppError

internal object LoginErrorText {
    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Usuario o contraseña incorrectos."
        AppError.Forbidden -> "Este usuario no tiene acceso a esta terminal."
        AppError.Conflict -> "Conflicto al iniciar sesión. Intenta de nuevo."
        is AppError.Validation -> "Revisa los datos e intenta de nuevo."
        AppError.RateLimit -> "Demasiados intentos. Espera unos segundos antes de reintentar."
        is AppError.Network -> "Sin conexión con el servidor. Verifica tu red."
        is AppError.Unknown -> "Error inesperado. Intenta de nuevo."
    }
}
