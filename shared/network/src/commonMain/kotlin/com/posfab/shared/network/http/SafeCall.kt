package com.posfab.shared.network.http

import com.posfab.shared.core.diagnostics.NetworkHealthTracker
import com.posfab.shared.core.logging.AppLogger
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.time.Clock

suspend inline fun <reified T> safeCall(noinline block: suspend () -> HttpResponse): AppResult<T> =
    loggedCall(block, acceptStatus = { false }) { response -> decodeBody<T>(response) }

suspend inline fun <reified T : Any> safeCallOrNull(
    absentStatus: HttpStatusCode = HttpStatusCode.NotFound,
    noinline block: suspend () -> HttpResponse,
): AppResult<T?> =
    loggedCall(block, acceptStatus = { it == absentStatus }) { response ->
        if (response.status == absentStatus) null else response.body<T>()
    }

@PublishedApi
internal suspend inline fun <reified T> decodeBody(response: HttpResponse): T =
    if (T::class == Unit::class) {
        @Suppress("UNCHECKED_CAST")
        Unit as T
    } else {
        response.body()
    }

@PublishedApi
internal suspend fun <R> loggedCall(
    block: suspend () -> HttpResponse,
    acceptStatus: (HttpStatusCode) -> Boolean,
    decode: suspend (HttpResponse) -> R,
): AppResult<R> {
    val startedAt = Clock.System.now().toEpochMilliseconds()
    return try {
        val response = block()
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
        val request = response.call.request
        val requestId = request.headers["X-Request-Id"] ?: "-"
        val endpoint = request.url.encodedPath
        val method = request.method.value
        if (response.status.isSuccess() || acceptStatus(response.status)) {
            NetworkHealthTracker.markSuccess()
            AppLogger.i("http.request request_id=$requestId method=$method endpoint=$endpoint status=${response.status.value} elapsed_ms=$elapsedMs")
            AppResult.Success(decode(response))
        } else {
            val errorBody = runCatching { response.bodyAsText() }.getOrNull()
            val error = ApiErrorMapper.fromStatus(response.status, errorBody)
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
