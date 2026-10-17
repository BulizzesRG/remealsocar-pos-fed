package com.posfab.shared.network.cash

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import com.posfab.shared.network.http.safeCallOrNull
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

interface CashApi {
    suspend fun openSession(accessToken: String, request: OpenCashSessionRequestDto): AppResult<CashIdResponseDto>
    suspend fun closeSession(accessToken: String, request: CloseCashSessionRequestDto): AppResult<CashCloseResponseDto>
    suspend fun currentSession(accessToken: String, terminalId: Long): AppResult<CashSessionResponseDto?>
    suspend fun summary(accessToken: String, terminalId: Long): AppResult<CashSummaryResponseDto>
    suspend fun payout(
        accessToken: String,
        idempotencyKey: String,
        request: CashPayoutRequestDto,
    ): AppResult<CashPayoutResponseDto>
}

class CashApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : CashApi {
    override suspend fun openSession(
        accessToken: String,
        request: OpenCashSessionRequestDto,
    ): AppResult<CashIdResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/cash/sessions/open") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun closeSession(
        accessToken: String,
        request: CloseCashSessionRequestDto,
    ): AppResult<CashCloseResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/cash/sessions/close") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun currentSession(
        accessToken: String,
        terminalId: Long,
    ): AppResult<CashSessionResponseDto?> = safeCallOrNull {
        httpClient.get("${config.apiBaseUrl}/cash/sessions/current?terminal_id=$terminalId") {
            bearer(accessToken)
        }
    }

    override suspend fun summary(
        accessToken: String,
        terminalId: Long,
    ): AppResult<CashSummaryResponseDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/cash/sessions/summary") {
            bearer(accessToken)
            parameter("terminal_id", terminalId)
        }
    }

    override suspend fun payout(
        accessToken: String,
        idempotencyKey: String,
        request: CashPayoutRequestDto,
    ): AppResult<CashPayoutResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/cash/payouts") {
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
