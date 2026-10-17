package com.posfab.shared.features.credit.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.input.DateMask
import com.posfab.shared.core.input.PhoneInput
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.credit.domain.CustomerEdit
import com.posfab.shared.features.credit.domain.CustomerMatch
import com.posfab.shared.features.credit.domain.LoadedCustomer
import com.posfab.shared.features.credit.usecase.CreditUseCases
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.round
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
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

class CreditViewModel(
    private val creditUseCases: CreditUseCases,
    permissions: Set<Permission> = emptySet(),
    private val now: () -> Instant = { Clock.System.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(
        CreditState(
            canLoadLibreta = Permission.MANAGE_CREDIT in permissions,
            canEditCustomers = Permission.MANAGE_CREDIT in permissions,
        ),
    )
    val state: StateFlow<CreditState> = _state.asStateFlow()

    private var debtorsJob: Job? = null
    private var statementJob: Job? = null
    private var customerSearchJob: Job? = null
    private var listSearchJob: Job? = null
    private var keySequence = 0

    fun load() {
        debtorsJob?.cancel()
        _state.update {
            it.copy(isLoading = true, errorMessage = null, nowEpochMillis = now().toEpochMilliseconds())
        }
        debtorsJob = scope.launch {
            when (val result = creditUseCases.debtors()) {
                is AppResult.Success -> _state.update { it.copy(isLoading = false, debtors = result.value) }
                is AppResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = CreditErrorText.from(result.error))
                }
            }
        }
    }

    fun refresh() {
        val selectedId = _state.value.selectedId
        load()
        if (selectedId != null) select(selectedId)
    }

    fun onFilterChange(value: String) {
        listSearchJob?.cancel()
        val query = value.trim()
        val searches = query.length >= MIN_SEARCH_LENGTH
        _state.update {
            it.copy(
                filter = value,
                searchResults = emptyList(),
                isSearching = searches,
                searchFailed = false,
            )
        }
        if (!searches) return
        val includeInactive = _state.value.canEditCustomers
        listSearchJob = scope.launch {
            delay(CUSTOMER_SEARCH_DEBOUNCE_MS)
            val result = guarded { creditUseCases.searchCustomers(query, LIST_SEARCH_LIMIT, includeInactive) }
            _state.update {
                if (it.filter.trim() != query) {
                    it
                } else {
                    when (result) {
                        is AppResult.Success -> it.copy(searchResults = result.value, isSearching = false, searchFailed = false)
                        is AppResult.Failure -> it.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            searchFailed = true,
                            errorMessage = CreditErrorText.from(result.error),
                        )
                    }
                }
            }
        }
    }

    fun select(customerId: Long) {
        statementJob?.cancel()
        _state.update {
            val sameCustomer = it.selectedId == customerId
            it.copy(
                selectedId = customerId,
                selected = it.selected?.takeIf { statement -> statement.id == customerId },
                isStatementLoading = true,
                errorMessage = null,
                edit = it.edit?.takeIf { edit -> edit.customerId == customerId },
                savedNotice = it.savedNotice?.takeIf { sameCustomer },
            )
        }
        statementJob = scope.launch {
            val result = creditUseCases.statement(customerId, LEDGER_LIMIT)
            if (_state.value.selectedId != customerId) return@launch
            when (result) {
                is AppResult.Success -> _state.update { it.copy(isStatementLoading = false, selected = result.value) }
                is AppResult.Failure -> _state.update {
                    it.copy(isStatementLoading = false, errorMessage = CreditErrorText.from(result.error))
                }
            }
        }
    }

    fun back() {
        val edit = _state.value.edit
        if (edit != null) {
            if (edit.isSaving || edit.confirm != null) return
            if (edit.hasChanges) {
                updateEdit { copy(confirm = CustomerEditConfirm.DISCARD, leaveOnDiscard = true) }
                return
            }
        }
        leaveDetail()
    }

    private fun leaveDetail() {
        statementJob?.cancel()
        _state.update {
            if (it.edit?.isSaving == true) {
                it
            } else {
                it.copy(
                    selectedId = null,
                    selected = null,
                    isStatementLoading = false,
                    errorMessage = null,
                    edit = null,
                    savedNotice = null,
                )
            }
        }
    }

    fun startEdit() {
        _state.update {
            val statement = it.selected
            if (!it.canEditCustomers || it.edit != null || it.isStatementLoading ||
                statement == null || statement.id != it.selectedId
            ) {
                it
            } else {
                val form = CustomerEditForm(
                    name = statement.name,
                    nickname = statement.nickname.orEmpty(),
                    phone = statement.phone.orEmpty(),
                    address = statement.address.orEmpty(),
                    active = statement.active,
                )
                it.copy(
                    edit = CustomerEditState(customerId = statement.id, code = statement.code, form = form, original = form),
                    savedNotice = null,
                )
            }
        }
    }

    fun onEditName(value: String) {
        editForm { copy(name = value.take(MAX_NAME_LENGTH)) }
    }

    fun onEditNickname(value: String) {
        editForm { copy(nickname = value.take(MAX_NICKNAME_LENGTH)) }
    }

    fun onEditPhone(value: String) {
        editForm { copy(phone = PhoneInput.sanitize(value)) }
    }

    fun onEditAddress(value: String) {
        editForm { copy(address = value.take(MAX_ADDRESS_LENGTH)) }
    }

    fun onEditActive(value: Boolean) {
        editForm { copy(active = value) }
    }

    fun saveEdit() {
        val edit = _state.value.edit ?: return
        if (edit.isSaving || edit.confirm != null) return
        if (!edit.hasChanges) {
            _state.update { it.copy(edit = null) }
            return
        }
        val error = when {
            !isValidName(edit.form.name) -> CreditStrings.NAME_REQUIRED
            !PhoneInput.isComplete(edit.form.phone) -> CreditStrings.PHONE_INVALID
            else -> null
        }
        if (error != null) {
            updateEdit { copy(errorMessage = error, focusRequest = focusRequest + 1) }
            return
        }
        val confirm = if (edit.isDeactivating) CustomerEditConfirm.DEACTIVATE else CustomerEditConfirm.SAVE
        updateEdit { copy(confirm = confirm, leaveOnDiscard = false, errorMessage = null) }
    }

    fun confirmEdit() {
        val previous = _state.getAndUpdate { current ->
            val open = current.edit
            if (open == null || open.isSaving || open.confirm == null || open.confirm == CustomerEditConfirm.DISCARD) {
                current
            } else {
                current.copy(edit = open.copy(isSaving = true, confirm = null, errorMessage = null))
            }
        }
        val before = previous.edit ?: return
        if (before.isSaving || before.confirm == null || before.confirm == CustomerEditConfirm.DISCARD) return
        val customerId = before.customerId
        val form = before.form
        scope.launch {
            val change = CustomerEdit(
                name = form.name.trim(),
                nickname = form.nickname.trim().ifEmpty { null },
                phone = form.phone.trim().ifEmpty { null },
                address = form.address.trim().ifEmpty { null },
                active = form.active,
            )
            val result = guarded { creditUseCases.updateCustomer(customerId, change) }
            if (_state.value.edit?.customerId != customerId) return@launch
            when (result) {
                is AppResult.Success -> {
                    _state.update { it.copy(edit = null, savedNotice = CreditStrings.SAVED) }
                    select(customerId)
                    load()
                    refreshListSearch()
                }
                is AppResult.Failure -> updateEdit {
                    copy(
                        isSaving = false,
                        errorMessage = CreditErrorText.edit(result.error),
                        focusRequest = focusRequest + 1,
                    )
                }
            }
        }
    }

    fun cancelEdit() {
        val edit = _state.value.edit ?: return
        if (edit.isSaving || edit.confirm != null) return
        if (edit.hasChanges) {
            updateEdit { copy(confirm = CustomerEditConfirm.DISCARD, leaveOnDiscard = false) }
        } else {
            _state.update { it.copy(edit = null) }
        }
    }

    fun onEditEscape() {
        val edit = _state.value.edit ?: return
        if (edit.confirm != null) dismissEditConfirm() else cancelEdit()
    }

    fun dismissEditConfirm() {
        updateEdit {
            if (isSaving || confirm == null) this else copy(confirm = null, leaveOnDiscard = false, focusRequest = focusRequest + 1)
        }
    }

    fun confirmDiscard() {
        val edit = _state.value.edit ?: return
        if (edit.isSaving || edit.confirm != CustomerEditConfirm.DISCARD) return
        _state.update { it.copy(edit = null) }
        if (edit.leaveOnDiscard) leaveDetail()
    }

    private fun refreshListSearch() {
        val filter = _state.value.filter
        if (filter.trim().length >= MIN_SEARCH_LENGTH) onFilterChange(filter)
    }

    private fun editForm(change: CustomerEditForm.() -> CustomerEditForm) {
        updateEdit { if (isSaving || confirm != null) this else copy(form = form.change(), errorMessage = null) }
    }

    private fun updateEdit(change: CustomerEditState.() -> CustomerEditState) {
        _state.update { current -> current.edit?.let { current.copy(edit = it.change()) } ?: current }
    }

    fun openLibreta() {
        _state.update {
            if (!it.canLoadLibreta || it.showsDetail || it.showsLibreta) {
                it
            } else {
                it.copy(libreta = LibretaState(), errorMessage = null)
            }
        }
    }

    fun closeLibreta() {
        val libreta = _state.value.libreta ?: return
        if (libreta.form.isSaving) return
        customerSearchJob?.cancel()
        _state.update { current ->
            if (current.libreta?.form?.isSaving == true) current else current.copy(libreta = null)
        }
        if (_state.value.libreta == null) refresh()
    }

    fun onLibretaEscape() {
        val libreta = _state.value.libreta ?: return
        if (libreta.formIsEmpty) closeLibreta() else clearLibretaForm()
    }

    fun clearLibretaForm() {
        customerSearchJob?.cancel()
        updateLibreta {
            if (form.isSaving) {
                this
            } else {
                copy(
                    form = LibretaForm(),
                    suggestions = emptyList(),
                    isSearching = false,
                    pendingKey = null,
                    errorMessage = null,
                    savedNotice = null,
                    focusRequest = focusRequest + 1,
                )
            }
        }
    }

    fun onLibretaName(value: String) {
        if (!editLibreta { copy(name = value) }) return
        customerSearchJob?.cancel()
        updateLibreta { copy(isSearching = false) }
        val libreta = _state.value.libreta ?: return
        val query = value.trim()
        if (libreta.form.selected != null || query.length < MIN_SEARCH_LENGTH) {
            updateLibreta { copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        customerSearchJob = scope.launch {
            delay(CUSTOMER_SEARCH_DEBOUNCE_MS)
            searchCustomers(query)
        }
    }

    fun onLibretaNickname(value: String) {
        editLibreta { copy(nickname = value.take(MAX_NICKNAME_LENGTH)) }
    }

    fun onLibretaPhone(value: String) {
        editLibreta { copy(phone = PhoneInput.sanitize(value)) }
    }

    fun onLibretaAmount(value: String) {
        val sanitized = sanitizeAmount(value)
        editLibreta { copy(amount = sanitized, amountInputRejected = sanitized != value) }
    }

    fun onLibretaOwedSince(value: String) {
        editLibreta { copy(owedSince = DateMask.apply(previous = owedSince, typed = value)) }
    }

    fun pickSuggestion(customerId: Long) {
        val match = _state.value.libreta?.suggestions?.firstOrNull { it.id == customerId } ?: return
        customerSearchJob?.cancel()
        updateLibreta {
            if (form.isSaving) {
                this
            } else {
                copy(
                    form = form.copy(selected = match),
                    suggestions = emptyList(),
                    isSearching = false,
                    pendingKey = null,
                    errorMessage = null,
                    focusRequest = focusRequest + 1,
                )
            }
        }
    }

    fun clearSelection() {
        customerSearchJob?.cancel()
        updateLibreta {
            if (form.isSaving || form.selected == null) {
                this
            } else {
                copy(
                    form = form.copy(selected = null, name = "", nickname = "", phone = ""),
                    suggestions = emptyList(),
                    isSearching = false,
                    pendingKey = null,
                    errorMessage = null,
                    focusRequest = focusRequest + 1,
                )
            }
        }
    }

    fun correct(customerId: Long) {
        customerSearchJob?.cancel()
        updateLibreta {
            val loaded = loadedToday.firstOrNull { it.customerId == customerId }
            if (form.isSaving || loaded == null) {
                this
            } else {
                copy(
                    form = LibretaForm(
                        amount = plainAmount(loaded.amount),
                        owedSince = loaded.owedSince?.let(::typedDate).orEmpty(),
                        selected = CustomerMatch(loaded.customerId, loaded.displayName, loaded.phone, loaded.amount),
                    ),
                    suggestions = emptyList(),
                    isSearching = false,
                    pendingKey = null,
                    errorMessage = null,
                    focusRequest = focusRequest + 1,
                )
            }
        }
    }

    fun saveLibreta() {
        val libreta = _state.value.libreta ?: return
        val form = libreta.form
        if (form.isSaving) return
        if (form.selected == null && !isValidName(form.name)) {
            showLibretaError(CreditStrings.NAME_REQUIRED)
            return
        }
        if (form.amountInputRejected) {
            updateLibreta {
                copy(
                    form = this.form.copy(amount = "", amountInputRejected = false),
                    errorMessage = CreditStrings.INPUT_REJECTED,
                )
            }
            return
        }
        val amount = form.amount.toDoubleOrNull()?.takeIf { it > 0.0 }
        if (amount == null) {
            showLibretaError(CreditStrings.AMOUNT_REQUIRED)
            return
        }
        if (form.selected == null && !PhoneInput.isComplete(form.phone)) {
            showLibretaError(CreditStrings.PHONE_INVALID)
            return
        }
        val owedSince = when (val parsed = parseOwedSince(form.owedSince)) {
            is DateInput.Invalid -> {
                showLibretaError(parsed.message)
                return
            }
            is DateInput.Valid -> parsed.iso
        }
        val key = libreta.pendingKey ?: buildKey()
        val previous = _state.getAndUpdate { current ->
            val open = current.libreta
            if (open == null || open.form.isSaving) {
                current
            } else {
                current.copy(
                    libreta = open.copy(form = open.form.copy(isSaving = true), pendingKey = key, errorMessage = null),
                )
            }
        }
        val before = previous.libreta
        if (before == null || before.form.isSaving) return
        customerSearchJob?.cancel()
        scope.launch { runSave(before.form, roundMoney(amount), owedSince, key) }
    }

    private suspend fun runSave(form: LibretaForm, amount: Double, owedSince: String?, key: String) {
        val customer = form.selected ?: when (
            val created = guarded {
                creditUseCases.createCustomer(form.name, form.nickname, form.phone)
            }
        ) {
            is AppResult.Success -> {
                updateLibreta {
                    copy(
                        form = this.form.copy(selected = created.value),
                        suggestions = emptyList(),
                        isSearching = false,
                    )
                }
                created.value
            }
            is AppResult.Failure -> {
                failSave(created.error, key)
                return
            }
        }
        when (val result = guarded { creditUseCases.loadOpeningBalance(customer.id, amount, owedSince, key) }) {
            is AppResult.Success -> updateLibreta {
                val entry = LoadedCustomer(customer.id, customer.displayName, amount, customer.phone, owedSince)
                copy(
                    form = LibretaForm(),
                    suggestions = emptyList(),
                    isSearching = false,
                    loadedToday = listOf(entry) + loadedToday.filterNot { it.customerId == customer.id },
                    errorMessage = null,
                    pendingKey = null,
                    savedNotice = CreditStrings.savedNotice(customer.displayName, amount),
                    focusRequest = focusRequest + 1,
                )
            }
            is AppResult.Failure -> failSave(result.error, key)
        }
    }

    private fun failSave(error: AppError, key: String) {
        updateLibreta {
            copy(
                form = form.copy(isSaving = false),
                pendingKey = if (error is AppError.Network) key else null,
                errorMessage = CreditErrorText.libreta(error),
                focusRequest = focusRequest + 1,
            )
        }
    }

    private suspend fun searchCustomers(query: String) {
        updateLibreta { copy(isSearching = true) }
        val result = guarded { creditUseCases.searchCustomers(query, CUSTOMER_SEARCH_LIMIT) }
        updateLibreta {
            if (form.selected != null || form.name.trim() != query) return@updateLibreta this
            when (result) {
                is AppResult.Success -> copy(suggestions = result.value, isSearching = false)
                is AppResult.Failure -> copy(suggestions = emptyList(), isSearching = false)
            }
        }
    }

    private fun editLibreta(change: LibretaForm.() -> LibretaForm): Boolean {
        var applied = false
        updateLibreta {
            if (form.isSaving) {
                this
            } else {
                applied = true
                copy(form = form.change(), pendingKey = null, errorMessage = null, savedNotice = null)
            }
        }
        return applied
    }

    private fun showLibretaError(message: String) {
        updateLibreta { copy(errorMessage = message) }
    }

    private fun updateLibreta(change: LibretaState.() -> LibretaState) {
        _state.update { current -> current.libreta?.let { current.copy(libreta = it.change()) } ?: current }
    }

    private fun isValidName(name: String): Boolean {
        val compact = name.filterNot { it.isWhitespace() }
        return compact.isNotEmpty() && !compact.all { it.isDigit() }
    }

    private fun parseOwedSince(value: String): DateInput {
        val text = value.trim()
        if (text.isEmpty()) return DateInput.Valid(null)
        val parts = text.split('/')
        val numbers = parts.mapNotNull { it.toIntOrNull() }
        if (parts.size != DATE_PARTS || numbers.size != DATE_PARTS || parts.last().length != YEAR_DIGITS) {
            return DateInput.Invalid(CreditStrings.DATE_INVALID)
        }
        val (day, month, year) = numbers
        val date = try {
            LocalDate(year, month, day)
        } catch (invalid: IllegalArgumentException) {
            return DateInput.Invalid(CreditStrings.DATE_INVALID)
        }
        val today = now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return if (date > today) DateInput.Invalid(CreditStrings.DATE_FUTURE) else DateInput.Valid(date.toString())
    }

    private fun typedDate(iso: String): String? = runCatching {
        val date = LocalDate.parse(iso)
        "${twoDigits(date.day)}/${twoDigits(date.month.number)}/${date.year}"
    }.getOrNull()

    private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

    private suspend fun <T> guarded(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppResult.Failure(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()))
    }

    private fun buildKey(): String {
        keySequence += 1
        return "libreta-${now().toEpochMilliseconds()}-$keySequence-${Random.nextInt(1_000, 9_999)}"
    }

    private fun sanitizeAmount(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        if (dot < 0) return filtered.take(MAX_AMOUNT_DIGITS)
        val whole = filtered.substring(0, dot).take(MAX_AMOUNT_DIGITS)
        val decimals = filtered.substring(dot + 1).replace(".", "").take(MAX_AMOUNT_DECIMALS)
        return "$whole.$decimals"
    }

    private fun roundMoney(amount: Double): Double = round(amount * 100.0) / 100.0

    private fun plainAmount(amount: Double): String {
        val cents = abs(round(amount * 100.0).toLong())
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    private sealed interface DateInput {
        data class Valid(val iso: String?) : DateInput
        data class Invalid(val message: String) : DateInput
    }

    companion object {
        const val LEDGER_LIMIT = 100
        const val CUSTOMER_SEARCH_DEBOUNCE_MS = 250L
        const val CUSTOMER_SEARCH_LIMIT = 5
        const val LIST_SEARCH_LIMIT = 10
        private const val MAX_NAME_LENGTH = 255
        private const val MAX_ADDRESS_LENGTH = 255
        private const val MIN_SEARCH_LENGTH = 2
        private const val MAX_AMOUNT_DIGITS = 6
        private const val MAX_AMOUNT_DECIMALS = 2
        private const val MAX_NICKNAME_LENGTH = 60
        private const val DATE_PARTS = 3
        private const val YEAR_DIGITS = 4
    }
}
