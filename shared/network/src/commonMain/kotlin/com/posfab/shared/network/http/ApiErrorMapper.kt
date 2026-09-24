package com.posfab.shared.network.http

import com.posfab.shared.core.result.AppError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ApiErrorMapper {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fromThrowable(throwable: Throwable): AppError = when (throwable) {
        is ClientRequestException -> fromStatus(throwable.response.status)
        is ServerResponseException -> AppError.Network("Server error ${throwable.response.status.value}")
        else -> AppError.Network(throwable.message ?: "Network error")
    }

    /**
     * Maps an HTTP status to an [AppError]. When [body] is the backend error envelope
     * (`{"error": "..."}`) its message is surfaced on validation errors so screens can show
     * what the server actually rejected (e.g. "lotCode is required for productId=3").
     */
    fun fromStatus(status: HttpStatusCode, body: String? = null): AppError = when (status) {
        HttpStatusCode.Unauthorized -> AppError.Unauthorized
        HttpStatusCode.Forbidden -> AppError.Forbidden
        HttpStatusCode.Conflict -> AppError.Conflict
        HttpStatusCode.TooManyRequests -> AppError.RateLimit
        HttpStatusCode.UnprocessableEntity,
        HttpStatusCode.BadRequest,
        HttpStatusCode.NotFound,
        -> AppError.Validation(serverMessage(body) ?: "Request rejected: ${status.value}")

        else -> AppError.Network("HTTP ${status.value}")
    }

    fun serverMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
