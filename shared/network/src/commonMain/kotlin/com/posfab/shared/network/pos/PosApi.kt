package com.posfab.shared.network.pos

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

interface PosApi {
    suspend fun searchProducts(accessToken: String, query: String, limit: Int): AppResult<List<ProductDto>>
    suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<ProductDto?>
    suspend fun openCurrentSaleDraft(accessToken: String, terminalCode: String): AppResult<DraftDto>
    suspend fun addDraftLine(accessToken: String, draftId: String, request: AddLineRequestDto): AppResult<DraftDto>
    suspend fun updateDraftLine(accessToken: String, draftId: String, lineId: String, request: UpdateLineRequestDto): AppResult<DraftDto>
    suspend fun removeDraftLine(accessToken: String, draftId: String, lineId: String, expectedVersion: Long): AppResult<DraftDto>
    suspend fun validateDraft(accessToken: String, draftId: String): AppResult<ValidateDraftResponseDto>
    suspend fun resolveLots(accessToken: String, draftId: String): AppResult<DraftDto>
    suspend fun checkoutDraft(
        accessToken: String,
        draftId: String,
        request: CheckoutRequestDto,
        idempotencyKey: String,
    ): AppResult<CheckoutResponseDto>
}

class PosApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : PosApi {
    override suspend fun searchProducts(accessToken: String, query: String, limit: Int): AppResult<List<ProductDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/api/v1/products/search") {
            bearer(accessToken)
            parameter("q", query)
            parameter("limit", limit)
        }
    }

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<ProductDto?> = safeCall {
        httpClient.get("${config.apiBaseUrl}/api/v1/products/barcode/$barcode") {
            bearer(accessToken)
        }
    }

    override suspend fun openCurrentSaleDraft(accessToken: String, terminalCode: String): AppResult<DraftDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/api/v1/pos/drafts/current") {
            bearer(accessToken)
            parameter("type", "SALE")
            parameter("terminalCode", terminalCode)
            parameter("create", true)
        }
    }

    override suspend fun addDraftLine(accessToken: String, draftId: String, request: AddLineRequestDto): AppResult<DraftDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/api/v1/pos/drafts/$draftId/lines") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun updateDraftLine(accessToken: String, draftId: String, lineId: String, request: UpdateLineRequestDto): AppResult<DraftDto> = safeCall {
        httpClient.patch("${config.apiBaseUrl}/api/v1/pos/drafts/$draftId/lines/$lineId") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun removeDraftLine(accessToken: String, draftId: String, lineId: String, expectedVersion: Long): AppResult<DraftDto> = safeCall {
        httpClient.delete("${config.apiBaseUrl}/api/v1/pos/drafts/$draftId/lines/$lineId") {
            bearer(accessToken)
            parameter("expectedVersion", expectedVersion)
        }
    }

    override suspend fun validateDraft(accessToken: String, draftId: String): AppResult<ValidateDraftResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/api/v1/pos/drafts/$draftId/validate") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun resolveLots(accessToken: String, draftId: String): AppResult<DraftDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/api/v1/pos/drafts/$draftId/resolve-lots") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun checkoutDraft(
        accessToken: String,
        draftId: String,
        request: CheckoutRequestDto,
        idempotencyKey: String,
    ): AppResult<CheckoutResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/api/v1/pos/drafts/$draftId/checkout") {
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
