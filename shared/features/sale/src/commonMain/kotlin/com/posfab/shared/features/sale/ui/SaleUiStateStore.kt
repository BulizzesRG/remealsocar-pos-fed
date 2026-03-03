package com.posfab.shared.features.sale.ui

data class SaleUiRecoveryState(
    val searchQuery: String = "",
    val barcodeInput: String = "",
    val selectedLineId: String? = null,
    val draftId: String? = null,
    val savedAtEpochMs: Long,
)

interface SaleUiStateStore {
    suspend fun load(): SaleUiRecoveryState?
    suspend fun save(state: SaleUiRecoveryState)
    suspend fun clear()
}

object NoOpSaleUiStateStore : SaleUiStateStore {
    override suspend fun load(): SaleUiRecoveryState? = null
    override suspend fun save(state: SaleUiRecoveryState) = Unit
    override suspend fun clear() = Unit
}
