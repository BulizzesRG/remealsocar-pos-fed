package com.posfab.shared.features.catalog.domain

data class CatalogProductSummary(
    val id: String,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val baseUnit: String,
    val lotTracked: Boolean,
    val active: Boolean,
    val currentPrice: Double?,
)

data class CatalogProductDetail(
    val id: String,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val baseUnit: String,
    val lotTracked: Boolean,
    val active: Boolean,
    val currentPrice: Double?,
    val uoms: List<ProductUomConversion>,
    val priceHistory: List<PriceHistoryEntry>,
)

data class ProductUomConversion(
    val unit: String,
    val factorToBase: Double,
)

data class PriceHistoryEntry(
    val productId: String,
    val price: Double,
    val effectiveAt: String,
)

data class CatalogUnit(
    val code: String,
    val name: String,
)

data class ProductPage(
    val items: List<CatalogProductSummary>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

data class CreateProductInput(
    val name: String,
    val sku: String?,
    val barcode: String?,
    val baseUnit: String,
    val lotTracked: Boolean,
    val active: Boolean,
)

data class UpdateProductInput(
    val name: String,
    val sku: String?,
    val barcode: String?,
    val baseUnit: String,
    val lotTracked: Boolean,
    val active: Boolean,
)
