package com.posfab.shared.features.operations.ui

import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.InventoryOnHandItem
import com.posfab.shared.features.operations.domain.PurchaseLineInput
import com.posfab.shared.features.operations.domain.RequisitionLineInput

enum class OperationsTab {
    PURCHASES,
    INTERNAL_REQ,
    ON_HAND,
    LOTS,
    WASTE,
    ADJUSTMENTS,
}

data class OperationsState(
    val selectedTab: OperationsTab = OperationsTab.PURCHASES,
    val canManageOperations: Boolean = false,
    val canManageWasteAdjustments: Boolean = false,
    val terminalId: String = "",
    val businessUnit: String = "TIENDA",

    val isSubmittingPurchase: Boolean = false,
    val purchaseSupplier: String = "",
    val purchasePaidCash: Boolean = true,
    val purchasePaidFromTerminalId: String = "",
    val purchaseLineProductId: String = "",
    val purchaseLineUnit: String = "EA",
    val purchaseLineQtyInput: String = "1",
    val purchaseLineUnitCostInput: String = "0",
    val purchaseLineLotCode: String = "",
    val purchaseLineLotExpiry: String = "",
    val purchaseLines: List<PurchaseLineInput> = emptyList(),
    val purchaseIdempotencyKey: String? = null,
    val lastPurchaseResult: String? = null,

    val isSubmittingRequisition: Boolean = false,
    val requisitionSourceBu: String = "TIENDA",
    val requisitionTargetBu: String = "FONDA",
    val requisitionLineProductId: String = "",
    val requisitionLineUnit: String = "EA",
    val requisitionLineQtyInput: String = "1",
    val requisitionLineLotId: String = "",
    val requisitionLines: List<RequisitionLineInput> = emptyList(),
    val requisitionIdempotencyKey: String? = null,
    val lastRequisitionResult: String? = null,

    val isLoadingOnHand: Boolean = false,
    val onHandProductFilter: String = "",
    val onHandBusinessUnitFilter: String = "",
    val onHandQuickSearch: String = "",
    val onHandItems: List<InventoryOnHandItem> = emptyList(),

    val isLoadingLots: Boolean = false,
    val lotsProductId: String = "",
    val lotItems: List<InventoryLotItem> = emptyList(),

    val isSubmittingWaste: Boolean = false,
    val wasteProductId: String = "",
    val wasteBusinessUnit: String = "TIENDA",
    val wasteUnit: String = "EA",
    val wasteQtyInput: String = "",
    val wasteReasonCode: String = "CADUCADO",
    val wasteLotId: String = "",
    val wasteIdempotencyKey: String? = null,

    val isSubmittingAdjustment: Boolean = false,
    val adjustmentProductId: String = "",
    val adjustmentBusinessUnit: String = "TIENDA",
    val adjustmentUnit: String = "EA",
    val adjustmentQtyDeltaInput: String = "",
    val adjustmentReasonCode: String = "CONTEO",
    val adjustmentLotId: String = "",
    val adjustmentIdempotencyKey: String? = null,

    val notice: String? = null,
    val errorMessage: String? = null,
) {
    val visibleOnHandItems: List<InventoryOnHandItem>
        get() {
            val query = onHandQuickSearch.trim().lowercase()
            if (query.isEmpty()) return onHandItems
            return onHandItems.filter {
                it.productId.lowercase().contains(query) ||
                    it.productName.lowercase().contains(query) ||
                    it.businessUnit.lowercase().contains(query)
            }
        }
}
