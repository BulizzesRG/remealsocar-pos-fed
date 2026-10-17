package com.posfab.shared.network.catalog

import com.posfab.shared.network.pos.BarcodeLookupResponseDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    @Test
    fun by_barcode_product_match_decodes_lookup_envelope() {
        val body = """
            {"product":{"id":10,"sku":"AGUACATE","name":"Aguacate Hass","baseUnitId":1,
             "requiresLotTracking":false,"active":true,"currentPrice":"45.50"},
             "matchedOn":"PRODUCT","matchedUom":null}
        """.trimIndent()
        val dto = json.decodeFromString<BarcodeLookupResponseDto>(body)
        assertEquals(10L, dto.product.id)
        assertEquals("45.50", dto.product.currentPrice)
        assertEquals("PRODUCT", dto.matchedOn)
        assertNull(dto.matchedUom)
    }

    @Test
    fun by_barcode_uom_match_decodes_matched_uom() {
        val body = """
            {"product":{"id":12,"sku":"REFRESCO","name":"Refresco Cola","baseUnitId":1,
             "requiresLotTracking":false,"active":true,"currentPrice":null},
             "matchedOn":"UOM","matchedUom":{"id":55,"unitId":7,"factorToBase":"6.000000","barcode":"7501234567899"}}
        """.trimIndent()
        val dto = json.decodeFromString<BarcodeLookupResponseDto>(body)
        assertNull(dto.product.currentPrice)
        assertEquals(7L, dto.matchedUom?.unitId)
        assertEquals("6.000000", dto.matchedUom?.factorToBase)
    }

    @Test
    fun product_detail_decodes_prices_and_classification() {
        val body = """
            {"id":"3","sku":null,"barcode":"750","name":"Jamon","baseUnit":"KG","baseUnitId":2,
             "lotTracked":false,"active":true,"currentPrice":"120.00",
             "priceType":"WEIGHT","kitchenKind":"FONDA","counted":true,"piecesPerPack":20,
             "prices":{"cashTakeaway":"120.00","listTakeaway":"126.00","cashOnsite":"110.00",
             "listOnsite":"116.00","suggestedPrice":true},
             "uoms":[{"unit":"KG","factorToBase":"1.000000"}]}
        """.trimIndent()
        val dto = json.decodeFromString<ProductDetailDto>(body)
        assertEquals("WEIGHT", dto.priceType)
        assertEquals("FONDA", dto.kitchenKind)
        assertEquals(true, dto.counted)
        assertEquals(20, dto.piecesPerPack)
        assertEquals("126.00", dto.prices?.listTakeaway)
        assertEquals("110.00", dto.prices?.cashOnsite)
        assertEquals(true, dto.prices?.suggestedPrice)
    }

    @Test
    fun old_shape_product_detail_decodes_with_defaults() {
        val body = """
            {"id":"3","sku":null,"barcode":"750","name":"Aceite 1L","baseUnit":"PZA","baseUnitId":1,
             "lotTracked":false,"active":true,"currentPrice":"34.50",
             "uoms":[{"unit":"CAJA","factorToBase":"24.000000"}]}
        """.trimIndent()
        val dto = json.decodeFromString<ProductDetailDto>(body)
        assertEquals("34.50", dto.currentPrice)
        assertEquals("PIECE", dto.priceType)
        assertEquals(false, dto.counted)
        assertNull(dto.piecesPerPack)
        assertNull(dto.kitchenKind)
        assertNull(dto.prices)
        assertEquals("24.000000", dto.uoms.single().factorToBase)
    }

    @Test
    fun price_history_decodes_entries_with_onsite_prices() {
        val body = """
            {"currentSince":"2026-10-17T16:48:47.123456Z","items":[
             {"cashTakeaway":"17.00","listTakeaway":"18.00","cashOnsite":"20.00","listOnsite":"21.00",
              "suggestedPrice":false,"validFrom":"2026-10-01T12:00:00Z",
              "validUntil":"2026-10-17T16:48:47.123456Z","changedBy":"gerente"}]}
        """.trimIndent()
        val dto = json.decodeFromString<PriceHistoryResponseDto>(body)
        assertEquals("2026-10-17T16:48:47.123456Z", dto.currentSince)
        val item = dto.items.single()
        assertEquals("17.00", item.cashTakeaway)
        assertEquals("18.00", item.listTakeaway)
        assertEquals("20.00", item.cashOnsite)
        assertEquals("21.00", item.listOnsite)
        assertEquals(false, item.suggestedPrice)
        assertEquals("2026-10-01T12:00:00Z", item.validFrom)
        assertEquals("2026-10-17T16:48:47.123456Z", item.validUntil)
        assertEquals("gerente", item.changedBy)
    }

    @Test
    fun price_history_decodes_entries_without_onsite_prices() {
        val body = """
            {"currentSince":"2026-10-17T16:48:47Z","items":[
             {"cashTakeaway":"17.00","listTakeaway":"18.00","suggestedPrice":false,
              "validFrom":"2026-10-02T12:00:00Z","validUntil":"2026-10-03T12:00:00Z","changedBy":"admin"},
             {"cashTakeaway":"16.00","listTakeaway":"17.00","cashOnsite":null,"listOnsite":null,
              "suggestedPrice":false,"validFrom":"2026-10-01T12:00:00Z","validUntil":"2026-10-02T12:00:00Z",
              "changedBy":"gerente"}]}
        """.trimIndent()
        val dto = json.decodeFromString<PriceHistoryResponseDto>(body)
        assertEquals(2, dto.items.size)
        dto.items.forEach {
            assertNull(it.cashOnsite)
            assertNull(it.listOnsite)
        }
        assertEquals(listOf("admin", "gerente"), dto.items.map { it.changedBy })
    }

    @Test
    fun price_history_decodes_suggested_price() {
        val body = """
            {"currentSince":"2026-10-17T16:48:47Z","items":[
             {"cashTakeaway":"17.00","listTakeaway":"17.00","cashOnsite":"20.00","listOnsite":"20.00",
              "suggestedPrice":true,"validFrom":"2026-10-01T12:00:00Z","validUntil":"2026-10-02T12:00:00Z",
              "changedBy":"gerente"}]}
        """.trimIndent()
        val dto = json.decodeFromString<PriceHistoryResponseDto>(body)
        assertEquals(true, dto.items.single().suggestedPrice)
    }

    @Test
    fun price_history_without_prices_decodes_null_since_and_empty_items() {
        val explicit = json.decodeFromString<PriceHistoryResponseDto>("""{"currentSince":null,"items":[]}""")
        assertNull(explicit.currentSince)
        assertEquals(emptyList(), explicit.items)
        val omitted = json.decodeFromString<PriceHistoryResponseDto>("{}")
        assertNull(omitted.currentSince)
        assertEquals(emptyList(), omitted.items)
    }

    @Test
    fun price_history_entry_without_suggested_flag_defaults_to_false() {
        val body = """
            {"items":[{"cashTakeaway":"17.00","listTakeaway":"18.00",
             "validFrom":"2026-10-01T12:00:00Z","validUntil":"2026-10-02T12:00:00Z","changedBy":"gerente"}]}
        """.trimIndent()
        val dto = json.decodeFromString<PriceHistoryResponseDto>(body)
        assertEquals(false, dto.items.single().suggestedPrice)
        assertNull(dto.currentSince)
    }
}
