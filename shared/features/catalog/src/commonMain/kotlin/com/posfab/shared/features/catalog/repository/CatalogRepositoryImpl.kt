package com.posfab.shared.features.catalog.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.core.result.map
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.KitchenKind
import com.posfab.shared.features.catalog.domain.PriceType
import com.posfab.shared.features.catalog.domain.PricesInput
import com.posfab.shared.features.catalog.domain.ProductInput
import com.posfab.shared.features.catalog.domain.ProductPage
import com.posfab.shared.features.catalog.domain.ProductPrices
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.CreateProductRequestDto
import com.posfab.shared.network.catalog.ProductDetailDto
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.catalog.ProductPricesDto
import com.posfab.shared.network.catalog.SetProductPricesRequestDto
import com.posfab.shared.network.catalog.UpdateProductRequestDto

class CatalogRepositoryImpl(
    private val catalogApi: CatalogApi,
    private val executor: AuthorizedApiExecutor,
) : CatalogRepository {
    override suspend fun listProducts(query: String?, activeOnly: Boolean, offset: Int): AppResult<ProductPage> =
        authorized {
            catalogApi.listProducts(
                accessToken = it,
                query = query?.trim()?.ifBlank { null },
                limit = PAGE_LIMIT,
                offset = offset.coerceAtLeast(0),
                active = if (activeOnly) true else null,
            )
        }.map { page -> ProductPage(items = page.items.map { it.toSummary() }, total = page.total) }

    override suspend fun getProduct(productId: String): AppResult<CatalogProductDetail> =
        authorized { catalogApi.getProduct(it, productId) }.map { it.toDomain() }

    override suspend fun createProduct(input: ProductInput): AppResult<CatalogProductDetail> =
        authorized {
            catalogApi.createProduct(
                it,
                CreateProductRequestDto(
                    name = input.name,
                    sku = input.sku,
                    barcode = input.barcode,
                    baseUnit = input.priceType.baseUnit(),
                    lotTracked = input.lotTracked,
                    active = input.active,
                    priceType = input.priceType.name,
                    counted = input.counted,
                    piecesPerPack = input.piecesPerPack,
                    kitchenKind = input.kitchenKind?.name,
                ),
            )
        }.map { it.toDomain() }

    override suspend fun updateProduct(productId: String, input: ProductInput): AppResult<CatalogProductDetail> =
        authorized {
            catalogApi.updateProduct(
                it,
                productId,
                UpdateProductRequestDto(
                    name = input.name,
                    sku = input.sku,
                    barcode = input.barcode,
                    baseUnit = input.priceType.baseUnit(),
                    lotTracked = input.lotTracked,
                    active = input.active,
                    priceType = input.priceType.name,
                    counted = input.counted,
                    piecesPerPack = input.piecesPerPack ?: 0,
                    kitchenKind = input.kitchenKind?.name ?: "",
                ),
            )
        }.map { it.toDomain() }

    override suspend fun setPrices(productId: String, input: PricesInput): AppResult<CatalogProductDetail> =
        authorized {
            catalogApi.setPrices(
                it,
                productId,
                SetProductPricesRequestDto(
                    cashTakeaway = input.cashTakeaway.toDecimalString(),
                    listTakeaway = input.listTakeaway.toDecimalString(),
                    cashOnsite = input.cashOnsite?.toDecimalString(),
                    listOnsite = input.listOnsite?.toDecimalString(),
                    suggestedPrice = input.suggestedPrice,
                ),
            )
        }.map { it.toDomain() }

    override suspend fun assignInternalCode(productId: String): AppResult<CatalogProductDetail> =
        authorized { catalogApi.assignInternalCode(it, productId) }.map { it.toDomain() }

    override suspend fun findByBarcode(barcode: String): AppResult<CatalogProductDetail?> =
        when (val lookup = authorized { catalogApi.findByBarcode(it, barcode) }) {
            is AppResult.Success -> getProduct(lookup.value.product.id.toString())
            is AppResult.Failure ->
                if (lookup.error.isBarcodeNotFound()) AppResult.Success(null) else lookup
        }

    override suspend fun cashDiscountPct(): AppResult<Double> =
        when (val result = authorized { catalogApi.getBusinessConfig(it) }) {
            is AppResult.Success -> result.value.cashDiscountPct.toDoubleOrNull()
                ?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Unknown("cashDiscountPct"))
            is AppResult.Failure -> result
        }

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private fun AppError.isBarcodeNotFound(): Boolean =
        this is AppError.Validation && message.contains(BARCODE_NOT_FOUND, ignoreCase = true)

    private fun PriceType.baseUnit(): String = if (this == PriceType.WEIGHT) WEIGHT_UNIT else PIECE_UNIT

    private fun ProductListItemDto.toSummary() = CatalogProductSummary(
        id = id,
        name = name,
        barcode = barcode,
        priceType = PriceType.fromRaw(priceType),
        kitchenKind = KitchenKind.fromRaw(kitchenKind),
        active = active,
        cashTakeaway = prices?.cashTakeaway?.toDoubleOrNull() ?: currentPrice?.toDoubleOrNull(),
    )

    private fun ProductDetailDto.toDomain() = CatalogProductDetail(
        id = id,
        name = name,
        sku = sku,
        barcode = barcode,
        lotTracked = lotTracked,
        active = active,
        priceType = PriceType.fromRaw(priceType),
        counted = counted,
        piecesPerPack = piecesPerPack,
        kitchenKind = KitchenKind.fromRaw(kitchenKind),
        prices = prices?.toDomain(),
    )

    private fun ProductPricesDto.toDomain(): ProductPrices? {
        val cash = cashTakeaway.toDoubleOrNull() ?: return null
        return ProductPrices(
            cashTakeaway = cash,
            listTakeaway = listTakeaway.toDoubleOrNull() ?: cash,
            cashOnsite = cashOnsite?.toDoubleOrNull(),
            listOnsite = listOnsite?.toDoubleOrNull(),
            suggestedPrice = suggestedPrice,
        )
    }

    private fun Double.toDecimalString(): String = toBigDecimal().toPlainString()

    private companion object {
        const val PAGE_LIMIT = 50
        const val WEIGHT_UNIT = "KG"
        const val PIECE_UNIT = "PZA"
        const val BARCODE_NOT_FOUND = "Barcode not found"
    }
}
