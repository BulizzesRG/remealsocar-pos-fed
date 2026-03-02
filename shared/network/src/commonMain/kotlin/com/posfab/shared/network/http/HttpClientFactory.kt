package com.posfab.shared.network.http

import com.posfab.shared.config.PosConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
            if (config.enableVerboseLogs) {
                install(Logging) {
                    level = LogLevel.ALL
                    logger = Logger.DEFAULT
                }
            }
            expectSuccess = false
        }
    }
}
