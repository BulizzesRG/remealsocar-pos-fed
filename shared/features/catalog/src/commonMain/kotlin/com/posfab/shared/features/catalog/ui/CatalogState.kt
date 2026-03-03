package com.posfab.shared.features.catalog.ui

import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.CatalogUnit

enum class ActiveFilter {
    ALL,
    ACTIVE_ONLY,
    INACTIVE_ONLY,
}

data class ProductFormState(
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val baseUnit: String = "EA",
    val lotTracked: Boolean = false,
    val active: Boolean = true,
)

data class CatalogState(
    val isLoadingList: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isLoadingUnits: Boolean = false,
    val isSaving: Boolean = false,
    val query: String = "",
    val limit: Int = 20,
    val offset: Int = 0,
    val activeFilter: ActiveFilter = ActiveFilter.ALL,
    val total: Int = 0,
    val products: List<CatalogProductSummary> = emptyList(),
    val selectedProduct: CatalogProductDetail? = null,
    val form: ProductFormState = ProductFormState(),
    val hasUnsavedChanges: Boolean = false,
    val units: List<CatalogUnit> = emptyList(),
    val newUomUnit: String = "",
    val newUomFactorInput: String = "1",
    val newPriceInput: String = "",
    val barcodeCheckInput: String = "",
    val barcodeCheckResult: CatalogProductSummary? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
) {
    val canGoPrevPage: Boolean get() = offset > 0
    val canGoNextPage: Boolean get() = (offset + products.size) < total
}
