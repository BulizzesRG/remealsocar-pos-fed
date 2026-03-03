package com.posfab.shared.network.operations

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

interface OperationsApi {
    suspend fun createPurchase(accessToken: String, idempotencyKey: String, request: PurchaseRequestDto): AppResult<PurchaseResponseDto>
    suspend fun createInternalRequisition(accessToken: String, request: InternalRequisitionRequestDto, idempotencyKey: String): AppResult<InternalRequisitionResponseDto>
    suspend fun inventoryOnHand(accessToken: String, productId: String?, businessUnit: String?): AppResult<List<OnHandItemDto>>
    suspend fun inventoryLots(accessToken: String, productId: String): AppResult<List<InventoryLotDto>>
    suspend fun createWaste(accessToken: String, idempotencyKey: String, request: WasteRequestDto): AppResult<WasteResponseDto>
    suspend fun createAdjustment(accessToken: String, idempotencyKey: String, request: AdjustmentRequestDto): AppResult<AdjustmentResponseDto>
}

class OperationsApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : OperationsApi {
    override suspend fun createPurchase(accessToken: String, idempotencyKey: String, request: PurchaseRequestDto): AppResult<PurchaseResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/purchases") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun createInternalRequisition(accessToken: String, request: InternalRequisitionRequestDto, idempotencyKey: String): AppResult<InternalRequisitionResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/internal-requisitions") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun inventoryOnHand(accessToken: String, productId: String?, businessUnit: String?): AppResult<List<OnHandItemDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/inventory/on-hand") {
            bearer(accessToken)
            productId?.takeIf { it.isNotBlank() }?.let { parameter("product_id", it) }
            businessUnit?.takeIf { it.isNotBlank() }?.let { parameter("business_unit", it) }
        }
    }

    override suspend fun inventoryLots(accessToken: String, productId: String): AppResult<List<InventoryLotDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/inventory/lots") {
            bearer(accessToken)
            parameter("product_id", productId)
        }
    }

    override suspend fun createWaste(accessToken: String, idempotencyKey: String, request: WasteRequestDto): AppResult<WasteResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/inventory/waste") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun createAdjustment(accessToken: String, idempotencyKey: String, request: AdjustmentRequestDto): AppResult<AdjustmentResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/inventory/adjustments") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
