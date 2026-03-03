package com.posfab.shared.features.operations.common

import com.posfab.shared.core.result.AppError

object OperationsErrorText {
    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Sesion expirada. Vuelve a iniciar sesion."
        AppError.Forbidden -> "No tienes permisos para esta operacion."
        AppError.Conflict -> "Conflicto detectado. Verifica si la operacion ya fue registrada."
        is AppError.Validation -> "Datos invalidos: ${error.message}"
        AppError.RateLimit -> "Demasiadas solicitudes. Intenta nuevamente en unos segundos."
        is AppError.Network -> "Error de red: ${error.message}"
        is AppError.Unknown -> "Error inesperado: ${error.message}"
    }
}
