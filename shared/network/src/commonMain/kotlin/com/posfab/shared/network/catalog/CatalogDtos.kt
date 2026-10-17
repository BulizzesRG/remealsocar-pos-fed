package com.posfab.shared.network.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductPricesDto(
    @SerialName("cashTakeaway") val cashTakeaway: String,
    @SerialName("listTakeaway") val listTakeaway: String,
    @SerialName("cashOnsite") val cashOnsite: String? = null,
    @SerialName("listOnsite") val listOnsite: String? = null,
    @SerialName("suggestedPrice") val suggestedPrice: Boolean = false,
)

@Serializable
data class ProductListItemDto(
    val id: String,
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    @SerialName("baseUnit") val baseUnit: String,
    @SerialName("baseUnitId") val baseUnitId: Long,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean,
    @SerialName("currentPrice") val currentPrice: String? = null,
    @SerialName("priceType") val priceType: String = "PIECE",
    val counted: Boolean = false,
    @SerialName("piecesPerPack") val piecesPerPack: Int? = null,
    @SerialName("kitchenKind") val kitchenKind: String? = null,
    val prices: ProductPricesDto? = null,
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
    @SerialName("baseUnitId") val baseUnitId: Long,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean,
    @SerialName("currentPrice") val currentPrice: String? = null,
    @SerialName("priceType") val priceType: String = "PIECE",
    val counted: Boolean = false,
    @SerialName("piecesPerPack") val piecesPerPack: Int? = null,
    @SerialName("kitchenKind") val kitchenKind: String? = null,
    val prices: ProductPricesDto? = null,
    val uoms: List<ProductUomDto> = emptyList(),
)

@Serializable
data class ProductUomDto(
    val unit: String,
    @SerialName("factorToBase") val factorToBase: String,
)

@Serializable
data class UnitDto(
    val id: Long,
    val code: String,
    val name: String,
    val active: Boolean,
)

@Serializable
data class CreateProductRequestDto(
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("baseUnit") val baseUnit: String,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean = true,
    @SerialName("priceType") val priceType: String,
    val counted: Boolean,
    @SerialName("piecesPerPack") val piecesPerPack: Int? = null,
    @SerialName("kitchenKind") val kitchenKind: String? = null,
)

@Serializable
data class UpdateProductRequestDto(
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("baseUnit") val baseUnit: String,
    @SerialName("lotTracked") val lotTracked: Boolean,
    val active: Boolean,
    @SerialName("priceType") val priceType: String,
    val counted: Boolean,
    @SerialName("piecesPerPack") val piecesPerPack: Int?,
    @SerialName("kitchenKind") val kitchenKind: String?,
)

@Serializable
data class SetProductPricesRequestDto(
    @SerialName("cashTakeaway") val cashTakeaway: String,
    @SerialName("listTakeaway") val listTakeaway: String? = null,
    @SerialName("cashOnsite") val cashOnsite: String? = null,
    @SerialName("listOnsite") val listOnsite: String? = null,
    @SerialName("suggestedPrice") val suggestedPrice: Boolean,
)

@Serializable
data class BusinessConfigDto(
    @SerialName("cashDiscountPct") val cashDiscountPct: String,
    val timezone: String,
)

@Serializable
data class BusinessUnitDto(
    val id: Long,
    val code: String,
    val name: String,
    val active: Boolean = true,
)

@Serializable
data class PriceHistoryEntryDto(
    @SerialName("cashTakeaway") val cashTakeaway: String,
    @SerialName("listTakeaway") val listTakeaway: String,
    @SerialName("cashOnsite") val cashOnsite: String? = null,
    @SerialName("listOnsite") val listOnsite: String? = null,
    @SerialName("suggestedPrice") val suggestedPrice: Boolean = false,
    @SerialName("validFrom") val validFrom: String,
    @SerialName("validUntil") val validUntil: String,
    @SerialName("changedBy") val changedBy: String,
)

@Serializable
data class PriceHistoryResponseDto(
    @SerialName("currentSince") val currentSince: String? = null,
    val items: List<PriceHistoryEntryDto> = emptyList(),
)
