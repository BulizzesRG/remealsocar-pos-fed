package com.posfab.shared.features.shopping.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.input.ScanBurstDetector
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.domain.ShoppingProduct
import com.posfab.shared.features.shopping.domain.plainQty
import com.posfab.shared.features.shopping.usecase.ShoppingUseCases
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ShoppingViewModel(
    private val useCases: ShoppingUseCases,
    permissions: Set<Permission> = emptySet(),
    private val now: () -> Instant = { Clock.System.now() },
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(ShoppingState(canClose = Permission.MANAGE_INVENTORY in permissions))
    val state: StateFlow<ShoppingState> = _state.asStateFlow()

    private val inputScans = ScanBurstDetector()
    private var suggestionJob: Job? = null
    private var suggestionGeneration = 0
    private var keySequence = 0
    private var pendingAddRequest: AddRequest? = null
    private var pendingCloseListId: Long? = null
    private var pendingFinishRequest: FinishRequest? = null
    private var lastLoadError: String? = null

    fun load() {
        val generation = _state.updateAndGet {
            it.copy(isLoading = true, loadGeneration = it.loadGeneration + 1)
        }.loadGeneration
        scope.launch {
            val result = guarded { useCases.current() }
            _state.update { current ->
                if (current.loadGeneration != generation) return@update current
                when (result) {
                    is AppResult.Success -> {
                        val open = result.value.open
                        current.copy(
                            isLoading = false,
                            errorMessage = current.errorMessage?.takeIf { it != lastLoadError },
                            open = open,
                            toShop = result.value.toShop,
                            edit = current.edit?.takeIf { edit -> open?.contains(edit.id) == true },
                        )
                    }
                    is AppResult.Failure -> {
                        val message = ShoppingErrorText.load(result.error)
                        lastLoadError = message
                        current.copy(isLoading = false, errorMessage = message)
                    }
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(errorMessage = null, notice = null) }
        load()
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    fun titleOf(list: ShoppingList): String = listTitle(list.createdAt, timeZone, today())

    fun titleOf(summary: ShoppingListSummary): String = listTitle(summary.createdAt, timeZone, today())

    fun historyLabel(summary: ShoppingListSummary): String = historyRowLabel(summary, timeZone, today())

    fun onInputChange(value: String) {
        inputScans.onChange(_state.value.input, value, nowMs())
        _state.update { it.copy(input = value, inputError = null) }
        scheduleSuggestions(value)
    }

    fun onQtyChange(value: String) {
        _state.update { it.copy(qtyInput = QtyInput.sanitize(value), inputError = null) }
    }

    fun onInputSubmit() {
        val scanned = inputScans.scanOnConfirm(nowMs())?.code
        submit(scanned)
    }

    fun addFromButton() {
        inputScans.reset()
        submit(null)
    }

    fun pickSuggestion(product: ShoppingProduct) {
        val snapshot = _state.value
        if (snapshot.isAdding) return
        val qty = qtyOrReport(snapshot.qtyInput) ?: return
        cancelSuggestions()
        _state.update { it.copy(suggestions = emptyList()) }
        addItem(product.name.take(MAX_TEXT_LENGTH), product.id, qty.value, submittedInput = snapshot.input)
    }

    fun startEdit(item: ShoppingItem) {
        _state.update { current ->
            val open = current.open
            if (open == null || !open.contains(item.id) || current.edit != null || current.confirm != null) {
                current
            } else {
                val qtyInput = item.qty?.let(::plainQty).orEmpty()
                current.copy(edit = ItemEditState(id = item.id, text = item.text, qtyInput = qtyInput, productId = item.productId))
            }
        }
    }

    fun updateEditText(value: String) {
        editState { if (isSaving) this else copy(text = value, error = null) }
    }

    fun updateEditQty(value: String) {
        editState { if (isSaving) this else copy(qtyInput = QtyInput.sanitize(value), error = null) }
    }

    fun cancelEdit() {
        editState(clear = true) { this }
    }

    fun saveEdit() {
        val snapshot = _state.value
        val edit = snapshot.edit ?: return
        if (edit.isSaving || snapshot.confirm != null) return
        val text = edit.text.trim()
        val error = when {
            text.isEmpty() -> ShoppingStrings.TEXT_REQUIRED
            text.length > MAX_TEXT_LENGTH -> ShoppingStrings.TEXT_TOO_LONG
            !QtyInput.isValid(edit.qtyInput) -> ShoppingStrings.QTY_INVALID
            else -> null
        }
        if (error != null) {
            editState { copy(error = error) }
            return
        }
        if (!edit.hasChanges) {
            _state.update { it.copy(edit = null) }
            return
        }
        val qty = QtyInput.toQty(edit.qtyInput)
        _state.update { it.copy(edit = edit.copy(isSaving = true, error = null)) }
        scope.launch {
            val result = guarded { useCases.updateItem(edit.id, text, edit.productId, qty) }
            when (result) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        edit = null,
                        notice = ShoppingStrings.SAVED,
                        errorMessage = null,
                        open = current.open?.withItem(result.value),
                    )
                }
                is AppResult.Failure -> handleItemFailure(result.error) {
                    editState { copy(isSaving = false, error = ShoppingErrorText.from(result.error)) }
                }
            }
        }
    }

    fun requestDelete(id: Long) {
        _state.update { current ->
            val item = current.open?.items?.firstOrNull { it.id == id }
            if (item == null || current.confirm != null || current.edit?.isSaving == true) {
                current
            } else {
                current.copy(confirm = ShoppingConfirm.DeleteItem(id, item.text))
            }
        }
    }

    fun confirm() {
        when (_state.value.confirm) {
            ShoppingConfirm.Close -> confirmClose()
            is ShoppingConfirm.Finish -> confirmFinish()
            is ShoppingConfirm.DeleteItem -> confirmDelete()
            null -> Unit
        }
    }

    fun dismissConfirm() {
        _state.update { if (it.isConfirming) it else it.copy(confirm = null) }
    }

    fun dismiss() = dismissConfirm()

    fun confirmDelete() {
        val target = beginConfirm<ShoppingConfirm.DeleteItem>() ?: return
        scope.launch {
            when (val result = guarded { useCases.deleteItem(target.id) }) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        isConfirming = false,
                        confirm = null,
                        edit = null,
                        notice = ShoppingStrings.removed(target.text),
                        errorMessage = null,
                        open = current.open?.withoutItem(target.id),
                    )
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isConfirming = false, confirm = null) }
                    handleItemFailure(result.error) {
                        val message = ShoppingErrorText.from(result.error)
                        _state.update { current ->
                            if (current.edit != null) {
                                current.copy(edit = current.edit.copy(error = message))
                            } else {
                                current.copy(errorMessage = message, notice = null)
                            }
                        }
                    }
                }
            }
        }
    }

    fun setStatus(item: ShoppingItem, status: ShoppingItemStatus) {
        val target = if (item.status == status) ShoppingItemStatus.PENDING else status
        val before = _state.getAndUpdate { current ->
            if (current.busyItemId != null || current.toShop?.contains(item.id) != true) current else current.copy(busyItemId = item.id)
        }
        if (before.busyItemId != null || before.toShop?.contains(item.id) != true) return
        scope.launch {
            when (val result = guarded { useCases.setStatus(item.id, target) }) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        busyItemId = null,
                        errorMessage = null,
                        toShop = current.toShop?.withItem(result.value),
                    )
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(busyItemId = null) }
                    if (result.error is AppError.Conflict) {
                        _state.update { it.copy(notice = ShoppingStrings.LIST_FINISHED, errorMessage = null) }
                        load()
                    } else {
                        if (ShoppingErrorText.isItemGone(result.error)) load()
                        _state.update { it.copy(errorMessage = ShoppingErrorText.from(result.error), notice = null) }
                    }
                }
            }
        }
    }

    fun requestClose() {
        _state.update { current ->
            if (!current.showCloseButton || current.hasDialog) current else current.copy(confirm = ShoppingConfirm.Close)
        }
    }

    fun confirmClose() {
        val snapshot = _state.value
        val listId = snapshot.open?.id ?: return
        beginConfirm<ShoppingConfirm.Close>() ?: return
        val key = snapshot.pendingCloseKey?.takeIf { pendingCloseListId == listId } ?: buildKey()
        pendingCloseListId = listId
        _state.update { it.copy(pendingCloseKey = key) }
        scope.launch {
            when (val result = guarded { useCases.close(key, listId) }) {
                is AppResult.Success -> {
                    pendingCloseListId = null
                    _state.update {
                        it.copy(
                            isConfirming = false,
                            confirm = null,
                            pendingCloseKey = null,
                            notice = ShoppingStrings.CLOSED_NOTICE,
                            errorMessage = null,
                        )
                    }
                    afterListTransition()
                }
                is AppResult.Failure -> confirmFailed(result.error, keepKey = { it.copy(pendingCloseKey = key) }) {
                    it.copy(pendingCloseKey = null).also { pendingCloseListId = null }
                }
            }
        }
    }

    fun requestFinish() {
        _state.update { current ->
            val toShop = current.toShop
            if (!current.showFinishButton || toShop == null || current.hasDialog) {
                current
            } else {
                val candidates = toShop.notBought
                current.copy(confirm = ShoppingConfirm.Finish(candidates, candidates.map { it.id }.toSet()))
            }
        }
    }

    fun toggleCarry(id: Long) {
        _state.update { current ->
            val finish = current.confirm as? ShoppingConfirm.Finish
            if (finish == null || current.isConfirming || finish.candidates.none { it.id == id }) {
                current
            } else {
                val carry = if (id in finish.carry) finish.carry - id else finish.carry + id
                current.copy(confirm = finish.copy(carry = carry))
            }
        }
    }

    fun confirmFinish() {
        val snapshot = _state.value
        val listId = snapshot.toShop?.id ?: return
        val finish = beginConfirm<ShoppingConfirm.Finish>() ?: return
        val carry = finish.candidates.map { it.id }.filter { it in finish.carry }
        val request = FinishRequest(listId, carry)
        val key = snapshot.pendingFinishKey?.takeIf { pendingFinishRequest == request } ?: buildKey()
        pendingFinishRequest = request
        _state.update { it.copy(pendingFinishKey = key) }
        scope.launch {
            when (val result = guarded { useCases.finish(key, listId, carry) }) {
                is AppResult.Success -> {
                    pendingFinishRequest = null
                    _state.update {
                        it.copy(
                            isConfirming = false,
                            confirm = null,
                            pendingFinishKey = null,
                            notice = ShoppingStrings.FINISHED_NOTICE,
                            errorMessage = null,
                        )
                    }
                    afterListTransition()
                }
                is AppResult.Failure -> confirmFailed(result.error, keepKey = { it.copy(pendingFinishKey = key) }) {
                    it.copy(pendingFinishKey = null).also { pendingFinishRequest = null }
                }
            }
        }
    }

    fun toggleHistory() {
        val shown = _state.updateAndGet { it.copy(showHistory = !it.showHistory) }
        if (shown.showHistory && !shown.historyLoaded) loadHistory()
    }

    fun loadHistory() {
        val before = _state.getAndUpdate {
            if (it.isLoadingHistory) it else it.copy(isLoadingHistory = true, historyError = null)
        }
        if (before.isLoadingHistory) return
        scope.launch {
            val result = guarded { useCases.history(HISTORY_LIMIT) }
            _state.update { current ->
                when (result) {
                    is AppResult.Success -> current.copy(
                        isLoadingHistory = false,
                        history = result.value,
                        historyLoaded = true,
                        historyError = null,
                    )
                    is AppResult.Failure -> current.copy(
                        isLoadingHistory = false,
                        historyError = ShoppingErrorText.history(result.error),
                    )
                }
            }
        }
    }

    fun openHistory(id: Long) {
        _state.update {
            it.copy(
                view = ShoppingView.HistoryDetail(id),
                historyDetail = null,
                isLoadingDetail = true,
                detailError = null,
            )
        }
        scope.launch {
            val result = guarded { useCases.list(id) }
            _state.update { current ->
                if (current.view != ShoppingView.HistoryDetail(id)) return@update current
                when (result) {
                    is AppResult.Success -> current.copy(isLoadingDetail = false, historyDetail = result.value)
                    is AppResult.Failure -> current.copy(
                        isLoadingDetail = false,
                        detailError = ShoppingErrorText.detail(result.error),
                    )
                }
            }
        }
    }

    fun backFromHistory() {
        _state.update {
            it.copy(view = ShoppingView.Lists, historyDetail = null, isLoadingDetail = false, detailError = null)
        }
    }

    fun onEscape(): Boolean {
        val snapshot = _state.value
        return when {
            snapshot.confirm != null -> {
                dismissConfirm()
                true
            }
            snapshot.edit != null -> {
                cancelEdit()
                true
            }
            snapshot.view is ShoppingView.HistoryDetail -> {
                backFromHistory()
                true
            }
            snapshot.input.isNotEmpty() || snapshot.qtyInput.isNotEmpty() || snapshot.suggestions.isNotEmpty() -> {
                clearInput()
                true
            }
            else -> false
        }
    }

    private fun submit(scanned: String?) {
        val snapshot = _state.value
        if (snapshot.isAdding || snapshot.hasDialog) return
        val typed = snapshot.input.trim()
        val code = scanned ?: typed.takeIf { it.isScanCode() }
        if (code != null) {
            lookupAndAdd(code, snapshot.qtyInput)
            return
        }
        if (typed.isEmpty()) return
        if (typed.length > MAX_TEXT_LENGTH) {
            _state.update { it.copy(inputError = ShoppingStrings.TEXT_TOO_LONG) }
            return
        }
        val qty = qtyOrReport(snapshot.qtyInput) ?: return
        cancelSuggestions()
        addItem(typed, null, qty.value, submittedInput = snapshot.input)
    }

    private fun lookupAndAdd(code: String, qtyInput: String) {
        val qty = qtyOrReport(qtyInput) ?: return
        val before = _state.getAndUpdate {
            if (it.isAdding) it else it.copy(isAdding = true, input = "", suggestions = emptyList(), inputError = null)
        }
        if (before.isAdding) return
        cancelSuggestions()
        scope.launch {
            when (val result = guarded { useCases.findProductByBarcode(code) }) {
                is AppResult.Success -> {
                    val product = result.value
                    if (product == null) {
                        _state.update {
                            it.copy(isAdding = false, notice = null, errorMessage = ShoppingStrings.notFoundWithCode(code))
                        }
                    } else {
                        addItem(product.name.take(MAX_TEXT_LENGTH), product.id, qty.value, submittedInput = "", claimed = true)
                    }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        isAdding = false,
                        input = if (it.input.isEmpty()) code else it.input,
                        notice = null,
                        errorMessage = ShoppingErrorText.from(result.error),
                    )
                }
            }
        }
    }

    private fun addItem(text: String, productId: Long?, qty: Double?, submittedInput: String, claimed: Boolean = false) {
        val request = AddRequest(text, productId, qty)
        val before = _state.getAndUpdate {
            if (it.isAdding && !claimed) it else it.copy(isAdding = true, inputError = null)
        }
        if (before.isAdding && !claimed) return
        val key = before.pendingAddKey?.takeIf { pendingAddRequest == request } ?: buildKey()
        pendingAddRequest = request
        _state.update { it.copy(pendingAddKey = key) }
        scope.launch {
            when (val result = guarded { useCases.addItem(key, text, productId, qty) }) {
                is AppResult.Success -> {
                    pendingAddRequest = null
                    _state.update {
                        it.copy(
                            isAdding = false,
                            pendingAddKey = null,
                            input = if (it.input == submittedInput) "" else it.input,
                            qtyInput = "",
                            suggestions = emptyList(),
                            inputError = null,
                            errorMessage = null,
                            notice = ShoppingStrings.added(result.value.text.ifBlank { text }),
                        )
                    }
                    load()
                }
                is AppResult.Failure -> {
                    val keep = result.error is AppError.Network
                    if (!keep) pendingAddRequest = null
                    _state.update {
                        it.copy(
                            isAdding = false,
                            pendingAddKey = key.takeIf { keep },
                            notice = null,
                            errorMessage = ShoppingErrorText.from(result.error),
                        )
                    }
                }
            }
        }
    }

    private fun handleItemFailure(error: AppError, otherwise: () -> Unit) {
        when {
            error is AppError.Conflict -> {
                _state.update { it.copy(edit = null, notice = ShoppingStrings.LIST_CLOSED, errorMessage = null) }
                load()
            }
            ShoppingErrorText.isItemGone(error) -> {
                _state.update { it.copy(edit = null, notice = null, errorMessage = ShoppingStrings.ITEM_NOT_FOUND) }
                load()
            }
            else -> otherwise()
        }
    }

    private inline fun <reified T : ShoppingConfirm> beginConfirm(): T? {
        var started: T? = null
        _state.update { current ->
            val confirm = current.confirm
            if (confirm is T && !current.isConfirming) {
                started = confirm
                current.copy(isConfirming = true)
            } else {
                current
            }
        }
        return started
    }

    private fun confirmFailed(
        error: AppError,
        keepKey: (ShoppingState) -> ShoppingState,
        dropKey: (ShoppingState) -> ShoppingState,
    ) {
        val keep = error is AppError.Network
        _state.update {
            val base = it.copy(
                isConfirming = false,
                confirm = null,
                notice = null,
                errorMessage = ShoppingErrorText.from(error),
            )
            if (keep) keepKey(base) else dropKey(base)
        }
        if (error is AppError.Conflict || error is AppError.Validation) load()
    }

    private fun afterListTransition() {
        _state.update { it.copy(historyLoaded = false) }
        load()
        if (_state.value.showHistory) loadHistory()
    }

    private fun scheduleSuggestions(value: String) {
        val query = value.trim()
        cancelSuggestions()
        if (query.length < MIN_QUERY_LENGTH || query.all { it.isDigit() } || query.length > MAX_QUERY_LENGTH) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        val generation = suggestionGeneration
        suggestionJob = scope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            val result = guarded { useCases.searchProducts(query, SUGGESTION_LIMIT) }
            _state.update { current ->
                if (generation != suggestionGeneration || current.input.trim() != query) return@update current
                when (result) {
                    is AppResult.Success -> current.copy(suggestions = result.value)
                    is AppResult.Failure -> current.copy(suggestions = emptyList())
                }
            }
        }
    }

    private fun cancelSuggestions() {
        suggestionGeneration += 1
        suggestionJob?.cancel()
        suggestionJob = null
    }

    private fun clearInput() {
        cancelSuggestions()
        inputScans.reset()
        _state.update { it.copy(input = "", qtyInput = "", suggestions = emptyList(), inputError = null) }
    }

    private fun qtyOrReport(qtyInput: String): ParsedQty? {
        if (qtyInput.isBlank()) return ParsedQty(null)
        val qty = QtyInput.toQty(qtyInput)
        if (qty == null) {
            _state.update { it.copy(inputError = ShoppingStrings.QTY_INVALID) }
            return null
        }
        return ParsedQty(qty)
    }

    private fun editState(clear: Boolean = false, change: ItemEditState.() -> ItemEditState) {
        _state.update { current ->
            val edit = current.edit ?: return@update current
            when {
                clear && !edit.isSaving && current.confirm == null -> current.copy(edit = null)
                clear -> current
                else -> current.copy(edit = edit.change())
            }
        }
    }

    private fun String.isScanCode(): Boolean = length in SCAN_MIN_LENGTH..SCAN_MAX_LENGTH && all { it.isDigit() }

    private fun nowMs(): Long = now().toEpochMilliseconds()

    private suspend fun <T> guarded(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppResult.Failure(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()))
    }

    private fun buildKey(): String {
        keySequence += 1
        return "shop-${nowMs()}-$keySequence-${Random.nextInt(1_000, 9_999)}"
    }

    private data class AddRequest(val text: String, val productId: Long?, val qty: Double?)

    private data class FinishRequest(val listId: Long, val carry: List<Long>)

    private class ParsedQty(val value: Double?)

    companion object {
        const val SUGGESTION_DEBOUNCE_MS = 250L
        const val SUGGESTION_LIMIT = 5
        const val HISTORY_LIMIT = 20
        const val MAX_TEXT_LENGTH = 120
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 80
        private const val SCAN_MIN_LENGTH = 6
        private const val SCAN_MAX_LENGTH = 80
    }
}
