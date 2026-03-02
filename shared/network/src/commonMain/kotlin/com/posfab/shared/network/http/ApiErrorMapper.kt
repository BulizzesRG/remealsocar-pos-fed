package com.posfab.shared.network.http

import com.posfab.shared.core.result.AppError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode

object ApiErrorMapper {
    fun fromThrowable(throwable: Throwable): AppError = when (throwable) {
        is ClientRequestException -> fromStatus(throwable.response.status)
        is ServerResponseException -> AppError.Network("Server error ${throwable.response.status.value}")
        else -> AppError.Network(throwable.message ?: "Network error")
    }

    fun fromStatus(status: HttpStatusCode): AppError = when (status) {
        HttpStatusCode.Unauthorized -> AppError.Unauthorized
        HttpStatusCode.Forbidden -> AppError.Forbidden
        HttpStatusCode.Conflict -> AppError.Conflict
        HttpStatusCode.TooManyRequests -> AppError.RateLimit
        HttpStatusCode.UnprocessableEntity,
        HttpStatusCode.BadRequest,
        HttpStatusCode.NotFound,
        -> AppError.Validation("Request rejected: ${status.value}")

        else -> AppError.Network("HTTP ${status.value}")
    }
}
