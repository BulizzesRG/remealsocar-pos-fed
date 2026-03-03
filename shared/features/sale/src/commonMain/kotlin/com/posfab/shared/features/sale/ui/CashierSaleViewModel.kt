package com.posfab.shared.features.sale.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CheckoutMode
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.usecase.SaleUseCases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class CashierSaleViewModel(
    private val saleUseCases: SaleUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(CashierSaleState())
    val state: StateFlow<CashierSaleState> = _state.asStateFlow()

    init {
        openOrCreateDraft()
    }

    fun openOrCreateDraft() {
        scope.launch {
            _state.value = _state.value.copy(isInitializing = true, errorMessage = null, notice = null)
            when (val result = saleUseCases.openDraft()) {
                is AppResult.Success -> {
                    val firstLine = result.value.lines.firstOrNull()
                    _state.value = _state.value.copy(
                        isInitializing = false,
                        draft = result.value,
                        selectedLineId = firstLine?.id,
                        editQtyInput = firstLine?.qty?.toString().orEmpty(),
                        editUnitInput = firstLine?.unit.orEmpty(),
                        editPriceInput = firstLine?.unitPrice?.toString().orEmpty(),
                        editLotInput = firstLine?.lotId.orEmpty(),
                        validationIssues = emptyList(),
                        checkoutResult = null,
                        checkoutIdempotencyKey = null,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isInitializing = false,
                        errorMessage = SaleErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(value: String) {
        _state.value = _state.value.copy(searchQuery = value)
    }

    fun onBarcodeChange(value: String) {
        _state.value = _state.value.copy(barcodeInput = value)
    }

    fun searchProducts() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) return

        scope.launch {
            _state.value = _state.value.copy(isBusy = true, errorMessage = null)
            when (val result = saleUseCases.searchProducts(query, limit = 12)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isBusy = false, searchResults = result.value)
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        errorMessage = SaleErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun addByBarcode() {
        val barcode = _state.value.barcodeInput.trim()
        if (barcode.isEmpty()) return

        scope.launch {
            _state.value = _state.value.copy(isBusy = true, errorMessage = null)
            when (val lookup = saleUseCases.barcodeLookup(barcode)) {
                is AppResult.Success -> {
                    val product = lookup.value
                    if (product == null) {
                        _state.value = _state.value.copy(isBusy = false, errorMessage = "Codigo no encontrado")
                    } else {
                        addProductInternal(product)
                        _state.value = _state.value.copy(barcodeInput = "")
                    }
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        errorMessage = SaleErrorText.from(lookup.error),
                    )
                }
            }
        }
    }

    fun addProduct(product: SaleProduct) {
        scope.launch { addProductInternal(product) }
    }

    fun selectLine(lineId: String) {
        val line = _state.value.draft?.lines?.firstOrNull { it.id == lineId } ?: return
        _state.value = _state.value.copy(
            selectedLineId = lineId,
            editQtyInput = line.qty.toString(),
            editUnitInput = line.unit,
            editPriceInput = line.unitPrice.toString(),
            editLotInput = line.lotId.orEmpty(),
        )
    }

    fun incrementSelectedLineQty() {
        val currentLine = selectedLine() ?: return
        updateLine(currentLine.copy(qty = currentLine.qty + 1.0))
    }

    fun decrementSelectedLineQty() {
        val currentLine = selectedLine() ?: return
        val nextQty = (currentLine.qty - 1.0).coerceAtLeast(1.0)
        updateLine(currentLine.copy(qty = nextQty))
    }

    fun updateSelectedLineUnit(unit: String) {
        val currentLine = selectedLine() ?: return
        updateLine(currentLine.copy(unit = unit.trim().ifBlank { currentLine.unit }))
    }

    fun updateSelectedLinePrice(price: Double) {
        val currentLine = selectedLine() ?: return
        updateLine(currentLine.copy(unitPrice = price.coerceAtLeast(0.0)))
    }

    fun updateSelectedLineLot(lotId: String?) {
        val currentLine = selectedLine() ?: return
        updateLine(currentLine.copy(lotId = lotId?.trim()?.ifBlank { null }))
    }

    fun onEditQtyChange(value: String) {
        _state.value = _state.value.copy(editQtyInput = value)
    }

    fun onEditUnitChange(value: String) {
        _state.value = _state.value.copy(editUnitInput = value)
    }

    fun onEditPriceChange(value: String) {
        _state.value = _state.value.copy(editPriceInput = value)
    }

    fun onEditLotChange(value: String) {
        _state.value = _state.value.copy(editLotInput = value)
    }

    fun applySelectedLineEdits() {
        val currentLine = selectedLine() ?: return
        val snapshot = _state.value
        val qty = snapshot.editQtyInput.toDoubleOrNull() ?: currentLine.qty
        val unit = snapshot.editUnitInput.trim().ifBlank { currentLine.unit }
        val price = snapshot.editPriceInput.toDoubleOrNull() ?: currentLine.unitPrice
        val lot = snapshot.editLotInput.trim().ifBlank { null }
        updateLine(currentLine.copy(qty = qty.coerceAtLeast(1.0), unit = unit, unitPrice = price.coerceAtLeast(0.0), lotId = lot))
    }

    fun removeSelectedLine() {
        val snapshot = _state.value
        val draft = snapshot.draft ?: return
        val lineId = snapshot.selectedLineId ?: return

        scope.launch {
            _state.value = snapshot.copy(isBusy = true, errorMessage = null, notice = null)
            val result = saleUseCases.removeLine(draft.id, lineId, draft.version)
            applyMutationResult(result)
        }
    }

    fun validateDraft() {
        val draft = _state.value.draft ?: return

        scope.launch {
            _state.value = _state.value.copy(isBusy = true, errorMessage = null, notice = null)
            when (val result = saleUseCases.validateDraft(draft.id)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        validationIssues = result.value.issues,
                        notice = if (result.value.hasIssues) "Se encontraron observaciones." else "Borrador valido.",
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        errorMessage = SaleErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun resolveLots() {
        val draft = _state.value.draft ?: return

        scope.launch {
            _state.value = _state.value.copy(isBusy = true, errorMessage = null, notice = null)
            when (val result = saleUseCases.resolveLots(draft.id)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        draft = result.value,
                        validationIssues = emptyList(),
                        notice = "Lotes resueltos.",
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        errorMessage = SaleErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun checkoutCash() = checkout(CheckoutMode.CASH)

    fun checkoutCredit() = checkout(CheckoutMode.ON_CREDIT)

    fun startNewSale() {
        openOrCreateDraft()
    }

    private fun checkout(mode: CheckoutMode) {
        val snapshot = _state.value
        val draft = snapshot.draft ?: return
        if (snapshot.isCheckoutInFlight) return

        val checkoutKey = snapshot.checkoutIdempotencyKey ?: buildCheckoutKey(draft.id)
        _state.value = snapshot.copy(
            isCheckoutInFlight = true,
            errorMessage = null,
            notice = null,
            checkoutIdempotencyKey = checkoutKey,
        )

        scope.launch {
            when (val result = saleUseCases.checkout(draft.id, mode, draft.version, checkoutKey)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isCheckoutInFlight = false,
                        checkoutResult = result.value,
                        notice = "Venta cerrada. Folio ${result.value.folio}",
                        draft = draft.copy(status = "COMPLETED"),
                        validationIssues = emptyList(),
                        searchResults = emptyList(),
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isCheckoutInFlight = false,
                        errorMessage = SaleErrorText.from(result.error),
                    )
                }
            }
        }
    }

    private suspend fun addProductInternal(product: SaleProduct) {
        val draft = _state.value.draft ?: return
        val result = saleUseCases.addLine(
            draftId = draft.id,
            product = product,
            qty = 1.0,
            unit = product.defaultUnit,
            price = product.unitPrice,
            lotId = null,
            expectedVersion = draft.version,
        )
        applyMutationResult(result)
    }

    private fun selectedLine(): SaleLine? {
        val snapshot = _state.value
        val lineId = snapshot.selectedLineId ?: return null
        return snapshot.draft?.lines?.firstOrNull { it.id == lineId }
    }

    private fun updateLine(line: SaleLine) {
        val draft = _state.value.draft ?: return

        scope.launch {
            _state.value = _state.value.copy(isBusy = true, errorMessage = null, notice = null)
            val result = saleUseCases.updateLine(
                draftId = draft.id,
                lineId = line.id,
                qty = line.qty,
                unit = line.unit,
                price = line.unitPrice,
                lotId = line.lotId,
                expectedVersion = draft.version,
            )
            applyMutationResult(result)
        }
    }

    private fun applyMutationResult(result: SaleMutationResult) {
        when (result) {
            is SaleMutationResult.Success -> {
                val selected = result.draft.lines.firstOrNull { it.id == _state.value.selectedLineId } ?: result.draft.lines.firstOrNull()
                _state.value = _state.value.copy(
                    isBusy = false,
                    draft = result.draft,
                    selectedLineId = selected?.id,
                    editQtyInput = selected?.qty?.toString().orEmpty(),
                    editUnitInput = selected?.unit.orEmpty(),
                    editPriceInput = selected?.unitPrice?.toString().orEmpty(),
                    editLotInput = selected?.lotId.orEmpty(),
                    notice = null,
                )
            }
            is SaleMutationResult.ConflictRefetched -> {
                val selected = result.draft.lines.firstOrNull()
                _state.value = _state.value.copy(
                    isBusy = false,
                    draft = result.draft,
                    selectedLineId = selected?.id,
                    editQtyInput = selected?.qty?.toString().orEmpty(),
                    editUnitInput = selected?.unit.orEmpty(),
                    editPriceInput = selected?.unitPrice?.toString().orEmpty(),
                    editLotInput = selected?.lotId.orEmpty(),
                    notice = "Otro usuario modifico el borrador. Se recargo la ultima version.",
                )
            }
            is SaleMutationResult.Failure -> {
                _state.value = _state.value.copy(
                    isBusy = false,
                    errorMessage = SaleErrorText.from(result.error),
                )
            }
        }
    }

    private fun buildCheckoutKey(draftId: String): String {
        return "sale-$draftId-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000, 9_999)}"
    }
}
