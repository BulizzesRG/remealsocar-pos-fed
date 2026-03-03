package com.posfab.shared.network.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductListItemDto(
    val id: String,
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    @SerialName("baseUnit") val baseUnit: String,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean,
    @SerialName("currentPrice") val currentPrice: Double? = null,
)

@Serializable
data class ProductListResponseDto(
    val items: List<ProductListItemDto> = emptyList(),
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class ProductDetailDto(
    val id: String,
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    @SerialName("baseUnit") val baseUnit: String,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean,
    @SerialName("currentPrice") val currentPrice: Double? = null,
    val uoms: List<ProductUomDto> = emptyList(),
    @SerialName("priceHistory") val priceHistory: List<PriceHistoryDto> = emptyList(),
)

@Serializable
data class ProductUomDto(
    val unit: String,
    @SerialName("factorToBase") val factorToBase: Double,
)

@Serializable
data class PriceHistoryDto(
    @SerialName("productId") val productId: String,
    val price: Double,
    @SerialName("effectiveAt") val effectiveAt: String,
)

@Serializable
data class UnitDto(
    val code: String,
    val name: String,
)

@Serializable
data class CreateProductRequestDto(
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("baseUnit") val baseUnit: String,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean = true,
)

@Serializable
data class UpdateProductRequestDto(
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("baseUnit") val baseUnit: String,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean,
)

@Serializable
data class AddProductUomRequestDto(
    val unit: String,
    @SerialName("factorToBase") val factorToBase: Double,
)

@Serializable
data class CreatePriceHistoryRequestDto(
    @SerialName("productId") val productId: String,
    val price: Double,
)
