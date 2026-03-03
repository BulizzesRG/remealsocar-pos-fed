package com.posfab.shared.network.catalog

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

interface CatalogApi {
    suspend fun listProducts(
        accessToken: String,
        query: String?,
        limit: Int,
        offset: Int,
        active: Boolean?,
    ): AppResult<ProductListResponseDto>

    suspend fun createProduct(accessToken: String, request: CreateProductRequestDto): AppResult<ProductDetailDto>
    suspend fun getProduct(accessToken: String, productId: String): AppResult<ProductDetailDto>
    suspend fun updateProduct(accessToken: String, productId: String, request: UpdateProductRequestDto): AppResult<ProductDetailDto>
    suspend fun addProductUom(accessToken: String, productId: String, request: AddProductUomRequestDto): AppResult<Unit>
    suspend fun listUnits(accessToken: String): AppResult<List<UnitDto>>
    suspend fun addPriceHistory(accessToken: String, request: CreatePriceHistoryRequestDto): AppResult<Unit>
    suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<ProductDetailDto?>
}

class CatalogApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : CatalogApi {
    override suspend fun listProducts(
        accessToken: String,
        query: String?,
        limit: Int,
        offset: Int,
        active: Boolean?,
    ): AppResult<ProductListResponseDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/products") {
            bearer(accessToken)
            query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
            parameter("limit", limit)
            parameter("offset", offset)
            active?.let { parameter("active", it) }
        }
    }

    override suspend fun createProduct(accessToken: String, request: CreateProductRequestDto): AppResult<ProductDetailDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/products") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun getProduct(accessToken: String, productId: String): AppResult<ProductDetailDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/products/$productId") {
            bearer(accessToken)
        }
    }

    override suspend fun updateProduct(accessToken: String, productId: String, request: UpdateProductRequestDto): AppResult<ProductDetailDto> = safeCall {
        httpClient.put("${config.apiBaseUrl}/products/$productId") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun addProductUom(accessToken: String, productId: String, request: AddProductUomRequestDto): AppResult<Unit> = safeCall {
        httpClient.post("${config.apiBaseUrl}/products/$productId/uoms") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun listUnits(accessToken: String): AppResult<List<UnitDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/units") {
            bearer(accessToken)
        }
    }

    override suspend fun addPriceHistory(accessToken: String, request: CreatePriceHistoryRequestDto): AppResult<Unit> = safeCall {
        httpClient.post("${config.apiBaseUrl}/price-history") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<ProductDetailDto?> = safeCall {
        httpClient.get("${config.apiBaseUrl}/products/by-barcode") {
            bearer(accessToken)
            parameter("barcode", barcode)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}
