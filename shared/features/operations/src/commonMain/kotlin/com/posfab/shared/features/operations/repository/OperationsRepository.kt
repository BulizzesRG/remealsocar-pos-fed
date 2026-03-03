package com.posfab.shared.features.operations.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.operations.domain.AdjustmentInput
import com.posfab.shared.features.operations.domain.AdjustmentResult
import com.posfab.shared.features.operations.domain.InternalRequisitionInput
import com.posfab.shared.features.operations.domain.InternalRequisitionResult
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.InventoryOnHandItem
import com.posfab.shared.features.operations.domain.PurchaseInput
import com.posfab.shared.features.operations.domain.PurchaseResult
import com.posfab.shared.features.operations.domain.WasteInput
import com.posfab.shared.features.operations.domain.WasteResult

interface OperationsRepository {
    suspend fun createPurchase(input: PurchaseInput, idempotencyKey: String): AppResult<PurchaseResult>
    suspend fun createInternalRequisition(input: InternalRequisitionInput, idempotencyKey: String): AppResult<InternalRequisitionResult>
    suspend fun fetchOnHand(productId: String?, businessUnit: String?): AppResult<List<InventoryOnHandItem>>
    suspend fun fetchLots(productId: String): AppResult<List<InventoryLotItem>>
    suspend fun createWaste(input: WasteInput, idempotencyKey: String): AppResult<WasteResult>
    suspend fun createAdjustment(input: AdjustmentInput, idempotencyKey: String): AppResult<AdjustmentResult>
}
