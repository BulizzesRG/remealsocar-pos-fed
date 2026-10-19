package com.posfab.shared.features.shopping.ui

import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.domain.ShoppingProduct

sealed interface ShoppingView {
    data object Lists : ShoppingView
    data class HistoryDetail(val id: Long) : ShoppingView
}

data class ItemEditState(
    val id: Long,
    val text: String,
    val qtyInput: String,
    val productId: Long? = null,
    val originalText: String = text,
    val originalQtyInput: String = qtyInput,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val hasChanges: Boolean get() = text.trim() != originalText.trim() || qtyInput.trim() != originalQtyInput.trim()
}

sealed interface ShoppingConfirm {
    data object Close : ShoppingConfirm
    data class Finish(val candidates: List<ShoppingItem>, val carry: Set<Long>) : ShoppingConfirm {
        val hasCandidates: Boolean get() = candidates.isNotEmpty()
    }
    data class DeleteItem(val id: Long, val text: String) : ShoppingConfirm
}

data class ShoppingState(
    val view: ShoppingView = ShoppingView.Lists,
    val isLoading: Boolean = false,
    val loadGeneration: Int = 0,
    val errorMessage: String? = null,
    val notice: String? = null,
    val open: ShoppingList? = null,
    val toShop: ShoppingList? = null,
    val input: String = "",
    val qtyInput: String = "",
    val inputError: String? = null,
    val suggestions: List<ShoppingProduct> = emptyList(),
    val isAdding: Boolean = false,
    val pendingAddKey: String? = null,
    val edit: ItemEditState? = null,
    val confirm: ShoppingConfirm? = null,
    val isConfirming: Boolean = false,
    val busyItemId: Long? = null,
    val pendingCloseKey: String? = null,
    val pendingFinishKey: String? = null,
    val showHistory: Boolean = false,
    val history: List<ShoppingListSummary> = emptyList(),
    val historyLoaded: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val historyError: String? = null,
    val historyDetail: ShoppingList? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    val canClose: Boolean = false,
) {
    val showCloseButton: Boolean get() = canClose && open?.items?.isNotEmpty() == true
    val showFinishButton: Boolean get() = canClose && toShop != null
    val hasDialog: Boolean get() = confirm != null || edit != null
}
