package com.posfab.shared.network.pos

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.cash.CashSessionResponseDto
import com.posfab.shared.network.http.safeCall
import com.posfab.shared.network.http.safeCallOrNull
import io.ktor.client.HttpClient
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

interface PosApi {
    suspend fun currentCashSession(accessToken: String, terminalId: Long): AppResult<Boolean>
    suspend fun getCurrentSaleDraft(accessToken: String): AppResult<DraftDto>
    suspend fun createDraft(accessToken: String, request: DraftCreateRequestDto): AppResult<DraftCreateResponseDto>
    suspend fun getDraft(accessToken: String, draftId: Long): AppResult<DraftDto>
    suspend fun addDraftLine(accessToken: String, draftId: Long, request: AddLineRequestDto): AppResult<DraftDto>
    suspend fun updateDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        request: UpdateLineRequestDto,
    ): AppResult<DraftDto>
    suspend fun removeDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        expectedVersion: Int,
    ): AppResult<DraftDto>
    suspend fun validateDraft(accessToken: String, draftId: Long): AppResult<ValidateDraftResponseDto>
    suspend fun resolveLots(accessToken: String, draftId: Long): AppResult<ResolveLotsResponseDto>
    suspend fun checkoutDraft(
        accessToken: String,
        draftId: Long,
        request: CheckoutRequestDto,
    ): AppResult<CheckoutResponseDto>
    suspend fun cancelDraft(accessToken: String, draftId: Long, request: CancelDraftRequestDto): AppResult<Unit>
    suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto>
}

class PosApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : PosApi {
    override suspend fun currentCashSession(accessToken: String, terminalId: Long): AppResult<Boolean> {
        val result = safeCallOrNull<CashSessionResponseDto> {
            httpClient.get("${config.apiBaseUrl}/cash/sessions/current") {
                bearer(accessToken)
                parameter("terminal_id", terminalId)
            }
        }
        return when (result) {
            is AppResult.Success -> AppResult.Success(result.value != null)
            is AppResult.Failure -> result
        }
    }

    override suspend fun getCurrentSaleDraft(accessToken: String): AppResult<DraftDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/pos/drafts/current") {
            bearer(accessToken)
            parameter("type", "SALE")
        }
    }

    override suspend fun createDraft(accessToken: String, request: DraftCreateRequestDto): AppResult<DraftCreateResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/pos/drafts") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun getDraft(accessToken: String, draftId: Long): AppResult<DraftDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/pos/drafts/$draftId") {
            bearer(accessToken)
        }
    }

    override suspend fun addDraftLine(accessToken: String, draftId: Long, request: AddLineRequestDto): AppResult<DraftDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/pos/drafts/$draftId/lines") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun updateDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        request: UpdateLineRequestDto,
    ): AppResult<DraftDto> = safeCall {
        httpClient.put("${config.apiBaseUrl}/pos/drafts/$draftId/lines/$lineId") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun removeDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        expectedVersion: Int,
    ): AppResult<DraftDto> = safeCall {
        httpClient.delete("${config.apiBaseUrl}/pos/drafts/$draftId/lines/$lineId") {
            bearer(accessToken)
            parameter("expectedVersion", expectedVersion)
        }
    }

    override suspend fun validateDraft(accessToken: String, draftId: Long): AppResult<ValidateDraftResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/pos/drafts/$draftId/validate") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun resolveLots(accessToken: String, draftId: Long): AppResult<ResolveLotsResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/pos/drafts/$draftId/resolve-lots") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun checkoutDraft(
        accessToken: String,
        draftId: Long,
        request: CheckoutRequestDto,
    ): AppResult<CheckoutResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/pos/drafts/$draftId/checkout") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun cancelDraft(
        accessToken: String,
        draftId: Long,
        request: CancelDraftRequestDto,
    ): AppResult<Unit> = safeCall {
        httpClient.post("${config.apiBaseUrl}/pos/drafts/$draftId/cancel") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/products/by-barcode") {
            bearer(accessToken)
            parameter("barcode", barcode)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
