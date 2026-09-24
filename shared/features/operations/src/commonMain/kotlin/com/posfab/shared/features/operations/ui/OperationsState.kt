package com.posfab.shared.features.operations.ui

import com.posfab.shared.features.operations.domain.AdjustmentReason
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.InventoryOnHandItem
import com.posfab.shared.features.operations.domain.OperationsBusinessUnit
import com.posfab.shared.features.operations.domain.OperationsProduct
import com.posfab.shared.features.operations.domain.ProductUnitOption
import com.posfab.shared.features.operations.domain.PurchaseLineInput
import com.posfab.shared.features.operations.domain.RequisitionLineInput
import com.posfab.shared.features.operations.domain.Supplier
import com.posfab.shared.features.operations.domain.WasteReason

enum class OperationsTab {
    PURCHASES,
    INTERNAL_REQ,
    ON_HAND,
    LOTS,
    WASTE,
    ADJUSTMENTS,
}

enum class OperationsConfirmAction {
    PURCHASE,
    REQUISITION,
    WASTE,
    ADJUSTMENT,
}

data class OperationsConfirmDialogState(
    val action: OperationsConfirmAction,
    val title: String,
    val message: String,
    val confirmLabel: String = "Confirmar",
    val cancelLabel: String = "Cancelar",
)

/**
 * Product picker shared by the tab that is currently visible: search, selected product,
 * the units it can be captured in and (for lot-tracked products) its lots.
 */
data class ProductPickerState(
    val query: String = "",
    val results: List<OperationsProduct> = emptyList(),
    val isSearching: Boolean = false,
    val selectedProduct: OperationsProduct? = null,
    val unitOptions: List<ProductUnitOption> = emptyList(),
    val selectedUnit: ProductUnitOption? = null,
    val isLoadingUnits: Boolean = false,
    val lots: List<InventoryLotItem> = emptyList(),
    val selectedLot: InventoryLotItem? = null,
    val isLoadingLots: Boolean = false,
)

data class OperationsState(
    val selectedTab: OperationsTab = OperationsTab.PURCHASES,
    /** ADMIN or MANAGER: the backend requires one of these roles for every mutation here. */
    val canManageOperations: Boolean = false,
    val terminalId: Long = 0L,
    val terminalCode: String = "",
    /** Business unit of the registered device (normally TIENDA). */
    val defaultBusinessUnitId: Long = 0L,

    val isLoadingReferenceData: Boolean = false,
    val businessUnits: List<OperationsBusinessUnit> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),

    val picker: ProductPickerState = ProductPickerState(),

    // Purchases
    val isSubmittingPurchase: Boolean = false,
    val purchaseBusinessUnitId: Long? = null,
    val purchaseSupplierId: Long? = null,
    val purchaseSupplierDocNo: String = "",
    val purchasePaidCash: Boolean = false,
    val purchaseLineQtyInput: String = "1",
    val purchaseLineUnitCostInput: String = "",
    val purchaseLineLotCode: String = "",
    val purchaseLineExpiryDate: String = "",
    val purchaseLines: List<PurchaseLineInput> = emptyList(),
    val purchaseIdempotencyKey: String? = null,
    val lastPurchaseResult: String? = null,
    val showNewSupplierForm: Boolean = false,
    val newSupplierName: String = "",
    val newSupplierPhone: String = "",
    val newSupplierTaxId: String = "",
    val isCreatingSupplier: Boolean = false,

    // Internal requisitions (consumption in FONDA / TORTERIA)
    val isSubmittingRequisition: Boolean = false,
    val requisitionBusinessUnitId: Long? = null,
    val requisitionLineQtyInput: String = "1",
    val requisitionLines: List<RequisitionLineInput> = emptyList(),
    val requisitionIdempotencyKey: String? = null,
    val lastRequisitionResult: String? = null,

    // On hand
    val isLoadingOnHand: Boolean = false,
    val onHandBusinessUnitId: Long? = null,
    val onHandQuickSearch: String = "",
    val onHandItems: List<InventoryOnHandItem> = emptyList(),

    // Waste (TIENDA only)
    val isSubmittingWaste: Boolean = false,
    val wasteQtyInput: String = "",
    val wasteReason: WasteReason = WasteReason.EXPIRED,
    val wasteNotes: String = "",
    val wasteIdempotencyKey: String? = null,

    // Adjustments (TIENDA only)
    val isSubmittingAdjustment: Boolean = false,
    val adjustmentQtyDeltaInput: String = "",
    val adjustmentReason: AdjustmentReason = AdjustmentReason.COUNT_DIFFERENCE,
    val adjustmentNotes: String = "",
    val adjustmentIdempotencyKey: String? = null,

    val confirmDialog: OperationsConfirmDialogState? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
) {
    val tiendaBusinessUnit: OperationsBusinessUnit?
        get() = businessUnits.firstOrNull { it.code.equals("TIENDA", ignoreCase = true) }

    /** Units that may consume stock through an internal requisition. */
    val consumptionBusinessUnits: List<OperationsBusinessUnit>
        get() = businessUnits.filter { it.code.equals("FONDA", true) || it.code.equals("TORTERIA", true) }

    val selectedPurchaseBusinessUnit: OperationsBusinessUnit?
        get() = businessUnits.firstOrNull { it.id == purchaseBusinessUnitId }

    val selectedSupplier: Supplier?
        get() = suppliers.firstOrNull { it.id == purchaseSupplierId }

    val selectedRequisitionBusinessUnit: OperationsBusinessUnit?
        get() = businessUnits.firstOrNull { it.id == requisitionBusinessUnitId }

    val selectedOnHandBusinessUnit: OperationsBusinessUnit?
        get() = businessUnits.firstOrNull { it.id == onHandBusinessUnitId }

    val purchaseTotal: Double
        get() = purchaseLines.sumOf { it.lineTotal }

    val visibleOnHandItems: List<InventoryOnHandItem>
        get() {
            val query = onHandQuickSearch.trim().lowercase()
            if (query.isEmpty()) return onHandItems
            return onHandItems.filter {
                it.productId.toString() == query ||
                    it.productName.lowercase().contains(query) ||
                    it.businessUnitCode.lowercase().contains(query)
            }
        }
}
