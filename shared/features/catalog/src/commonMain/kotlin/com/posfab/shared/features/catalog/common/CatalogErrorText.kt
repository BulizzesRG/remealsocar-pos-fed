package com.posfab.shared.features.catalog.common

import com.posfab.shared.core.result.AppError

object CatalogErrorText {
    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Sesion expirada. Inicia sesion de nuevo."
        AppError.Forbidden -> "No tienes permisos para catalogo."
        AppError.Conflict -> "Conflicto en catalogo. Verifica codigo de barras/UOM duplicados."
        is AppError.Validation -> "Datos invalidos: ${error.message}"
        AppError.RateLimit -> "Demasiadas solicitudes. Intenta en unos segundos."
        is AppError.Network -> "Error de red: ${error.message}"
        is AppError.Unknown -> "Error inesperado: ${error.message}"
    }
}
