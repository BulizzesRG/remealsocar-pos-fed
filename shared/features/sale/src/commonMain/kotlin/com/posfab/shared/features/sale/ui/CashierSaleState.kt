package com.posfab.shared.features.sale.ui

import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.DraftIssue
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleProduct

data class SaleConfirmDialogState(
    val title: String,
    val message: String,
    val confirmLabel: String = "Confirmar",
    val cancelLabel: String = "Cancelar",
    val isDestructive: Boolean = false,
)

data class CashierSaleState(
    val isInitializing: Boolean = true,
    val isBusy: Boolean = false,
    val isCheckoutInFlight: Boolean = false,
    val searchQuery: String = "",
    val barcodeInput: String = "",
    val searchResults: List<SaleProduct> = emptyList(),
    val draft: SaleDraft? = null,
    val selectedLineId: String? = null,
    val editQtyInput: String = "",
    val editUnitInput: String = "",
    val editPriceInput: String = "",
    val editLotInput: String = "",
    val validationIssues: List<DraftIssue> = emptyList(),
    val checkoutResult: CheckoutResult? = null,
    val checkoutIdempotencyKey: String? = null,
    val confirmDialog: SaleConfirmDialogState? = null,
    val notice: String? = null,
    val errorMessage: String? = null,
)
