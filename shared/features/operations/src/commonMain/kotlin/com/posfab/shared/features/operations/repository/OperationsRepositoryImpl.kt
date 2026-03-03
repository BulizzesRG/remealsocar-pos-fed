package com.posfab.shared.features.operations.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.operations.domain.AdjustmentInput
import com.posfab.shared.features.operations.domain.AdjustmentResult
import com.posfab.shared.features.operations.domain.InternalRequisitionInput
import com.posfab.shared.features.operations.domain.InternalRequisitionResult
import com.posfab.shared.features.operations.domain.InventoryLotItem
import com.posfab.shared.features.operations.domain.InventoryOnHandItem
import com.posfab.shared.features.operations.domain.PurchaseInput
import com.posfab.shared.features.operations.domain.PurchaseLineInput
import com.posfab.shared.features.operations.domain.PurchaseResult
import com.posfab.shared.features.operations.domain.RequisitionLineInput
import com.posfab.shared.features.operations.domain.WasteInput
import com.posfab.shared.features.operations.domain.WasteResult
import com.posfab.shared.network.operations.AdjustmentRequestDto
import com.posfab.shared.network.operations.AdjustmentResponseDto
import com.posfab.shared.network.operations.InternalRequisitionLineDto
import com.posfab.shared.network.operations.InternalRequisitionRequestDto
import com.posfab.shared.network.operations.InternalRequisitionResponseDto
import com.posfab.shared.network.operations.InventoryLotDto
import com.posfab.shared.network.operations.OnHandItemDto
import com.posfab.shared.network.operations.OperationsApi
import com.posfab.shared.network.operations.PurchaseLineDto
import com.posfab.shared.network.operations.PurchaseRequestDto
import com.posfab.shared.network.operations.PurchaseResponseDto
import com.posfab.shared.network.operations.WasteRequestDto
import com.posfab.shared.network.operations.WasteResponseDto

class OperationsRepositoryImpl(
    private val operationsApi: OperationsApi,
    private val executor: AuthorizedApiExecutor,
) : OperationsRepository {
    override suspend fun createPurchase(input: PurchaseInput, idempotencyKey: String): AppResult<PurchaseResult> {
        return when (
            val result = authorized {
                operationsApi.createPurchase(
                    accessToken = it,
                    idempotencyKey = idempotencyKey,
                    request = PurchaseRequestDto(
                        supplier = input.supplier,
                        terminalId = input.terminalId,
                        businessUnit = input.businessUnit,
                        paidCash = input.paidCash,
                        paidFromTerminalId = input.paidFromTerminalId,
                        lines = input.lines.map { it.toPurchaseLineDto() },
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun createInternalRequisition(input: InternalRequisitionInput, idempotencyKey: String): AppResult<InternalRequisitionResult> {
        return when (
            val result = authorized {
                operationsApi.createInternalRequisition(
                    accessToken = it,
                    idempotencyKey = idempotencyKey,
                    request = InternalRequisitionRequestDto(
                        sourceBusinessUnit = input.sourceBusinessUnit,
                        targetBusinessUnit = input.targetBusinessUnit,
                        terminalId = input.terminalId,
                        lines = input.lines.map { it.toRequisitionLineDto() },
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun fetchOnHand(productId: String?, businessUnit: String?): AppResult<List<InventoryOnHandItem>> {
        return when (val result = authorized { operationsApi.inventoryOnHand(it, productId, businessUnit) }) {
            is AppResult.Success -> AppResult.Success(result.value.map { it.toOnHandDomain() })
            is AppResult.Failure -> result
        }
    }

    override suspend fun fetchLots(productId: String): AppResult<List<InventoryLotItem>> {
        return when (val result = authorized { operationsApi.inventoryLots(it, productId) }) {
            is AppResult.Success -> AppResult.Success(result.value.map { it.toLotDomain() })
            is AppResult.Failure -> result
        }
    }

    override suspend fun createWaste(input: WasteInput, idempotencyKey: String): AppResult<WasteResult> {
        return when (
            val result = authorized {
                operationsApi.createWaste(
                    accessToken = it,
                    idempotencyKey = idempotencyKey,
                    request = WasteRequestDto(
                        productId = input.productId,
                        businessUnit = input.businessUnit,
                        unit = input.unit,
                        qty = input.qty,
                        reasonCode = input.reasonCode,
                        lotId = input.lotId,
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun createAdjustment(input: AdjustmentInput, idempotencyKey: String): AppResult<AdjustmentResult> {
        return when (
            val result = authorized {
                operationsApi.createAdjustment(
                    accessToken = it,
                    idempotencyKey = idempotencyKey,
                    request = AdjustmentRequestDto(
                        productId = input.productId,
                        businessUnit = input.businessUnit,
                        unit = input.unit,
                        qtyDelta = input.qtyDelta,
                        reasonCode = input.reasonCode,
                        lotId = input.lotId,
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private fun PurchaseLineInput.toPurchaseLineDto() = PurchaseLineDto(
        productId = productId,
        unit = unit,
        qty = qty,
        unitCost = unitCost,
        lotCode = lotCode,
        lotExpiry = lotExpiry,
    )

    private fun PurchaseResponseDto.toDomain() = PurchaseResult(
        purchaseId = purchaseId,
        folio = folio,
        message = message,
    )

    private fun RequisitionLineInput.toRequisitionLineDto() = InternalRequisitionLineDto(
        productId = productId,
        unit = unit,
        qty = qty,
        lotId = lotId,
    )

    private fun InternalRequisitionResponseDto.toDomain() = InternalRequisitionResult(
        requisitionId = requisitionId,
        folio = folio,
        message = message,
    )

    private fun OnHandItemDto.toOnHandDomain() = InventoryOnHandItem(
        productId = productId,
        productName = productName ?: productId,
        businessUnit = businessUnit,
        unit = unit,
        onHand = onHand,
    )

    private fun InventoryLotDto.toLotDomain() = InventoryLotItem(
        productId = productId,
        lotId = lotId,
        lotCode = lotCode ?: lotId ?: "-",
        expiryDate = expiryDate,
        onHand = onHand,
        unit = unit ?: "",
    )

    private fun WasteResponseDto.toDomain() = WasteResult(
        wasteId = wasteId,
        message = message,
    )

    private fun AdjustmentResponseDto.toDomain() = AdjustmentResult(
        adjustmentId = adjustmentId,
        message = message,
    )
}
