package com.posfab.shared.features.sale.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.input.PhoneInput
import com.posfab.shared.core.input.ScanBurstDetector
import com.posfab.shared.core.logging.AppLogger
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashSessionCheck
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleCustomer
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
    private var customerSearchJob: Job? = null
    private val searchScans = ScanBurstDetector()
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
        if (_state.value.isCheckoutInFlight || _state.value.credit.isAbonoInFlight) return
        loadJob?.cancel()
        searchJob?.cancel()
        customerSearchJob?.cancel()
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
                            activeDraftId = accounts.value.first { draft -> !draft.isDelivery }.id,
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
                current.abonoOverlayOpen -> current
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
        searchScans.onChange(_state.value.query, value, now())
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
        val scanned = searchScans.scanOnConfirm(now())?.code
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        val code = scanned ?: query.takeIf { it.isBarcode() }
        if (code != null) {
            if (blockedByDelivery()) {
                _state.update { it.copy(query = "", searchResults = emptyList()) }
                return
            }
            _state.update { it.copy(query = "", searchResults = emptyList(), errorMessage = null, notice = null) }
            val scannedOn = scannedAccount()
            scope.launch { lookupBarcode(code, scannedOn) }
        } else {
            searchJob = scope.launch { search(query) }
        }
    }

    fun addProduct(product: SaleProduct) {
        if (_state.value.abonoOverlayOpen) return
        if (blockedByDelivery()) return
        if (!product.isSellable) {
            _state.update { it.copy(errorMessage = SaleStrings.NO_PRICE) }
            return
        }
        clearSearch()
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
        if (blockedByDelivery()) return
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
            val scannedOn = scannedAccount()
            scope.launch { lookupBarcode(scan.code, scannedOn) }
            return
        }
        if (_state.value.promptInputRejected) {
            _state.update {
                it.copy(promptInput = "", promptInputRejected = false, errorMessage = SaleStrings.INPUT_REJECTED)
            }
            return
        }
        if (blockedByDelivery()) {
            dismissPrompt()
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
        if (blockedByDelivery()) return
        _state.update { current ->
            val line = current.draft?.lines?.firstOrNull { it.id == lineId } ?: return@update current
            current.copy(selectedLineId = line.id, qtyInput = SaleStrings.qtyText(line.qty))
        }
    }

    fun incrementQty() {
        if (blockedByDelivery()) return
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
        if (blockedByDelivery()) return
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
        if (blockedByDelivery()) return
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
        if (blockedByDelivery()) return
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
        if (blockedByDelivery()) return
        val snapshot = _state.value
        if (snapshot.prompt != null || snapshot.abonoOverlayOpen) return
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
        if (blockedByDelivery()) return
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
        if (_state.value.isBusy) {
            enqueue { openCharge() }
            return
        }
        openCharge()
    }

    private fun openCharge() {
        _state.update {
            if (it.canCharge) {
                val paysWith = it.draft?.takeIf { draft -> draft.isDelivery }?.paysWith
                it.copy(
                    phase = SalePhase.Charging,
                    paymentType = SalePaymentType.CASH,
                    paidInput = paysWith?.let(::paysWithInput).orEmpty(),
                    paidInputRejected = false,
                    credit = CreditState(),
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
        val previous = _state.getAndUpdate {
            if (
                it.phase == SalePhase.Charging &&
                type in it.paymentOptions &&
                !it.isCheckoutInFlight &&
                !it.isPaidLocked &&
                !it.credit.isAbonoBusy &&
                it.paymentType != type
            ) {
                it.copy(
                    paymentType = type,
                    paidInput = "",
                    paidInputRejected = false,
                    credit = CreditState(),
                    errorMessage = null,
                )
            } else {
                it
            }
        }
        if (previous.paymentType != _state.value.paymentType) customerSearchJob?.cancel()
    }

    fun onCustomerQueryChange(value: String) {
        if (!_state.value.isPickerActive()) return
        customerSearchJob?.cancel()
        _state.update {
            it.copy(credit = it.credit.copy(pickerQuery = value, isSearching = false), errorMessage = null)
        }
        val query = value.trim()
        if (query.length < MIN_PICKER_QUERY_LENGTH) {
            _state.update { it.copy(credit = it.credit.copy(pickerResults = emptyList(), searchedQuery = null)) }
            return
        }
        customerSearchJob = scope.launch {
            delay(CUSTOMER_SEARCH_DEBOUNCE_MS)
            searchCustomers(query, pickSingleMatch = false)
        }
    }

    fun submitCustomerQuery() {
        val snapshot = _state.value
        if (!snapshot.isPickerActive()) return
        val query = snapshot.credit.pickerQuery.trim()
        if (query.isEmpty()) return
        customerSearchJob?.cancel()
        customerSearchJob = scope.launch { searchCustomers(query, pickSingleMatch = true) }
    }

    fun selectCustomer(customer: SaleCustomer) {
        if (!_state.value.isPickerActive()) return
        customerSearchJob?.cancel()
        _state.update {
            if (it.isPickerActive()) it.copy(credit = it.credit.selecting(customer), errorMessage = null) else it
        }
    }

    fun changeCustomer() {
        _state.update { current ->
            val credit = current.credit
            val blocked = credit.customer == null ||
                credit.isAbonoBusy ||
                current.isPaidLocked ||
                current.isCheckoutInFlight
            if (blocked) current else current.copy(credit = CreditState(), errorMessage = null)
        }
    }

    fun startNewCustomer() {
        if (!_state.value.isPickerActive()) return
        customerSearchJob?.cancel()
        _state.update { current ->
            if (!current.isPickerActive()) return@update current
            val typed = current.credit.pickerQuery.trim()
            val name = if (typed.all(Char::isDigit)) "" else typed
            current.copy(
                credit = current.credit.copy(
                    newCustomer = NewCustomerForm(name = name),
                    pickerResults = emptyList(),
                    isSearching = false,
                    searchedQuery = null,
                ),
                errorMessage = null,
            )
        }
    }

    fun onNewCustomerName(value: String) {
        _state.update { current ->
            val form = current.credit.newCustomer
            if (form == null || form.isSaving) {
                current
            } else {
                current.copy(credit = current.credit.copy(newCustomer = form.copy(name = value)), errorMessage = null)
            }
        }
    }

    fun onNewCustomerNickname(value: String) {
        _state.update { current ->
            val form = current.credit.newCustomer
            if (form == null || form.isSaving) {
                current
            } else {
                current.copy(credit = current.credit.copy(newCustomer = form.copy(nickname = value)), errorMessage = null)
            }
        }
    }

    fun onNewCustomerPhone(value: String) {
        _state.update { current ->
            val form = current.credit.newCustomer
            if (form == null || form.isSaving) {
                current
            } else {
                current.copy(credit = current.credit.copy(newCustomer = form.copy(phone = PhoneInput.sanitize(value))), errorMessage = null)
            }
        }
    }

    fun saveNewCustomer() {
        val form = _state.value.credit.newCustomer ?: return
        if (form.isSaving) return
        val name = form.name.trim()
        if (name.isEmpty() || name.all { it.isDigit() || it.isWhitespace() }) {
            _state.update { it.copy(errorMessage = SaleStrings.CUSTOMER_NAME_REQUIRED) }
            return
        }
        if (!PhoneInput.isComplete(form.phone.trim())) {
            _state.update { it.copy(errorMessage = SaleStrings.CUSTOMER_PHONE_INVALID) }
            return
        }
        val nickname = form.nickname.trim().takeIf { it.isNotEmpty() }
        val phone = form.phone.trim().takeIf { it.isNotEmpty() }
        val previous = _state.getAndUpdate { current ->
            val pending = current.credit.newCustomer
            if (pending == null || pending.isSaving) {
                current
            } else {
                current.copy(credit = current.credit.copy(newCustomer = pending.copy(isSaving = true)), errorMessage = null)
            }
        }
        if (previous.credit.newCustomer?.isSaving != false) return
        scope.launch {
            val result = guarded { saleUseCases.createCustomer(name, nickname, phone) }
            _state.update { current ->
                val pending = current.credit.newCustomer ?: return@update current
                when (result) {
                    is AppResult.Success -> current.copy(
                        credit = current.credit.copy(newCustomer = null).selecting(result.value),
                        errorMessage = null,
                    )
                    is AppResult.Failure -> current.copy(
                        credit = current.credit.copy(newCustomer = pending.copy(isSaving = false)),
                        errorMessage = if (result.error is AppError.Conflict) {
                            SaleStrings.CUSTOMER_SAVE_FAILED
                        } else {
                            SaleErrorText.from(result.error)
                        },
                    )
                }
            }
        }
    }

    fun cancelNewCustomer() {
        _state.update { current ->
            val form = current.credit.newCustomer
            if (form == null || form.isSaving) {
                current
            } else {
                current.copy(credit = current.credit.copy(newCustomer = null), errorMessage = null)
            }
        }
    }

    fun onAbonoInputChange(value: String) {
        val credit = _state.value.credit
        if (credit.isAbonoLocked || credit.isAbonoInFlight) return
        val sanitized = sanitizeAmount(value, MAX_AMOUNT_DIGITS)
        _state.update {
            it.copy(
                credit = it.credit.copy(abonoInput = sanitized, abonoInputRejected = sanitized != value),
                errorMessage = null,
            )
        }
    }

    fun payAllDebt() {
        _state.update { current ->
            val credit = current.credit
            val customer = credit.customer
            if (customer == null || !credit.showsAbonoRow || credit.isAbonoLocked || credit.isAbonoInFlight) {
                current
            } else {
                current.copy(
                    credit = credit.copy(abonoInput = formatAmount(customer.balance), abonoInputRejected = false),
                    errorMessage = null,
                )
            }
        }
    }

    fun confirmAbono() {
        val snapshot = _state.value
        if (!snapshot.isCustomerStepActive) return
        val credit = snapshot.credit
        if (credit.abonoInputRejected) {
            _state.update {
                it.copy(
                    credit = it.credit.copy(abonoInput = "", abonoInputRejected = false),
                    errorMessage = SaleStrings.INPUT_REJECTED,
                )
            }
            return
        }
        val customer = credit.customer ?: return
        if (!credit.showsAbonoRow || snapshot.isCheckoutInFlight) return
        val amount = credit.abonoAmount ?: return
        if (roundMoney(amount) > roundMoney(customer.balance)) {
            _state.update { it.copy(errorMessage = SaleStrings.ABONO_EXCEEDS_DEBT) }
            return
        }
        val key = credit.abonoKey ?: buildAbonoKey(customer.id)
        val previous = _state.getAndUpdate {
            if (it.credit.isAbonoInFlight) {
                it
            } else {
                it.copy(credit = it.credit.copy(isAbonoInFlight = true, abonoKey = key), errorMessage = null)
            }
        }
        if (previous.credit.isAbonoInFlight) return
        scope.launch { runAbono(customer, roundMoney(amount), key) }
    }

    fun openAbono() {
        if (blockedByDelivery()) return
        customerSearchJob?.cancel()
        if (_state.value.isBusy) {
            enqueue { showAbono() }
            return
        }
        showAbono()
    }

    private fun showAbono() {
        _state.update {
            if (it.canOpenAbono) {
                it.copy(abonoOverlayOpen = true, credit = CreditState(), errorMessage = null, notice = null)
            } else {
                it
            }
        }
    }

    fun closeAbono() {
        if (_state.value.credit.isAbonoBusy) return
        customerSearchJob?.cancel()
        _state.update {
            if (it.abonoOverlayOpen && !it.credit.isAbonoBusy) {
                it.copy(abonoOverlayOpen = false, credit = CreditState(), errorMessage = null)
            } else {
                it
            }
        }
    }

    fun dismissCreditStep(): Boolean {
        val snapshot = _state.value
        val credit = snapshot.credit
        return when {
            !snapshot.isCustomerStepActive -> false
            credit.newCustomer != null -> {
                cancelNewCustomer()
                true
            }
            credit.showsPicker && credit.pickerQuery.isNotEmpty() -> {
                onCustomerQueryChange("")
                true
            }
            snapshot.isCreditCharge && credit.customer == null -> {
                selectPaymentType(SalePaymentType.CASH)
                true
            }
            snapshot.abonoOverlayOpen -> {
                closeAbono()
                true
            }
            else -> false
        }
    }

    fun onFunctionKey(key: SaleFunctionKey) {
        val snapshot = _state.value
        if (snapshot.confirm != null || snapshot.prompt != null || snapshot.abonoOverlayOpen) return
        when (snapshot.phase) {
            SalePhase.Selling -> when (key) {
                SaleFunctionKey.F1 -> setChannel(snapshot.channel.other())
                SaleFunctionKey.F2 -> startCharge()
                SaleFunctionKey.F3 -> openAbono()
                SaleFunctionKey.F4 -> openAccount()
                SaleFunctionKey.F5 -> refreshDeliveries()
            }
            SalePhase.Charging -> SaleShortcuts.paymentTypeFor(key)
                ?.takeIf { it in snapshot.paymentOptions }
                ?.let(::selectPaymentType)
            else -> Unit
        }
    }

    private fun SaleChannel.other(): SaleChannel =
        if (this == SaleChannel.TAKEAWAY) SaleChannel.ONSITE else SaleChannel.TAKEAWAY

    fun setChannel(channel: SaleChannel) {
        if (blockedByDelivery()) return
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
        if (_state.value.credit.isAbonoBusy) return
        customerSearchJob?.cancel()
        _state.update {
            if (it.phase == SalePhase.Charging && !it.isCheckoutInFlight && !it.credit.isAbonoBusy) {
                it.copy(
                    phase = SalePhase.Selling,
                    paymentType = SalePaymentType.CASH,
                    paidInput = "",
                    paidInputRejected = false,
                    checkoutIdempotencyKey = null,
                    credit = CreditState(),
                    errorMessage = null,
                )
            } else {
                it
            }
        }
    }

    fun confirmChargeFromKeyboard() {
        if (_state.value.paymentType == SalePaymentType.CREDIT) return
        confirmCharge()
    }

    fun confirmCharge() {
        paidScans.scanOnConfirm(now())?.let { scan ->
            _state.update { it.copy(paidInput = scan.valueBeforeBurst, paidInputRejected = false) }
            scope.launch { lookupScanWhileCharging(scan.code) }
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
        val customer = if (paymentType == SalePaymentType.CREDIT) snapshot.credit.customer ?: return else null
        val key = snapshot.checkoutIdempotencyKey ?: buildCheckoutKey(draft.id)
        val previous = _state.getAndUpdate {
            if (it.isCheckoutInFlight) {
                it
            } else {
                it.copy(isCheckoutInFlight = true, checkoutIdempotencyKey = key, errorMessage = null)
            }
        }
        if (previous.isCheckoutInFlight) return
        scope.launch { runCheckout(draft, paymentType, paid, key, customer) }
    }

    fun startNextSale() {
        enqueue {
            resumeSelling()
            reloadDeliveries()
        }
    }

    fun refreshDeliveries() {
        val snapshot = _state.value
        if (snapshot.phase != SalePhase.Selling && snapshot.phase != SalePhase.Done) return
        if (snapshot.isCheckoutInFlight) return
        enqueue { reloadDeliveries() }
    }

    private suspend fun reloadDeliveries() {
        val phase = _state.value.phase
        if (phase != SalePhase.Selling && phase != SalePhase.Done) return
        when (val listed = saleUseCases.openDeliveryDrafts()) {
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(listed.error)) }
            is AppResult.Success -> {
                val fresh = listed.value.filter { it.isDelivery && it.isOpen }.sortedBy { it.id }
                _state.update { state ->
                    if (state.phase != SalePhase.Selling && state.phase != SalePhase.Done) return@update state
                    val activeGone = state.isDeliveryAccount && fresh.none { it.id == state.activeDraftId }
                    val withFresh = state.copy(accounts = state.saleAccounts + fresh)
                    if (activeGone) {
                        withFresh.switchedTo(null).copy(
                            notice = SaleStrings.DELIVERY_ALREADY_CHARGED,
                            errorMessage = null,
                        )
                    } else {
                        withFresh
                    }
                }
                ensureActiveAccount()
            }
        }
    }

    private fun blockedByDelivery(): Boolean {
        val snapshot = _state.value
        if (!snapshot.isDeliveryAccount || snapshot.phase != SalePhase.Selling) return false
        _state.update { it.copy(notice = SaleStrings.DELIVERY_READ_ONLY, errorMessage = null) }
        return true
    }

    private suspend fun runCheckout(
        draft: SaleDraft,
        paymentType: SalePaymentType,
        paid: Double,
        key: String,
        customer: SaleCustomer?,
    ) {
        try {
            checkout(draft, paymentType, paid, key, customer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failCheckout(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()), key)
        }
    }

    private suspend fun checkout(
        draft: SaleDraft,
        paymentType: SalePaymentType,
        paid: Double,
        key: String,
        customer: SaleCustomer?,
    ) {
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
        when (val result = saleUseCases.checkout(chargedDraft.id, paymentType, paid, key, customer?.id)) {
            is AppResult.Failure ->
                if (result.error is AppError.Conflict) {
                    recoverFromCheckoutConflict(chargedDraft.id)
                } else {
                    failCheckout(result.error, key)
                }
            is AppResult.Success -> {
                val totalled = if (result.value.totalCharged <= 0.0) {
                    result.value.copy(totalCharged = chargedDraft.totals.amountFor(paymentType))
                } else {
                    result.value
                }
                val withCustomer = if (customer != null) withCustomerBalance(totalled, customer) else totalled
                val charged = if (chargedDraft.isDelivery) {
                    withCustomer.copy(isDelivery = true, deliveryCustomerName = chargedDraft.customerName)
                } else {
                    withCustomer
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
                        credit = CreditState(),
                        abonoOverlayOpen = false,
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

    private suspend fun withCustomerBalance(result: CheckoutResult, customer: SaleCustomer): CheckoutResult {
        val balanceAfter = when (val fetched = saleUseCases.customer(customer.id)) {
            is AppResult.Success -> fetched.value.balance
            is AppResult.Failure -> roundMoney(customer.balance + result.totalCharged)
        }
        return result.copy(customerName = customer.displayName, customerBalanceAfter = balanceAfter)
    }

    private suspend fun searchCustomers(query: String, pickSingleMatch: Boolean) {
        _state.update { it.copy(credit = it.credit.copy(isSearching = true)) }
        val result = guarded { saleUseCases.searchCustomers(query, CUSTOMER_SEARCH_LIMIT) }
        _state.update { current ->
            val credit = current.credit
            if (!current.isPickerActive() || credit.pickerQuery.trim() != query) return@update current
            when (result) {
                is AppResult.Failure -> current.copy(
                    credit = credit.copy(isSearching = false),
                    errorMessage = SaleErrorText.from(result.error),
                )
                is AppResult.Success -> {
                    val found = if (current.abonoOverlayOpen) result.value.filter { it.hasDebt } else result.value
                    val single = found.singleOrNull()
                    if (pickSingleMatch && single != null) {
                        current.copy(credit = credit.selecting(single), errorMessage = null)
                    } else {
                        current.copy(
                            credit = credit.copy(
                                isSearching = false,
                                pickerResults = found,
                                searchedQuery = query,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun runAbono(customer: SaleCustomer, amount: Double, key: String) {
        val result = guarded { saleUseCases.recordCustomerPayment(customer.id, amount, key) }
        _state.update { current ->
            val credit = current.credit
            val selected = credit.customer
            if (selected == null || selected.id != customer.id) {
                val notice = (result as? AppResult.Success)?.value?.let {
                    SaleStrings.abonoRegistered(it.amount, customer.displayName, it.balance)
                }
                return@update current.copy(
                    credit = credit.copy(isAbonoInFlight = false),
                    notice = notice ?: current.notice,
                )
            }
            when (result) {
                is AppResult.Success -> current.copy(
                    credit = credit.copy(
                        customer = selected.copy(balance = result.value.balance),
                        abonoInput = "",
                        abonoInputRejected = false,
                        isAbonoInFlight = false,
                        abonoKey = null,
                        lastAbono = LastAbono(result.value.amount, selected.displayName, result.value.balance),
                    ),
                    errorMessage = null,
                )
                is AppResult.Failure -> current.copy(
                    credit = credit.copy(
                        isAbonoInFlight = false,
                        abonoKey = if (result.error is AppError.Network) key else null,
                    ),
                    errorMessage = abonoErrorText(result.error),
                )
            }
        }
    }

    private suspend fun <T> guarded(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppResult.Failure(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()))
    }

    private fun abonoErrorText(error: AppError): String {
        val message = when (error) {
            is AppError.Validation -> error.message
            is AppError.Unknown -> error.message
            else -> null
        }
        return if (message?.contains(OPEN_CASH_REQUIRED, ignoreCase = true) == true) {
            SaleStrings.ABONO_CASH_CLOSED
        } else {
            SaleErrorText.from(error)
        }
    }

    private fun CashierSaleState.isPickerActive(): Boolean =
        isCustomerStepActive && credit.showsPicker && !isCheckoutInFlight

    private fun CreditState.selecting(customer: SaleCustomer): CreditState = copy(
        customer = customer,
        pickerQuery = "",
        pickerResults = emptyList(),
        isSearching = false,
        searchedQuery = null,
        abonoInput = "",
        abonoInputRejected = false,
        abonoKey = null,
        lastAbono = null,
    )

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
                val delivery = draft.isDelivery || _state.value.accountById(draftId)?.isDelivery == true
                when {
                    draft.isOpen -> {
                        applyDraft(draft, null, null)
                        _state.update { it.copy(errorMessage = SaleStrings.CHECKOUT_CONFLICT) }
                    }
                    draft.status == CHECKED_OUT ->
                        closeAccount(
                            draftId,
                            if (delivery) SaleStrings.DELIVERY_ALREADY_CHARGED else SaleStrings.POSSIBLY_CHARGED,
                        )
                    delivery && draft.status == CANCELLED -> closeAccount(draftId, SaleStrings.DELIVERY_CANCELLED)
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

    private fun clearSearch() {
        searchJob?.cancel()
        searchScans.reset()
        _state.update { it.copy(query = "", searchResults = emptyList()) }
    }

    private suspend fun search(query: String) {
        when (val result = saleUseCases.searchProducts(query, SEARCH_LIMIT)) {
            is AppResult.Success -> _state.update { it.copy(searchResults = result.value) }
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(result.error)) }
        }
    }

    private fun scannedAccount(): Long? = _state.value.takeIf { it.phase == SalePhase.Selling }?.activeDraftId

    private suspend fun lookupBarcode(code: String, scannedOn: Long?) {
        when (val lookup = saleUseCases.findProductByBarcode(code)) {
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(lookup.error)) }
            is AppResult.Success -> {
                val product = lookup.value
                when {
                    product == null -> lookupCustomerByCode(code) { applyScannedCustomer(it, scannedOn) }
                    !product.isSellable -> _state.update { it.copy(errorMessage = SaleStrings.NO_PRICE) }
                    else -> addProduct(product)
                }
            }
        }
    }

    private suspend fun lookupScanWhileCharging(code: String) {
        when (val lookup = saleUseCases.findProductByBarcode(code)) {
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(lookup.error)) }
            is AppResult.Success ->
                if (lookup.value == null) {
                    lookupCustomerByCode(code, ::switchChargeToCredit)
                } else {
                    _state.update { it.copy(notice = SaleStrings.FINISH_CHARGE_FIRST) }
                }
        }
    }

    private suspend fun lookupCustomerByCode(
        code: String,
        onFound: (SaleCustomer) -> Unit,
    ) {
        when (val lookup = saleUseCases.findCustomerByCode(code)) {
            is AppResult.Failure -> _state.update { it.copy(errorMessage = SaleErrorText.from(lookup.error)) }
            is AppResult.Success -> {
                val customer = lookup.value
                when {
                    customer == null -> _state.update { it.copy(errorMessage = SaleStrings.notFoundWithCode(code)) }
                    !customer.active -> _state.update { it.copy(errorMessage = SaleStrings.CUSTOMER_INACTIVE) }
                    else -> onFound(customer)
                }
            }
        }
    }

    private fun applyScannedCustomer(customer: SaleCustomer, scannedOn: Long?) {
        if (_state.value.phase == SalePhase.Charging) {
            switchChargeToCredit(customer)
            return
        }
        enqueue {
            val wasDone = _state.value.phase == SalePhase.Done
            resumeSelling()
            val stillOnScannedAccount = wasDone || scannedOn == null || _state.value.activeDraftId == scannedOn
            if (stillOnScannedAccount) openForScannedCustomer(customer)
        }
    }

    private fun switchChargeToCredit(customer: SaleCustomer) {
        var switched = false
        _state.update { current ->
            switched = false
            when {
                current.phase != SalePhase.Charging -> current
                current.isPaidLocked || current.isCheckoutInFlight || current.credit.isAbonoBusy ->
                    current.copy(notice = SaleStrings.FINISH_CHARGE_FIRST)
                current.isDeliveryAccount -> current.copy(notice = SaleStrings.DELIVERY_READ_ONLY, errorMessage = null)
                else -> {
                    switched = true
                    current.chargingOnCredit(customer)
                }
            }
        }
        if (switched) {
            paidScans.reset()
            customerSearchJob?.cancel()
        }
    }

    private fun openForScannedCustomer(customer: SaleCustomer) {
        val snapshot = _state.value
        if (snapshot.phase != SalePhase.Selling) return
        if (snapshot.confirm != null || snapshot.prompt != null || snapshot.isDeliveryAccount) return
        if (snapshot.abonoOverlayOpen && snapshot.credit.isAbonoBusy) return
        customerSearchJob?.cancel()
        val hasLines = snapshot.draft?.lines?.isNotEmpty() == true && snapshot.listTotal > 0
        when {
            hasLines && !snapshot.abonoOverlayOpen -> {
                paidScans.reset()
                _state.update { it.chargingOnCredit(customer).copy(phase = SalePhase.Charging) }
            }
            customer.hasDebt -> _state.update {
                it.copy(
                    abonoOverlayOpen = true,
                    credit = CreditState().selecting(customer),
                    errorMessage = null,
                    notice = null,
                )
            }
            else -> _state.update { it.copy(notice = SaleStrings.noDebt(customer.displayName), errorMessage = null) }
        }
    }

    private fun CashierSaleState.chargingOnCredit(customer: SaleCustomer): CashierSaleState = copy(
        paymentType = SalePaymentType.CREDIT,
        paidInput = "",
        paidInputRejected = false,
        credit = CreditState().selecting(customer),
        errorMessage = null,
        notice = null,
    )

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
            current.copy(accounts = accounts.filterNot { it.isDelivery } + accounts.filter { it.isDelivery })
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
        ensureSaleAccount()
    }

    private suspend fun ensureSaleAccount() {
        val active = _state.value.draft
        if (active != null && !active.isDelivery) return
        promptScans.reset()
        val fallback = _state.value.saleAccounts.firstOrNull()
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
        ensureSaleAccount()
        val active = _state.value.activeDraftId ?: return
        promptScans.reset()
        paidScans.reset()
        _state.update { it.activating(active) }
    }

    private suspend fun openAccountsOrNew(): AppResult<List<SaleDraft>> =
        when (val open = saleUseCases.openDrafts()) {
            is AppResult.Failure -> open
            is AppResult.Success -> {
                val sales = open.value.filterNot { it.isDelivery }
                val deliveries = open.value.filter { it.isDelivery }
                if (sales.isNotEmpty()) {
                    AppResult.Success(sales + deliveries)
                } else {
                    when (val created = saleUseCases.newDraft()) {
                        is AppResult.Failure -> created
                        is AppResult.Success -> AppResult.Success(listOf(created.value) + deliveries)
                    }
                }
            }
        }

    private fun CashierSaleState.withoutAccount(draftId: Long): CashierSaleState {
        val index = accounts.indexOfFirst { it.id == draftId }
        if (index < 0) return this
        val remaining = accounts.filterNot { it.id == draftId }
        if (activeDraftId != draftId) return copy(accounts = remaining)
        val sales = saleAccounts
        val saleIndex = sales.indexOfFirst { it.id == draftId }
        val next = if (saleIndex < 0) {
            remaining.firstOrNull { !it.isDelivery }
        } else {
            sales.getOrNull(saleIndex + 1) ?: sales.getOrNull(saleIndex - 1)
        }
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

    private fun CashierSaleState.switchedTo(draftId: Long?): CashierSaleState = copy(
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
        credit = CreditState(),
        abonoOverlayOpen = false,
        notice = null,
        errorMessage = null,
    )

    private fun SaleTotals.amountFor(paymentType: SalePaymentType): Double = when (paymentType) {
        SalePaymentType.CARD, SalePaymentType.CREDIT -> listTotal
        SalePaymentType.CASH, SalePaymentType.TRANSFER -> cashTotal
    }

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

    private fun buildAbonoKey(customerId: Long): String =
        "abono-$customerId-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000, 9_999)}"

    private fun buildCheckoutKey(draftId: Long): String =
        "sale-$draftId-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000, 9_999)}"

    private fun Double.isWholeGrams(): Boolean = abs(this * GRAMS_PER_KILO - round(this * GRAMS_PER_KILO)) < GRAMS_TOLERANCE

    private fun String.isBarcode(): Boolean = length in BARCODE_LENGTH && all(Char::isDigit)

    private fun paysWithInput(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val formatted = if (cents % 100 == 0L) (cents / 100).toString() else formatAmount(amount)
        val sanitized = sanitizeAmount(formatted, MAX_AMOUNT_DIGITS)
        return if (sanitized == formatted) sanitized else ""
    }

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
        const val CANCELLED = "CANCELLED"
        const val CUSTOMER_SEARCH_DEBOUNCE_MS = 250L
        const val CUSTOMER_SEARCH_LIMIT = 5
        const val OPEN_CASH_REQUIRED = "Open cash session is required"
        val BARCODE_LENGTH = 6..80
    }
}
