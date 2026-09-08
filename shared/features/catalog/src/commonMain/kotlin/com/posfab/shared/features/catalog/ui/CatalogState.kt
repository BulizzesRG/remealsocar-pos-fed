package com.posfab.shared.features.catalog.ui

import com.posfab.shared.features.catalog.domain.BusinessUnit
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.CatalogUnit

enum class ActiveFilter {
    ALL,
    ACTIVE_ONLY,
    INACTIVE_ONLY,
}

enum class CatalogFormMode { IDLE, CREATING, EDITING }

data class ProductFormState(
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val baseUnit: String = "EA",
    val lotTracked: Boolean = false,
    val active: Boolean = true,
    val initialPrice: String = "",
)

data class CatalogState(
    val isLoadingList: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isLoadingUnits: Boolean = false,
    val isSaving: Boolean = false,
    val formMode: CatalogFormMode = CatalogFormMode.IDLE,
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
    val isLoadingBusinessUnits: Boolean = false,
    val businessUnits: List<BusinessUnit> = emptyList(),
    /** Business unit that receives new prices. Defaults to the terminal's unit. */
    val selectedBusinessUnitId: Long? = null,
    val newUomUnit: String = "",
    val newUomFactorInput: String = "1",
    val newPriceInput: String = "",
    val barcodeCheckInput: String = "",
    val barcodeCheckResult: CatalogProductSummary? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
    val showQuickAddDialog: Boolean = false,
    val quickAddName: String = "",
    val quickAddPrice: String = "",
    val quickAddUnit: String = "EA",
    val isQuickAdding: Boolean = false,
    val showLotTrackingWarning: Boolean = false,
    val showAdvancedFields: Boolean = false,
) {
    val canGoPrevPage: Boolean get() = offset > 0
    val canGoNextPage: Boolean get() = (offset + products.size) < total
    val paginationText: String get() {
        if (total == 0) return "Sin resultados"
        val first = offset + 1
        val last = (offset + products.size).coerceAtMost(total)
        return "Mostrando $first\u2013$last de $total productos"
    }
}
