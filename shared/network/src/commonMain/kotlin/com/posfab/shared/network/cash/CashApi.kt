package com.posfab.shared.network.cash

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import com.posfab.shared.network.http.safeCallOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

interface CashApi {
    suspend fun openSession(accessToken: String, request: OpenCashSessionRequestDto): AppResult<CashIdResponseDto>
    suspend fun closeSession(accessToken: String, request: CloseCashSessionRequestDto): AppResult<CashSessionResponseDto>
    suspend fun currentSession(accessToken: String, terminalId: Long): AppResult<CashSessionResponseDto?>
    suspend fun dailyReport(accessToken: String, date: String, terminalId: Long): AppResult<DailyCashReportResponseDto>
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
    ): AppResult<CashSessionResponseDto> = safeCall {
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

    override suspend fun dailyReport(
        accessToken: String,
        date: String,
        terminalId: Long,
    ): AppResult<DailyCashReportResponseDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/cash/reports/daily?date=$date&terminal_id=$terminalId") {
            bearer(accessToken)
        }
    }

    private fun HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
