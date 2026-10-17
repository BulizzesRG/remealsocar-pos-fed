package com.posfab.shared.features.catalog.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.PriceHistory
import com.posfab.shared.features.catalog.domain.PricesInput
import com.posfab.shared.features.catalog.domain.ProductInput
import com.posfab.shared.features.catalog.domain.ProductPage

interface CatalogRepository {
    suspend fun listProducts(query: String?, activeOnly: Boolean, offset: Int): AppResult<ProductPage>
    suspend fun getProduct(productId: String): AppResult<CatalogProductDetail>
    suspend fun createProduct(input: ProductInput): AppResult<CatalogProductDetail>
    suspend fun updateProduct(productId: String, input: ProductInput): AppResult<CatalogProductDetail>
    suspend fun setPrices(productId: String, input: PricesInput): AppResult<CatalogProductDetail>
    suspend fun getPriceHistory(productId: String): AppResult<PriceHistory>
    suspend fun assignInternalCode(productId: String): AppResult<CatalogProductDetail>
    suspend fun findByBarcode(barcode: String): AppResult<CatalogProductDetail?>
    suspend fun cashDiscountPct(): AppResult<Double>
}
