package com.posfab.shared.network.reports

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
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReportsContractTest {
    private val config = PosConfig(
        env = PosEnv.DEV,
        apiBaseUrl = "http://localhost:8080",
        logLevel = LogLevel.DEBUG,
        requestTimeoutMs = 5_000,
        enableVerboseLogs = false,
    )

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    @Test
    fun daily_sales_with_terminal_sends_terminal_id_and_decodes_items() = runTest {
        var path = ""
        var auth: String? = null
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath + "?" + request.url.encodedQuery
            auth = request.headers["Authorization"]
            method = request.method
            respond(
                """
                [{"id":12,"folio":"S-000012","terminalId":1,"businessUnitId":1,"paymentType":"CASH",
                  "paymentStatus":"PAID","soldAt":"2026-10-16T01:15:00Z","totalAmount":"34.00",
                  "cashDiscount":"2.00","channel":"TAKEAWAY"},
                 {"id":13,"folio":"S-000013","terminalId":1,"businessUnitId":1,"paymentType":"CREDIT",
                  "paymentStatus":"ON_CREDIT","soldAt":"2026-10-16T02:40:00Z","totalAmount":"18.00",
                  "cashDiscount":"0.00","channel":"ONSITE","customerName":"Lupe Hernández","customerNickname":"Doña Lupe"}]
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = ReportsApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.dailySales("tok-1", "2026-10-15", 1L)

        val items = assertIs<AppResult.Success<List<SaleHistoryItemDto>>>(result).value
        assertEquals("/sales?date=2026-10-15&terminal_id=1", path)
        assertEquals("Bearer tok-1", auth)
        assertEquals(HttpMethod.Get, method)
        assertEquals(2, items.size)
        val cash = items[0]
        assertEquals(12L, cash.id)
        assertEquals("S-000012", cash.folio)
        assertEquals(1L, cash.terminalId)
        assertEquals(1L, cash.businessUnitId)
        assertEquals("CASH", cash.paymentType)
        assertEquals("PAID", cash.paymentStatus)
        assertEquals("2026-10-16T01:15:00Z", cash.soldAt)
        assertEquals("34.00", cash.totalAmount)
        assertEquals("2.00", cash.cashDiscount)
        assertEquals("TAKEAWAY", cash.channel)
        assertNull(cash.customerName)
        assertNull(cash.customerNickname)
        val credit = items[1]
        assertEquals("ON_CREDIT", credit.paymentStatus)
        assertEquals("ONSITE", credit.channel)
        assertEquals("Lupe Hernández", credit.customerName)
        assertEquals("Doña Lupe", credit.customerNickname)
    }

    @Test
    fun daily_sales_without_terminal_omits_terminal_id() = runTest {
        var url = ""
        val engine = MockEngine { request ->
            url = request.url.toString()
            respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
        val api = ReportsApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.dailySales("tok", "2026-10-15", null)

        assertEquals(emptyList(), assertIs<AppResult.Success<List<SaleHistoryItemDto>>>(result).value)
        assertEquals("http://localhost:8080/sales?date=2026-10-15", url)
        assertFalse(url.contains("terminal_id"))
    }

    @Test
    fun sale_detail_with_customer_decodes_header_and_lines() = runTest {
        var path = ""
        var auth: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            auth = request.headers["Authorization"]
            respond(
                """
                {"id":13,"folio":"S-000013","soldAt":"2026-10-16T02:40:00Z","terminalId":1,"terminalCode":"POS1",
                 "businessUnitId":1,"businessUnitCode":"TIENDA","paymentType":"CREDIT","paymentStatus":"ON_CREDIT",
                 "channel":"ONSITE","subtotal":"18.00","cashDiscount":"0.00","totalAmount":"18.00",
                 "customerId":13,"customerName":"Lupe Hernández","customerNickname":"Doña Lupe",
                 "lines":[{"id":40,"productId":1,"productName":"Coca cola","unitCode":"PZA","priceType":"PIECE",
                           "qty":"1.000000","unitPrice":"18.0000","lineTotal":"18.00"}]}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = ReportsApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.saleDetail("tok-2", 13L)

        val dto = assertIs<AppResult.Success<SaleDetailDto>>(result).value
        assertEquals("/sales/13", path)
        assertEquals("Bearer tok-2", auth)
        assertEquals(13L, dto.id)
        assertEquals("S-000013", dto.folio)
        assertEquals("2026-10-16T02:40:00Z", dto.soldAt)
        assertEquals(1L, dto.terminalId)
        assertEquals("POS1", dto.terminalCode)
        assertEquals(1L, dto.businessUnitId)
        assertEquals("TIENDA", dto.businessUnitCode)
        assertEquals("CREDIT", dto.paymentType)
        assertEquals("ON_CREDIT", dto.paymentStatus)
        assertEquals("ONSITE", dto.channel)
        assertEquals("18.00", dto.subtotal)
        assertEquals("0.00", dto.cashDiscount)
        assertEquals("18.00", dto.totalAmount)
        assertEquals(13L, dto.customerId)
        assertEquals("Lupe Hernández", dto.customerName)
        assertEquals("Doña Lupe", dto.customerNickname)
        assertEquals(
            listOf(SaleDetailLineDto(40L, 1L, "Coca cola", "PZA", "PIECE", "1.000000", "18.0000", "18.00")),
            dto.lines,
        )
    }

    @Test
    fun sale_detail_without_customer_decodes_absent_fields_as_null() = runTest {
        val engine = MockEngine {
            respond(
                """
                {"id":12,"folio":"S-000012","soldAt":"2026-10-16T01:15:00Z","terminalId":2,"terminalCode":"POS2",
                 "businessUnitId":1,"businessUnitCode":"TIENDA","paymentType":"CASH","paymentStatus":"PAID",
                 "channel":"TAKEAWAY","subtotal":"36.00","cashDiscount":"2.00","totalAmount":"34.00",
                 "lines":[{"id":38,"productId":13,"productName":"Jamón","unitCode":"KG","priceType":"WEIGHT",
                           "qty":"0.250000","unitPrice":"126.0000","lineTotal":"31.50"},
                          {"id":39,"productId":3,"productName":"Varios","unitCode":"PZA","priceType":"OPEN",
                           "qty":"1.000000","unitPrice":"4.5000","lineTotal":"4.50"}]}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = ReportsApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.saleDetail("tok", 12L)

        val dto = assertIs<AppResult.Success<SaleDetailDto>>(result).value
        assertNull(dto.customerId)
        assertNull(dto.customerName)
        assertNull(dto.customerNickname)
        assertEquals("POS2", dto.terminalCode)
        assertEquals("2.00", dto.cashDiscount)
        assertEquals(2, dto.lines.size)
        assertEquals("WEIGHT", dto.lines[0].priceType)
        assertEquals("0.250000", dto.lines[0].qty)
        assertEquals("KG", dto.lines[0].unitCode)
        assertEquals("OPEN", dto.lines[1].priceType)
        assertEquals("4.50", dto.lines[1].lineTotal)
    }

    @Test
    fun sale_detail_404_maps_to_failure() = runTest {
        val engine = MockEngine {
            respond("""{"error":"Sale not found"}""", HttpStatusCode.NotFound, jsonHeaders)
        }
        val api = ReportsApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.saleDetail("tok", 999L)

        assertIs<AppResult.Failure>(result)
    }

    @Test
    fun daily_breakdown_sends_range_and_decodes_rows() = runTest {
        var path = ""
        var auth: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath + "?" + request.url.encodedQuery
            auth = request.headers["Authorization"]
            respond(
                """
                {"byPayment":[{"saleDate":"2026-10-15","businessUnitId":1,"businessUnitCode":"TIENDA",
                               "paymentType":"CASH","salesCount":3,"subtotal":"120.00","cashDiscount":"6.00",
                               "totalAmount":"114.00"}],
                 "byKitchen":[{"saleDate":"2026-10-15","businessUnitId":1,"businessUnitCode":"TIENDA",
                               "lineCount":4,"amount":"64.00"},
                              {"saleDate":"2026-10-15","businessUnitId":1,"businessUnitCode":"TIENDA",
                               "kitchenKind":"FONDA","lineCount":1,"amount":"50.00"}]}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = ReportsApiClient(HttpClientFactory.create(config, engine), config)

        val result = api.dailyBreakdown("tok-3", "2026-10-15", "2026-10-15")

        val dto = assertIs<AppResult.Success<DailyBreakdownDto>>(result).value
        assertEquals("/reports/sales/daily-breakdown?date_from=2026-10-15&date_to=2026-10-15", path)
        assertEquals("Bearer tok-3", auth)
        assertEquals(
            listOf(PaymentBreakdownRowDto("2026-10-15", 1L, "TIENDA", "CASH", 3L, "120.00", "6.00", "114.00")),
            dto.byPayment,
        )
        assertEquals(2, dto.byKitchen.size)
        val store = dto.byKitchen[0]
        assertNull(store.kitchenKind)
        assertEquals(4L, store.lineCount)
        assertEquals("64.00", store.amount)
        assertEquals("FONDA", dto.byKitchen[1].kitchenKind)
        assertEquals("50.00", dto.byKitchen[1].amount)
    }
}
