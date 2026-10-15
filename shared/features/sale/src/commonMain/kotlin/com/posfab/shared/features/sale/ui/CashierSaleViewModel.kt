package com.posfab.shared.features.sale.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.logging.AppLogger
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleLineInput
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SalePriceType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.domain.SaleTotals
import com.posfab.shared.features.sale.domain.roundMoney
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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Clock

class CashierSaleViewModel(
    private val saleUseCases: SaleUseCases,
    private val cardPaymentAdapter: CardPaymentAdapter = NoOpCardPaymentAdapter,
    private val receiptPrinterAdapter: ReceiptPrinterAdapter = NoOpReceiptPrinterAdapter,
    val onNavigateToCash: () -> Unit = {},
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(CashierSaleState())
    val state: StateFlow<CashierSaleState> = _state.asStateFlow()

    private val mutations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val promptScans = ScanBurstDetector()
    private val paidScans = ScanBurstDetector()

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
        promptScans.reset()
        paidScans.reset()
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
            when (val accounts = openAccountsOrNew()) {
                is AppResult.Failure -> _state.update {
                    it.copy(phase = SalePhase.CashUnavailable(SaleErrorText.from(accounts.error)))
                }
                is AppResult.Success -> {
                    val kitchen = saleUseCases.kitchenProducts()
                    val varios = saleUseCases.variosProduct()
                    _state.update {
                        it.copy(
                            phase = SalePhase.Selling,
                            accounts = accounts.value,
                            activeDraftId = accounts.value.first().id,
                            kitchenProducts = (kitchen as? AppResult.Success)?.value.orEmpty(),
                            variosProduct = (varios as? AppResult.Success)?.value,
                            notice = if (kitchen is AppResult.Failure) SaleStrings.KITCHEN_UNAVAILABLE else null,
                        )
                    }
                }
            }
        }
    }

    fun retry() = load()

    fun selectAccount(draftId: Long) {
        _state.update { current ->
            when {
                current.accountById(draftId) == null -> current
                current.phase == SalePhase.Charging -> current.copy(notice = SaleStrings.FINISH_CHARGE_FIRST)
                current.phase != SalePhase.Selling && current.phase != SalePhase.Done -> current
                current.confirm != null -> current
                current.prompt != null -> current
                else -> current.copy(
                    phase = SalePhase.Selling,
                    activeDraftId = draftId,
                    lastResult = null,
                    selectedLineId = null,
                    qtyInput = "",
                    notice = null,
                    errorMessage = null,
                )
            }
        }
    }

    fun openAccount() {
        if (!_state.value.canOpenAccount) return
        enqueue {
            if (!_state.value.canOpenAccount) return@enqueue
            when (val created = saleUseCases.newDraft()) {
                is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(created.error)) }
                is AppResult.Success -> {
                    upsertAccount(created.value)
                    promptScans.reset()
                    paidScans.reset()
                    _state.update { it.activating(created.value.id) }
                }
            }
        }
    }

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
            _state.update { it.copy(errorMessage = SaleStrings.NO_PRICE) }
            return
        }
        if (product.priceType != SalePriceType.PIECE) {
            promptForProduct(product)
            return
        }
        when (_state.value.phase) {
            SalePhase.Done -> {
                val target = _state.value.activeDraftId
                enqueue {
                    resumeSelling()
                    val current = _state.value
                    val draftId = target?.takeIf { current.accountById(it) != null } ?: current.activeDraftId
                    if (current.phase == SalePhase.Selling && draftId != null) addOrIncrement(draftId, product)
                }
            }
            SalePhase.Selling -> enqueueOnAccount { draftId -> addOrIncrement(draftId, product) }
            SalePhase.Charging, SalePhase.Loading -> _state.update { it.copy(notice = SaleStrings.FINISH_CHARGE_FIRST) }
            else -> Unit
        }
    }

    fun onPromptInputChange(value: String) {
        val snapshot = _state.value
        if (snapshot.prompt != null) promptScans.onChange(snapshot.promptInput, value, now())
        _state.update { current ->
            val prompt = current.prompt ?: return@update current
            val kilos = prompt is SalePrompt.Weight && prompt.mode == WeightMode.KILOS
            val sanitized = if (kilos) sanitizeKilos(value) else sanitizeAmount(value, MAX_PROMPT_AMOUNT_DIGITS)
            current.copy(promptInput = sanitized, promptInputRejected = sanitized != value)
        }
    }

    fun setWeightMode(mode: WeightMode) {
        promptScans.reset()
        _state.update { current ->
            val prompt = current.prompt as? SalePrompt.Weight ?: return@update current
            current.copy(prompt = prompt.copy(mode = mode), promptInput = "", promptInputRejected = false)
        }
    }

    fun pickQuickAmount(amount: Int) {
        promptScans.reset()
        _state.update {
            if (it.prompt != null) it.copy(promptInput = amount.toString(), promptInputRejected = false) else it
        }
    }

    fun pickWeight(kilos: Double) {
        promptScans.reset()
        _state.update { current ->
            val prompt = current.prompt as? SalePrompt.Weight ?: return@update current
            current.copy(
                prompt = prompt.copy(mode = WeightMode.KILOS),
                promptInput = SaleStrings.kilosInput(kilos),
                promptInputRejected = false,
            )
        }
    }

    fun dismissPrompt() {
        promptScans.reset()
        _state.update { it.copy(prompt = null, promptInput = "", promptInputRejected = false) }
    }

    fun changeSelectedLine() {
        if (!isSelling()) return
        val line = _state.value.selectedLine ?: return
        if (line.priceType == SalePriceType.PIECE) return
        if (line.priceType == SalePriceType.WEIGHT && !line.qty.isWholeGrams()) {
            val amount = formatAmount(roundMoney(line.qty * line.cashUnitPrice))
            openPrompt(productFrom(line), lineId = line.id, input = amount, mode = WeightMode.PESOS)
            return
        }
        val input = if (line.priceType == SalePriceType.WEIGHT) {
            SaleStrings.kilosInput(line.qty)
        } else {
            formatAmount(line.unitPrice)
        }
        openPrompt(productFrom(line), lineId = line.id, input = input)
    }

    fun confirmPrompt() {
        promptScans.scanOnConfirm(now())?.let { scan ->
            dismissPrompt()
            scope.launch { lookupBarcode(scan.code) }
            return
        }
        if (_state.value.promptInputRejected) {
            _state.update {
                it.copy(promptInput = "", promptInputRejected = false, errorMessage = SaleStrings.INPUT_REJECTED)
            }
            return
        }
        val snapshot = _state.value
        val prompt = snapshot.prompt ?: return
        val value = snapshot.promptValue ?: return
        if (snapshot.phase != SalePhase.Selling) return
        val input = when (prompt) {
            is SalePrompt.OpenAmount -> SaleLineInput.OpenPrice(value)
            is SalePrompt.Weight ->
                if (prompt.mode == WeightMode.PESOS) SaleLineInput.Amount(value) else SaleLineInput.Qty(value)
        }
        val product = prompt.product
        val lineId = prompt.lineId
        _state.update { it.copy(prompt = null, promptInput = "") }
        enqueueOnAccount { draftId ->
            if (lineId == null) {
                addWithRetry(draftId, product) { draft -> saleUseCases.addLine(draft.id, product, input, draft.version) }
            } else {
                val account = _state.value.accountById(draftId) ?: return@enqueueOnAccount
                lineIn(draftId, lineId) ?: return@enqueueOnAccount
                applyMutationResult(
                    draftId = draftId,
                    result = saleUseCases.updateLine(draftId, lineId, input, account.version),
                    selectLine = { lineId },
                    successNotice = SaleStrings.changed(product.name),
                )
            }
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
        val selected = _state.value.selectedLine ?: return
        if (selected.priceType != SalePriceType.PIECE) return
        val lineId = selected.id
        enqueueOnAccount { draftId ->
            val line = lineIn(draftId, lineId) ?: return@enqueueOnAccount
            changeQty(draftId, line, line.qty + 1)
        }
    }

    fun decrementQty() {
        if (!isSelling()) return
        val selected = _state.value.selectedLine ?: return
        if (selected.priceType != SalePriceType.PIECE) return
        val lineId = selected.id
        enqueueOnAccount { draftId ->
            val line = lineIn(draftId, lineId) ?: return@enqueueOnAccount
            if (line.qty > 1) changeQty(draftId, line, (line.qty - 1).coerceAtLeast(1.0))
        }
    }

    fun onQtyInputChange(value: String) {
        _state.update { it.copy(qtyInput = value.filter(Char::isDigit).take(MAX_QTY_DIGITS)) }
    }

    fun applyQty() {
        if (!isSelling()) return
        val snapshot = _state.value
        val line = snapshot.selectedLine ?: return
        if (line.priceType != SalePriceType.PIECE) return
        val qty = snapshot.qtyInput.toIntOrNull()
        if (qty == null || qty < 1) {
            _state.update { it.copy(qtyInput = SaleStrings.qtyText(line.qty)) }
            return
        }
        if (qty.toDouble() == line.qty) return
        enqueueOnAccount { draftId ->
            val current = lineIn(draftId, line.id) ?: return@enqueueOnAccount
            changeQty(draftId, current, qty.toDouble())
        }
    }

    fun removeSelectedLine() {
        if (!isSelling()) return
        val lineId = _state.value.selectedLineId ?: return
        enqueueOnAccount { draftId ->
            val account = _state.value.accountById(draftId) ?: return@enqueueOnAccount
            if (account.lines.none { it.id == lineId }) return@enqueueOnAccount
            val result = saleUseCases.removeLine(draftId, lineId, account.version)
            applyMutationResult(draftId, result, selectLine = { null })
        }
    }

    fun requestClear() {
        val snapshot = _state.value
        if (snapshot.prompt != null) return
        if (snapshot.canCloseEmptyAccount) {
            confirmClear()
            return
        }
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
        enqueueOnAccount { draftId ->
            val account = _state.value.accountById(draftId) ?: return@enqueueOnAccount
            when (val cancelled = saleUseCases.cancelDraft(draftId, account.version)) {
                is AppResult.Failure ->
                    if (cancelled.error is AppError.Conflict) {
                        refreshAfterClearConflict(draftId)
                    } else {
                        showErrorOn(draftId, cancelled.error)
                    }
                is AppResult.Success -> closeAccount(draftId, null)
            }
        }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirm = null) }
    }

    fun startCharge() {
        paidScans.reset()
        _state.update {
            if (it.canCharge) {
                it.copy(
                    phase = SalePhase.Charging,
                    paymentType = SalePaymentType.CASH,
                    paidInput = "",
                    paidInputRejected = false,
                    errorMessage = null,
                    notice = null,
                )
            } else {
                it
            }
        }
    }

    fun selectPaymentType(type: SalePaymentType) {
        paidScans.reset()
        _state.update {
            if (
                it.phase == SalePhase.Charging &&
                !it.isCheckoutInFlight &&
                !it.isPaidLocked &&
                it.paymentType != type
            ) {
                it.copy(paymentType = type, paidInput = "", paidInputRejected = false, errorMessage = null)
            } else {
                it
            }
        }
    }

    fun setChannel(channel: SaleChannel) {
        val snapshot = _state.value
        if (!snapshot.canSetChannel || snapshot.channel == channel) return
        enqueueOnAccount { draftId ->
            val account = _state.value.accountById(draftId) ?: return@enqueueOnAccount
            if (account.channel == channel) return@enqueueOnAccount
            val result = saleUseCases.setChannel(draftId, channel, account.version)
            applyMutationResult(
                draftId = draftId,
                result = result,
                selectLine = { _state.value.selectedLineId },
                successNotice = SaleStrings.channelNotice(channel),
            )
        }
    }

    fun payExact() {
        if (_state.value.isPaidLocked) return
        paidScans.reset()
        _state.update { it.copy(paidInput = formatAmount(it.amountDue), paidInputRejected = false) }
        confirmCharge()
    }

    fun payWith(amount: Int) {
        if (_state.value.isPaidLocked) return
        paidScans.reset()
        _state.update { it.copy(paidInput = amount.toString(), paidInputRejected = false, errorMessage = null) }
    }

    fun onPaidInputChange(value: String) {
        val snapshot = _state.value
        if (snapshot.isPaidLocked) return
        paidScans.onChange(snapshot.paidInput, value, now())
        val sanitized = sanitizeAmount(value, MAX_AMOUNT_DIGITS)
        _state.update { it.copy(paidInput = sanitized, paidInputRejected = sanitized != value, errorMessage = null) }
    }

    fun cancelCharge() {
        paidScans.reset()
        _state.update {
            if (it.phase == SalePhase.Charging && !it.isCheckoutInFlight) {
                it.copy(
                    phase = SalePhase.Selling,
                    paymentType = SalePaymentType.CASH,
                    paidInput = "",
                    paidInputRejected = false,
                    checkoutIdempotencyKey = null,
                    errorMessage = null,
                )
            } else {
                it
            }
        }
    }

    fun confirmCharge() {
        paidScans.scanOnConfirm(now())?.let { scan ->
            _state.update {
                it.copy(
                    paidInput = scan.valueBeforeBurst,
                    paidInputRejected = false,
                    notice = SaleStrings.FINISH_CHARGE_FIRST,
                )
            }
            return
        }
        if (_state.value.paidInputRejected) {
            _state.update {
                it.copy(paidInput = "", paidInputRejected = false, errorMessage = SaleStrings.INPUT_REJECTED)
            }
            return
        }
        val snapshot = _state.value
        if (snapshot.phase != SalePhase.Charging || !snapshot.canConfirmCharge || snapshot.isBusy) return
        val draft = snapshot.draft ?: return
        val paymentType = snapshot.paymentType
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
        scope.launch { runCheckout(draft, paymentType, paid, key) }
    }

    fun startNextSale() {
        enqueue { resumeSelling() }
    }

    private suspend fun runCheckout(draft: SaleDraft, paymentType: SalePaymentType, paid: Double, key: String) {
        try {
            checkout(draft, paymentType, paid, key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failCheckout(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()), key)
        }
    }

    private suspend fun checkout(draft: SaleDraft, paymentType: SalePaymentType, paid: Double, key: String) {
        var chargedDraft = draft
        if (draft.lines.any { it.lotTracked }) {
            when (val resolved = saleUseCases.resolveLots(draft.id)) {
                is AppResult.Failure -> {
                    failCheckout(resolved.error, key)
                    return
                }
                is AppResult.Success -> {
                    chargedDraft = resolved.value
                    upsertAccount(resolved.value)
                }
            }
        }
        when (val result = saleUseCases.checkout(chargedDraft.id, paymentType, paid, key)) {
            is AppResult.Failure ->
                if (result.error is AppError.Conflict) {
                    recoverFromCheckoutConflict(chargedDraft.id)
                } else {
                    failCheckout(result.error, key)
                }
            is AppResult.Success -> {
                val charged = if (result.value.totalCharged <= 0.0) {
                    result.value.copy(totalCharged = chargedDraft.totals.amountFor(paymentType))
                } else {
                    result.value
                }
                _state.update {
                    it.withoutAccount(chargedDraft.id).copy(
                        phase = SalePhase.Done,
                        lastResult = charged,
                        isCheckoutInFlight = false,
                        checkoutIdempotencyKey = null,
                        paymentType = SalePaymentType.CASH,
                        paidInput = "",
                        paidInputRejected = false,
                        selectedLineId = null,
                        qtyInput = "",
                        confirm = null,
                        prompt = null,
                        promptInput = "",
                        promptInputRejected = false,
                        notice = null,
                        errorMessage = null,
                    )
                }
                promptScans.reset()
                paidScans.reset()
                runHardwareHooks(charged, chargedDraft.lines)
            }
        }
    }

    private suspend fun recoverFromCheckoutConflict(draftId: Long) {
        when (val refreshed = saleUseCases.draft(draftId)) {
            is AppResult.Failure -> {
                val keepKey = refreshed.error is AppError.Network
                _state.update {
                    it.copy(
                        isCheckoutInFlight = false,
                        checkoutIdempotencyKey = if (keepKey) it.checkoutIdempotencyKey else null,
                        errorMessage = SaleErrorText.from(refreshed.error),
                    )
                }
            }
            is AppResult.Success -> {
                val draft = refreshed.value
                paidScans.reset()
                _state.update {
                    it.copy(
                        phase = SalePhase.Selling,
                        isCheckoutInFlight = false,
                        checkoutIdempotencyKey = null,
                        paidInput = "",
                        paidInputRejected = false,
                        paymentType = SalePaymentType.CASH,
                    )
                }
                when {
                    draft.isOpen -> {
                        applyDraft(draft, null, null)
                        _state.update { it.copy(errorMessage = SaleStrings.CHECKOUT_CONFLICT) }
                    }
                    draft.status == CHECKED_OUT -> closeAccount(draftId, SaleStrings.POSSIBLY_CHARGED)
                    else -> closeAccount(draftId, SaleStrings.ACCOUNT_CLOSED)
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
                    !product.isSellable -> _state.update { it.copy(errorMessage = SaleStrings.NO_PRICE) }
                    else -> addProduct(product)
                }
            }
        }
    }

    private fun promptForProduct(product: SaleProduct) {
        if (_state.value.confirm != null) return
        when (_state.value.phase) {
            SalePhase.Selling -> openPrompt(product)
            SalePhase.Done -> enqueue {
                resumeSelling()
                if (_state.value.phase == SalePhase.Selling) openPrompt(product)
            }
            SalePhase.Charging, SalePhase.Loading -> _state.update { it.copy(notice = SaleStrings.FINISH_CHARGE_FIRST) }
            else -> Unit
        }
    }

    private fun openPrompt(
        product: SaleProduct,
        lineId: Long? = null,
        input: String = "",
        mode: WeightMode = WeightMode.KILOS,
    ) {
        val prompt = if (product.priceType == SalePriceType.WEIGHT) {
            SalePrompt.Weight(product, lineId, mode)
        } else {
            SalePrompt.OpenAmount(product, lineId)
        }
        promptScans.reset()
        _state.update { current ->
            if (current.confirm != null) {
                current
            } else {
                current.copy(
                    prompt = prompt,
                    promptInput = input,
                    promptInputRejected = false,
                    errorMessage = null,
                    notice = null,
                )
            }
        }
    }

    private fun productFrom(line: SaleLine): SaleProduct = SaleProduct(
        id = line.productId,
        name = line.productName,
        barcode = null,
        unitId = line.unitId,
        cashPrice = line.cashUnitPrice,
        listPrice = line.unitPrice,
        listOnsitePrice = null,
        cashOnsitePrice = null,
        priceType = line.priceType,
        kitchenKind = null,
        lotTracked = line.lotTracked,
    )

    private suspend fun addOrIncrement(draftId: Long, product: SaleProduct) {
        addWithRetry(draftId, product) { draft -> mutateForProduct(draft, product) }
    }

    private suspend fun addWithRetry(
        draftId: Long,
        product: SaleProduct,
        mutation: suspend (SaleDraft) -> SaleMutationResult,
    ) {
        val first = attemptOn(draftId, mutation) ?: return
        val result = if (first is SaleMutationResult.ConflictRefetched) {
            applyDraft(first.draft, _state.value.selectedLineId, null)
            when (val retry = attemptOn(draftId, mutation) ?: return) {
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
            draftId = draftId,
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

    private suspend fun attemptOn(
        draftId: Long,
        mutation: suspend (SaleDraft) -> SaleMutationResult,
    ): SaleMutationResult? {
        val snapshot = _state.value
        if (snapshot.phase != SalePhase.Selling) return null
        val draft = snapshot.accountById(draftId) ?: return null
        return mutation(draft)
    }

    private suspend fun mutateForProduct(draft: SaleDraft, product: SaleProduct): SaleMutationResult {
        val existing = draft.lines.firstOrNull { it.productId == product.id && it.unitId == product.unitId }
        return if (existing != null) {
            saleUseCases.updateLine(draft.id, existing.id, SaleLineInput.Qty(existing.qty + 1), draft.version)
        } else {
            saleUseCases.addLine(draft.id, product, SaleLineInput.Qty(1.0), draft.version)
        }
    }

    private suspend fun changeQty(draftId: Long, line: SaleLine, qty: Double) {
        val draft = _state.value.accountById(draftId) ?: return
        val result = saleUseCases.updateLine(draft.id, line.id, SaleLineInput.Qty(qty), draft.version)
        applyMutationResult(draftId, result, selectLine = { line.id })
    }

    private suspend fun refreshAfterClearConflict(draftId: Long) {
        when (val refreshed = saleUseCases.draft(draftId)) {
            is AppResult.Failure -> showErrorOn(draftId, refreshed.error)
            is AppResult.Success ->
                if (refreshed.value.isOpen) {
                    applyDraft(refreshed.value, null, SaleStrings.SALE_CHANGED)
                } else {
                    closeAccount(draftId, SaleStrings.ACCOUNT_CLOSED)
                }
        }
    }

    private suspend fun applyMutationResult(
        draftId: Long,
        result: SaleMutationResult,
        selectLine: (SaleDraft) -> Long?,
        successNotice: String? = null,
    ) {
        when (result) {
            is SaleMutationResult.Success -> applyDraft(result.draft, selectLine(result.draft), successNotice)
            is SaleMutationResult.ConflictRefetched ->
                applyDraft(result.draft, _state.value.selectedLineId, SaleStrings.SALE_CHANGED)
            is SaleMutationResult.DraftClosed -> closeAccount(draftId, SaleStrings.ACCOUNT_CLOSED)
            is SaleMutationResult.Failure -> showErrorOn(draftId, result.error)
        }
    }

    private fun showErrorOn(draftId: Long, error: AppError) {
        _state.update {
            if (it.activeDraftId == draftId) it.copy(errorMessage = SaleErrorText.from(error), notice = null) else it
        }
    }

    private fun applyDraft(draft: SaleDraft, preferredLineId: Long?, notice: String?) {
        _state.update { current ->
            if (current.accountById(draft.id) == null) return@update current
            val accounts = current.accounts.map { if (it.id == draft.id) draft else it }
            if (current.activeDraftId != draft.id) return@update current.copy(accounts = accounts)
            val selected = draft.lines.firstOrNull { it.id == preferredLineId }
            val promptLineGone = current.prompt?.lineId?.let { id -> draft.lines.none { it.id == id } } == true
            current.copy(
                accounts = accounts,
                selectedLineId = selected?.id,
                qtyInput = selected?.let { line -> SaleStrings.qtyText(line.qty) }.orEmpty(),
                prompt = if (promptLineGone) null else current.prompt,
                promptInput = if (promptLineGone) "" else current.promptInput,
                notice = notice,
                errorMessage = null,
            )
        }
    }

    private fun upsertAccount(draft: SaleDraft) {
        _state.update { current ->
            val accounts = if (current.accountById(draft.id) != null) {
                current.accounts.map { if (it.id == draft.id) draft else it }
            } else {
                current.accounts + draft
            }
            current.copy(accounts = accounts)
        }
    }

    private fun removeAccount(draftId: Long): Long? {
        promptScans.reset()
        return _state.updateAndGet { it.withoutAccount(draftId) }.activeDraftId
    }

    private suspend fun closeAccount(draftId: Long, notice: String?) {
        val wasActive = _state.value.activeDraftId == draftId
        removeAccount(draftId)
        if (wasActive) _state.update { it.copy(notice = notice, errorMessage = null) }
        ensureActiveAccount()
    }

    private suspend fun ensureActiveAccount() {
        if (_state.value.draft != null) return
        promptScans.reset()
        val fallback = _state.value.accounts.firstOrNull()
        if (fallback != null) {
            _state.update { it.switchedTo(fallback.id) }
            return
        }
        when (val created = saleUseCases.newDraft()) {
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(created.error)) }
            is AppResult.Success -> {
                upsertAccount(created.value)
                _state.update { it.switchedTo(created.value.id) }
            }
        }
    }

    private suspend fun resumeSelling() {
        if (_state.value.phase != SalePhase.Done) return
        ensureActiveAccount()
        val active = _state.value.activeDraftId ?: return
        promptScans.reset()
        paidScans.reset()
        _state.update { it.activating(active) }
    }

    private suspend fun openAccountsOrNew(): AppResult<List<SaleDraft>> =
        when (val open = saleUseCases.openDrafts()) {
            is AppResult.Failure -> open
            is AppResult.Success ->
                if (open.value.isNotEmpty()) {
                    open
                } else {
                    when (val created = saleUseCases.newDraft()) {
                        is AppResult.Failure -> created
                        is AppResult.Success -> AppResult.Success(listOf(created.value))
                    }
                }
        }

    private fun CashierSaleState.withoutAccount(draftId: Long): CashierSaleState {
        val index = accounts.indexOfFirst { it.id == draftId }
        if (index < 0) return this
        val remaining = accounts.filterNot { it.id == draftId }
        if (activeDraftId != draftId) return copy(accounts = remaining)
        val next = accounts.getOrNull(index + 1) ?: accounts.getOrNull(index - 1)
        return copy(
            accounts = remaining,
            activeDraftId = next?.id,
            selectedLineId = null,
            qtyInput = "",
            prompt = null,
            promptInput = "",
            promptInputRejected = false,
        )
    }

    private fun CashierSaleState.switchedTo(draftId: Long): CashierSaleState = copy(
        activeDraftId = draftId,
        selectedLineId = null,
        qtyInput = "",
        prompt = null,
        promptInput = "",
        promptInputRejected = false,
    )

    private fun CashierSaleState.activating(draftId: Long): CashierSaleState = copy(
        phase = SalePhase.Selling,
        activeDraftId = draftId,
        lastResult = null,
        selectedLineId = null,
        qtyInput = "",
        prompt = null,
        promptInput = "",
        promptInputRejected = false,
        paymentType = SalePaymentType.CASH,
        paidInput = "",
        paidInputRejected = false,
        checkoutIdempotencyKey = null,
        notice = null,
        errorMessage = null,
    )

    private fun SaleTotals.amountFor(paymentType: SalePaymentType): Double =
        if (paymentType == SalePaymentType.CARD) listTotal else cashTotal

    private fun lineIn(draftId: Long, lineId: Long): SaleLine? =
        _state.value.accountById(draftId)?.lines?.firstOrNull { it.id == lineId }

    private fun enqueue(mutation: suspend () -> Unit) {
        _state.update { it.copy(pendingMutations = it.pendingMutations + 1) }
        mutations.trySend(mutation)
    }

    private fun enqueueOnAccount(mutation: suspend (draftId: Long) -> Unit) {
        val draftId = _state.value.activeDraftId
        if (draftId == null) {
            enqueue {
                if (_state.value.phase != SalePhase.Selling) return@enqueue
                ensureActiveAccount()
                val current = _state.value
                val created = current.activeDraftId ?: return@enqueue
                if (current.phase == SalePhase.Selling) mutation(created)
            }
            return
        }
        enqueue {
            val current = _state.value
            if (current.phase == SalePhase.Selling && current.accountById(draftId) != null) mutation(draftId)
        }
    }

    private fun isSelling(): Boolean = _state.value.phase == SalePhase.Selling

    private fun runHardwareHooks(result: CheckoutResult, lines: List<SaleLine>) {
        scope.launch {
            runCatching { cardPaymentAdapter.onSaleCompleted(result) }
                .onFailure { AppLogger.w("hardware.card_adapter_failed sale_id=${result.saleId}") }
            runCatching { receiptPrinterAdapter.printSaleReceipt(result, lines) }
                .onFailure { AppLogger.w("hardware.printer_adapter_failed sale_id=${result.saleId}") }
        }
    }

    private fun buildCheckoutKey(draftId: Long): String =
        "sale-$draftId-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000, 9_999)}"

    private fun Double.isWholeGrams(): Boolean = abs(this * GRAMS_PER_KILO - round(this * GRAMS_PER_KILO)) < GRAMS_TOLERANCE

    private fun String.isBarcode(): Boolean = length in BARCODE_LENGTH && all(Char::isDigit)

    private fun formatAmount(amount: Double): String = SaleStrings.money(amount).removePrefix("$").replace(",", "")

    private fun sanitizeAmount(value: String, maxWholeDigits: Int): String =
        sanitizeDecimal(value, maxWholeDigits, MAX_AMOUNT_DECIMALS)

    private fun sanitizeKilos(value: String): String = sanitizeDecimal(value, MAX_KILOS_DIGITS, MAX_KILOS_DECIMALS)

    private fun sanitizeDecimal(value: String, maxWholeDigits: Int, maxDecimals: Int): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        if (dot < 0) return filtered.take(maxWholeDigits)
        val whole = filtered.substring(0, dot).take(maxWholeDigits)
        val decimals = filtered.substring(dot + 1).replace(".", "").take(maxDecimals)
        return "$whole.$decimals"
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_LIMIT = 8
        const val MAX_QTY_DIGITS = 4
        const val MAX_AMOUNT_DIGITS = 6
        const val MAX_PROMPT_AMOUNT_DIGITS = 5
        const val MAX_AMOUNT_DECIMALS = 2
        const val MAX_KILOS_DIGITS = 2
        const val MAX_KILOS_DECIMALS = 3
        const val GRAMS_PER_KILO = 1000.0
        const val GRAMS_TOLERANCE = 1e-6
        const val CHECKED_OUT = "CHECKED_OUT"
        val BARCODE_LENGTH = 6..80
    }
}
