package com.posfab.shared.features.operations.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
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
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.operations.AdjustmentLineRequestDto
import com.posfab.shared.network.operations.AdjustmentRequestDto
import com.posfab.shared.network.operations.InternalRequisitionCreateRequestDto
import com.posfab.shared.network.operations.InternalRequisitionLineRequestDto
import com.posfab.shared.network.operations.OperationsApi
import com.posfab.shared.network.operations.PurchaseCreateRequestDto
import com.posfab.shared.network.operations.PurchaseLineRequestDto
import com.posfab.shared.network.operations.SupplierCreateRequestDto
import com.posfab.shared.network.operations.WasteLineRequestDto
import com.posfab.shared.network.operations.WasteRequestDto

class OperationsRepositoryImpl(
    private val operationsApi: OperationsApi,
    private val catalogApi: CatalogApi,
    private val executor: AuthorizedApiExecutor,
) : OperationsRepository {

    // ---- Reference data ---------------------------------------------------

    override suspend fun listBusinessUnits(): AppResult<List<OperationsBusinessUnit>> =
        authorized { catalogApi.listBusinessUnits(it) }.map { list ->
            list.filter { it.active }.map { OperationsBusinessUnit(it.id, it.code, it.name) }
        }

    override suspend fun listUnits(): AppResult<List<OperationsUnit>> =
        authorized { catalogApi.listUnits(it) }.map { list ->
            list.filter { it.active }.map { OperationsUnit(it.id, it.code, it.name) }
        }

    override suspend fun listSuppliers(): AppResult<List<Supplier>> =
        authorized { operationsApi.listSuppliers(it, activeOnly = true) }.map { list ->
            list.map { Supplier(it.id, it.name, it.phone, it.taxId, it.active) }
        }

    override suspend fun createSupplier(input: SupplierInput): AppResult<Supplier> {
        val request = SupplierCreateRequestDto(
            name = input.name.trim(),
            phone = input.phone?.trim()?.ifBlank { null },
            taxId = input.taxId?.trim()?.ifBlank { null },
        )
        return authorized { operationsApi.createSupplier(it, request) }.map { created ->
            Supplier(created.id, request.name, request.phone, request.taxId, active = true)
        }
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<OperationsProduct>> =
        authorized { catalogApi.listProducts(it, query.trim().ifBlank { null }, limit, 0, true) }.map { page ->
            page.items.mapNotNull { it.toDomain() }
        }

    override suspend fun productUnitOptions(product: OperationsProduct): AppResult<List<ProductUnitOption>> {
        val base = ProductUnitOption(product.baseUnitId, product.baseUnitCode, factorToBase = 1.0)
        val units = when (val result = listUnits()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val detail = when (val result = authorized { catalogApi.getProduct(it, product.id.toString()) }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val conversions = detail.uoms.mapNotNull { uom ->
            val unit = units.firstOrNull { it.code.equals(uom.unit, ignoreCase = true) } ?: return@mapNotNull null
            if (unit.id == base.unitId) return@mapNotNull null
            ProductUnitOption(unit.id, unit.code, uom.factorToBase.toDoubleOrNull() ?: 1.0)
        }
        return AppResult.Success(listOf(base) + conversions)
    }

    // ---- Operations -------------------------------------------------------

    override suspend fun createPurchase(input: PurchaseInput, idempotencyKey: String): AppResult<PurchaseResult> {
        val request = PurchaseCreateRequestDto(
            businessUnitId = input.businessUnitId,
            terminalId = input.terminalId,
            supplierId = input.supplierId,
            supplierDocNo = input.supplierDocNo?.trim()?.ifBlank { null },
            paidCash = input.paidCash,
            paidFromTerminalId = if (input.paidCash) input.paidFromTerminalId else null,
            items = input.lines.map { line ->
                PurchaseLineRequestDto(
                    productId = line.productId,
                    unitId = line.unitId,
                    qty = line.qty.toDecimalString(),
                    unitCost = line.unitCost.toDecimalString(),
                    lotCode = line.lotCode?.trim()?.ifBlank { null },
                    expiryDate = line.expiryDate?.trim()?.ifBlank { null },
                )
            },
        )
        return authorized { operationsApi.createPurchase(it, idempotencyKey, request) }
            .map { PurchaseResult(it.id) }
    }

    override suspend fun createInternalRequisition(input: InternalRequisitionInput, idempotencyKey: String): AppResult<InternalRequisitionResult> {
        val request = InternalRequisitionCreateRequestDto(
            businessUnitId = input.businessUnitId,
            terminalId = input.terminalId,
            items = input.lines.map { line ->
                InternalRequisitionLineRequestDto(
                    productId = line.productId,
                    unitId = line.unitId,
                    qty = line.qty.toDecimalString(),
                    lotId = line.lotId,
                )
            },
        )
        return authorized { operationsApi.createInternalRequisition(it, idempotencyKey, request) }
            .map { InternalRequisitionResult(it.id, it.folio) }
    }

    override suspend fun fetchOnHand(productId: Long?, businessUnitId: Long?): AppResult<List<InventoryOnHandItem>> {
        val rows = when (val result = authorized { operationsApi.inventoryOnHand(it, productId, businessUnitId) }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        if (rows.isEmpty()) return AppResult.Success(emptyList())

        // The backend returns ids only; resolve names/codes so the screen is readable.
        val businessUnits = when (val result = listBusinessUnits()) {
            is AppResult.Success -> result.value.associateBy { it.id }
            is AppResult.Failure -> return result
        }
        val products = when (val result = authorized { catalogApi.listProducts(it, null, PRODUCT_LOOKUP_LIMIT, 0, null) }) {
            is AppResult.Success -> result.value.items.mapNotNull { it.toDomain() }.associateBy { it.id }
            is AppResult.Failure -> return result
        }
        return AppResult.Success(
            rows.map { row ->
                val product = products[row.productId]
                InventoryOnHandItem(
                    productId = row.productId,
                    productName = product?.name ?: "Producto #${row.productId}",
                    businessUnitId = row.businessUnitId,
                    businessUnitCode = businessUnits[row.businessUnitId]?.code ?: "#${row.businessUnitId}",
                    qtyBase = row.qtyBase.toDoubleOrNull() ?: 0.0,
                    baseUnitCode = product?.baseUnitCode ?: "",
                )
            }
        )
    }

    override suspend fun fetchLots(productId: Long): AppResult<List<InventoryLotItem>> =
        authorized { operationsApi.inventoryLots(it, productId) }.map { list ->
            list.map {
                InventoryLotItem(
                    lotId = it.lotId,
                    productId = it.productId,
                    lotCode = it.lotCode,
                    expiryDate = it.expiryDate,
                    qtyBase = it.qtyBase.toDoubleOrNull() ?: 0.0,
                )
            }
        }

    override suspend fun createWaste(input: WasteInput, idempotencyKey: String): AppResult<InventoryOperationResult> {
        val request = WasteRequestDto(
            terminalId = input.terminalId,
            businessUnitId = input.businessUnitId,
            lines = input.lines.map { line ->
                WasteLineRequestDto(
                    productId = line.productId,
                    unitId = line.unitId,
                    qty = line.qty.toDecimalString(),
                    lotId = line.lotId,
                    reasonCode = line.reason.code,
                    notes = line.notes?.trim()?.ifBlank { null },
                )
            },
        )
        return authorized { operationsApi.createWaste(it, idempotencyKey, request) }
            .map { InventoryOperationResult(it.id) }
    }

    override suspend fun createAdjustment(input: AdjustmentInput, idempotencyKey: String): AppResult<InventoryOperationResult> {
        val request = AdjustmentRequestDto(
            terminalId = input.terminalId,
            businessUnitId = input.businessUnitId,
            lines = input.lines.map { line ->
                AdjustmentLineRequestDto(
                    productId = line.productId,
                    unitId = line.unitId,
                    qtyDelta = line.qtyDelta.toDecimalString(),
                    lotId = line.lotId,
                    reasonCode = line.reason.code,
                    notes = line.notes?.trim()?.ifBlank { null },
                )
            },
        )
        return authorized { operationsApi.createAdjustment(it, idempotencyKey, request) }
            .map { InventoryOperationResult(it.id) }
    }

    // ---- Helpers ----------------------------------------------------------

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

    private fun ProductListItemDto.toDomain(): OperationsProduct? {
        val productId = id.toLongOrNull() ?: return null
        return OperationsProduct(
            id = productId,
            name = name,
            sku = sku,
            barcode = barcode,
            baseUnitCode = baseUnit,
            baseUnitId = baseUnitId,
            lotTracked = lotTracked,
        )
    }

    private fun Double.toDecimalString(): String = toBigDecimal().toPlainString()

    private companion object {
        /** Backend caps `limit` at 200; enough for the current catalog size. */
        const val PRODUCT_LOOKUP_LIMIT = 200
    }
}
