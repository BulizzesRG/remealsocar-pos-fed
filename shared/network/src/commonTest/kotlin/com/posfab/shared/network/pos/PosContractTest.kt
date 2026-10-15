package com.posfab.shared.network.pos

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PosContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

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
}
