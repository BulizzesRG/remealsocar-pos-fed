package com.posfab.shared.features.cash.ui

import com.posfab.shared.core.result.AppError

object CashErrorText {
    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Sesion expirada. Inicia sesion nuevamente."
        AppError.Forbidden -> "No tienes permisos para operar caja en esta terminal."
        AppError.Conflict -> "Conflicto de estado de caja. Se recargo la sesion actual."
        is AppError.Validation -> "Datos invalidos: ${error.message}"
        AppError.RateLimit -> "Demasiadas solicitudes. Intenta de nuevo en unos segundos."
        is AppError.Network -> "Error de red: ${error.message}"
        is AppError.Unknown -> "Error inesperado: ${error.message}"
    }
}
