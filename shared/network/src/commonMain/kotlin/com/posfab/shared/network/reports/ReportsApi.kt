package com.posfab.shared.network.reports

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

interface ReportsApi {
    suspend fun dailySales(accessToken: String, date: String, terminalId: String): AppResult<List<SaleHistoryItemDto>>
    suspend fun dailyTotals(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<DailySalesTotalsDto>>
    suspend fun topDebtors(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<DebtorItemDto>>
    suspend fun wasteTotals(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<WasteItemDto>>
    suspend fun integrityCheck(accessToken: String): AppResult<IntegrityCheckDto>
}

class ReportsApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : ReportsApi {
    override suspend fun dailySales(accessToken: String, date: String, terminalId: String): AppResult<List<SaleHistoryItemDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/sales") {
            bearer(accessToken)
            parameter("date", date)
            parameter("terminal_id", terminalId)
        }
    }

    override suspend fun dailyTotals(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<DailySalesTotalsDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/reports/daily-totals") {
            bearer(accessToken)
            parameter("date_from", dateFrom)
            parameter("date_to", dateTo)
        }
    }

    override suspend fun topDebtors(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<DebtorItemDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/reports/debtors") {
            bearer(accessToken)
            parameter("date_from", dateFrom)
            parameter("date_to", dateTo)
        }
    }

    override suspend fun wasteTotals(accessToken: String, dateFrom: String, dateTo: String): AppResult<List<WasteItemDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/reports/waste") {
            bearer(accessToken)
            parameter("date_from", dateFrom)
            parameter("date_to", dateTo)
        }
    }

    override suspend fun integrityCheck(accessToken: String): AppResult<IntegrityCheckDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/admin/integrity-check") {
            bearer(accessToken)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
