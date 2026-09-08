package com.posfab.shared.config

import com.posfab.shared.core.logging.LogLevel

enum class PosEnv { DEV, PROD }

data class PosConfig(
    val env: PosEnv,
    val apiBaseUrl: String,
    val logLevel: LogLevel,
    val requestTimeoutMs: Long,
    val enableVerboseLogs: Boolean,
)

object PosConfigLoader {
    fun fromMap(source: Map<String, String>): PosConfig {
        val env = when (source["POS_ENV"]?.trim()?.lowercase()) {
            "prod" -> PosEnv.PROD
            else -> PosEnv.DEV
        }

        val defaultBaseUrl = "http://localhost:8080"
        val logLevel = when (source["POS_LOG_LEVEL"]?.trim()?.uppercase()) {
            "INFO" -> LogLevel.INFO
            "WARN" -> LogLevel.WARN
            "ERROR" -> LogLevel.ERROR
            "NONE" -> LogLevel.NONE
            else -> LogLevel.DEBUG
        }

        return PosConfig(
            env = env,
            apiBaseUrl = source["POS_API_BASE_URL"]?.ifBlank { null } ?: defaultBaseUrl,
            logLevel = logLevel,
            requestTimeoutMs = source["POS_REQUEST_TIMEOUT_MS"]?.toLongOrNull() ?: 15_000L,
            enableVerboseLogs = source["POS_ENABLE_VERBOSE_LOGS"]?.toBooleanStrictOrNull() ?: (env == PosEnv.DEV),
        )
    }
}
