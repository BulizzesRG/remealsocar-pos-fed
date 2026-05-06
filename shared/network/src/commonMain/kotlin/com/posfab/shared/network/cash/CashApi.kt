package com.posfab.shared.network.cash

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
    ): AppResult<CashSessionResponseDto?> {
        return try {
            safeCall<CashSessionResponseDto> {
                httpClient.get("${config.apiBaseUrl}/cash/sessions/current?terminal_id=$terminalId") {
                    bearer(accessToken)
                }
            }.let { result ->
                when (result) {
                    is AppResult.Success -> AppResult.Success(result.value)
                    is AppResult.Failure -> result
                }
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                AppResult.Success(null)
            } else {
                AppResult.Failure(com.posfab.shared.network.http.ApiErrorMapper.fromStatus(e.response.status))
            }
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

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
