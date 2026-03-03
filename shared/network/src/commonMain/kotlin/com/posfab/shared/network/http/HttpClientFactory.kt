package com.posfab.shared.network.http

import com.posfab.shared.config.PosConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.random.Random

object HttpClientFactory {
    fun create(
        config: PosConfig,
        engine: HttpClientEngine,
    ): HttpClient {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }

        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = config.requestTimeoutMs
                connectTimeoutMillis = config.requestTimeoutMs
                socketTimeoutMillis = config.requestTimeoutMs
            }
            install(HttpRequestRetry) {
                maxRetries = 2
                exponentialDelay(base = 200.0, maxDelayMs = 1200)
                retryOnExceptionIf { request, _ -> canRetry(request.method, request.headers["Idempotency-Key"]) }
                retryIf { request, response ->
                    canRetry(request.method, request.headers["Idempotency-Key"]) &&
                        response.status.value in 500..599
                }
            }
            defaultRequest {
                if (headers["X-Request-Id"].isNullOrBlank()) {
                    headers.append("X-Request-Id", buildRequestId())
                }
            }
            if (config.enableVerboseLogs) {
                install(Logging) {
                    level = LogLevel.ALL
                    logger = Logger.DEFAULT
                }
            }
            expectSuccess = false
        }
    }

    private fun canRetry(method: HttpMethod, idempotencyKey: String?): Boolean {
        return method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options ||
            (method == HttpMethod.Post && !idempotencyKey.isNullOrBlank())
    }

    private fun buildRequestId(): String =
        "req-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1000, 9999)}"
}
