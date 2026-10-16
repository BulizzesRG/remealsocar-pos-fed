package com.posfab.shared.network.cash

import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.logging.LogLevel
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    @Test
    fun summary_closed_decodes_null_session_and_previous_amount() = runTest {
        var path = ""
        var auth: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath + "?" + request.url.encodedQuery
            auth = request.headers["Authorization"]
            respond("""{"status":"CLOSED","previousClosingAmount":null,"session":null}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.summary("tok-2", 4L)

        val dto = assertIs<AppResult.Success<CashSummaryResponseDto>>(result).value
        assertEquals("CLOSED", dto.status)
        assertNull(dto.previousClosingAmount)
        assertNull(dto.session)
        assertEquals("/cash/sessions/summary?terminal_id=4", path)
        assertEquals("Bearer tok-2", auth)
    }

    @Test
    fun summary_closed_keeps_previous_closing_amount() = runTest {
        val engine = MockEngine {
            respond("""{"status":"CLOSED","previousClosingAmount":"1250.00","session":null}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val dto = assertIs<AppResult.Success<CashSummaryResponseDto>>(api.summary("token", 1L)).value

        assertEquals("1250.00", dto.previousClosingAmount)
    }

    @Test
    fun summary_open_decodes_session_and_payouts() = runTest {
        val engine = MockEngine {
            respond(OPEN_SUMMARY_JSON, HttpStatusCode.OK, jsonHeaders)
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val dto = assertIs<AppResult.Success<CashSummaryResponseDto>>(api.summary("token", 1L)).value

        assertEquals("OPEN", dto.status)
        val session = assertNotNull(dto.session)
        assertEquals(7L, session.sessionId)
        assertEquals("2026-10-16T14:15:00Z", session.openedAt)
        assertEquals("500.00", session.openingAmount)
        assertEquals("1300.00", session.cashSales)
        assertEquals("80.00", session.creditPayments)
        assertEquals("500.00", session.payouts)
        assertEquals("0.00", session.otherOut)
        assertEquals("1380.00", session.expectedInDrawer)
        assertEquals("200.00", session.transferSales)
        assertEquals("150.00", session.cardSales)
        assertEquals("75.00", session.creditSales)
        assertEquals(2, session.openAccounts)
        assertEquals(2, session.payoutList.size)
        val first = session.payoutList[0]
        assertEquals(1L, first.id)
        assertEquals("500.00", first.amount)
        assertEquals("SURTIDO", first.reason)
        assertEquals("gasolina", first.notes)
        assertEquals("2026-10-16T16:30:00Z", first.createdAt)
        assertNull(session.payoutList[1].notes)
    }

    @Test
    fun payout_posts_idempotent_body_with_explicit_null_notes() = runTest {
        var method: HttpMethod? = null
        var path = ""
        var key: String? = null
        var auth: String? = null
        var sentBody = ""
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            key = request.headers["Idempotency-Key"]
            auth = request.headers["Authorization"]
            sentBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """
                {"id":1,"amount":"500.00","reason":"SURTIDO","notes":null,
                 "createdAt":"2026-10-16T16:30:00Z","expectedInDrawer":"880.00"}
                """.trimIndent(),
                HttpStatusCode.Created,
                jsonHeaders,
            )
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.payout(
            accessToken = "tok-3",
            idempotencyKey = "PAYOUT-abc",
            request = CashPayoutRequestDto(terminalId = 1L, amount = "500.00", reason = "SURTIDO", notes = null),
        )

        val dto = assertIs<AppResult.Success<CashPayoutResponseDto>>(result).value
        assertEquals(HttpMethod.Post, method)
        assertEquals("/cash/payouts", path)
        assertEquals("PAYOUT-abc", key)
        assertEquals("Bearer tok-3", auth)
        assertEquals("""{"terminalId":1,"amount":"500.00","reason":"SURTIDO","notes":null}""", sentBody)
        assertEquals(1L, dto.id)
        assertNull(dto.notes)
        assertEquals("880.00", dto.expectedInDrawer)
    }

    @Test
    fun payout_sends_notes_when_present() = runTest {
        var sentBody = ""
        val engine = MockEngine { request ->
            sentBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """{"id":2,"amount":"50.00","reason":"OTRO","notes":"gasolina","createdAt":"2026-10-16T17:00:00Z","expectedInDrawer":"830.00"}""",
                HttpStatusCode.Created,
                jsonHeaders,
            )
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        api.payout("token", "PAYOUT-def", CashPayoutRequestDto(1L, "50.00", "OTRO", "gasolina"))

        assertEquals("""{"terminalId":1,"amount":"50.00","reason":"OTRO","notes":"gasolina"}""", sentBody)
    }

    @Test
    fun close_session_sends_counted_amount_and_decodes_delta() = runTest {
        var path = ""
        var sentBody = ""
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            sentBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                """
                {"id":7,"terminalId":1,"openedAt":"2026-10-16T14:15:00Z","closedAt":"2026-10-17T02:00:00Z",
                 "summary":$SESSION_JSON,
                 "countedAmount":"1250.00","delta":"-20.00"}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = CashApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.closeSession("token", CloseCashSessionRequestDto(terminalId = 1L, closingAmount = "1250.00"))

        val dto = assertIs<AppResult.Success<CashCloseResponseDto>>(result).value
        assertEquals("/cash/sessions/close", path)
        assertEquals("""{"terminalId":1,"closingAmount":"1250.00"}""", sentBody)
        assertEquals(7L, dto.id)
        assertEquals("2026-10-17T02:00:00Z", dto.closedAt)
        assertEquals("1250.00", dto.countedAmount)
        assertEquals("-20.00", dto.delta)
        assertEquals("1380.00", dto.summary.expectedInDrawer)
        assertEquals(2, dto.summary.payoutList.size)
    }

    private companion object {
        val SESSION_JSON = """
            {"sessionId":7,"openedAt":"2026-10-16T14:15:00Z",
            "openingAmount":"500.00","cashSales":"1300.00","creditPayments":"80.00","payouts":"500.00","otherOut":"0.00",
            "expectedInDrawer":"1380.00","transferSales":"200.00","cardSales":"150.00","creditSales":"75.00","openAccounts":2,
            "payoutList":[{"id":1,"amount":"500.00","reason":"SURTIDO","notes":"gasolina","createdAt":"2026-10-16T16:30:00Z"},
            {"id":2,"amount":"0.50","reason":"PERSONAL","notes":null,"createdAt":"2026-10-16T17:00:00Z"}]}
        """.trimIndent()

        val OPEN_SUMMARY_JSON = """{"status":"OPEN","previousClosingAmount":"500.00","session":$SESSION_JSON}"""
    }
}
