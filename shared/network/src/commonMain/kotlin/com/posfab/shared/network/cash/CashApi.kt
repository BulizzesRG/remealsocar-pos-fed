package com.posfab.shared.network.cash

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

interface CashApi {
    suspend fun openSession(accessToken: String, request: OpenCashSessionRequestDto): AppResult<CashSessionDto>
    suspend fun closeSession(accessToken: String, request: CloseCashSessionRequestDto): AppResult<CashSessionDto>
    suspend fun currentSession(accessToken: String, terminalId: String): AppResult<CashSessionDto?>
    suspend fun dailyReport(accessToken: String, date: String, terminalId: String): AppResult<DailyCashReportDto>
}

class CashApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : CashApi {
    override suspend fun openSession(accessToken: String, request: OpenCashSessionRequestDto): AppResult<CashSessionDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/cash/sessions/open") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun closeSession(accessToken: String, request: CloseCashSessionRequestDto): AppResult<CashSessionDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/cash/sessions/close") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun currentSession(accessToken: String, terminalId: String): AppResult<CashSessionDto?> = safeCall {
        httpClient.get("${config.apiBaseUrl}/cash/sessions/current") {
            bearer(accessToken)
            parameter("terminal_id", terminalId)
        }
    }

    override suspend fun dailyReport(accessToken: String, date: String, terminalId: String): AppResult<DailyCashReportDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/cash/reports/daily") {
            bearer(accessToken)
            parameter("date", date)
            parameter("terminal_id", terminalId)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
