package com.posfab.shared.network.orders

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

interface OrdersApi {
    suspend fun create(accessToken: String, idempotencyKey: String, request: OrderCreateRequestDto): AppResult<OrderDto>
    suspend fun list(accessToken: String, status: String, date: String?): AppResult<OrderListResponseDto>
    suspend fun get(accessToken: String, id: Long): AppResult<OrderDto>
    suspend fun update(accessToken: String, id: Long, request: OrderUpdateRequestDto): AppResult<OrderDto>
    suspend fun setStage(accessToken: String, id: Long, request: OrderStageRequestDto): AppResult<OrderDto>
    suspend fun cancel(accessToken: String, id: Long, request: OrderCancelRequestDto): AppResult<OrderDto>
}

class OrdersApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : OrdersApi {
    override suspend fun create(
        accessToken: String,
        idempotencyKey: String,
        request: OrderCreateRequestDto,
    ): AppResult<OrderDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/orders") {
            bearer(accessToken)
            header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun list(
        accessToken: String,
        status: String,
        date: String?,
    ): AppResult<OrderListResponseDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/orders") {
            bearer(accessToken)
            parameter("status", status)
            date?.let { parameter("date", it) }
        }
    }

    override suspend fun get(
        accessToken: String,
        id: Long,
    ): AppResult<OrderDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/orders/$id") {
            bearer(accessToken)
        }
    }

    override suspend fun update(
        accessToken: String,
        id: Long,
        request: OrderUpdateRequestDto,
    ): AppResult<OrderDto> = safeCall {
        httpClient.put("${config.apiBaseUrl}/orders/$id") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun setStage(
        accessToken: String,
        id: Long,
        request: OrderStageRequestDto,
    ): AppResult<OrderDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/orders/$id/stage") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun cancel(
        accessToken: String,
        id: Long,
        request: OrderCancelRequestDto,
    ): AppResult<OrderDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/orders/$id/cancel") {
            bearer(accessToken)
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
