package com.posfab.shared.features.catalog.ui

import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.KitchenKind
import com.posfab.shared.features.catalog.domain.PriceHistory
import com.posfab.shared.features.catalog.domain.PriceType

enum class CatalogFormMode { IDLE, CREATING, EDITING }

sealed interface PendingAction {
    data object Cancel : PendingAction
    data class Select(val id: String) : PendingAction
    data object Create : PendingAction
    data class ScanOpen(val detail: CatalogProductDetail) : PendingAction
}

sealed interface CatalogConfirm {
    data object Save : CatalogConfirm
    data class Discard(val next: PendingAction) : CatalogConfirm
}

data class ProductForm(
    val name: String = "",
    val barcode: String = "",
    val priceType: PriceType = PriceType.PIECE,
    val cashTakeaway: String = "",
    val listTakeaway: String = "",
    val cashOnsite: String = "",
    val listOnsite: String = "",
    val listTakeawayEdited: Boolean = false,
    val listOnsiteEdited: Boolean = false,
    val suggestedPrice: Boolean = false,
    val kitchenKind: KitchenKind? = null,
    val counted: Boolean = false,
    val piecesPerPack: String = "",
    val active: Boolean = true,
    val sku: String? = null,
    val lotTracked: Boolean = false,
    val generateCodeOnSave: Boolean = false,
)

data class CatalogState(
    val query: String = "",
    val isLoadingList: Boolean = false,
    val products: List<CatalogProductSummary> = emptyList(),
    val total: Int = 0,
    val activeOnly: Boolean = true,
    val mode: CatalogFormMode = CatalogFormMode.IDLE,
    val selected: CatalogProductDetail? = null,
    val form: ProductForm = ProductForm(),
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val isGeneratingCode: Boolean = false,
    val cashDiscountPct: Double = 5.0,
    val showCountSection: Boolean = false,
    val priceHistory: PriceHistory? = null,
    val isLoadingPriceHistory: Boolean = false,
    val priceHistoryError: String? = null,
    val showPriceHistory: Boolean = false,
    val notice: String? = null,
    val errorMessage: String? = null,
    val isLoadingMore: Boolean = false,
    val confirm: CatalogConfirm? = null,
) {
    val canLoadMore: Boolean get() = products.size < total
}
