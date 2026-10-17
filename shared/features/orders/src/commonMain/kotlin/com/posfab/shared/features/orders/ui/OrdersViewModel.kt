package com.posfab.shared.features.orders.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.input.PhoneInput
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.Order
import com.posfab.shared.features.orders.domain.OrderFields
import com.posfab.shared.features.orders.domain.OrderLineInput
import com.posfab.shared.features.orders.domain.OrderMutationResult
import com.posfab.shared.features.orders.domain.OrderPriceType
import com.posfab.shared.features.orders.domain.OrderProduct
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.domain.OrderStatus
import com.posfab.shared.features.orders.domain.roundMoney
import com.posfab.shared.features.orders.usecase.OrdersUseCases
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class OrdersViewModel(
    private val ordersUseCases: OrdersUseCases,
    private val now: () -> Instant = { Clock.System.now() },
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    private val mutations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var customerSearchJob: Job? = null
    private var productSearchJob: Job? = null
    private var kitchenJob: Job? = null
    private var keySequence = 0

    init {
        scope.launch {
            for (mutation in mutations) {
                try {
                    mutation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    _state.update { it.copy(errorMessage = OrdersStrings.FAILED) }
                }
            }
        }
    }

    fun load() {
        val today = today()
        val generation = _state.updateAndGet {
            it.copy(
                isLoading = true,
                errorMessage = null,
                loadGeneration = it.loadGeneration + 1,
            )
        }.loadGeneration
        scope.launch {
            val openCall = async { guarded { ordersUseCases.openOrders() } }
            val paidCall = async { guarded { ordersUseCases.ordersOfDay(OrderStatus.PAID, today) } }
            val cancelledCall = async { guarded { ordersUseCases.ordersOfDay(OrderStatus.CANCELLED, today) } }
            val open = openCall.await()
            val paid = paidCall.await()
            val cancelled = cancelledCall.await()
            _state.update { current ->
                if (current.loadGeneration != generation) {
                    current
                } else {
                    val failure = listOf(open, paid, cancelled).firstNotNullOfOrNull { (it as? AppResult.Failure)?.error }
                    current.copy(
                        open = open.valueOr(current.open),
                        paidToday = paid.valueOr(current.paidToday),
                        cancelledToday = cancelled.valueOr(current.cancelledToday),
                        isLoading = false,
                        errorMessage = failure?.let(OrdersErrorText::load),
                    )
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.isEditing) return
        load()
    }

    fun togglePaid() {
        _state.update { it.copy(showPaid = !it.showPaid) }
    }

    fun toggleCancelled() {
        _state.update { it.copy(showCancelled = !it.showCancelled) }
    }

    fun newOrder() {
        val snapshot = _state.value
        if (snapshot.isCreating || snapshot.isEditing || snapshot.openingId != null) return
        val key = snapshot.pendingCreateKey ?: buildKey()
        _state.update { it.copy(isCreating = true, pendingCreateKey = key, errorMessage = null, notice = null) }
        scope.launch {
            when (val result = guarded { ordersUseCases.createOrder(key) }) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isCreating = false,
                            pendingCreateKey = null,
                            view = OrdersView.Editing(result.value.id),
                            editing = formFor(result.value, isNew = true),
                        )
                    }
                    ensureKitchenProducts()
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        isCreating = false,
                        pendingCreateKey = key.takeIf { result.error is AppError.Network },
                        errorMessage = OrdersErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun open(orderId: Long) {
        val snapshot = _state.value
        if (snapshot.isCreating || snapshot.isEditing || snapshot.openingId != null) return
        _state.update { it.copy(openingId = orderId, errorMessage = null, notice = null) }
        scope.launch {
            val result = guarded { ordersUseCases.order(orderId) }
            _state.update { current ->
                if (current.openingId != orderId) return@update current
                when (result) {
                    is AppResult.Success ->
                        if (result.value.status == OrderStatus.OPEN) {
                            current.copy(
                                openingId = null,
                                view = OrdersView.Editing(orderId),
                                editing = formFor(result.value, isNew = false),
                            )
                        } else {
                            current.copy(openingId = null, notice = OrdersStrings.ORDER_CLOSED)
                        }
                    is AppResult.Failure -> current.copy(
                        openingId = null,
                        errorMessage = OrdersErrorText.from(result.error),
                    )
                }
            }
            val opened = _state.value.editing?.orderId == orderId
            if (opened) ensureKitchenProducts() else load()
        }
    }

    fun back() {
        val form = _state.value.editing ?: return
        if (form.isBusy || form.pendingMutations > 0) return
        if (form.hasUnsavedChanges) {
            editForm { copy(confirmLeave = true, prompt = null) }
            return
        }
        leaveEditor(form)
    }

    fun confirmLeave() {
        val form = _state.value.editing ?: return
        if (!form.confirmLeave) return
        leaveEditor(form)
    }

    fun stay() {
        editForm { copy(confirmLeave = false) }
    }

    fun onEscape(): Boolean {
        val snapshot = _state.value
        if (snapshot.cancelDialog != null) {
            dismissCancel()
            return true
        }
        val form = snapshot.editing ?: return false
        when {
            form.confirmLeave -> stay()
            form.prompt != null -> dismissPrompt()
            else -> back()
        }
        return true
    }

    fun onCustomerQuery(value: String) {
        val accepted = editFields {
            copy(customerQuery = value.take(MAX_QUERY_LENGTH), customerSearchError = null, searchedQuery = null)
        }
        if (!accepted) return
        customerSearchJob?.cancel()
        val form = _state.value.editing ?: return
        val query = value.trim()
        if (form.selected != null || query.length < MIN_QUERY_LENGTH) {
            editForm { copy(suggestions = emptyList(), isSearchingCustomers = false) }
            return
        }
        val orderId = form.orderId
        customerSearchJob = scope.launch {
            delay(CUSTOMER_SEARCH_DEBOUNCE_MS)
            searchCustomers(orderId, query)
        }
    }

    fun onCustomerQueryEnter() {
        val form = _state.value.editing ?: return
        form.suggestions.singleOrNull()?.let { pickCustomer(it.id) }
    }

    fun pickCustomer(customerId: Long) {
        val match = _state.value.editing?.suggestions?.firstOrNull { it.id == customerId } ?: return
        customerSearchJob?.cancel()
        editFields {
            copy(
                selected = match,
                newCustomer = null,
                customerQuery = "",
                suggestions = emptyList(),
                isSearchingCustomers = false,
                address = match.address?.takeIf { it.isNotBlank() } ?: address,
                isDirty = true,
                focus = OrderFocus.ADDRESS,
                focusRequest = focusRequest + 1,
            )
        }
    }

    fun clearCustomer() {
        customerSearchJob?.cancel()
        editFields {
            copy(
                selected = null,
                customerQuery = "",
                suggestions = emptyList(),
                isSearchingCustomers = false,
                isDirty = true,
                focus = OrderFocus.CUSTOMER,
                focusRequest = focusRequest + 1,
            )
        }
    }

    fun startNewCustomer() {
        customerSearchJob?.cancel()
        editFields {
            val query = customerQuery.trim()
            val digits = query.isNotEmpty() && query.all { it.isDigit() }
            copy(
                selected = null,
                newCustomer = NewCustomerForm(
                    name = if (digits) "" else query,
                    phone = if (digits) PhoneInput.sanitize(query) else "",
                ),
                customerQuery = "",
                suggestions = emptyList(),
                isSearchingCustomers = false,
                isDirty = true,
                focus = OrderFocus.NEW_CUSTOMER_NAME,
                focusRequest = focusRequest + 1,
            )
        }
    }

    fun cancelNewCustomer() {
        editFields {
            copy(newCustomer = null, focus = OrderFocus.CUSTOMER, focusRequest = focusRequest + 1)
        }
    }

    fun onNewCustomerName(value: String) {
        editFields { newCustomer?.let { copy(newCustomer = it.copy(name = value.take(MAX_NAME_LENGTH)), isDirty = true) } ?: this }
    }

    fun onNewCustomerPhone(value: String) {
        editFields { newCustomer?.let { copy(newCustomer = it.copy(phone = PhoneInput.sanitize(value)), isDirty = true) } ?: this }
    }

    fun onAddress(value: String) {
        editFields { copy(address = value.take(MAX_TEXT_LENGTH), isDirty = true) }
    }

    fun onNotes(value: String) {
        editFields { copy(notes = value.take(MAX_TEXT_LENGTH), isDirty = true) }
    }

    fun onPaysWith(value: String) {
        editFields {
            copy(paysWithInput = sanitizeDecimal(value, MAX_AMOUNT_DIGITS, MAX_AMOUNT_DECIMALS), isDirty = true)
        }
    }

    fun saveNewCustomer() {
        val snapshot = _state.value
        val form = snapshot.editing ?: return
        val newCustomer = form.newCustomer ?: return
        if (!form.canSaveCustomer || snapshot.cancelDialog != null) return
        if (!newCustomer.hasValidName) {
            editForm { copy(customerError = OrdersStrings.NAME_INVALID) }
            return
        }
        if (!PhoneInput.isComplete(newCustomer.phone)) {
            editForm { copy(customerError = OrdersStrings.PHONE_INCOMPLETE) }
            return
        }
        val orderId = form.orderId
        editForm { copy(isSavingCustomer = true, customerError = null, errorMessage = null) }
        scope.launch {
            val result = guarded {
                ordersUseCases.createCustomer(
                    newCustomer.name.trim(),
                    newCustomer.phone.ifEmpty { null },
                    form.address.trim().ifEmpty { null },
                )
            }
            editForm(orderId) {
                when (result) {
                    is AppResult.Success -> copy(
                        isSavingCustomer = false,
                        selected = result.value,
                        newCustomer = null,
                        isDirty = true,
                        focus = OrderFocus.PRODUCT,
                        focusRequest = focusRequest + 1,
                    )
                    is AppResult.Failure -> copy(
                        isSavingCustomer = false,
                        customerError = OrdersErrorText.customer(result.error),
                    )
                }
            }
        }
    }

    fun save() {
        val form = _state.value.editing ?: return
        if (!form.canSave || _state.value.cancelDialog != null) return
        val customerId = form.selected?.id ?: return
        val orderId = form.orderId
        editForm { copy(isSaving = true, errorMessage = null, confirmLeave = false) }
        scope.launch {
            val current = currentForm(orderId) ?: return@launch
            val fields = OrderFields(
                customerId = customerId,
                address = current.address.trim(),
                notes = current.notes.trim(),
                paysWith = current.paysWithToSend,
            )
            when (val result = guarded { ordersUseCases.updateOrder(orderId, current.version, fields) }) {
                is AppResult.Success -> finishEditor(orderId, OrdersStrings.SAVED)
                is AppResult.Failure -> failEditorAction(orderId, result.error)
            }
        }
    }

    fun markSent(orderId: Long) {
        val snapshot = _state.value
        val form = snapshot.editing
        if (form != null) {
            if (form.orderId != orderId || !form.canMarkSent) return
            editForm { copy(isMarkingSent = true, errorMessage = null) }
            scope.launch {
                when (val result = guarded { ordersUseCases.setStage(orderId, OrderStage.ENVIADO, form.version) }) {
                    is AppResult.Success -> finishEditor(orderId, OrdersStrings.SENT)
                    is AppResult.Failure -> failEditorAction(orderId, result.error)
                }
            }
            return
        }
        if (snapshot.busyOrderId != null || snapshot.cancelDialog != null) return
        val order = snapshot.open.firstOrNull { it.id == orderId } ?: return
        if (order.stage != OrderStage.ANOTADO) return
        _state.update { it.copy(busyOrderId = orderId, errorMessage = null, notice = null) }
        scope.launch {
            val result = guarded { ordersUseCases.setStage(orderId, OrderStage.ENVIADO, order.version) }
            _state.update {
                when (result) {
                    is AppResult.Success -> it.copy(busyOrderId = null, notice = OrdersStrings.SENT)
                    is AppResult.Failure -> it.copy(busyOrderId = null, errorMessage = OrdersErrorText.from(result.error))
                }
            }
            load()
        }
    }

    fun startCancel(orderId: Long) {
        val snapshot = _state.value
        if (snapshot.cancelDialog != null || snapshot.busyOrderId != null) return
        val form = snapshot.editing
        if (form != null) {
            if (form.orderId != orderId || !form.canCancel) return
            editForm { copy(prompt = null) }
        } else if (snapshot.open.none { it.id == orderId }) {
            return
        }
        _state.update { it.copy(cancelDialog = CancelDialog(orderId), notice = null) }
    }

    fun pickCancelReason(reason: CancelReason) {
        _state.update { state ->
            state.cancelDialog?.takeIf { !it.isSubmitting }?.let {
                state.copy(cancelDialog = it.copy(reason = reason, errorMessage = null))
            } ?: state
        }
    }

    fun onCancelNotes(value: String) {
        _state.update { state ->
            state.cancelDialog?.takeIf { !it.isSubmitting }?.let {
                state.copy(cancelDialog = it.copy(notes = value.take(MAX_TEXT_LENGTH), errorMessage = null))
            } ?: state
        }
    }

    fun dismissCancel() {
        _state.update { state ->
            if (state.cancelDialog?.isSubmitting == true) state else state.copy(cancelDialog = null)
        }
    }

    fun confirmCancel() {
        val snapshot = _state.value
        val dialog = snapshot.cancelDialog ?: return
        val reason = dialog.reason ?: return
        if (!dialog.canConfirm) return
        val orderId = dialog.orderId
        val version = snapshot.editing?.takeIf { it.orderId == orderId }?.version
            ?: snapshot.open.firstOrNull { it.id == orderId }?.version
            ?: return
        _state.update { it.copy(cancelDialog = dialog.copy(isSubmitting = true, errorMessage = null)) }
        scope.launch {
            val notes = dialog.notes.trim().takeIf { it.isNotEmpty() }
            val result = guarded { ordersUseCases.cancelOrder(orderId, reason, notes, version) }
            val closed = result is AppResult.Failure && OrdersErrorText.isClosed(result.error)
            if (result is AppResult.Success || closed) {
                val notice = if (closed) OrdersStrings.ORDER_CLOSED else OrdersStrings.CANCELLED
                _state.update { it.copy(cancelDialog = null).closingEditorOf(orderId, notice) }
                load()
            } else if (result is AppResult.Failure) {
                _state.update { state ->
                    state.copy(
                        cancelDialog = state.cancelDialog?.copy(
                            isSubmitting = false,
                            errorMessage = OrdersErrorText.from(result.error),
                        ),
                    )
                }
            }
        }
    }

    fun onProductQuery(value: String) {
        if (!editFields { copy(productQuery = value.take(MAX_QUERY_LENGTH)) }) return
        productSearchJob?.cancel()
        val form = _state.value.editing ?: return
        val query = value.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            editForm { copy(productResults = emptyList(), isSearchingProducts = false) }
            return
        }
        val orderId = form.orderId
        productSearchJob = scope.launch {
            delay(PRODUCT_SEARCH_DEBOUNCE_MS)
            searchProducts(orderId, query)
        }
    }

    fun onProductQueryEnter() {
        val form = _state.value.editing ?: return
        form.productResults.singleOrNull()?.let(::addProduct)
    }

    fun addProduct(product: OrderProduct) {
        val form = _state.value.editing ?: return
        if (!canMutate() || !product.priceType.isSellable) return
        when (product.priceType) {
            OrderPriceType.PIECE -> {
                clearProductSearch()
                enqueueOnOrder(form.orderId) { addPiece(form.orderId, product) }
            }
            OrderPriceType.WEIGHT -> editForm { copy(prompt = OrderPrompt.Weight(product), errorMessage = null) }
            OrderPriceType.OPEN -> editForm { copy(prompt = OrderPrompt.OpenAmount(product), errorMessage = null) }
            OrderPriceType.OTHER -> Unit
        }
    }

    fun onPromptInput(value: String) {
        editForm {
            when (val current = prompt) {
                is OrderPrompt.Weight ->
                    copy(prompt = current.copy(input = sanitizeDecimal(value, MAX_KILOS_DIGITS, MAX_KILOS_DECIMALS)), errorMessage = null)
                is OrderPrompt.OpenAmount ->
                    copy(prompt = current.copy(input = sanitizeDecimal(value, MAX_PROMPT_DIGITS, MAX_AMOUNT_DECIMALS)), errorMessage = null)
                null -> this
            }
        }
    }

    fun pickWeight(kilos: Double) {
        editForm {
            when (val current = prompt) {
                is OrderPrompt.Weight -> copy(prompt = current.copy(input = formatKilos(kilos)), errorMessage = null)
                else -> this
            }
        }
    }

    fun confirmPrompt() {
        val form = _state.value.editing ?: return
        val prompt = form.prompt ?: return
        val value = prompt.parsed
        if (value == null) {
            editForm { copy(errorMessage = OrdersStrings.PROMPT_INVALID) }
            return
        }
        val input = when (prompt) {
            is OrderPrompt.Weight -> OrderLineInput.Qty(round(value * GRAMS_PER_KILO) / GRAMS_PER_KILO)
            is OrderPrompt.OpenAmount -> OrderLineInput.OpenPrice(roundMoney(value))
        }
        editForm { copy(prompt = null, errorMessage = null) }
        clearProductSearch()
        val orderId = form.orderId
        enqueueOnOrder(orderId) {
            val current = currentForm(orderId) ?: return@enqueueOnOrder
            applyMutation(orderId, ordersUseCases.addLine(orderId, prompt.product, input, current.version))
        }
    }

    fun dismissPrompt() {
        editForm { copy(prompt = null, errorMessage = null) }
    }

    fun incrementLine(lineId: Long) {
        val form = _state.value.editing ?: return
        if (!canMutate()) return
        val orderId = form.orderId
        enqueueOnOrder(orderId) {
            val current = currentForm(orderId) ?: return@enqueueOnOrder
            val line = current.lines.firstOrNull { it.id == lineId } ?: return@enqueueOnOrder
            if (line.priceType != OrderPriceType.PIECE) return@enqueueOnOrder
            applyMutation(orderId, ordersUseCases.updateLineQty(orderId, lineId, line.qty + 1.0, current.version))
        }
    }

    fun decrementLine(lineId: Long) {
        val form = _state.value.editing ?: return
        if (!canMutate()) return
        val orderId = form.orderId
        enqueueOnOrder(orderId) {
            val current = currentForm(orderId) ?: return@enqueueOnOrder
            val line = current.lines.firstOrNull { it.id == lineId } ?: return@enqueueOnOrder
            if (line.priceType != OrderPriceType.PIECE) return@enqueueOnOrder
            val result = if (line.qty <= 1.0) {
                ordersUseCases.removeLine(orderId, lineId, current.version)
            } else {
                ordersUseCases.updateLineQty(orderId, lineId, line.qty - 1.0, current.version)
            }
            applyMutation(orderId, result)
        }
    }

    fun removeLine(lineId: Long) {
        val form = _state.value.editing ?: return
        if (!canMutate()) return
        val orderId = form.orderId
        enqueueOnOrder(orderId) {
            val current = currentForm(orderId) ?: return@enqueueOnOrder
            if (current.lines.none { it.id == lineId }) return@enqueueOnOrder
            applyMutation(orderId, ordersUseCases.removeLine(orderId, lineId, current.version))
        }
    }

    private suspend fun addPiece(orderId: Long, product: OrderProduct) {
        val current = currentForm(orderId) ?: return
        val existing = current.lines.firstOrNull { it.productId == product.id && it.priceType == OrderPriceType.PIECE }
        val result = if (existing != null) {
            ordersUseCases.updateLineQty(orderId, existing.id, existing.qty + 1.0, current.version)
        } else {
            ordersUseCases.addLine(orderId, product, OrderLineInput.Qty(1.0), current.version)
        }
        applyMutation(orderId, result)
    }

    private fun applyMutation(orderId: Long, result: OrderMutationResult) {
        when (result) {
            is OrderMutationResult.Success -> editForm(orderId) {
                copy(lines = result.cart.lines, total = result.cart.total, version = result.cart.version, errorMessage = null)
            }
            is OrderMutationResult.ConflictRefetched -> editForm(orderId) {
                copy(
                    lines = result.cart.lines,
                    total = result.cart.total,
                    version = result.cart.version,
                    errorMessage = OrdersStrings.CONFLICT,
                )
            }
            is OrderMutationResult.Closed -> {
                _state.update { it.closingEditorOf(orderId, OrdersStrings.ORDER_CLOSED) }
                load()
            }
            is OrderMutationResult.Failure ->
                if (OrdersErrorText.isClosed(result.error)) {
                    _state.update { it.closingEditorOf(orderId, OrdersStrings.ORDER_CLOSED) }
                    load()
                } else {
                    editForm(orderId) { copy(errorMessage = OrdersErrorText.from(result.error)) }
                }
        }
    }

    private fun enqueueOnOrder(orderId: Long, mutation: suspend () -> Unit) {
        editForm(orderId) { copy(pendingMutations = pendingMutations + 1, errorMessage = null) }
        mutations.trySend {
            try {
                if (currentForm(orderId) != null) {
                    guardedUnit(orderId, mutation)
                }
            } finally {
                editForm(orderId) { copy(pendingMutations = (pendingMutations - 1).coerceAtLeast(0)) }
            }
        }
    }

    private suspend fun guardedUnit(orderId: Long, mutation: suspend () -> Unit) {
        try {
            mutation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            editForm(orderId) { copy(errorMessage = OrdersStrings.FAILED) }
        }
    }

    private suspend fun searchCustomers(orderId: Long, query: String) {
        editForm(orderId) { copy(isSearchingCustomers = true) }
        val result = guarded { ordersUseCases.searchCustomers(query, CUSTOMER_SEARCH_LIMIT) }
        editForm(orderId) {
            if (selected != null || customerQuery.trim() != query) {
                copy(isSearchingCustomers = false)
            } else {
                when (result) {
                    is AppResult.Success -> copy(
                        suggestions = result.value,
                        isSearchingCustomers = false,
                        searchedQuery = query,
                        customerSearchError = null,
                    )
                    is AppResult.Failure -> copy(
                        suggestions = emptyList(),
                        isSearchingCustomers = false,
                        searchedQuery = query,
                        customerSearchError = OrdersStrings.CUSTOMER_SEARCH_FAILED,
                    )
                }
            }
        }
    }

    private suspend fun searchProducts(orderId: Long, query: String) {
        editForm(orderId) { copy(isSearchingProducts = true) }
        val result = guarded { ordersUseCases.searchProducts(query, PRODUCT_SEARCH_LIMIT) }
        editForm(orderId) {
            if (productQuery.trim() != query) {
                copy(isSearchingProducts = false)
            } else {
                copy(productResults = (result as? AppResult.Success)?.value.orEmpty(), isSearchingProducts = false)
            }
        }
    }

    private fun ensureKitchenProducts() {
        if (_state.value.kitchenProducts.isNotEmpty() || kitchenJob?.isActive == true) return
        kitchenJob = scope.launch {
            val result = guarded { ordersUseCases.kitchenProducts() }
            if (result is AppResult.Success) _state.update { it.copy(kitchenProducts = result.value) }
        }
    }

    private fun clearProductSearch() {
        productSearchJob?.cancel()
        editForm { copy(productQuery = "", productResults = emptyList(), isSearchingProducts = false) }
    }

    private fun leaveEditor(form: OrderForm) {
        _state.update { it.closingEditorOf(form.orderId, null) }
        if (form.isEmptyOnServer) {
            scope.launch {
                val result = guarded { ordersUseCases.cancelEmpty(form.orderId, form.version) }
                if (result is AppResult.Failure && !OrdersErrorText.isClosed(result.error)) {
                    _state.update { it.copy(notice = OrdersStrings.DISCARD_FAILED) }
                }
                load()
            }
        } else {
            load()
        }
    }

    private fun finishEditor(orderId: Long, notice: String) {
        _state.update { it.closingEditorOf(orderId, notice) }
        load()
    }

    private fun failEditorAction(orderId: Long, error: AppError) {
        if (OrdersErrorText.isClosed(error)) {
            finishEditor(orderId, OrdersStrings.ORDER_CLOSED)
            return
        }
        editForm(orderId) {
            copy(isSaving = false, isMarkingSent = false, errorMessage = OrdersErrorText.from(error))
        }
    }

    private fun OrdersState.closingEditorOf(orderId: Long, notice: String?): OrdersState {
        if (editing?.orderId != orderId) return if (notice != null) copy(notice = notice) else this
        customerSearchJob?.cancel()
        productSearchJob?.cancel()
        return copy(view = OrdersView.List, editing = null, notice = notice, errorMessage = null)
    }

    private fun formFor(order: Order, isNew: Boolean): OrderForm = OrderForm(
        orderId = order.id,
        version = order.version,
        isNew = isNew,
        stage = order.stage,
        hadCustomer = order.customer != null,
        hadPaysWith = order.capturedPaysWith != null,
        selected = order.customer,
        address = order.address.orEmpty(),
        notes = order.notes.orEmpty(),
        paysWithInput = order.capturedPaysWith?.let(::plainAmount).orEmpty(),
        lines = order.lines,
        total = order.total,
        focus = if (order.customer == null) OrderFocus.CUSTOMER else OrderFocus.PRODUCT,
        focusRequest = 1,
    )

    private fun currentForm(orderId: Long): OrderForm? = _state.value.editing?.takeIf { it.orderId == orderId }

    private fun canMutate(): Boolean {
        val snapshot = _state.value
        val form = snapshot.editing ?: return false
        return !form.isBusy && form.prompt == null && !form.confirmLeave && snapshot.cancelDialog == null
    }

    private fun editForm(orderId: Long? = null, change: OrderForm.() -> OrderForm) {
        _state.update { current ->
            val form = current.editing ?: return@update current
            if (orderId != null && form.orderId != orderId) current else current.copy(editing = form.change())
        }
    }

    private fun editFields(change: OrderForm.() -> OrderForm): Boolean {
        var applied = false
        _state.update { current ->
            val form = current.editing
            if (form == null || form.isBusy || form.prompt != null || current.cancelDialog != null) {
                current
            } else {
                applied = true
                current.copy(editing = form.change().copy(errorMessage = null, customerError = null), notice = null)
            }
        }
        return applied
    }

    private fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    private fun <T> AppResult<T>.valueOr(fallback: T): T = (this as? AppResult.Success)?.value ?: fallback

    private suspend fun <T> guarded(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppResult.Failure(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()))
    }

    private fun buildKey(): String {
        keySequence += 1
        return "order-${now().toEpochMilliseconds()}-$keySequence-${Random.nextInt(1_000, 9_999)}"
    }

    private fun sanitizeDecimal(value: String, maxWholeDigits: Int, maxDecimals: Int): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        if (dot < 0) return filtered.take(maxWholeDigits)
        val whole = filtered.substring(0, dot).take(maxWholeDigits)
        val decimals = filtered.substring(dot + 1).replace(".", "").take(maxDecimals)
        return "$whole.$decimals"
    }

    private fun formatKilos(kilos: Double): String {
        val grams = round(kilos * GRAMS_PER_KILO).toLong()
        return "${grams / 1000}.${(grams % 1000).toString().padStart(3, '0')}"
    }

    private fun plainAmount(amount: Double): String {
        val cents = abs(round(amount * 100.0).toLong())
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    companion object {
        const val CUSTOMER_SEARCH_DEBOUNCE_MS = 250L
        const val PRODUCT_SEARCH_DEBOUNCE_MS = 300L
        const val CUSTOMER_SEARCH_LIMIT = 5
        const val PRODUCT_SEARCH_LIMIT = 8
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 80
        private const val MAX_NAME_LENGTH = 120
        private const val MAX_TEXT_LENGTH = 255
        private const val MAX_AMOUNT_DIGITS = 6
        private const val MAX_PROMPT_DIGITS = 5
        private const val MAX_AMOUNT_DECIMALS = 2
        private const val MAX_KILOS_DIGITS = 2
        private const val MAX_KILOS_DECIMALS = 3
        private const val GRAMS_PER_KILO = 1000.0
    }
}
