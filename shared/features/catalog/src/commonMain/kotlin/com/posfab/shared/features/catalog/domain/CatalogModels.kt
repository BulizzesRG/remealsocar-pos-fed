package com.posfab.shared.features.catalog.domain

enum class PriceType {
    PIECE,
    WEIGHT,
    OPEN,
    ;

    companion object {
        fun fromRaw(raw: String?): PriceType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PIECE
    }
}

enum class KitchenKind {
    FONDA,
    TORTERIA,
    ;

    companion object {
        fun fromRaw(raw: String?): KitchenKind? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class ProductPrices(
    val cashTakeaway: Double,
    val listTakeaway: Double,
    val cashOnsite: Double?,
    val listOnsite: Double?,
    val suggestedPrice: Boolean,
)

data class CatalogProductSummary(
    val id: String,
    val name: String,
    val barcode: String?,
    val priceType: PriceType,
    val kitchenKind: KitchenKind?,
    val active: Boolean,
    val cashTakeaway: Double?,
)

data class CatalogProductDetail(
    val id: String,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val lotTracked: Boolean,
    val active: Boolean,
    val priceType: PriceType,
    val counted: Boolean,
    val piecesPerPack: Int?,
    val kitchenKind: KitchenKind?,
    val prices: ProductPrices?,
)

data class ProductPage(
    val items: List<CatalogProductSummary>,
    val total: Int,
)

data class ProductInput(
    val name: String,
    val barcode: String?,
    val priceType: PriceType,
    val counted: Boolean,
    val piecesPerPack: Int?,
    val kitchenKind: KitchenKind?,
    val active: Boolean,
    val sku: String?,
    val lotTracked: Boolean,
)

data class PricesInput(
    val cashTakeaway: Double,
    val listTakeaway: Double,
    val cashOnsite: Double?,
    val listOnsite: Double?,
    val suggestedPrice: Boolean,
)
