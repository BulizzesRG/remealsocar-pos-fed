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
    fun product_detail_decodes_decimal_strings() {
        val body = """
            {"id":"3","sku":null,"barcode":"750","name":"Aceite 1L","baseUnit":"PZA","baseUnitId":1,
             "lotTracked":false,"active":true,"currentPrice":"34.50",
             "uoms":[{"unit":"CAJA","factorToBase":"24.000000"}],
             "priceHistory":[{"productId":"3","price":"34.50","effectiveAt":"2026-09-07T10:00:00Z"}]}
        """.trimIndent()
        val dto = json.decodeFromString<ProductDetailDto>(body)
        assertEquals("34.50", dto.currentPrice)
        assertEquals("24.000000", dto.uoms.single().factorToBase)
        assertEquals("34.50", dto.priceHistory.single().price)
    }
}
