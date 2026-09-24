package com.posfab.shared.features.operations.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.operations.domain.AdjustmentInput
import com.posfab.shared.features.operations.domain.InternalRequisitionInput
import com.posfab.shared.features.operations.domain.InternalRequisitionResult
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.InventoryOnHandItem
import com.posfab.shared.features.operations.domain.InventoryOperationResult
import com.posfab.shared.features.operations.domain.OperationsBusinessUnit
import com.posfab.shared.features.operations.domain.OperationsProduct
import com.posfab.shared.features.operations.domain.OperationsUnit
import com.posfab.shared.features.operations.domain.ProductUnitOption
import com.posfab.shared.features.operations.domain.PurchaseInput
import com.posfab.shared.features.operations.domain.PurchaseResult
import com.posfab.shared.features.operations.domain.Supplier
import com.posfab.shared.features.operations.domain.SupplierInput
import com.posfab.shared.features.operations.domain.WasteInput

interface OperationsRepository {
    // Reference data
    suspend fun listBusinessUnits(): AppResult<List<OperationsBusinessUnit>>
    suspend fun listUnits(): AppResult<List<OperationsUnit>>
    suspend fun listSuppliers(): AppResult<List<Supplier>>
    suspend fun createSupplier(input: SupplierInput): AppResult<Supplier>
    suspend fun searchProducts(query: String, limit: Int = 20): AppResult<List<OperationsProduct>>
    /** Base unit plus UOM conversions of the product, resolved to unit ids. */
    suspend fun productUnitOptions(product: OperationsProduct): AppResult<List<ProductUnitOption>>

    // Operations
    suspend fun createPurchase(input: PurchaseInput, idempotencyKey: String): AppResult<PurchaseResult>
    suspend fun createInternalRequisition(input: InternalRequisitionInput, idempotencyKey: String): AppResult<InternalRequisitionResult>
    suspend fun fetchOnHand(productId: Long?, businessUnitId: Long?): AppResult<List<InventoryOnHandItem>>
    suspend fun fetchLots(productId: Long): AppResult<List<InventoryLotItem>>
    suspend fun createWaste(input: WasteInput, idempotencyKey: String): AppResult<InventoryOperationResult>
    suspend fun createAdjustment(input: AdjustmentInput, idempotencyKey: String): AppResult<InventoryOperationResult>
}
