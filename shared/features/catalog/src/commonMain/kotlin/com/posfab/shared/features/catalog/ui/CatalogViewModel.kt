package com.posfab.shared.features.catalog.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.input.ScanBurstDetector
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.catalog.common.CatalogErrorText
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.common.moneyInput
import com.posfab.shared.features.catalog.common.proposeListPrice
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.KitchenKind
import com.posfab.shared.features.catalog.domain.PriceType
import com.posfab.shared.features.catalog.domain.PricesInput
import com.posfab.shared.features.catalog.domain.ProductInput
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
import kotlin.time.Clock

class CatalogViewModel(
    private val useCases: CatalogUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val searchScans = ScanBurstDetector()

    private val repository get() = useCases.repository
    private var listDebounceJob: Job? = null
    private var listJob: Job? = null
    private var selectJob: Job? = null
    private var lookupJob: Job? = null
    private var loadMoreJob: Job? = null
    private var priceHistoryJob: Job? = null

    init {
        loadCashDiscountPct()
        refreshList()
    }

    fun onQueryChange(value: String) {
        searchScans.onChange(_state.value.query, value, now())
        _state.update { it.copy(query = value) }
        listDebounceJob?.cancel()
        listDebounceJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            refreshList()
        }
    }

    fun onActiveOnlyChange(activeOnly: Boolean) {
        _state.update { it.copy(activeOnly = activeOnly) }
        refreshList()
    }

    fun refreshList() {
        listDebounceJob?.cancel()
        listJob?.cancel()
        loadMoreJob?.cancel()
        val snapshot = _state.value
        val query = snapshot.query.trim().ifBlank { null }
        _state.update { it.copy(isLoadingList = true, isLoadingMore = false) }
        listJob = scope.launch {
            when (val result = repository.listProducts(query, snapshot.activeOnly, 0)) {
                is AppResult.Success -> _state.update {
                    it.copy(isLoadingList = false, products = result.value.items, total = result.value.total)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isLoadingList = false, errorMessage = CatalogErrorText.from(result.error))
                }
            }
        }
    }

    fun loadMore() {
        val before = _state.getAndUpdate {
            if (it.isLoadingMore || it.isLoadingList || !it.canLoadMore) it else it.copy(isLoadingMore = true)
        }
        if (before.isLoadingMore || before.isLoadingList || !before.canLoadMore) return
        val query = before.query.trim().ifBlank { null }
        val offset = before.products.size
        loadMoreJob = scope.launch {
            when (val result = repository.listProducts(query, before.activeOnly, offset)) {
                is AppResult.Success -> _state.update { current ->
                    val known = current.products.map { it.id }.toSet()
                    current.copy(
                        isLoadingMore = false,
                        products = current.products + result.value.items.filterNot { it.id in known },
                        total = result.value.total,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isLoadingMore = false, errorMessage = CatalogErrorText.from(result.error))
                }
            }
        }
    }

    fun onSearchSubmit() {
        val scanned = searchScans.scanOnConfirm(now())?.code
        val typed = _state.value.query.trim()
        val query = scanned ?: typed
        if (!query.isScanCode()) {
            refreshList()
            return
        }
        listDebounceJob?.cancel()
        lookupJob?.cancel()
        selectJob?.cancel()
        _state.update { it.copy(query = "") }
        lookupJob = scope.launch {
            when (val result = repository.findByBarcode(query)) {
                is AppResult.Success -> {
                    val detail = result.value
                    if (detail != null) {
                        leaveTo(PendingAction.ScanOpen(detail))
                    } else {
                        _state.update { it.copy(errorMessage = CatalogStrings.notFoundWithCode(query), notice = null) }
                    }
                    refreshList()
                }
                is AppResult.Failure -> _state.update { it.copy(errorMessage = CatalogErrorText.from(result.error)) }
            }
        }
    }

    fun selectProduct(id: String) = leaveTo(PendingAction.Select(id))

    fun startCreate() = leaveTo(PendingAction.Create)

    fun cancel() = leaveTo(PendingAction.Cancel)

    fun confirmDiscard() {
        val confirm = _state.value.confirm as? CatalogConfirm.Discard ?: return
        _state.update { it.copy(confirm = null, hasUnsavedChanges = false) }
        perform(confirm.next)
    }

    fun confirmSave() {
        if (_state.value.confirm != CatalogConfirm.Save) return
        _state.update { it.copy(confirm = null) }
        runSave()
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirm = null) }
    }

    private fun leaveTo(next: PendingAction) {
        val snapshot = _state.value
        if (snapshot.mode != CatalogFormMode.IDLE && snapshot.hasUnsavedChanges) {
            _state.update { it.copy(confirm = CatalogConfirm.Discard(next)) }
        } else {
            perform(next)
        }
    }

    private fun perform(next: PendingAction) {
        when (next) {
            PendingAction.Cancel -> closeForm()
            PendingAction.Create -> openCreate()
            is PendingAction.Select -> loadProduct(next.id)
            is PendingAction.ScanOpen -> openProduct(next.detail)
        }
    }

    private fun loadProduct(id: String) {
        selectJob?.cancel()
        lookupJob?.cancel()
        selectJob = scope.launch {
            when (val result = repository.getProduct(id)) {
                is AppResult.Success -> openProduct(result.value)
                is AppResult.Failure -> _state.update { it.copy(errorMessage = CatalogErrorText.from(result.error)) }
            }
        }
    }

    private fun openCreate() {
        priceHistoryJob?.cancel()
        _state.update {
            it.withoutPriceHistory().copy(
                mode = CatalogFormMode.CREATING,
                selected = null,
                form = ProductForm(),
                hasUnsavedChanges = false,
                isSaving = false,
                isGeneratingCode = false,
                showCountSection = false,
                notice = null,
                errorMessage = null,
            )
        }
    }

    fun onNameChange(value: String) = updateForm { copy(name = value) }

    fun onBarcodeChange(value: String) = updateForm {
        val barcode = value.trim()
        copy(barcode = barcode, generateCodeOnSave = generateCodeOnSave && barcode.isBlank())
    }

    fun onPriceTypeChange(type: PriceType) = updateForm {
        copy(
            priceType = type,
            counted = if (type == PriceType.OPEN) false else counted,
            piecesPerPack = if (type == PriceType.PIECE) piecesPerPack else "",
        )
    }

    fun onCashTakeawayChange(value: String) {
        val pct = _state.value.cashDiscountPct
        updateForm {
            val cash = value.moneyChars()
            copy(cashTakeaway = cash, listTakeaway = derivedList(cash, listTakeaway, listTakeawayEdited, suggestedPrice, pct))
        }
    }

    fun onListTakeawayChange(value: String) = updateForm {
        if (suggestedPrice) this else copy(listTakeaway = value.moneyChars(), listTakeawayEdited = true)
    }

    fun onCashOnsiteChange(value: String) {
        val pct = _state.value.cashDiscountPct
        updateForm {
            val cash = value.moneyChars()
            if (cash.isBlank()) {
                copy(cashOnsite = cash, listOnsite = "", listOnsiteEdited = false)
            } else {
                copy(cashOnsite = cash, listOnsite = derivedList(cash, listOnsite, listOnsiteEdited, suggestedPrice, pct))
            }
        }
    }

    fun onListOnsiteChange(value: String) = updateForm {
        if (suggestedPrice || cashOnsite.isBlank()) this else copy(listOnsite = value.moneyChars(), listOnsiteEdited = true)
    }

    fun onSuggestedPriceChange(checked: Boolean) {
        val pct = _state.value.cashDiscountPct
        updateForm {
            copy(
                suggestedPrice = checked,
                listTakeaway = derivedList(cashTakeaway, listTakeaway, edited = false, suggested = checked, pct = pct),
                listOnsite = if (cashOnsite.isBlank()) "" else derivedList(cashOnsite, listOnsite, edited = false, suggested = checked, pct = pct),
                listTakeawayEdited = false,
                listOnsiteEdited = false,
            )
        }
    }

    fun onKitchenKindChange(kind: KitchenKind?) = updateForm { copy(kitchenKind = kind) }

    fun onCountedChange(counted: Boolean) = updateForm {
        copy(counted = if (priceType == PriceType.OPEN) false else counted)
    }

    fun onPiecesPerPackChange(value: String) = updateForm {
        copy(piecesPerPack = value.filterIndexed { index, c -> c.isDigit() || (index == 0 && c == '-') })
    }

    fun onActiveChange(active: Boolean) = updateForm { copy(active = active) }

    fun toggleCountSection() {
        _state.update { it.copy(showCountSection = !it.showCountSection) }
    }

    fun togglePriceHistory() {
        val snapshot = _state.value
        if (snapshot.mode != CatalogFormMode.EDITING) return
        val opening = !snapshot.showPriceHistory
        _state.update { it.copy(showPriceHistory = opening) }
        if (opening && snapshot.priceHistory == null && !snapshot.isLoadingPriceHistory) loadPriceHistory()
    }

    private fun loadPriceHistory() {
        val productId = _state.value.selected?.id ?: return
        priceHistoryJob?.cancel()
        _state.update { it.copy(isLoadingPriceHistory = true, priceHistoryError = null) }
        priceHistoryJob = scope.launch {
            val result = repository.getPriceHistory(productId)
            _state.update {
                if (it.selected?.id != productId) {
                    it
                } else {
                    when (result) {
                        is AppResult.Success -> it.copy(isLoadingPriceHistory = false, priceHistory = result.value)
                        is AppResult.Failure -> it.copy(
                            isLoadingPriceHistory = false,
                            priceHistoryError = CatalogErrorText.from(result.error),
                        )
                    }
                }
            }
        }
    }

    private fun invalidatePriceHistory() {
        priceHistoryJob?.cancel()
        _state.update { it.copy(priceHistory = null, isLoadingPriceHistory = false, priceHistoryError = null) }
        if (_state.value.showPriceHistory) loadPriceHistory()
    }

    private fun CatalogState.withoutPriceHistory(): CatalogState = copy(
        priceHistory = null,
        isLoadingPriceHistory = false,
        priceHistoryError = null,
        showPriceHistory = false,
    )

    fun requestGenerateCode() {
        val snapshot = _state.value
        if (snapshot.isGeneratingCode || snapshot.isSaving) return
        when (snapshot.mode) {
            CatalogFormMode.IDLE -> Unit
            CatalogFormMode.CREATING -> updateForm { copy(generateCodeOnSave = !generateCodeOnSave, barcode = "") }
            CatalogFormMode.EDITING -> {
                val productId = snapshot.selected?.id ?: return
                val before = _state.getAndUpdate {
                    if (it.isGeneratingCode || it.isSaving) it else it.copy(isGeneratingCode = true, errorMessage = null, notice = null)
                }
                if (before.isGeneratingCode || before.isSaving) return
                scope.launch {
                    when (val result = repository.assignInternalCode(productId)) {
                        is AppResult.Success -> _state.update {
                            it.copy(
                                isGeneratingCode = false,
                                selected = result.value,
                                form = it.form.copy(barcode = result.value.barcode.orEmpty(), generateCodeOnSave = false),
                                notice = CatalogStrings.CODE_GENERATED,
                            )
                        }
                        is AppResult.Failure -> _state.update {
                            it.copy(isGeneratingCode = false, errorMessage = CatalogErrorText.from(result.error))
                        }
                    }
                }
            }
        }
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.mode == CatalogFormMode.IDLE || snapshot.isSaving || snapshot.isGeneratingCode) return
        val validation = validate(snapshot.form)
        if (validation is FormValidation.Invalid) {
            _state.update { it.copy(errorMessage = validation.message, notice = null) }
            return
        }
        _state.update { it.copy(confirm = CatalogConfirm.Save, errorMessage = null) }
    }

    private fun runSave() {
        val snapshot = _state.value
        if (snapshot.mode == CatalogFormMode.IDLE || snapshot.isSaving) return
        val form = snapshot.form
        val validation = validate(form)
        if (validation is FormValidation.Invalid) {
            _state.update { it.copy(errorMessage = validation.message, notice = null) }
            return
        }
        val valid = validation as FormValidation.Valid
        val before = _state.getAndUpdate {
            if (it.isSaving || it.isGeneratingCode) it else it.copy(isSaving = true, errorMessage = null, notice = null)
        }
        if (before.isSaving || before.isGeneratingCode) return
        scope.launch {
            val existing = _state.value.selected?.takeIf { _state.value.mode == CatalogFormMode.EDITING }
            val saved = if (existing == null) {
                repository.createProduct(valid.product)
            } else {
                repository.updateProduct(existing.id, valid.product)
            }
            var detail = saved.valueOrStop() ?: return@launch
            _state.update { it.copy(selected = detail, mode = CatalogFormMode.EDITING) }
            if (existing == null) refreshList()

            if (valid.prices != null) {
                detail = repository.setPrices(detail.id, valid.prices).valueOrStop() ?: return@launch
                _state.update { it.copy(selected = detail) }
                invalidatePriceHistory()
            }

            if (form.generateCodeOnSave && valid.product.barcode == null) {
                detail = repository.assignInternalCode(detail.id).valueOrStop() ?: return@launch
                _state.update { it.copy(selected = detail, form = it.form.copy(generateCodeOnSave = false)) }
            }

            _state.update {
                it.copy(
                    selected = detail,
                    form = formFrom(detail, it.cashDiscountPct),
                    hasUnsavedChanges = false,
                    isSaving = false,
                    notice = CatalogStrings.SAVED,
                )
            }
            refreshList()
        }
    }

    private fun closeForm() {
        priceHistoryJob?.cancel()
        _state.update {
            it.withoutPriceHistory().copy(
                mode = CatalogFormMode.IDLE,
                selected = null,
                form = ProductForm(),
                hasUnsavedChanges = false,
                isSaving = false,
                isGeneratingCode = false,
                showCountSection = false,
                notice = null,
                errorMessage = null,
            )
        }
    }

    private fun loadCashDiscountPct() {
        scope.launch {
            val result = repository.cashDiscountPct()
            if (result is AppResult.Success) {
                _state.update { it.copy(cashDiscountPct = result.value) }
            }
        }
    }

    private fun openProduct(detail: CatalogProductDetail) {
        priceHistoryJob?.cancel()
        _state.update {
            it.withoutPriceHistory().copy(
                mode = CatalogFormMode.EDITING,
                selected = detail,
                form = formFrom(detail, it.cashDiscountPct),
                hasUnsavedChanges = false,
                isSaving = false,
                isGeneratingCode = false,
                notice = null,
                errorMessage = null,
            )
        }
    }

    private fun formFrom(detail: CatalogProductDetail, pct: Double): ProductForm {
        val prices = detail.prices
        val suggested = prices?.suggestedPrice ?: false
        val takeawayEdited = prices != null && !suggested &&
            prices.listTakeaway != proposeListPrice(prices.cashTakeaway, pct)
        val onsiteCash = prices?.cashOnsite
        val onsiteList = prices?.listOnsite
        val onsiteEdited = !suggested && onsiteCash != null && onsiteList != null &&
            onsiteList != proposeListPrice(onsiteCash, pct)
        return ProductForm(
            name = detail.name,
            barcode = detail.barcode.orEmpty(),
            priceType = detail.priceType,
            cashTakeaway = moneyInput(prices?.cashTakeaway),
            listTakeaway = moneyInput(prices?.listTakeaway),
            cashOnsite = moneyInput(prices?.cashOnsite),
            listOnsite = moneyInput(prices?.listOnsite),
            listTakeawayEdited = takeawayEdited,
            listOnsiteEdited = onsiteEdited,
            suggestedPrice = suggested,
            kitchenKind = detail.kitchenKind,
            counted = detail.counted,
            piecesPerPack = detail.piecesPerPack?.toString().orEmpty(),
            active = detail.active,
            sku = detail.sku,
            lotTracked = detail.lotTracked,
            generateCodeOnSave = false,
        )
    }

    private fun updateForm(block: ProductForm.() -> ProductForm) {
        _state.update {
            if (it.mode == CatalogFormMode.IDLE) it else it.copy(form = it.form.block(), hasUnsavedChanges = true, notice = null)
        }
    }

    private fun <T> AppResult<T>.valueOrStop(): T? = when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> {
            stopSaving(error)
            null
        }
    }

    private fun stopSaving(error: AppError) {
        _state.update { it.copy(isSaving = false, errorMessage = CatalogErrorText.from(error)) }
    }

    private fun validate(form: ProductForm): FormValidation {
        val name = form.name.trim()
        if (name.isEmpty()) return FormValidation.Invalid(CatalogStrings.NAME_REQUIRED)

        val prices = if (form.priceType == PriceType.OPEN) {
            null
        } else {
            when (val result = validatePrices(form)) {
                is PricesValidation.Invalid -> return FormValidation.Invalid(result.message)
                is PricesValidation.Valid -> result.prices
            }
        }

        val piecesPerPack = if (form.priceType == PriceType.WEIGHT) {
            null
        } else {
            when (val raw = form.piecesPerPack.trim()) {
                "" -> null
                else -> {
                    val parsed = raw.toIntOrNull() ?: return FormValidation.Invalid(CatalogStrings.PIECES_INVALID)
                    when {
                        parsed < 0 -> return FormValidation.Invalid(CatalogStrings.PIECES_INVALID)
                        parsed <= 1 -> null
                        else -> parsed
                    }
                }
            }
        }

        val product = ProductInput(
            name = name,
            barcode = form.barcode.trim().ifBlank { null },
            priceType = form.priceType,
            counted = form.counted && form.priceType != PriceType.OPEN,
            piecesPerPack = piecesPerPack,
            kitchenKind = form.kitchenKind,
            active = form.active,
            sku = form.sku,
            lotTracked = form.lotTracked,
        )
        return FormValidation.Valid(product, prices)
    }

    private fun validatePrices(form: ProductForm): PricesValidation {
        val pct = _state.value.cashDiscountPct
        val cashTakeaway = form.cashTakeaway.toDoubleOrNull()?.takeIf { it > 0 }
            ?: return PricesValidation.Invalid(CatalogStrings.CASH_REQUIRED)
        val listTakeaway = resolveList(form.listTakeaway, cashTakeaway, form.suggestedPrice, pct)
            ?: return PricesValidation.Invalid(CatalogStrings.CARD_REQUIRED)
        if (listTakeaway < cashTakeaway) return PricesValidation.Invalid(CatalogStrings.CARD_BELOW_CASH)

        var cashOnsite: Double? = null
        var listOnsite: Double? = null
        if (form.cashOnsite.isNotBlank()) {
            val cash = form.cashOnsite.toDoubleOrNull()?.takeIf { it > 0 }
                ?: return PricesValidation.Invalid(CatalogStrings.ONSITE_INVALID)
            val list = resolveList(form.listOnsite, cash, form.suggestedPrice, pct)
                ?: return PricesValidation.Invalid(CatalogStrings.CARD_REQUIRED)
            if (list < cash) return PricesValidation.Invalid(CatalogStrings.CARD_BELOW_CASH)
            cashOnsite = cash
            listOnsite = list
        }

        return PricesValidation.Valid(
            PricesInput(
                cashTakeaway = cashTakeaway,
                listTakeaway = listTakeaway,
                cashOnsite = cashOnsite,
                listOnsite = listOnsite,
                suggestedPrice = form.suggestedPrice,
            ),
        )
    }

    private fun resolveList(raw: String, cash: Double, suggested: Boolean, pct: Double): Double? = when {
        suggested -> cash
        raw.isBlank() -> proposeListPrice(cash, pct)
        else -> raw.toDoubleOrNull()
    }

    private fun derivedList(cash: String, current: String, edited: Boolean, suggested: Boolean, pct: Double): String = when {
        suggested -> cash
        edited -> current
        else -> cash.toDoubleOrNull()?.takeIf { it > 0 }?.let { moneyInput(proposeListPrice(it, pct)) } ?: ""
    }

    private fun String.moneyChars(): String {
        val cleaned = filter { it.isDigit() || it == '.' }
        val dot = cleaned.indexOf('.')
        if (dot < 0) return cleaned
        val decimals = cleaned.substring(dot + 1).filter { it.isDigit() }.take(MAX_DECIMALS)
        return "${cleaned.substring(0, dot)}.$decimals"
    }

    private fun String.isScanCode(): Boolean = length in SCAN_MIN_LENGTH..SCAN_MAX_LENGTH && all { it.isDigit() }

    private sealed interface FormValidation {
        data class Valid(val product: ProductInput, val prices: PricesInput?) : FormValidation
        data class Invalid(val message: String) : FormValidation
    }

    private sealed interface PricesValidation {
        data class Valid(val prices: PricesInput) : PricesValidation
        data class Invalid(val message: String) : PricesValidation
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SCAN_MIN_LENGTH = 6
        const val SCAN_MAX_LENGTH = 80
        const val MAX_DECIMALS = 2
    }
}
