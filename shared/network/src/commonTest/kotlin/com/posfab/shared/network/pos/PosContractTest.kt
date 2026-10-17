package com.posfab.shared.network.pos

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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class PosContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    private val config = PosConfig(
        env = PosEnv.DEV,
        apiBaseUrl = "http://localhost:8080",
        logLevel = LogLevel.DEBUG,
        requestTimeoutMs = 5_000,
        enableVerboseLogs = false,
    )

    private class Captured {
        var method: HttpMethod? = null
        var path = ""
        var query = ""
        var auth: String? = null
    }

    private fun api(captured: Captured, response: String): PosApiClient {
        val engine = MockEngine { request ->
            captured.method = request.method
            captured.path = request.url.encodedPath
            captured.query = request.url.encodedQuery
            captured.auth = request.headers["Authorization"]
            respond(response, HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }
        return PosApiClient(HttpClientFactory.create(config, engine), config)
    }

    @Test
    fun delivery_listing_requests_delivery_type_and_decodes_delivery_fields() = runTest {
        val captured = Captured()

        val result = api(captured, DELIVERY_LIST_JSON).listOpenDrafts("tok-1", "DELIVERY")

        val dto = assertIs<AppResult.Success<DraftListDto>>(result).value
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/pos/drafts", captured.path)
        assertEquals("type=DELIVERY&status=OPEN", captured.query)
        assertEquals("Bearer tok-1", captured.auth)
        val delivery = dto.items.single()
        assertEquals("DELIVERY", delivery.type)
        assertEquals("Rosa Martínez", delivery.customerName)
        assertEquals("la de la esquina", delivery.customerNickname)
        assertEquals("Calle 5 #12", delivery.deliveryAddress)
        assertEquals("ENVIADO", delivery.deliveryStage)
        assertEquals("200.00", delivery.paysWith)
    }

    @Test
    fun sale_listing_convenience_requests_sale_type() = runTest {
        val captured = Captured()

        val result = api(captured, """{"items":[]}""").listOpenSaleDrafts("tok-2")

        assertIs<AppResult.Success<DraftListDto>>(result)
        assertEquals("/pos/drafts", captured.path)
        assertEquals("type=SALE&status=OPEN", captured.query)
    }

    @Test
    fun delivery_draft_without_optional_fields_decodes_nulls() {
        val body = """
            {"id":31,"type":"DELIVERY","status":"OPEN","terminalId":3,"version":0,"lines":[],
             "totals":{"amount":"0.00","subtotal":"0.00","tax":"0.00","total":"0.00"},
             "customerName":"Pedro Barraza","deliveryStage":"ANOTADO"}
        """.trimIndent()
        val dto = json.decodeFromString<DraftDto>(body)
        assertEquals("Pedro Barraza", dto.customerName)
        assertEquals("ANOTADO", dto.deliveryStage)
        assertNull(dto.customerNickname)
        assertNull(dto.deliveryAddress)
        assertNull(dto.paysWith)
    }

    @Test
    fun sale_draft_without_delivery_fields_decodes_nulls() {
        val body = """
            {"id":9,"type":"SALE","status":"OPEN","terminalId":1,"version":2,"lines":[],
             "totals":{"amount":"0.00","subtotal":"0.00","tax":"0.00","total":"0.00"}}
        """.trimIndent()
        val dto = json.decodeFromString<DraftDto>(body)
        assertEquals("SALE", dto.type)
        assertNull(dto.customerName)
        assertNull(dto.customerNickname)
        assertNull(dto.deliveryAddress)
        assertNull(dto.deliveryStage)
        assertNull(dto.paysWith)
    }

    @Test
    fun draft_with_cash_fields_decodes() {
        val body = """
            {"id":7,"type":"SALE","status":"OPEN","terminalId":1,"version":3,
             "lines":[{"id":11,"lineNo":1,"productId":5,"unitId":2,"qty":"2.000000",
             "unitPrice":"22.00","cashUnitPrice":"21.00","productName":"Cigarro suelto",
             "unitCode":"PZA","lineTotal":"44.00"}],
             "totals":{"amount":"44.00","subtotal":"44.00","tax":"0.00","total":"44.00",
             "cashDiscount":"2.00","cashTotal":"42.00"}}
        """.trimIndent()
        val dto = json.decodeFromString<DraftDto>(body)
        assertEquals("21.00", dto.lines.single().cashUnitPrice)
        assertEquals("2.00", dto.totals.cashDiscount)
        assertEquals("42.00", dto.totals.cashTotal)
        assertEquals("44.00", dto.totals.total)
    }

    @Test
    fun old_shape_draft_decodes_with_defaults() {
        val body = """
            {"id":7,"type":"SALE","status":"OPEN","terminalId":1,"version":1,
             "lines":[{"id":11,"lineNo":1,"productId":5,"unitId":2,"qty":"1.000000",
             "unitPrice":"22.00","productName":"Cigarro suelto","unitCode":"PZA","lineTotal":"22.00"}],
             "totals":{"amount":"22.00","subtotal":"22.00","tax":"0.00","total":"22.00"}}
        """.trimIndent()
        val dto = json.decodeFromString<DraftDto>(body)
        assertNull(dto.lines.single().cashUnitPrice)
        assertNull(dto.totals.cashTotal)
        assertEquals("0", dto.totals.cashDiscount)
        assertEquals("22.00", dto.totals.total)
        assertEquals("TAKEAWAY", dto.channel)
        assertEquals("PIECE", dto.lines.single().priceType)
    }

    @Test
    fun draft_line_with_price_type_decodes() {
        val body = """
            {"id":7,"type":"SALE","status":"OPEN","terminalId":1,"version":2,
             "lines":[{"id":11,"lineNo":1,"productId":13,"unitId":3,"qty":"0.350000",
             "unitPrice":"126.00","cashUnitPrice":"120.00","productName":"Jamón",
             "unitCode":"KG","lineTotal":"44.10","priceType":"WEIGHT"}],
             "totals":{"amount":"44.10","subtotal":"44.10","tax":"0.00","total":"44.10",
             "cashDiscount":"2.10","cashTotal":"42.00"}}
        """.trimIndent()
        val dto = json.decodeFromString<DraftDto>(body)
        assertEquals("WEIGHT", dto.lines.single().priceType)
        assertEquals("0.350000", dto.lines.single().qty)
    }

    @Test
    fun add_line_request_with_amount_encodes_without_qty() {
        val request = AddLineRequestDto(productId = 13L, unitId = 3L, amount = "20.00", expectedVersion = 4)
        val encoded = json.encodeToString(AddLineRequestDto.serializer(), request)
        assertEquals("""{"productId":13,"unitId":3,"amount":"20.00","expectedVersion":4}""", encoded)
        assertFalse(encoded.contains("qty"))
    }

    @Test
    fun update_line_request_with_amount_encodes_without_qty() {
        val request = UpdateLineRequestDto(amount = "20.00", expectedVersion = 5)
        val encoded = json.encodeToString(UpdateLineRequestDto.serializer(), request)
        assertEquals("""{"amount":"20.00","expectedVersion":5}""", encoded)
    }

    @Test
    fun draft_with_channel_decodes() {
        val body = """
            {"id":7,"type":"SALE","status":"OPEN","terminalId":1,"version":4,"channel":"ONSITE",
             "lines":[],
             "totals":{"amount":"0.00","subtotal":"0.00","tax":"0.00","total":"0.00",
             "cashDiscount":"0.00","cashTotal":"0.00"}}
        """.trimIndent()
        val dto = json.decodeFromString<DraftDto>(body)
        assertEquals("ONSITE", dto.channel)
        assertEquals(4, dto.version)
    }

    @Test
    fun draft_channel_request_encodes() {
        val encoded = json.encodeToString(DraftChannelRequestDto.serializer(), DraftChannelRequestDto("ONSITE", 3))
        assertEquals("""{"channel":"ONSITE","expectedVersion":3}""", encoded)
    }

    @Test
    fun checkout_response_decodes_total_amount() {
        val body = """{"id":90,"folio":"F-0090","subtotal":"44.00","cashDiscount":"2.00","totalAmount":"42.00"}"""
        val dto = json.decodeFromString<CheckoutResponseDto>(body)
        assertEquals(90L, dto.id)
        assertEquals("F-0090", dto.folio)
        assertEquals("42.00", dto.totalAmount)
        assertEquals("2.00", dto.cashDiscount)
        assertEquals("44.00", dto.subtotal)
    }

    @Test
    fun old_checkout_response_decodes_without_totals() {
        val dto = json.decodeFromString<CheckoutResponseDto>("""{"id":90,"folio":"F-0090"}""")
        assertNull(dto.totalAmount)
        assertNull(dto.cashDiscount)
        assertNull(dto.subtotal)
    }

    @Test
    fun by_barcode_decodes_prices_price_type_and_kitchen_kind() {
        val body = """
            {"product":{"id":10,"sku":"COMIDA","name":"Comida","baseUnitId":1,
             "requiresLotTracking":false,"active":true,"currentPrice":"63.00",
             "priceType":"PIECE","kitchenKind":"FONDA",
             "prices":{"cashTakeaway":"63.00","listTakeaway":"66.00","cashOnsite":"63.00",
             "listOnsite":"66.00","suggestedPrice":false}},
             "matchedOn":"PRODUCT","matchedUom":null}
        """.trimIndent()
        val dto = json.decodeFromString<BarcodeLookupResponseDto>(body)
        assertEquals("PIECE", dto.product.priceType)
        assertEquals("FONDA", dto.product.kitchenKind)
        assertEquals("63.00", dto.product.prices?.cashTakeaway)
        assertEquals("66.00", dto.product.prices?.listTakeaway)
    }

    @Test
    fun by_barcode_without_new_fields_uses_defaults() {
        val body = """
            {"product":{"id":10,"sku":"AGUACATE","name":"Aguacate Hass","baseUnitId":1,
             "requiresLotTracking":false,"active":true,"currentPrice":"45.50"},
             "matchedOn":"PRODUCT","matchedUom":null}
        """.trimIndent()
        val dto = json.decodeFromString<BarcodeLookupResponseDto>(body)
        assertEquals("PIECE", dto.product.priceType)
        assertNull(dto.product.kitchenKind)
        assertNull(dto.product.prices)
    }

    @Test
    fun draft_list_decodes() {
        val body = """
            {"items":[
             {"id":9,"type":"SALE","status":"OPEN","terminalId":1,"version":2,
              "lines":[{"id":21,"lineNo":1,"productId":5,"unitId":2,"qty":"1.000000",
              "unitPrice":"22.00","cashUnitPrice":"21.00","productName":"Cigarro suelto",
              "unitCode":"PZA","lineTotal":"22.00"}],
              "totals":{"amount":"22.00","subtotal":"22.00","tax":"0.00","total":"22.00",
              "cashDiscount":"1.00","cashTotal":"21.00"}},
             {"id":12,"type":"SALE","status":"OPEN","terminalId":1,"version":0,"lines":[],
              "totals":{"amount":"0.00","subtotal":"0.00","tax":"0.00","total":"0.00",
              "cashDiscount":"0.00","cashTotal":"0.00"}}
            ]}
        """.trimIndent()
        val dto = json.decodeFromString<DraftListDto>(body)
        assertEquals(listOf(9L, 12L), dto.items.map { it.id })
        assertEquals("21.00", dto.items[0].totals.cashTotal)
        assertEquals("0.00", dto.items[1].totals.cashTotal)
        assertEquals(1, dto.items[0].lines.size)
    }

    @Test
    fun empty_draft_list_decodes() {
        val dto = json.decodeFromString<DraftListDto>("""{"items":[]}""")
        assertEquals(emptyList(), dto.items)
    }

    private companion object {
        const val DELIVERY_LIST_JSON = """
            {"items":[
             {"id":30,"type":"DELIVERY","status":"OPEN","terminalId":3,"version":4,
              "lines":[{"id":41,"lineNo":1,"productId":75,"unitId":2,"qty":"2.000000",
              "unitPrice":"75.00","cashUnitPrice":"71.25","productName":"Comida",
              "unitCode":"PZA","lineTotal":"150.00"}],
              "totals":{"amount":"150.00","subtotal":"150.00","tax":"0.00","total":"150.00",
              "cashDiscount":"7.50","cashTotal":"142.50"},
              "channel":"TAKEAWAY","customerName":"Rosa Martínez","customerNickname":"la de la esquina",
              "deliveryAddress":"Calle 5 #12","deliveryStage":"ENVIADO","paysWith":"200.00"}
            ]}
        """
    }
}
