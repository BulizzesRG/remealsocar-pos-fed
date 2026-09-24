package com.posfab.shared.network.operations

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

interface OperationsApi {
    suspend fun listSuppliers(accessToken: String, activeOnly: Boolean = true): AppResult<List<SupplierDto>>
    suspend fun createSupplier(accessToken: String, request: SupplierCreateRequestDto): AppResult<IdResponseDto>
    suspend fun createPurchase(accessToken: String, idempotencyKey: String, request: PurchaseCreateRequestDto): AppResult<IdResponseDto>
    suspend fun createInternalRequisition(accessToken: String, idempotencyKey: String, request: InternalRequisitionCreateRequestDto): AppResult<IdWithFolioResponseDto>
    suspend fun inventoryOnHand(accessToken: String, productId: Long?, businessUnitId: Long?): AppResult<List<OnHandResponseDto>>
    suspend fun inventoryLots(accessToken: String, productId: Long): AppResult<List<InventoryLotResponseDto>>
    suspend fun createWaste(accessToken: String, idempotencyKey: String, request: WasteRequestDto): AppResult<IdResponseDto>
    suspend fun createAdjustment(accessToken: String, idempotencyKey: String, request: AdjustmentRequestDto): AppResult<IdResponseDto>
}

class OperationsApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : OperationsApi {
    override suspend fun listSuppliers(accessToken: String, activeOnly: Boolean): AppResult<List<SupplierDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/suppliers") {
            bearer(accessToken)
            parameter("active", activeOnly)
        }
    }

    override suspend fun createSupplier(accessToken: String, request: SupplierCreateRequestDto): AppResult<IdResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/suppliers") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun createPurchase(accessToken: String, idempotencyKey: String, request: PurchaseCreateRequestDto): AppResult<IdResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/purchases") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun createInternalRequisition(accessToken: String, idempotencyKey: String, request: InternalRequisitionCreateRequestDto): AppResult<IdWithFolioResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/internal-requisitions") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun inventoryOnHand(accessToken: String, productId: Long?, businessUnitId: Long?): AppResult<List<OnHandResponseDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/inventory/on-hand") {
            bearer(accessToken)
            productId?.let { parameter("product_id", it) }
            businessUnitId?.let { parameter("business_unit_id", it) }
        }
    }

    override suspend fun inventoryLots(accessToken: String, productId: Long): AppResult<List<InventoryLotResponseDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/inventory/lots") {
            bearer(accessToken)
            parameter("product_id", productId)
        }
    }

    override suspend fun createWaste(accessToken: String, idempotencyKey: String, request: WasteRequestDto): AppResult<IdResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/inventory/waste") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun createAdjustment(accessToken: String, idempotencyKey: String, request: AdjustmentRequestDto): AppResult<IdResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/inventory/adjustments") {
            bearer(accessToken)
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private fun HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
