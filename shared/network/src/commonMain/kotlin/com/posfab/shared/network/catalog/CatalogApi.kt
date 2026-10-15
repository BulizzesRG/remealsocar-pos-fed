package com.posfab.shared.network.catalog

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import com.posfab.shared.network.pos.BarcodeLookupResponseDto
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
    suspend fun setPrices(accessToken: String, productId: String, request: SetProductPricesRequestDto): AppResult<ProductDetailDto>
    suspend fun assignInternalCode(accessToken: String, productId: String): AppResult<ProductDetailDto>
    suspend fun getBusinessConfig(accessToken: String): AppResult<BusinessConfigDto>
    suspend fun listUnits(accessToken: String): AppResult<List<UnitDto>>
    suspend fun listBusinessUnits(accessToken: String): AppResult<List<BusinessUnitDto>>
    suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto>
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

    override suspend fun setPrices(
        accessToken: String,
        productId: String,
        request: SetProductPricesRequestDto,
    ): AppResult<ProductDetailDto> = safeCall {
        httpClient.put("${config.apiBaseUrl}/products/$productId/prices") {
            bearer(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun assignInternalCode(accessToken: String, productId: String): AppResult<ProductDetailDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/products/$productId/internal-code") {
            bearer(accessToken)
        }
    }

    override suspend fun getBusinessConfig(accessToken: String): AppResult<BusinessConfigDto> = safeCall {
        httpClient.get("${config.apiBaseUrl}/config/business") {
            bearer(accessToken)
        }
    }

    override suspend fun listUnits(accessToken: String): AppResult<List<UnitDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/units") {
            bearer(accessToken)
        }
    }

    override suspend fun listBusinessUnits(accessToken: String): AppResult<List<BusinessUnitDto>> = safeCall {
        httpClient.get("${config.apiBaseUrl}/business-units") {
            bearer(accessToken)
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
