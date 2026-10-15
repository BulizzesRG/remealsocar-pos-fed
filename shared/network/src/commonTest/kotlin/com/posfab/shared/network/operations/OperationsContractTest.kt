package com.posfab.shared.network.operations

import com.posfab.shared.core.result.AppError
import com.posfab.shared.network.http.ApiErrorMapper
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OperationsContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    @Test
    fun purchase_request_matches_backend_example() {
        val request = PurchaseCreateRequestDto(
            businessUnitId = 1,
            terminalId = 3,
            supplierId = 1,
            supplierDocNo = "FAC-1001",
            paidCash = true,
            paidFromTerminalId = 3,
            items = listOf(PurchaseLineRequestDto(productId = 1, unitId = 2, qty = "20.000", unitCost = "18.20")),
        )
        val encoded = json.encodeToString(request)
        assertEquals(
            """{"businessUnitId":1,"terminalId":3,"supplierId":1,"supplierDocNo":"FAC-1001","paidCash":true,"paidFromTerminalId":3,"items":[{"productId":1,"unitId":2,"qty":"20.000","unitCost":"18.20"}]}""",
            encoded,
        )
    }

    @Test
    fun requisition_request_sends_consuming_business_unit() {
        val request = InternalRequisitionCreateRequestDto(
            businessUnitId = 2,
            terminalId = 3,
            items = listOf(InternalRequisitionLineRequestDto(productId = 1, unitId = 2, qty = "3", lotId = 9)),
        )
        assertEquals(
            """{"businessUnitId":2,"terminalId":3,"items":[{"productId":1,"unitId":2,"qty":"3","lotId":9}]}""",
            json.encodeToString(request),
        )
    }

    @Test
    fun waste_and_adjustment_requests_carry_terminal_and_reason_codes() {
        val waste = WasteRequestDto(
            terminalId = 1, businessUnitId = 1,
            lines = listOf(WasteLineRequestDto(productId = 5, unitId = 1, qty = "1.5", reasonCode = "DAMAGED", notes = "Caja rota")),
        )
        assertEquals(
            """{"terminalId":1,"businessUnitId":1,"lines":[{"productId":5,"unitId":1,"qty":"1.5","reasonCode":"DAMAGED","notes":"Caja rota"}]}""",
            json.encodeToString(waste),
        )
        val adjustment = AdjustmentRequestDto(
            terminalId = 1, businessUnitId = 1,
            lines = listOf(AdjustmentLineRequestDto(productId = 5, unitId = 1, qtyDelta = "-2", reasonCode = "COUNT_DIFFERENCE")),
        )
        assertEquals(
            """{"terminalId":1,"businessUnitId":1,"lines":[{"productId":5,"unitId":1,"qtyDelta":"-2","reasonCode":"COUNT_DIFFERENCE"}]}""",
            json.encodeToString(adjustment),
        )
    }

    @Test
    fun inventory_responses_decode_decimal_strings_and_ids() {
        val onHand = json.decodeFromString<List<OnHandResponseDto>>("""[{"productId":1,"businessUnitId":1,"qtyBase":"100.000"}]""")
        assertEquals(1L, onHand.single().productId)
        assertEquals("100.000", onHand.single().qtyBase)

        val lots = json.decodeFromString<List<InventoryLotResponseDto>>(
            """[{"lotId":9,"productId":1,"lotCode":"L001","expiryDate":null,"qtyBase":"5.000"}]"""
        )
        assertEquals(9L, lots.single().lotId)
        assertNull(lots.single().expiryDate)

        val folio = json.decodeFromString<IdWithFolioResponseDto>("""{"id":12,"folio":"REQ-FON-0001"}""")
        assertEquals("REQ-FON-0001", folio.folio)
        val supplier = json.decodeFromString<List<SupplierDto>>("""[{"id":1,"name":"Proveedor Uno","phone":null,"taxId":null,"active":true}]""")
        assertEquals("Proveedor Uno", supplier.single().name)
    }

    @Test
    fun error_mapper_surfaces_backend_error_message_on_validation_errors() {
        val error = ApiErrorMapper.fromStatus(HttpStatusCode.BadRequest, """{"error":"lotCode is required for productId=3"}""")
        assertIs<AppError.Validation>(error)
        assertEquals("lotCode is required for productId=3", error.message)

        val fallback = ApiErrorMapper.fromStatus(HttpStatusCode.BadRequest, "<html>nope</html>")
        assertIs<AppError.Validation>(fallback)
        assertEquals("Request rejected: 400", fallback.message)

        assertEquals(AppError.Conflict, ApiErrorMapper.fromStatus(HttpStatusCode.Conflict, """{"error":"dup"}"""))
    }
}
