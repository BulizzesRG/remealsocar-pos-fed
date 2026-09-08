package com.posfab.shared.features.devicesetup

import com.posfab.shared.core.result.AppError

internal object DeviceSetupErrorText {
    fun from(error: AppError): String = when (error) {
        AppError.Unauthorized -> "Credenciales de administrador incorrectas."
        AppError.Forbidden -> "Este usuario no puede registrar dispositivos."
        AppError.Conflict -> "La terminal o el nombre del dispositivo ya están registrados."
        is AppError.Validation -> "Revisa los datos del dispositivo e intenta de nuevo."
        AppError.RateLimit -> "Demasiados intentos. Espera unos segundos antes de reintentar."
        is AppError.Network -> "Sin conexión con el servidor. Verifica tu red."
        is AppError.Unknown -> "Error inesperado al registrar el dispositivo."
    }
}
