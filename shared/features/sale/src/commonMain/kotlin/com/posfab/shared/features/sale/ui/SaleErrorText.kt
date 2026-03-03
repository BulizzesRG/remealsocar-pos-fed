package com.posfab.shared.features.sale.ui

import com.posfab.shared.core.result.AppError

object SaleErrorText {
    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Sesion expirada. Inicia sesion nuevamente."
        AppError.Forbidden -> "No tienes permiso para esta accion en esta terminal."
        AppError.Conflict -> "El borrador cambio en paralelo. Se recargo la version mas reciente."
        is AppError.Validation -> "Datos invalidos: ${error.message}"
        AppError.RateLimit -> "Demasiadas solicitudes. Intenta de nuevo en unos segundos."
        is AppError.Network -> "Error de red: ${error.message}"
        is AppError.Unknown -> "Error inesperado: ${error.message}"
    }
}
