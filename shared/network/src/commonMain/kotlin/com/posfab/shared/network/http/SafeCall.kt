package com.posfab.shared.network.http

import com.posfab.shared.core.result.AppResult
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

suspend inline fun <reified T> safeCall(block: suspend () -> HttpResponse): AppResult<T> {
    return try {
        val response = block()
        if (response.status.isSuccess()) {
            if (T::class == Unit::class) {
                @Suppress("UNCHECKED_CAST")
                AppResult.Success(Unit as T)
            } else {
                AppResult.Success(response.body())
            }
        } else {
            AppResult.Failure(ApiErrorMapper.fromStatus(response.status))
        }
    } catch (throwable: Throwable) {
        AppResult.Failure(ApiErrorMapper.fromThrowable(throwable))
    }
}
