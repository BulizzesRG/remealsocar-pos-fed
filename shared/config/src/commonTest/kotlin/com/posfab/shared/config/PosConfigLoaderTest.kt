package com.posfab.shared.config

import com.posfab.shared.core.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PosConfigLoaderTest {
    @Test
    fun parses_dev_defaults() {
        val config = PosConfigLoader.fromMap(
            mapOf(
                "POS_ENV" to "dev",
                "POS_API_BASE_URL" to "http://localhost:9090",
            )
        )

        assertEquals(PosEnv.DEV, config.env)
        assertEquals("http://localhost:9090", config.apiBaseUrl)
        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertTrue(config.enableVerboseLogs)
    }

    @Test
    fun parses_prod_overrides() {
        val config = PosConfigLoader.fromMap(
            mapOf(
                "POS_ENV" to "prod",
                "POS_API_BASE_URL" to "https://api.store.internal",
                "POS_LOG_LEVEL" to "WARN",
                "POS_REQUEST_TIMEOUT_MS" to "8000",
                "POS_ENABLE_VERBOSE_LOGS" to "false",
            )
        )

        assertEquals(PosEnv.PROD, config.env)
        assertEquals("https://api.store.internal", config.apiBaseUrl)
        assertEquals(LogLevel.WARN, config.logLevel)
        assertEquals(8_000L, config.requestTimeoutMs)
        assertEquals(false, config.enableVerboseLogs)
    }
}
