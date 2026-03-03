package com.posfab.shared.features.catalog.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.CatalogUnit
import com.posfab.shared.features.catalog.domain.CreateProductInput
import com.posfab.shared.features.catalog.domain.PriceHistoryEntry
import com.posfab.shared.features.catalog.domain.ProductPage
import com.posfab.shared.features.catalog.domain.ProductUomConversion
import com.posfab.shared.features.catalog.domain.UpdateProductInput
import com.posfab.shared.network.catalog.AddProductUomRequestDto
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.CreatePriceHistoryRequestDto
import com.posfab.shared.network.catalog.CreateProductRequestDto
import com.posfab.shared.network.catalog.PriceHistoryDto
import com.posfab.shared.network.catalog.ProductDetailDto
import com.posfab.shared.network.catalog.ProductListResponseDto
import com.posfab.shared.network.catalog.ProductUomDto
import com.posfab.shared.network.catalog.UnitDto
import com.posfab.shared.network.catalog.UpdateProductRequestDto

class CatalogRepositoryImpl(
    private val catalogApi: CatalogApi,
    private val executor: AuthorizedApiExecutor,
) : CatalogRepository {
    override suspend fun listProducts(query: String?, limit: Int, offset: Int, active: Boolean?): AppResult<ProductPage> {
        return when (val result = authorized { catalogApi.listProducts(it, query, limit, offset, active) }) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun createProduct(input: CreateProductInput): AppResult<CatalogProductDetail> {
        return when (
            val result = authorized {
                catalogApi.createProduct(
                    it,
                    CreateProductRequestDto(
                        name = input.name,
                        sku = input.sku,
                        barcode = input.barcode,
                        baseUnit = input.baseUnit,
                        lotTracked = input.lotTracked,
                        active = input.active,
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun getProduct(productId: String): AppResult<CatalogProductDetail> {
        return when (val result = authorized { catalogApi.getProduct(it, productId) }) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun updateProduct(productId: String, input: UpdateProductInput): AppResult<CatalogProductDetail> {
        return when (
            val result = authorized {
                catalogApi.updateProduct(
                    it,
                    productId,
                    UpdateProductRequestDto(
                        name = input.name,
                        sku = input.sku,
                        barcode = input.barcode,
                        baseUnit = input.baseUnit,
                        lotTracked = input.lotTracked,
                        active = input.active,
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun addUom(productId: String, unit: String, factorToBase: Double): AppResult<Unit> =
        authorized {
            catalogApi.addProductUom(
                accessToken = it,
                productId = productId,
                request = AddProductUomRequestDto(unit = unit, factorToBase = factorToBase),
            )
        }

    override suspend fun listUnits(): AppResult<List<CatalogUnit>> {
        return when (val result = authorized { catalogApi.listUnits(it) }) {
            is AppResult.Success -> AppResult.Success(result.value.map { it.toDomainUnit() })
            is AppResult.Failure -> result
        }
    }

    override suspend fun addPrice(productId: String, price: Double): AppResult<Unit> =
        authorized {
            catalogApi.addPriceHistory(
                accessToken = it,
                request = CreatePriceHistoryRequestDto(productId = productId, price = price),
            )
        }

    override suspend fun findByBarcode(barcode: String): AppResult<CatalogProductDetail?> {
        return when (val result = authorized { catalogApi.findByBarcode(it, barcode) }) {
            is AppResult.Success -> AppResult.Success(result.value?.toDomain())
            is AppResult.Failure -> result
        }
    }

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private fun ProductListResponseDto.toDomain() = ProductPage(
        items = items.map {
            CatalogProductSummary(
                id = it.id,
                name = it.name,
                sku = it.sku,
                barcode = it.barcode,
                baseUnit = it.baseUnit,
                lotTracked = it.lotTracked,
                active = it.active,
                currentPrice = it.currentPrice,
            )
        },
        total = total,
        limit = limit,
        offset = offset,
    )

    private fun ProductDetailDto.toDomain() = CatalogProductDetail(
        id = id,
        name = name,
        sku = sku,
        barcode = barcode,
        baseUnit = baseUnit,
        lotTracked = lotTracked,
        active = active,
        currentPrice = currentPrice,
        uoms = uoms.map { it.toDomainUom() },
        priceHistory = priceHistory.map { it.toDomainPriceHistory() },
    )

    private fun ProductUomDto.toDomainUom() = ProductUomConversion(
        unit = unit,
        factorToBase = factorToBase,
    )

    private fun PriceHistoryDto.toDomainPriceHistory() = PriceHistoryEntry(
        productId = productId,
        price = price,
        effectiveAt = effectiveAt,
    )

    private fun UnitDto.toDomainUnit() = CatalogUnit(
        code = code,
        name = name,
    )
}
