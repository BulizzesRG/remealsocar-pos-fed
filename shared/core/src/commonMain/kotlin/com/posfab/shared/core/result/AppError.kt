package com.posfab.shared.core.result

sealed interface AppError {
    data object Unauthorized : AppError
    data object Forbidden : AppError
    data object Conflict : AppError
    data class Validation(val message: String) : AppError
    data object RateLimit : AppError
    data class Network(val message: String) : AppError
    data class Unknown(val message: String) : AppError
}
