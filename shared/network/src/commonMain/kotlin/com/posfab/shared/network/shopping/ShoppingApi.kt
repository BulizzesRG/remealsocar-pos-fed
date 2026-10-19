package com.posfab.shared.network.shopping

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

interface ShoppingApi {
    suspend fun current(accessToken: String): AppResult<ShoppingCurrentDto>
    suspend fun history(accessToken: String, limit: Int): AppResult<ShoppingHistoryDto>
    suspend fun get(accessToken: String, id: Long): AppResult<ShoppingListDto>
    suspend fun addItem(accessToken: String, idempotencyKey: String, request: ShoppingItemRequestDto): AppResult<ShoppingItemDto>
    suspend fun updateItem(accessToken: String, id: Long, request: ShoppingItemRequestDto): AppResult<ShoppingItemDto>
    suspend fun deleteItem(accessToken: String, id: Long): AppResult<Unit>
    suspend fun setItemStatus(accessToken: String, id: Long, request: ShoppingItemStatusRequestDto): AppResult<ShoppingItemDto>
    suspend fun close(accessToken: String, idempotencyKey: String, id: Long): AppResult<ShoppingListDto>
    suspend fun finish(
        accessToken: String,
        idempotencyKey: String,
        id: Long,
        request: ShoppingListDoneRequestDto,
    ): AppResult<ShoppingListDto>
}

class ShoppingApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : ShoppingApi {
    override suspend fun current(accessToken: String): AppResult<ShoppingCurrentDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/shopping-lists/current") {
            bearer(accessToken)
        }
    }

    override suspend fun history(accessToken: String, limit: Int): AppResult<ShoppingHistoryDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/shopping-lists") {
            bearer(accessToken)
            parameter("limit", limit)
        }
    }

    override suspend fun get(accessToken: String, id: Long): AppResult<ShoppingListDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/shopping-lists/$id") {
            bearer(accessToken)
        }
    }

    override suspend fun addItem(
        accessToken: String,
        idempotencyKey: String,
        request: ShoppingItemRequestDto,
    ): AppResult<ShoppingItemDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/shopping-lists/items") {
            bearer(accessToken)
            header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun updateItem(
        accessToken: String,
        id: Long,
        request: ShoppingItemRequestDto,
    ): AppResult<ShoppingItemDto> = safeCall {
        httpClient.put("${config.apiBaseUrl}/shopping-lists/items/$id") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun deleteItem(accessToken: String, id: Long): AppResult<Unit> = safeCall {
        httpClient.delete("${config.apiBaseUrl}/shopping-lists/items/$id") {
            bearer(accessToken)
        }
    }

    override suspend fun setItemStatus(
        accessToken: String,
        id: Long,
        request: ShoppingItemStatusRequestDto,
    ): AppResult<ShoppingItemDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/shopping-lists/items/$id/status") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun close(
        accessToken: String,
        idempotencyKey: String,
        id: Long,
    ): AppResult<ShoppingListDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/shopping-lists/$id/close") {
            bearer(accessToken)
            header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(JsonObject(emptyMap()))
        }
    }

    override suspend fun finish(
        accessToken: String,
        idempotencyKey: String,
        id: Long,
        request: ShoppingListDoneRequestDto,
    ): AppResult<ShoppingListDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/shopping-lists/$id/done") {
            bearer(accessToken)
            header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private fun HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }

    private companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}
