package com.posfab.shared.network.cash

import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.logging.LogLevel
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CashContractTest {
    private val config = PosConfig(
        env = PosEnv.DEV,
        apiBaseUrl = "http://localhost:8080",
        logLevel = LogLevel.DEBUG,
        requestTimeoutMs = 5_000,
        enableVerboseLogs = false,
    )

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    @Test
    fun current_session_404_maps_to_success_null() = runTest {
        val engine = MockEngine {
            respond("""{"error":"No open session"}""", HttpStatusCode.NotFound, jsonHeaders)
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.currentSession("token", 1L)

        val success = assertIs<AppResult.Success<CashSessionResponseDto?>>(result)
        assertNull(success.value)
    }

    @Test
    fun current_session_200_decodes_dto() = runTest {
        val engine = MockEngine {
            respond(
                """
                {"id":7,"terminalId":1,"businessUnitId":2,"status":"OPEN","openingAmount":"500.00",
                 "closingAmount":null,"openedAt":"2026-10-14T08:15:00-06:00","closedAt":null}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.currentSession("token", 1L)

        val success = assertIs<AppResult.Success<CashSessionResponseDto?>>(result)
        val dto = success.value
        assertEquals(7L, dto?.id)
        assertEquals("OPEN", dto?.status)
        assertEquals("500.00", dto?.openingAmount)
        assertEquals("2026-10-14T08:15:00-06:00", dto?.openedAt)
        assertNull(dto?.closedAt)
    }

    @Test
    fun current_session_500_maps_to_failure() = runTest {
        val engine = MockEngine {
            respond("""{"error":"boom"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.currentSession("token", 1L)

        assertIs<AppResult.Failure>(result)
    }

    @Test
    fun current_session_sends_terminal_id_and_bearer_token() = runTest {
        var path = ""
        var auth: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath + "?" + request.url.encodedQuery
            auth = request.headers["Authorization"]
            respond("{}", HttpStatusCode.NotFound, jsonHeaders)
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        api.currentSession("tok-1", 3L)

        assertEquals("/cash/sessions/current?terminal_id=3", path)
        assertEquals("Bearer tok-1", auth)
    }
}
