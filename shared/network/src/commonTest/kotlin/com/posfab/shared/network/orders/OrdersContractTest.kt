package com.posfab.shared.network.orders

import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.logging.LogLevel
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OrdersContractTest {
    private val config = PosConfig(
        env = PosEnv.DEV,
        apiBaseUrl = "http://localhost:8080",
        logLevel = LogLevel.DEBUG,
        requestTimeoutMs = 5_000,
        enableVerboseLogs = false,
    )

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private class Captured {
        var method: HttpMethod? = null
        var path = ""
        var query = ""
        var key: String? = null
        var auth: String? = null
        var body = ""
    }

    private fun api(
        captured: Captured,
        status: HttpStatusCode = HttpStatusCode.OK,
        response: String = FULL_ORDER_JSON,
    ): OrdersApiClient {
        val engine = MockEngine { request -> capture(captured, request, status, response) }
        return OrdersApiClient(HttpClientFactory.create(config, engine), config)
    }

    private fun MockRequestHandleScope.capture(
        captured: Captured,
        request: HttpRequestData,
        status: HttpStatusCode,
        response: String,
    ): HttpResponseData {
        captured.method = request.method
        captured.path = request.url.encodedPath
        captured.query = request.url.encodedQuery
        captured.key = request.headers["Idempotency-Key"]
        captured.auth = request.headers["Authorization"]
        captured.body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        return respond(response, status, jsonHeaders)
    }

    @Test
    fun create_posts_idempotent_body_and_decodes_full_order() = runTest {
        val captured = Captured()

        val result = api(captured, HttpStatusCode.Created).create(
            accessToken = "tok-1",
            idempotencyKey = "ORDER-abc",
            request = OrderCreateRequestDto(
                customerName = "Rosa Martínez",
                phone = "5551122896",
                address = "Calle 5 #12",
                paysWith = "200.00",
            ),
        )

        val dto = assertIs<AppResult.Success<OrderDto>>(result).value
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/orders", captured.path)
        assertEquals("ORDER-abc", captured.key)
        assertEquals("Bearer tok-1", captured.auth)
        assertEquals(
            """{"customerName":"Rosa Martínez","phone":"5551122896","address":"Calle 5 #12","paysWith":"200.00"}""",
            captured.body,
        )
        assertEquals(41L, dto.id)
        assertEquals(3, dto.version)
        assertEquals("OPEN", dto.status)
        assertEquals("ANOTADO", dto.stage)
        assertEquals(6L, dto.customerId)
        assertEquals("Rosa Martínez", dto.customerName)
        assertEquals("la de la esquina", dto.customerNickname)
        assertEquals("5551122896", dto.phone)
        assertEquals("Calle 5 #12", dto.address)
        assertEquals("sin cebolla", dto.notes)
        assertEquals("200.00", dto.paysWith)
        assertEquals("-34.00", dto.change)
        assertEquals("OTRO", dto.cancelReason)
        assertEquals("se fue", dto.cancelNotes)
        assertEquals("2026-10-16T18:00:00Z", dto.createdAt)
        assertEquals("2026-10-16T18:05:00Z", dto.updatedAt)
        assertEquals("S-000123", dto.checkoutFolio)
        assertEquals(1L, dto.terminalId)
        assertEquals(1, dto.lines.size)
        assertEquals("Torta de milanesa", dto.lines[0].productName)
        assertEquals("234.00", dto.totals.total)
    }

    @Test
    fun create_with_existing_customer_sends_only_customer_id() = runTest {
        val captured = Captured()

        api(captured, HttpStatusCode.Created).create("tok", "ORDER-def", OrderCreateRequestDto(customerId = 6L))

        assertEquals("""{"customerId":6}""", captured.body)
    }

    @Test
    fun list_open_without_date_sends_only_status() = runTest {
        val captured = Captured()

        val result = api(captured, response = LIST_JSON).list("tok-2", "OPEN", null)

        val dto = assertIs<AppResult.Success<OrderListResponseDto>>(result).value
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/orders", captured.path)
        assertEquals("status=OPEN", captured.query)
        assertEquals("Bearer tok-2", captured.auth)
        assertEquals(2, dto.items.size)
        val first = dto.items[0]
        assertEquals(41L, first.id)
        assertEquals(2, first.lineCount)
        assertEquals("ENVIADO", first.stage)
        assertEquals("150.00", first.totals.total)
        val second = dto.items[1]
        assertNull(second.customerId)
        assertNull(second.stage)
        assertEquals(0, second.lineCount)
    }

    @Test
    fun list_checked_out_with_date_sends_date() = runTest {
        val captured = Captured()

        val result = api(captured, response = """{"items":[]}""").list("tok", "CHECKED_OUT", "2026-10-16")

        val dto = assertIs<AppResult.Success<OrderListResponseDto>>(result).value
        assertEquals("status=CHECKED_OUT&date=2026-10-16", captured.query)
        assertEquals(emptyList(), dto.items)
    }

    @Test
    fun get_requests_order_by_id() = runTest {
        val captured = Captured()

        val result = api(captured).get("tok-3", 41L)

        val dto = assertIs<AppResult.Success<OrderDto>>(result).value
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/orders/41", captured.path)
        assertEquals("Bearer tok-3", captured.auth)
        assertEquals(41L, dto.id)
    }

    @Test
    fun update_puts_expected_version_and_present_fields() = runTest {
        val captured = Captured()

        api(captured).update(
            accessToken = "tok",
            id = 41L,
            request = OrderUpdateRequestDto(expectedVersion = 3, address = "Calle 7", paysWith = "500.00"),
        )

        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/orders/41", captured.path)
        assertNull(captured.key)
        assertEquals("""{"expectedVersion":3,"address":"Calle 7","paysWith":"500.00"}""", captured.body)
    }

    @Test
    fun set_stage_posts_stage_and_version() = runTest {
        val captured = Captured()

        api(captured).setStage("tok", 41L, OrderStageRequestDto(stage = "ENVIADO", expectedVersion = 4))

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/orders/41/stage", captured.path)
        assertEquals("""{"stage":"ENVIADO","expectedVersion":4}""", captured.body)
    }

    @Test
    fun cancel_without_notes_omits_notes() = runTest {
        val captured = Captured()

        api(captured).cancel("tok", 41L, OrderCancelRequestDto(reason = "NO_CONTESTO", expectedVersion = 5))

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/orders/41/cancel", captured.path)
        assertEquals("""{"reason":"NO_CONTESTO","expectedVersion":5}""", captured.body)
    }

    @Test
    fun cancel_with_notes_sends_notes() = runTest {
        val captured = Captured()

        api(captured).cancel("tok", 41L, OrderCancelRequestDto(reason = "OTRO", notes = "se fue", expectedVersion = 5))

        assertEquals("""{"reason":"OTRO","notes":"se fue","expectedVersion":5}""", captured.body)
    }

    @Test
    fun order_with_every_optional_field_absent_decodes_with_nulls() = runTest {
        val captured = Captured()

        val result = api(captured, response = MINIMAL_ORDER_JSON).get("tok", 42L)

        val dto = assertIs<AppResult.Success<OrderDto>>(result).value
        assertEquals(42L, dto.id)
        assertEquals("OPEN", dto.status)
        assertNull(dto.stage)
        assertNull(dto.customerId)
        assertNull(dto.customerName)
        assertNull(dto.customerNickname)
        assertNull(dto.phone)
        assertNull(dto.address)
        assertNull(dto.notes)
        assertNull(dto.paysWith)
        assertNull(dto.change)
        assertNull(dto.cancelReason)
        assertNull(dto.cancelNotes)
        assertNull(dto.checkoutFolio)
        assertEquals(emptyList(), dto.lines)
        assertEquals("0.00", dto.totals.total)
    }

    @Test
    fun get_404_maps_to_failure() = runTest {
        val captured = Captured()

        val result = api(captured, HttpStatusCode.NotFound, """{"error":"Order not found"}""").get("tok", 99L)

        assertIs<AppResult.Failure>(result)
    }

    private companion object {
        const val TOTALS_JSON = """{"amount":"234.00","subtotal":"234.00","tax":"0.00","total":"234.00","cashDiscount":"0.00","cashTotal":"234.00"}"""

        const val FULL_ORDER_JSON = """
            {"id":41,"version":3,"status":"OPEN","stage":"ANOTADO","customerId":6,"customerName":"Rosa Martínez",
             "customerNickname":"la de la esquina","phone":"5551122896","address":"Calle 5 #12","notes":"sin cebolla",
             "paysWith":"200.00","change":"-34.00","cancelReason":"OTRO","cancelNotes":"se fue",
             "createdAt":"2026-10-16T18:00:00Z","updatedAt":"2026-10-16T18:05:00Z","checkoutFolio":"S-000123","terminalId":1,
             "lines":[{"id":9,"lineNo":1,"productId":75,"unitId":1,"qty":"1.000000","unitPrice":"234.00","cashUnitPrice":"234.00",
             "productName":"Torta de milanesa","unitCode":"PZA","lineTotal":"234.00","priceType":"PIECE"}],
             "totals":$TOTALS_JSON}
        """

        const val MINIMAL_ORDER_JSON = """
            {"id":42,"version":1,"status":"OPEN","createdAt":"2026-10-16T18:00:00Z","updatedAt":"2026-10-16T18:00:00Z",
             "terminalId":5,"lines":[],"totals":{"amount":"0.00","subtotal":"0.00","tax":"0.00","total":"0.00"}}
        """

        const val LIST_JSON = """
            {"items":[
             {"id":41,"version":3,"status":"OPEN","stage":"ENVIADO","customerId":6,"customerName":"Rosa Martínez",
              "createdAt":"2026-10-16T18:00:00Z","updatedAt":"2026-10-16T18:05:00Z","terminalId":1,"lineCount":2,
              "totals":{"amount":"150.00","subtotal":"150.00","tax":"0.00","total":"150.00"}},
             {"id":43,"version":1,"status":"OPEN","createdAt":"2026-10-16T18:10:00Z","updatedAt":"2026-10-16T18:10:00Z",
              "terminalId":5,"lineCount":0,"totals":{"amount":"0.00","subtotal":"0.00","tax":"0.00","total":"0.00"}}
            ]}
        """
    }
}
