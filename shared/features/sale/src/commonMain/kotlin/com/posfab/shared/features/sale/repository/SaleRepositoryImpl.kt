package com.posfab.shared.features.sale.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.model.TerminalCode
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
import com.posfab.shared.network.pos.AddLineRequestDto
import com.posfab.shared.network.pos.CheckoutRequestDto
import com.posfab.shared.network.pos.CheckoutResponseDto
import com.posfab.shared.network.pos.DraftDto
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.ProductDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SaleRepositoryImpl(
    private val posApi: PosApi,
    private val sessionManager: SessionManager,
    private val executor: AuthorizedApiExecutor,
) : SaleRepository {
    private val checkoutMutex = Mutex()

    override suspend fun openOrCreateCurrentSaleDraft(): AppResult<SaleDraft> = authorized {
        val terminal = sessionManager.current()?.terminal ?: TerminalCode.POS1
        posApi.openCurrentSaleDraft(it, terminal.name)
    }.mapDraft()

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> = authorized {
        posApi.searchProducts(it, query, limit)
    }.mapProducts()

    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> = authorized {
        posApi.findByBarcode(it, barcode)
    }.mapProductOrNull()

    override suspend fun addLine(
        draftId: String,
        product: SaleProduct,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult = mutationWithConflictRefetch {
        posApi.addDraftLine(
            accessToken = it,
            draftId = draftId,
            request = AddLineRequestDto(
                productId = product.id,
                qty = qty,
                unit = unit,
                unitPrice = unitPrice,
                lotId = lotId,
                expectedVersion = expectedVersion,
            ),
        )
    }

    override suspend fun updateLine(
        draftId: String,
        lineId: String,
        qty: Double,
        unit: String,
        unitPrice: Double,
        lotId: String?,
        expectedVersion: Long,
    ): SaleMutationResult = mutationWithConflictRefetch {
        posApi.updateDraftLine(
            accessToken = it,
            draftId = draftId,
            lineId = lineId,
            request = com.posfab.shared.network.pos.UpdateLineRequestDto(
                qty = qty,
                unit = unit,
                unitPrice = unitPrice,
                lotId = lotId,
                expectedVersion = expectedVersion,
            ),
        )
    }

    override suspend fun removeLine(draftId: String, lineId: String, expectedVersion: Long): SaleMutationResult = mutationWithConflictRefetch {
        posApi.removeDraftLine(
            accessToken = it,
            draftId = draftId,
            lineId = lineId,
            expectedVersion = expectedVersion,
        )
    }

    override suspend fun validateDraft(draftId: String): AppResult<DraftValidation> = authorized {
        posApi.validateDraft(it, draftId)
    }.let { result ->
        when (result) {
            is AppResult.Success -> AppResult.Success(
                DraftValidation(result.value.issues.map { issue ->
                    DraftIssue(lineId = issue.lineId, code = issue.code, message = issue.message)
                })
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun resolveLots(draftId: String): AppResult<SaleDraft> = authorized {
        posApi.resolveLots(it, draftId)
    }.mapDraft()

    override suspend fun checkout(
        draftId: String,
        mode: CheckoutMode,
        expectedVersion: Long,
        idempotencyKey: String,
    ): AppResult<CheckoutResult> = checkoutMutex.withLock {
        val result = authorized {
            posApi.checkoutDraft(
                accessToken = it,
                draftId = draftId,
                request = CheckoutRequestDto(mode = mode.name, expectedVersion = expectedVersion),
                idempotencyKey = idempotencyKey,
            )
        }
        result.mapCheckout(mode)
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

    private fun AppResult<List<ProductDto>>.mapProducts(): AppResult<List<SaleProduct>> = when (this) {
        is AppResult.Success -> AppResult.Success(value.map { it.toDomain() })
        is AppResult.Failure -> this
    }

    private fun AppResult<ProductDto?>.mapProductOrNull(): AppResult<SaleProduct?> = when (this) {
        is AppResult.Success -> AppResult.Success(value?.toDomain())
        is AppResult.Failure -> this
    }

    private fun AppResult<CheckoutResponseDto>.mapCheckout(mode: CheckoutMode): AppResult<CheckoutResult> = when (this) {
        is AppResult.Success -> AppResult.Success(
            CheckoutResult(
                saleId = value.saleId,
                folio = value.folio,
                total = value.total,
                mode = mode,
            )
        )

        is AppResult.Failure -> this
    }

    private fun DraftDto.toDomain(): SaleDraft = SaleDraft(
        id = id,
        status = status,
        type = type,
        terminalCode = terminalCode,
        version = version,
        lines = lines.map { line ->
            SaleLine(
                id = line.id,
                productId = line.productId,
                productName = line.productName,
                barcode = line.barcode,
                qty = line.qty,
                unit = line.unit,
                unitPrice = line.unitPrice,
                lineTotal = line.lineTotal,
                lotId = line.lotId,
                lotTracked = line.lotTracked,
            )
        },
        totals = SaleTotals(
            subtotal = totals.subtotal,
            tax = totals.tax,
            total = totals.total,
        ),
    )

    private fun ProductDto.toDomain(): SaleProduct = SaleProduct(
        id = id,
        name = name,
        sku = sku,
        barcode = barcode,
        defaultUnit = defaultUnit,
        unitPrice = unitPrice,
        lotTracked = lotTracked,
    )
}
