package com.posfab.shared.network.customers

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

interface CustomersApi {
    suspend fun search(accessToken: String, query: String?, limit: Int): AppResult<List<CustomerDto>>
    suspend fun get(accessToken: String, customerId: Long): AppResult<CustomerDto>
    suspend fun create(accessToken: String, request: CreateCustomerRequestDto): AppResult<CustomerDto>
    suspend fun recordPayment(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: CustomerPaymentRequestDto,
    ): AppResult<CustomerPaymentResponseDto>
    suspend fun loadOpeningBalance(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: OpeningBalanceRequestDto,
    ): AppResult<OpeningBalanceResponseDto>
    suspend fun debtors(accessToken: String): AppResult<List<DebtorDto>>
    suspend fun ledger(accessToken: String, customerId: Long, limit: Int): AppResult<CustomerLedgerDto>
}

class CustomersApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : CustomersApi {
    override suspend fun search(
        accessToken: String,
        query: String?,
        limit: Int,
    ): AppResult<List<CustomerDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/customers") {
            bearer(accessToken)
            query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
            parameter("limit", limit)
        }
    }

    override suspend fun get(
        accessToken: String,
        customerId: Long,
    ): AppResult<CustomerDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/customers/$customerId") {
            bearer(accessToken)
        }
    }

    override suspend fun create(
        accessToken: String,
        request: CreateCustomerRequestDto,
    ): AppResult<CustomerDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/customers") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun recordPayment(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: CustomerPaymentRequestDto,
    ): AppResult<CustomerPaymentResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/customers/$customerId/payments") {
            bearer(accessToken)
            header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun loadOpeningBalance(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: OpeningBalanceRequestDto,
    ): AppResult<OpeningBalanceResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/customers/$customerId/opening-balance") {
            bearer(accessToken)
            header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun debtors(accessToken: String): AppResult<List<DebtorDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/customers/debtors") {
            bearer(accessToken)
        }
    }

    override suspend fun ledger(
        accessToken: String,
        customerId: Long,
        limit: Int,
    ): AppResult<CustomerLedgerDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/customers/$customerId/ledger") {
            bearer(accessToken)
            parameter("limit", limit)
        }
    }

    private fun HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }

    private companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}
