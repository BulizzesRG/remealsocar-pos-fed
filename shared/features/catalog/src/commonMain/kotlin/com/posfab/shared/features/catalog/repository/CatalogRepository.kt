package com.posfab.shared.features.catalog.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogUnit
import com.posfab.shared.features.catalog.domain.CreateProductInput
import com.posfab.shared.features.catalog.domain.ProductPage
import com.posfab.shared.features.catalog.domain.UpdateProductInput

interface CatalogRepository {
    suspend fun listProducts(query: String?, limit: Int, offset: Int, active: Boolean?): AppResult<ProductPage>
    suspend fun createProduct(input: CreateProductInput): AppResult<CatalogProductDetail>
    suspend fun getProduct(productId: String): AppResult<CatalogProductDetail>
    suspend fun updateProduct(productId: String, input: UpdateProductInput): AppResult<CatalogProductDetail>
    suspend fun addUom(productId: String, unit: String, factorToBase: Double): AppResult<Unit>
    suspend fun listUnits(): AppResult<List<CatalogUnit>>
    suspend fun addPrice(productId: String, price: Double): AppResult<Unit>
    suspend fun findByBarcode(barcode: String): AppResult<CatalogProductDetail?>
}
