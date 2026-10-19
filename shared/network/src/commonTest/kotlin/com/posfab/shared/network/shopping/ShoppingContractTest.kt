package com.posfab.shared.network.shopping

import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.logging.LogLevel
import com.posfab.shared.core.result.AppError
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

class ShoppingContractTest {
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
        response: String = ITEM_JSON,
    ): ShoppingApiClient {
        val engine = MockEngine { request -> capture(captured, request, status, response) }
        return ShoppingApiClient(HttpClientFactory.create(config, engine), config)
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
    fun current_decodes_open_and_to_shop_lists() = runTest {
        val captured = Captured()

        val result = api(captured, response = CURRENT_JSON).current("tok-1")

        val dto = assertIs<AppResult.Success<ShoppingCurrentDto>>(result).value
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/shopping-lists/current", captured.path)
        assertEquals("Bearer tok-1", captured.auth)
        val open = requireNotNull(dto.open)
        assertEquals(12L, open.id)
        assertEquals("OPEN", open.status)
        assertEquals("gerente", open.createdBy)
        assertNull(open.closedAt)
        assertEquals(2, open.itemCount)
        val linked = open.items[0]
        assertEquals(31L, linked.id)
        assertEquals("Coca cola 600 ml", linked.text)
        assertEquals(1L, linked.productId)
        assertEquals("Coca cola", linked.productName)
        assertEquals("3.000", linked.qty)
        assertEquals("PENDING", linked.status)
        assertEquals("cajero", linked.createdBy)
        assertEquals("2026-10-18T15:04:05Z", linked.createdAt)
        val free = open.items[1]
        assertNull(free.productId)
        assertNull(free.productName)
        assertNull(free.qty)
        assertNull(free.carriedFrom)
        val toShop = requireNotNull(dto.toShop)
        assertEquals("TO_SHOP", toShop.status)
        assertEquals("2026-10-17T22:00:00Z", toShop.closedAt)
        assertEquals("gerente", toShop.closedBy)
        assertEquals(1, toShop.boughtCount)
        assertEquals(1, toShop.notFoundCount)
        assertEquals(9L, toShop.items[1].carriedFrom)
    }

    @Test
    fun current_with_absent_lists_decodes_to_nulls() = runTest {
        val captured = Captured()

        val result = api(captured, response = "{}").current("tok")

        val dto = assertIs<AppResult.Success<ShoppingCurrentDto>>(result).value
        assertNull(dto.open)
        assertNull(dto.toShop)
    }

    @Test
    fun history_sends_limit_and_decodes_summaries() = runTest {
        val captured = Captured()

        val result = api(captured, response = HISTORY_JSON).history("tok-2", 20)

        val dto = assertIs<AppResult.Success<ShoppingHistoryDto>>(result).value
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/shopping-lists", captured.path)
        assertEquals("limit=20", captured.query)
        assertEquals(2, dto.items.size)
        val first = dto.items[0]
        assertEquals(10L, first.id)
        assertEquals("DONE", first.status)
        assertEquals("2026-10-17T23:00:00Z", first.doneAt)
        assertEquals(14, first.itemCount)
        assertEquals(12, first.boughtCount)
        assertEquals(2, first.notFoundCount)
        val second = dto.items[1]
        assertNull(second.closedBy)
        assertNull(second.doneBy)
        assertEquals(0, second.boughtCount)
    }

    @Test
    fun get_requests_the_list_by_id() = runTest {
        val captured = Captured()

        val result = api(captured, response = MINIMAL_LIST_JSON).get("tok", 10L)

        val dto = assertIs<AppResult.Success<ShoppingListDto>>(result).value
        assertEquals("/shopping-lists/10", captured.path)
        assertEquals(10L, dto.id)
        assertEquals(emptyList(), dto.items)
        assertEquals(0, dto.itemCount)
    }

    @Test
    fun add_item_posts_idempotent_body_without_absent_fields() = runTest {
        val captured = Captured()

        val result = api(captured, HttpStatusCode.Created).addItem("tok", "shop-1", ShoppingItemRequestDto(text = "jitomate"))

        assertIs<AppResult.Success<ShoppingItemDto>>(result)
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/shopping-lists/items", captured.path)
        assertEquals("shop-1", captured.key)
        assertEquals("""{"text":"jitomate"}""", captured.body)
    }

    @Test
    fun add_item_with_product_and_qty_sends_both() = runTest {
        val captured = Captured()

        api(captured, HttpStatusCode.Created).addItem(
            "tok",
            "shop-2",
            ShoppingItemRequestDto(text = "Coca cola", productId = 1L, qty = "2.5"),
        )

        assertEquals("""{"text":"Coca cola","productId":1,"qty":"2.5"}""", captured.body)
    }

    @Test
    fun update_item_puts_full_body_without_key() = runTest {
        val captured = Captured()

        api(captured).updateItem("tok", 31L, ShoppingItemRequestDto(text = "Coca", qty = "3"))

        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/shopping-lists/items/31", captured.path)
        assertNull(captured.key)
        assertEquals("""{"text":"Coca","qty":"3"}""", captured.body)
    }

    @Test
    fun delete_item_accepts_no_content() = runTest {
        val captured = Captured()

        val result = api(captured, HttpStatusCode.NoContent, "").deleteItem("tok", 31L)

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/shopping-lists/items/31", captured.path)
    }

    @Test
    fun set_item_status_posts_status() = runTest {
        val captured = Captured()

        api(captured).setItemStatus("tok", 31L, ShoppingItemStatusRequestDto("BOUGHT"))

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/shopping-lists/items/31/status", captured.path)
        assertEquals("""{"status":"BOUGHT"}""", captured.body)
    }

    @Test
    fun close_posts_empty_object_with_key() = runTest {
        val captured = Captured()

        api(captured, response = MINIMAL_LIST_JSON).close("tok", "shop-3", 12L)

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/shopping-lists/12/close", captured.path)
        assertEquals("shop-3", captured.key)
        assertEquals("{}", captured.body)
    }

    @Test
    fun finish_posts_carry_over_even_when_empty() = runTest {
        val captured = Captured()
        val client = api(captured, response = MINIMAL_LIST_JSON)

        client.finish("tok", "shop-4", 10L, ShoppingListDoneRequestDto(listOf(41L, 42L)))
        assertEquals("/shopping-lists/10/done", captured.path)
        assertEquals("shop-4", captured.key)
        assertEquals("""{"carryOver":[41,42]}""", captured.body)

        client.finish("tok", "shop-5", 10L, ShoppingListDoneRequestDto(emptyList()))
        assertEquals("""{"carryOver":[]}""", captured.body)
    }

    @Test
    fun error_body_surfaces_the_server_message() = runTest {
        val captured = Captured()

        val result = api(captured, HttpStatusCode.BadRequest, """{"error":"List is empty"}""").close("tok", "k", 12L)

        val failure = assertIs<AppResult.Failure>(result)
        assertEquals(AppError.Validation("List is empty"), failure.error)
    }

    @Test
    fun conflict_maps_to_conflict() = runTest {
        val captured = Captured()

        val result = api(captured, HttpStatusCode.Conflict, """{"error":"List is not open"}""").deleteItem("tok", 1L)

        assertEquals(AppError.Conflict, assertIs<AppResult.Failure>(result).error)
    }

    private companion object {
        const val ITEM_JSON = """
            {"id":31,"text":"Coca cola 600 ml","productId":1,"productName":"Coca cola","qty":"3.000","status":"PENDING",
             "createdBy":"cajero","createdAt":"2026-10-18T15:04:05Z"}
        """

        const val CURRENT_JSON = """
            {"open":{"id":12,"status":"OPEN","createdAt":"2026-10-18T15:00:00Z","createdBy":"gerente",
              "itemCount":2,"boughtCount":0,"notFoundCount":0,
              "items":[$ITEM_JSON,
               {"id":32,"text":"jitomate","status":"PENDING","createdBy":"gerente","createdAt":"2026-10-18T15:10:00Z"}]},
             "toShop":{"id":11,"status":"TO_SHOP","createdAt":"2026-10-17T14:00:00Z","createdBy":"cajero",
              "closedAt":"2026-10-17T22:00:00Z","closedBy":"gerente","itemCount":2,"boughtCount":1,"notFoundCount":1,
              "items":[{"id":21,"text":"cebolla","qty":"2.500","status":"BOUGHT","createdBy":"cajero","createdAt":"2026-10-17T14:00:00Z"},
               {"id":22,"text":"aceite","status":"NOT_FOUND","carriedFrom":9,"createdBy":"gerente","createdAt":"2026-10-17T14:05:00Z"}]}}
        """

        const val HISTORY_JSON = """
            {"items":[
             {"id":10,"status":"DONE","createdAt":"2026-10-11T14:00:00Z","createdBy":"gerente","closedAt":"2026-10-17T10:00:00Z",
              "closedBy":"gerente","doneAt":"2026-10-17T23:00:00Z","doneBy":"admin","itemCount":14,"boughtCount":12,"notFoundCount":2},
             {"id":8,"status":"DONE","createdAt":"2026-10-09T14:00:00Z","closedAt":"2026-10-10T10:00:00Z",
              "doneAt":"2026-10-11T09:00:00Z","itemCount":3}
            ]}
        """

        const val MINIMAL_LIST_JSON = """{"id":10,"status":"TO_SHOP","createdAt":"2026-10-11T14:00:00Z"}"""
    }
}
