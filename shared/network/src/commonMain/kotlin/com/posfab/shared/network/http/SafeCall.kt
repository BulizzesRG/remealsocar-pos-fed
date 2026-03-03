package com.posfab.shared.network.http

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.core.diagnostics.NetworkHealthTracker
import com.posfab.shared.core.logging.AppLogger
import com.posfab.shared.core.result.AppError
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock

suspend inline fun <reified T> safeCall(block: suspend () -> HttpResponse): AppResult<T> {
    val startedAt = Clock.System.now().toEpochMilliseconds()
    return try {
        val response = block()
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
        val request = response.call.request
        val requestId = request.headers["X-Request-Id"] ?: "-"
        val endpoint = request.url.encodedPath
        val method = request.method.value
        if (response.status.isSuccess()) {
            NetworkHealthTracker.markSuccess()
            AppLogger.i("http.request request_id=$requestId method=$method endpoint=$endpoint status=${response.status.value} elapsed_ms=$elapsedMs")
            if (T::class == Unit::class) {
                @Suppress("UNCHECKED_CAST")
                AppResult.Success(Unit as T)
            } else {
                AppResult.Success(response.body())
            }
        } else {
            val error = ApiErrorMapper.fromStatus(response.status)
            AppLogger.w("http.request request_id=$requestId method=$method endpoint=$endpoint status=${response.status.value} elapsed_ms=$elapsedMs error_category=${error::class.simpleName}")
            AppResult.Failure(error)
        }
    } catch (throwable: Throwable) {
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
        val error = ApiErrorMapper.fromThrowable(throwable)
        if (error is AppError.Network) {
            NetworkHealthTracker.markFailure("Network")
        }
        AppLogger.e("http.request method=UNKNOWN endpoint=UNKNOWN status=EXCEPTION elapsed_ms=$elapsedMs error_category=${error::class.simpleName}", throwable)
        AppResult.Failure(error)
    }
}
