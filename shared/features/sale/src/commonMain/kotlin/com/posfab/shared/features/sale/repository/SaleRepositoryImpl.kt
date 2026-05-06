package com.posfab.shared.features.sale.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CheckoutMode
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.DraftIssue
import com.posfab.shared.features.sale.domain.DraftValidation
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.pos.AddLineRequestDto
import com.posfab.shared.network.pos.CheckoutRequestDto
import com.posfab.shared.network.pos.DeleteLineRequestDto
import com.posfab.shared.network.pos.DraftCreateRequestDto
import com.posfab.shared.network.pos.DraftDto
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.UpdateLineRequestDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SaleRepositoryImpl(
    private val posApi: PosApi,
    private val catalogApi: CatalogApi,
    private val terminalId: Long,
    private val businessUnitId: Long,
    private val executor: AuthorizedApiExecutor,
) : SaleRepository {
    private val checkoutMutex = Mutex()

    override suspend fun isCashSessionOpen(): AppResult<Boolean> = authorized {
        posApi.isCashSessionOpen(it, terminalId)
    }

    override suspend fun openOrCreateCurrentSaleDraft(): AppResult<SaleDraft> {
        val currentResult = authorized { posApi.getCurrentSaleDraft(it) }
        if (currentResult is AppResult.Success) {
            return AppResult.Success(currentResult.value.toDomain())
        }
        // 404 maps to AppError.Validation — attempt to create a new draft
        val createResult = authorized {
            posApi.createDraft(
                accessToken = it,
                request = DraftCreateRequestDto(
                    type = "SALE",
                    terminalId = terminalId,
                    businessUnitId = businessUnitId,
                ),
            )
        }
        return when (createResult) {
            is AppResult.Success -> authorized { posApi.getDraft(it, createResult.value.id) }.mapDraft()
            is AppResult.Failure -> createResult
        }
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> {
        val result = authorized { catalogApi.listProducts(it, query, limit, 0, true) }
        return when (result) {
            is AppResult.Success -> AppResult.Success(result.value.items.map { item ->
                SaleProduct(
                    id = item.id,
                    name = item.name,
                    sku = item.sku,
                    barcode = item.barcode,
                    defaultUnit = item.baseUnit,
                    baseUnitId = item.baseUnitId,
                    unitPrice = item.currentPrice ?: 0.0,
                    lotTracked = item.lotTracked,
                )
            })
            is AppResult.Failure -> result
        }
    }

    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> {
        val result = authorized { posApi.findByBarcode(it, barcode) }
        return when (result) {
            is AppResult.Success -> {
                val dto = result.value
                val product = SaleProduct(
                    id = dto.product.id.toString(),
                    name = dto.product.name,
                    sku = dto.product.sku,
                    barcode = barcode,
                    defaultUnit = dto.matchedUom?.unitId?.toString() ?: dto.product.baseUnitId.toString(),
                    baseUnitId = dto.matchedUom?.unitId ?: dto.product.baseUnitId,
                    unitPrice = dto.product.currentPrice ?: 0.0,
                    lotTracked = dto.product.requiresLotTracking,
                )
                AppResult.Success(product)
            }
            is AppResult.Failure -> {
                // 404 means barcode not found — return null rather than propagating failure
                if (result.error is AppError.Validation) {
                    AppResult.Success(null)
                } else {
                    result
                }
            }
        }
    }

    override suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        qty: Double,
        unitId: Long,
        unitPrice: Double,
        lotId: Long?,
        expectedVersion: Int,
    ): SaleMutationResult = mutationWithConflictRefetch {
        posApi.addDraftLine(
            accessToken = it,
            draftId = draftId,
            request = AddLineRequestDto(
                productId = product.id.toLongOrNull() ?: 0L,
                unitId = unitId,
                qty = qty.toBigDecimal().toPlainString(),
                unitPrice = unitPrice.toBigDecimal().toPlainString(),
                lotId = lotId,
                expectedVersion = expectedVersion,
            ),
        )
    }

    override suspend fun updateLine(
        draftId: Long,
        lineId: Long,
        qty: Double,
        unitId: Long,
        unitPrice: Double,
        lotId: Long?,
        expectedVersion: Int,
    ): SaleMutationResult = mutationWithConflictRefetch {
        posApi.updateDraftLine(
            accessToken = it,
            draftId = draftId,
            lineId = lineId,
            request = UpdateLineRequestDto(
                // unitId == 0L signals "leave unchanged" — pass null to backend
                unitId = unitId.takeIf { id -> id > 0L },
                qty = qty.toBigDecimal().toPlainString(),
                unitPrice = unitPrice.toBigDecimal().toPlainString(),
                lotId = lotId,
                expectedVersion = expectedVersion,
            ),
        )
    }

    override suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult =
        mutationWithConflictRefetch {
            posApi.removeDraftLine(
                accessToken = it,
                draftId = draftId,
                lineId = lineId,
                request = DeleteLineRequestDto(expectedVersion = expectedVersion),
            )
        }

    override suspend fun validateDraft(draftId: Long): AppResult<DraftValidation> {
        val result = authorized { posApi.validateDraft(it, draftId) }
        return when (result) {
            is AppResult.Success -> AppResult.Success(
                DraftValidation(result.value.issues.map { issue ->
                    DraftIssue(lineId = issue.lineId, code = issue.code, message = issue.message)
                })
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> {
        val result = authorized { posApi.resolveLots(it, draftId) }
        return when (result) {
            is AppResult.Success -> {
                // resolve-lots returns a summary DTO, not a full DraftDto — refetch the draft
                authorized { posApi.getDraft(it, draftId) }.mapDraft()
            }
            is AppResult.Failure -> result
        }
    }

    override suspend fun checkout(
        draftId: Long,
        mode: CheckoutMode,
        expectedVersion: Int,
        idempotencyKey: String,
    ): AppResult<CheckoutResult> = checkoutMutex.withLock {
        // Capture total from current draft state before checkout
        val draftTotal = when (val draft = authorized { posApi.getDraft(it, draftId) }) {
            is AppResult.Success -> draft.value.totals.total.toDoubleOrNull() ?: 0.0
            is AppResult.Failure -> 0.0
        }
        val paymentStatus = when (mode) {
            CheckoutMode.CASH -> "PAID"
            CheckoutMode.ON_CREDIT -> "ON_CREDIT"
        }
        val result = authorized {
            posApi.checkoutDraft(
                accessToken = it,
                draftId = draftId,
                request = CheckoutRequestDto(
                    paymentStatus = paymentStatus,
                    idempotencyKey = idempotencyKey,
                ),
            )
        }
        when (result) {
            is AppResult.Success -> AppResult.Success(
                CheckoutResult(
                    saleId = result.value.id,
                    folio = result.value.folio.orEmpty(),
                    total = draftTotal,
                    mode = mode,
                )
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun refetchCurrentDraft(): AppResult<SaleDraft> = openOrCreateCurrentSaleDraft()

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private suspend fun mutationWithConflictRefetch(block: suspend (String) -> AppResult<DraftDto>): SaleMutationResult {
        return when (val result = authorized(block)) {
            is AppResult.Success -> SaleMutationResult.Success(result.value.toDomain())
            is AppResult.Failure -> {
                if (result.error is AppError.Conflict) {
                    when (val refetch = refetchCurrentDraft()) {
                        is AppResult.Success -> SaleMutationResult.ConflictRefetched(refetch.value)
                        is AppResult.Failure -> SaleMutationResult.Failure(result.error)
                    }
                } else {
                    SaleMutationResult.Failure(result.error)
                }
            }
        }
    }

    private fun AppResult<DraftDto>.mapDraft(): AppResult<SaleDraft> = when (this) {
        is AppResult.Success -> AppResult.Success(value.toDomain())
        is AppResult.Failure -> this
    }

    private fun DraftDto.toDomain(): SaleDraft = SaleDraft(
        id = id,
        status = status,
        type = type,
        terminalId = terminalId,
        businessUnitId = businessUnitId ?: 0L,
        version = version,
        lines = lines.map { line ->
            SaleLine(
                id = line.id,
                productId = line.productId,
                productName = line.productName,
                barcode = line.productBarcode,
                qty = line.qty.toDoubleOrNull() ?: 0.0,
                unit = line.unitCode,
                unitPrice = line.unitPrice?.toDoubleOrNull() ?: 0.0,
                lineTotal = line.lineTotal.toDoubleOrNull() ?: 0.0,
                lotId = line.lotId,
                lotTracked = line.lotTracked,
            )
        },
        totals = SaleTotals(
            subtotal = totals.subtotal.toDoubleOrNull() ?: 0.0,
            tax = totals.tax.toDoubleOrNull() ?: 0.0,
            total = totals.total.toDoubleOrNull() ?: 0.0,
        ),
    )
}
