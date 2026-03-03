package com.posfab.shared.features.reports.common

import com.posfab.shared.core.result.AppError

object ReportsErrorText {
    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Sesion expirada. Inicia sesion nuevamente."
        AppError.Forbidden -> "No tienes permiso para ver este panel."
        AppError.Conflict -> "Conflicto de datos. Intenta refrescar."
        is AppError.Validation -> "Solicitud invalida: ${error.message}"
        AppError.RateLimit -> "Demasiadas solicitudes. Espera unos segundos."
        is AppError.Network -> "Error de red: ${error.message}"
        is AppError.Unknown -> "Error inesperado: ${error.message}"
    }
}
