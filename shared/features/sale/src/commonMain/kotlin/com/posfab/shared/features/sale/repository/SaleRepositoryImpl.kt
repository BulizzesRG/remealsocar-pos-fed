package com.posfab.shared.features.sale.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SalePriceType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.features.sale.domain.roundMoney
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.pos.AddLineRequestDto
import com.posfab.shared.network.pos.CancelDraftRequestDto
import com.posfab.shared.network.pos.CheckoutRequestDto
import com.posfab.shared.network.pos.DraftChannelRequestDto
import com.posfab.shared.network.pos.DraftCreateRequestDto
import com.posfab.shared.network.pos.DraftDto
import com.posfab.shared.network.pos.DraftLineDto
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.UpdateLineRequestDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.round

class SaleRepositoryImpl(
    private val posApi: PosApi,
    private val catalogApi: CatalogApi,
    private val terminalId: Long,
    private val executor: AuthorizedApiExecutor,
) : SaleRepository {
    private val checkoutMutex = Mutex()

    override suspend fun cashSession(): AppResult<CashSessionCheck> =
        when (val result = authorized { posApi.currentCashSession(it, terminalId) }) {
            is AppResult.Success -> AppResult.Success(if (result.value) CashSessionCheck.Open else CashSessionCheck.Closed)
            is AppResult.Failure -> AppResult.Success(CashSessionCheck.Unavailable(result.error))
        }

    override suspend fun openDrafts(): AppResult<List<SaleDraft>> =
        when (val result = authorized { posApi.listOpenSaleDrafts(it) }) {
            is AppResult.Success -> AppResult.Success(result.value.items.map { it.toDomain() }.sortedBy { it.id })
            is AppResult.Failure -> result
        }

    override suspend fun draft(draftId: Long): AppResult<SaleDraft> =
        authorized { posApi.getDraft(it, draftId) }.mapDraft()

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<SaleProduct>> =
        when (val result = authorized { catalogApi.listProducts(it, query, SEARCH_FETCH_LIMIT, 0, true) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.items.mapNotNull { it.toSellableProduct() }.take(limit),
            )
            is AppResult.Failure -> result
        }

    override suspend fun kitchenProducts(): AppResult<List<SaleProduct>> =
        when (val result = authorized { catalogApi.listProducts(it, null, KITCHEN_LIMIT, 0, true) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.items
                    .mapNotNull { it.toSellableProduct() }
                    .filter { it.kitchenKind != null }
                    .sortedWith(compareBy<SaleProduct> { it.kitchenKind?.ordinal }.thenBy { it.name.lowercase() }),
            )
            is AppResult.Failure -> result
        }

    override suspend fun findProductByBarcode(barcode: String): AppResult<SaleProduct?> =
        when (val result = authorized { posApi.findByBarcode(it, barcode) }) {
            is AppResult.Success -> {
                val dto = result.value.product
                if (!dto.active) return AppResult.Success(null)
                AppResult.Success(
                    SaleProduct(
                        id = dto.id,
                        name = dto.name,
                        barcode = barcode,
                        unitId = result.value.matchedUom?.unitId ?: dto.baseUnitId,
                        cashPrice = dto.prices?.cashTakeaway?.toDoubleOrNull(),
                        listPrice = dto.prices?.listTakeaway?.toDoubleOrNull(),
                        listOnsitePrice = dto.prices?.listOnsite?.toDoubleOrNull(),
                        priceType = SalePriceType.fromRaw(dto.priceType),
                        kitchenKind = SaleKitchenKind.fromRaw(dto.kitchenKind),
                        lotTracked = dto.requiresLotTracking,
                    ),
                )
            }
            is AppResult.Failure -> if (result.error.isBarcodeNotFound()) AppResult.Success(null) else result
        }

    override suspend fun addLine(
        draftId: Long,
        product: SaleProduct,
        qty: Double,
        expectedVersion: Int,
    ): SaleMutationResult = mutationWithConflictRefetch(draftId) {
        posApi.addDraftLine(
            accessToken = it,
            draftId = draftId,
            request = AddLineRequestDto(
                productId = product.id,
                unitId = product.unitId,
                qty = formatQty(qty),
                unitPrice = null,
                expectedVersion = expectedVersion,
            ),
        )
    }

    override suspend fun updateLineQty(
        draftId: Long,
        lineId: Long,
        qty: Double,
        expectedVersion: Int,
    ): SaleMutationResult = mutationWithConflictRefetch(draftId) {
        posApi.updateDraftLine(
            accessToken = it,
            draftId = draftId,
            lineId = lineId,
            request = UpdateLineRequestDto(qty = formatQty(qty), expectedVersion = expectedVersion),
        )
    }

    override suspend fun removeLine(draftId: Long, lineId: Long, expectedVersion: Int): SaleMutationResult =
        mutationWithConflictRefetch(draftId) {
            posApi.removeDraftLine(
                accessToken = it,
                draftId = draftId,
                lineId = lineId,
                expectedVersion = expectedVersion,
            )
        }

    override suspend fun resolveLots(draftId: Long): AppResult<SaleDraft> =
        when (val result = authorized { posApi.resolveLots(it, draftId) }) {
            is AppResult.Success ->
                if (result.value.ok) {
                    authorized { posApi.getDraft(it, draftId) }.mapDraft()
                } else {
                    AppResult.Failure(
                        AppError.Validation(result.value.issues.firstOrNull()?.message ?: INSUFFICIENT_STOCK),
                    )
                }
            is AppResult.Failure -> result
        }

    override suspend fun cancelDraft(draftId: Long, expectedVersion: Int): AppResult<Unit> =
        authorized { posApi.cancelDraft(it, draftId, CancelDraftRequestDto(expectedVersion)) }

    override suspend fun setChannel(draftId: Long, channel: SaleChannel, expectedVersion: Int): SaleMutationResult =
        mutationWithConflictRefetch(draftId) {
            posApi.setDraftChannel(it, draftId, DraftChannelRequestDto(channel.name, expectedVersion))
        }

    override suspend fun checkout(
        draftId: Long,
        paymentType: SalePaymentType,
        paidAmount: Double,
        idempotencyKey: String,
    ): AppResult<CheckoutResult> = checkoutMutex.withLock {
        val result = authorized {
            posApi.checkoutDraft(
                accessToken = it,
                draftId = draftId,
                request = CheckoutRequestDto(
                    paymentStatus = PAYMENT_STATUS_PAID,
                    paymentType = paymentType.name,
                    paidAmount = formatMoney(paidAmount),
                    idempotencyKey = idempotencyKey,
                ),
            )
        }
        when (result) {
            is AppResult.Success -> AppResult.Success(
                CheckoutResult(
                    saleId = result.value.id,
                    folio = result.value.folio.orEmpty(),
                    paymentType = paymentType,
                    totalCharged = result.value.totalAmount?.toDoubleOrNull() ?: 0.0,
                    paidAmount = paidAmount,
                ),
            )
            is AppResult.Failure -> result
        }
    }

    override suspend fun newDraft(): AppResult<SaleDraft> {
        val created = authorized {
            posApi.createDraft(it, DraftCreateRequestDto(type = DRAFT_TYPE_SALE, terminalId = terminalId))
        }
        return when (created) {
            is AppResult.Success -> authorized { posApi.getDraft(it, created.value.id) }.mapDraft()
            is AppResult.Failure -> created
        }
    }

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private suspend fun mutationWithConflictRefetch(
        draftId: Long,
        block: suspend (String) -> AppResult<DraftDto>,
    ): SaleMutationResult =
        when (val result = authorized(block)) {
            is AppResult.Success -> SaleMutationResult.Success(result.value.toDomain())
            is AppResult.Failure ->
                if (result.error is AppError.Conflict) {
                    refetchAfterConflict(draftId, result.error)
                } else {
                    SaleMutationResult.Failure(result.error)
                }
        }

    private suspend fun refetchAfterConflict(draftId: Long, conflict: AppError): SaleMutationResult =
        when (val refetch = draft(draftId)) {
            is AppResult.Success ->
                if (refetch.value.isOpen) {
                    SaleMutationResult.ConflictRefetched(refetch.value)
                } else {
                    SaleMutationResult.DraftClosed(refetch.value)
                }
            is AppResult.Failure -> SaleMutationResult.Failure(conflict)
        }

    private fun AppResult<DraftDto>.mapDraft(): AppResult<SaleDraft> = when (this) {
        is AppResult.Success -> AppResult.Success(value.toDomain())
        is AppResult.Failure -> this
    }

    private fun DraftDto.toDomain(): SaleDraft = SaleDraft(
        id = id,
        status = status,
        version = version,
        lines = lines.map { it.toDomain() },
        totals = SaleTotals(
            cashTotal = (totals.cashTotal ?: totals.total).toDoubleOrNull() ?: 0.0,
            listTotal = totals.total.toDoubleOrNull() ?: 0.0,
            cashDiscount = totals.cashDiscount.toDoubleOrNull() ?: 0.0,
        ),
        channel = SaleChannel.fromRaw(channel),
    )

    private fun DraftLineDto.toDomain(): SaleLine {
        val quantity = qty.toDoubleOrNull() ?: 0.0
        val listPrice = unitPrice?.toDoubleOrNull() ?: cashUnitPrice?.toDoubleOrNull() ?: 0.0
        return SaleLine(
            id = id,
            productId = productId,
            productName = productName,
            qty = quantity,
            unitId = unitId,
            unitPrice = listPrice,
            cashUnitPrice = cashUnitPrice?.toDoubleOrNull() ?: listPrice,
            lineTotal = lineTotal.toDoubleOrNull() ?: roundMoney(quantity * listPrice),
            lotId = lotId,
            lotTracked = lotTracked,
        )
    }

    private fun ProductListItemDto.toSellableProduct(): SaleProduct? {
        val productId = id.toLongOrNull() ?: return null
        val cashPrice = prices?.cashTakeaway?.toDoubleOrNull() ?: return null
        val type = SalePriceType.fromRaw(priceType)
        if (type != SalePriceType.PIECE) return null
        return SaleProduct(
            id = productId,
            name = name,
            barcode = barcode,
            unitId = baseUnitId,
            cashPrice = cashPrice,
            listPrice = prices?.listTakeaway?.toDoubleOrNull(),
            listOnsitePrice = prices?.listOnsite?.toDoubleOrNull(),
            priceType = type,
            kitchenKind = SaleKitchenKind.fromRaw(kitchenKind),
            lotTracked = lotTracked,
        )
    }

    private fun AppError.isBarcodeNotFound(): Boolean =
        this is AppError.Validation && message.contains(BARCODE_NOT_FOUND, ignoreCase = true)

    private fun formatQty(qty: Double): String {
        val whole = round(qty)
        return if (abs(qty - whole) < QTY_EPSILON) whole.toLong().toString() else qty.toString()
    }

    private fun formatMoney(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private companion object {
        const val DRAFT_TYPE_SALE = "SALE"
        const val PAYMENT_STATUS_PAID = "PAID"
        const val BARCODE_NOT_FOUND = "Barcode not found"
        const val INSUFFICIENT_STOCK = "Insufficient stock"
        const val KITCHEN_LIMIT = 200
        const val SEARCH_FETCH_LIMIT = 50
        const val QTY_EPSILON = 1e-9
    }
}
