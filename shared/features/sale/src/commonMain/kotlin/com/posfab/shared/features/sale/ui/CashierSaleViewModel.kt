package com.posfab.shared.features.sale.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.logging.AppLogger
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.usecase.SaleUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

class CashierSaleViewModel(
    private val saleUseCases: SaleUseCases,
    private val cardPaymentAdapter: CardPaymentAdapter = NoOpCardPaymentAdapter,
    private val receiptPrinterAdapter: ReceiptPrinterAdapter = NoOpReceiptPrinterAdapter,
    val onNavigateToCash: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(CashierSaleState())
    val state: StateFlow<CashierSaleState> = _state.asStateFlow()

    private val mutations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        scope.launch {
            for (mutation in mutations) {
                try {
                    mutation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AppLogger.w("sale.mutation_failed reason=${failure::class.simpleName}")
                }
                _state.update { it.copy(pendingMutations = (it.pendingMutations - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun load() {
        if (_state.value.isCheckoutInFlight) return
        loadJob?.cancel()
        searchJob?.cancel()
        loadJob = scope.launch {
            _state.update { CashierSaleState(phase = SalePhase.Loading, pendingMutations = it.pendingMutations) }
            val phaseAfterCheck = when (val session = saleUseCases.cashSession()) {
                is AppResult.Failure -> SalePhase.CashUnavailable(SaleErrorText.from(session.error))
                is AppResult.Success -> when (val check = session.value) {
                    CashSessionCheck.Open -> null
                    CashSessionCheck.Closed -> SalePhase.CashClosed
                    is CashSessionCheck.Unavailable -> SalePhase.CashUnavailable(SaleErrorText.from(check.error))
                }
            }
            if (phaseAfterCheck != null) {
                _state.update { it.copy(phase = phaseAfterCheck) }
                return@launch
            }
            when (val draft = saleUseCases.currentOrNewDraft()) {
                is AppResult.Failure -> _state.update {
                    it.copy(phase = SalePhase.CashUnavailable(SaleErrorText.from(draft.error)))
                }
                is AppResult.Success -> {
                    val kitchen = saleUseCases.kitchenProducts()
                    _state.update {
                        it.copy(
                            phase = SalePhase.Selling,
                            draft = draft.value,
                            kitchenProducts = (kitchen as? AppResult.Success)?.value.orEmpty(),
                            notice = if (kitchen is AppResult.Failure) SaleStrings.KITCHEN_UNAVAILABLE else null,
                        )
                    }
                }
            }
        }
    }

    fun retry() = load()

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, errorMessage = null) }
        searchJob?.cancel()
        val query = value.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            search(query)
        }
    }

    fun onSubmit() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        if (query.isBarcode()) {
            _state.update { it.copy(query = "", searchResults = emptyList(), errorMessage = null, notice = null) }
            scope.launch { lookupBarcode(query) }
        } else {
            searchJob = scope.launch { search(query) }
        }
    }

    fun addProduct(product: SaleProduct) {
        if (!product.isSellable) {
            _state.update { it.copy(errorMessage = SaleStrings.NOT_SELLABLE_YET) }
            return
        }
        when (_state.value.phase) {
            SalePhase.Done -> {
                startNextSale()
                enqueue { addOrIncrement(product) }
            }
            SalePhase.Selling -> enqueue { addOrIncrement(product) }
            SalePhase.Charging, SalePhase.Loading -> _state.update { it.copy(notice = SaleStrings.FINISH_CHARGE_FIRST) }
            else -> Unit
        }
    }

    fun selectLine(lineId: Long) {
        _state.update { current ->
            val line = current.draft?.lines?.firstOrNull { it.id == lineId } ?: return@update current
            current.copy(selectedLineId = line.id, qtyInput = SaleStrings.qtyText(line.qty))
        }
    }

    fun incrementQty() {
        if (!isSelling()) return
        enqueueWhileSelling {
            val line = _state.value.selectedLine ?: return@enqueueWhileSelling
            changeQty(line, line.qty + 1)
        }
    }

    fun decrementQty() {
        if (!isSelling()) return
        enqueueWhileSelling {
            val line = _state.value.selectedLine ?: return@enqueueWhileSelling
            if (line.qty > 1) changeQty(line, (line.qty - 1).coerceAtLeast(1.0))
        }
    }

    fun onQtyInputChange(value: String) {
        _state.update { it.copy(qtyInput = value.filter(Char::isDigit).take(MAX_QTY_DIGITS)) }
    }

    fun applyQty() {
        if (!isSelling()) return
        val snapshot = _state.value
        val line = snapshot.selectedLine ?: return
        val qty = snapshot.qtyInput.toIntOrNull()
        if (qty == null || qty < 1) {
            _state.update { it.copy(qtyInput = SaleStrings.qtyText(line.qty)) }
            return
        }
        if (qty.toDouble() == line.qty) return
        enqueueWhileSelling {
            val current = _state.value.selectedLine ?: return@enqueueWhileSelling
            changeQty(current, qty.toDouble())
        }
    }

    fun removeSelectedLine() {
        if (!isSelling()) return
        enqueueWhileSelling {
            val snapshot = _state.value
            val draft = snapshot.draft ?: return@enqueueWhileSelling
            val line = snapshot.selectedLine ?: return@enqueueWhileSelling
            val result = saleUseCases.removeLine(draft.id, line.id, draft.version)
            applyMutationResult(result, selectLine = { null })
        }
    }

    fun requestClear() {
        _state.update {
            if (it.phase == SalePhase.Selling && it.draft?.lines?.isNotEmpty() == true) {
                it.copy(confirm = SaleConfirm.ClearCart)
            } else {
                it
            }
        }
    }

    fun confirmClear() {
        _state.update { it.copy(confirm = null) }
        enqueueWhileSelling {
            val draft = _state.value.draft ?: return@enqueueWhileSelling
            when (val cancelled = saleUseCases.cancelDraft(draft.id, draft.version)) {
                is AppResult.Failure ->
                    if (cancelled.error is AppError.Conflict) {
                        replaceDraft(saleUseCases.currentOrNewDraft(), SaleStrings.SALE_CHANGED)
                    } else {
                        _state.update { it.copy(errorMessage = SaleErrorText.from(cancelled.error)) }
                    }
                is AppResult.Success -> replaceDraft(saleUseCases.newDraft(), null)
            }
        }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirm = null) }
    }

    fun startCharge() {
        _state.update {
            if (it.canCharge) {
                it.copy(phase = SalePhase.Charging, paidInput = "", errorMessage = null, notice = null)
            } else {
                it
            }
        }
    }

    fun payExact() {
        if (_state.value.isPaidLocked) return
        _state.update { it.copy(paidInput = formatAmount(it.cashTotal)) }
        confirmCharge()
    }

    fun payWith(amount: Int) {
        if (_state.value.isPaidLocked) return
        _state.update { it.copy(paidInput = amount.toString(), errorMessage = null) }
    }

    fun onPaidInputChange(value: String) {
        if (_state.value.isPaidLocked) return
        _state.update { it.copy(paidInput = sanitizeAmount(value), errorMessage = null) }
    }

    fun cancelCharge() {
        _state.update {
            if (it.phase == SalePhase.Charging && !it.isCheckoutInFlight) {
                it.copy(
                    phase = SalePhase.Selling,
                    paidInput = "",
                    checkoutIdempotencyKey = null,
                    errorMessage = null,
                )
            } else {
                it
            }
        }
    }

    fun confirmCharge() {
        val snapshot = _state.value
        if (snapshot.phase != SalePhase.Charging || !snapshot.canConfirmCharge || snapshot.isBusy) return
        val draft = snapshot.draft ?: return
        val paid = snapshot.paidAmount ?: return
        val key = snapshot.checkoutIdempotencyKey ?: buildCheckoutKey(draft.id)
        val previous = _state.getAndUpdate {
            if (it.isCheckoutInFlight) {
                it
            } else {
                it.copy(isCheckoutInFlight = true, checkoutIdempotencyKey = key, errorMessage = null)
            }
        }
        if (previous.isCheckoutInFlight) return
        scope.launch { runCheckout(draft, paid, key) }
    }

    fun startNextSale() {
        enqueue {
            if (_state.value.phase != SalePhase.Done) return@enqueue
            when (val next = saleUseCases.newDraft()) {
                is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(next.error)) }
                is AppResult.Success -> _state.update {
                    it.copy(
                        phase = SalePhase.Selling,
                        draft = next.value,
                        lastResult = null,
                        query = "",
                        searchResults = emptyList(),
                        selectedLineId = null,
                        qtyInput = "",
                        paidInput = "",
                        checkoutIdempotencyKey = null,
                        notice = null,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private suspend fun runCheckout(draft: SaleDraft, paid: Double, key: String) {
        try {
            checkout(draft, paid, key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failCheckout(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()), key)
        }
    }

    private suspend fun checkout(draft: SaleDraft, paid: Double, key: String) {
        var chargedDraft = draft
        if (draft.lines.any { it.lotTracked }) {
            when (val resolved = saleUseCases.resolveLots(draft.id)) {
                is AppResult.Failure -> {
                    failCheckout(resolved.error, key)
                    return
                }
                is AppResult.Success -> {
                    chargedDraft = resolved.value
                    _state.update { it.copy(draft = resolved.value) }
                }
            }
        }
        when (val result = saleUseCases.checkoutCash(chargedDraft.id, paid, key)) {
            is AppResult.Failure ->
                if (result.error is AppError.Conflict) {
                    recoverFromCheckoutConflict()
                } else {
                    failCheckout(result.error, key)
                }
            is AppResult.Success -> {
                val charged = if (result.value.cashTotal <= 0.0) {
                    result.value.copy(cashTotal = chargedDraft.totals.cashTotal)
                } else {
                    result.value
                }
                _state.update {
                    it.copy(
                        phase = SalePhase.Done,
                        draft = null,
                        lastResult = charged,
                        isCheckoutInFlight = false,
                        checkoutIdempotencyKey = null,
                        paidInput = "",
                        selectedLineId = null,
                        qtyInput = "",
                        confirm = null,
                        notice = null,
                        errorMessage = null,
                    )
                }
                runHardwareHooks(charged, chargedDraft.lines)
            }
        }
    }

    private suspend fun recoverFromCheckoutConflict() {
        when (val refreshed = saleUseCases.currentOrNewDraft()) {
            is AppResult.Failure -> _state.update {
                it.copy(
                    isCheckoutInFlight = false,
                    checkoutIdempotencyKey = null,
                    errorMessage = SaleErrorText.from(refreshed.error),
                )
            }
            is AppResult.Success -> {
                val possiblyCharged = refreshed.value.lines.isEmpty()
                _state.update {
                    it.copy(
                        phase = SalePhase.Selling,
                        draft = refreshed.value,
                        isCheckoutInFlight = false,
                        checkoutIdempotencyKey = null,
                        paidInput = "",
                        selectedLineId = null,
                        qtyInput = "",
                        notice = if (possiblyCharged) SaleStrings.POSSIBLY_CHARGED else null,
                        errorMessage = if (possiblyCharged) null else SaleStrings.CHECKOUT_CONFLICT,
                    )
                }
            }
        }
    }

    private fun failCheckout(error: AppError, key: String) {
        val keepKey = error is AppError.Network
        _state.update {
            it.copy(
                isCheckoutInFlight = false,
                checkoutIdempotencyKey = if (keepKey) key else null,
                errorMessage = SaleErrorText.from(error),
            )
        }
    }

    private suspend fun search(query: String) {
        when (val result = saleUseCases.searchProducts(query, SEARCH_LIMIT)) {
            is AppResult.Success -> _state.update { it.copy(searchResults = result.value) }
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(result.error)) }
        }
    }

    private suspend fun lookupBarcode(code: String) {
        when (val lookup = saleUseCases.findProductByBarcode(code)) {
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(lookup.error)) }
            is AppResult.Success -> {
                val product = lookup.value
                when {
                    product == null -> _state.update { it.copy(errorMessage = SaleStrings.notFoundWithCode(code)) }
                    !product.isSellable -> _state.update { it.copy(errorMessage = SaleStrings.NOT_SELLABLE_YET) }
                    else -> addProduct(product)
                }
            }
        }
    }

    private suspend fun addOrIncrement(product: SaleProduct) {
        val first = mutateForProduct(product) ?: return
        val result = if (first is SaleMutationResult.ConflictRefetched) {
            applyDraft(first.draft, _state.value.selectedLineId, null)
            when (val retry = mutateForProduct(product) ?: return) {
                is SaleMutationResult.ConflictRefetched -> {
                    applyDraft(retry.draft, _state.value.selectedLineId, SaleStrings.notAdded(product.name))
                    return
                }
                else -> retry
            }
        } else {
            first
        }
        applyMutationResult(
            result = result,
            selectLine = { updated ->
                updated.lines
                    .filter { it.productId == product.id && it.unitId == product.unitId }
                    .maxByOrNull { it.id }
                    ?.id
            },
            successNotice = SaleStrings.added(product.name),
        )
    }

    private suspend fun mutateForProduct(product: SaleProduct): SaleMutationResult? {
        val snapshot = _state.value
        if (snapshot.phase != SalePhase.Selling) return null
        val draft = snapshot.draft ?: return null
        val existing = draft.lines.firstOrNull { it.productId == product.id && it.unitId == product.unitId }
        return if (existing != null) {
            saleUseCases.updateLineQty(draft.id, existing.id, existing.qty + 1, draft.version)
        } else {
            saleUseCases.addLine(draft.id, product, 1.0, draft.version)
        }
    }

    private suspend fun changeQty(line: SaleLine, qty: Double) {
        val draft = _state.value.draft ?: return
        val result = saleUseCases.updateLineQty(draft.id, line.id, qty, draft.version)
        applyMutationResult(result, selectLine = { line.id })
    }

    private fun replaceDraft(refreshed: AppResult<SaleDraft>, notice: String?) {
        when (refreshed) {
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(refreshed.error)) }
            is AppResult.Success -> _state.update {
                it.copy(
                    draft = refreshed.value,
                    selectedLineId = null,
                    qtyInput = "",
                    notice = notice,
                    errorMessage = null,
                )
            }
        }
    }

    private fun applyMutationResult(
        result: SaleMutationResult,
        selectLine: (SaleDraft) -> Long?,
        successNotice: String? = null,
    ) {
        when (result) {
            is SaleMutationResult.Success -> applyDraft(result.draft, selectLine(result.draft), successNotice)
            is SaleMutationResult.ConflictRefetched ->
                applyDraft(result.draft, _state.value.selectedLineId, SaleStrings.SALE_CHANGED)
            is SaleMutationResult.Failure -> _state.update {
                it.copy(errorMessage = SaleErrorText.from(result.error), notice = null)
            }
        }
    }

    private fun applyDraft(draft: SaleDraft, preferredLineId: Long?, notice: String?) {
        val selected = draft.lines.firstOrNull { it.id == preferredLineId }
        _state.update {
            it.copy(
                draft = draft,
                selectedLineId = selected?.id,
                qtyInput = selected?.let { line -> SaleStrings.qtyText(line.qty) }.orEmpty(),
                notice = notice,
                errorMessage = null,
            )
        }
    }

    private fun enqueue(mutation: suspend () -> Unit) {
        _state.update { it.copy(pendingMutations = it.pendingMutations + 1) }
        mutations.trySend(mutation)
    }

    private fun enqueueWhileSelling(mutation: suspend () -> Unit) {
        enqueue {
            if (_state.value.phase == SalePhase.Selling) mutation()
        }
    }

    private fun isSelling(): Boolean = _state.value.phase == SalePhase.Selling

    private fun runHardwareHooks(result: CashCheckoutResult, lines: List<SaleLine>) {
        scope.launch {
            runCatching { cardPaymentAdapter.onSaleCompleted(result) }
                .onFailure { AppLogger.w("hardware.card_adapter_failed sale_id=${result.saleId}") }
            runCatching { receiptPrinterAdapter.printSaleReceipt(result, lines) }
                .onFailure { AppLogger.w("hardware.printer_adapter_failed sale_id=${result.saleId}") }
        }
    }

    private fun buildCheckoutKey(draftId: Long): String =
        "sale-$draftId-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000, 9_999)}"

    private fun String.isBarcode(): Boolean = length in BARCODE_LENGTH && all(Char::isDigit)

    private fun formatAmount(amount: Double): String = SaleStrings.money(amount).removePrefix("$").replace(",", "")

    private fun sanitizeAmount(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        if (dot < 0) return filtered.take(MAX_AMOUNT_DIGITS)
        val whole = filtered.substring(0, dot).take(MAX_AMOUNT_DIGITS)
        val decimals = filtered.substring(dot + 1).replace(".", "").take(2)
        return "$whole.$decimals"
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_LIMIT = 8
        const val MAX_QTY_DIGITS = 4
        const val MAX_AMOUNT_DIGITS = 6
        val BARCODE_LENGTH = 6..80
    }
}
