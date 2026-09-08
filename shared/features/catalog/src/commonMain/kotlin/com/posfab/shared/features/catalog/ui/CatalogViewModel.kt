package com.posfab.shared.features.catalog.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.catalog.common.CatalogErrorText
import com.posfab.shared.features.catalog.common.CatalogUseCases
import com.posfab.shared.features.catalog.domain.CatalogProductDetail
import com.posfab.shared.features.catalog.domain.CatalogProductSummary
import com.posfab.shared.features.catalog.domain.CreateProductInput
import com.posfab.shared.features.catalog.domain.UpdateProductInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val useCases: CatalogUseCases,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Business unit of the terminal running the app; preselected for new prices. */
    private val defaultBusinessUnitId: Long? = null,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(
        CatalogState(
            isLoadingList = true,
            isLoadingUnits = true,
            isLoadingBusinessUnits = true,
            selectedBusinessUnitId = defaultBusinessUnitId,
        )
    )
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var listDebounceJob: Job? = null

    init {
        refreshList(resetOffset = true)
        loadUnits()
        loadBusinessUnits()
    }

    fun onBusinessUnitChange(businessUnitId: Long) {
        _state.value = _state.value.copy(selectedBusinessUnitId = businessUnitId)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        debounceListReload()
    }

    fun onLimitChange(value: String) {
        val parsed = value.toIntOrNull() ?: return
        if (parsed <= 0) return
        _state.value = _state.value.copy(limit = parsed.coerceIn(1, 100), offset = 0)
        refreshList(resetOffset = false)
    }

    fun onActiveFilterChange(filter: ActiveFilter) {
        _state.value = _state.value.copy(activeFilter = filter, offset = 0)
        refreshList(resetOffset = false)
    }

    fun goNextPage() {
        val snapshot = _state.value
        if (!snapshot.canGoNextPage) return
        _state.value = snapshot.copy(offset = snapshot.offset + snapshot.limit)
        refreshList(resetOffset = false)
    }

    fun goPrevPage() {
        val snapshot = _state.value
        if (!snapshot.canGoPrevPage) return
        _state.value = snapshot.copy(offset = (snapshot.offset - snapshot.limit).coerceAtLeast(0))
        refreshList(resetOffset = false)
    }

    fun refreshList(resetOffset: Boolean = false) {
        val snapshot = _state.value
        scope.launch {
            val offset = if (resetOffset) 0 else snapshot.offset
            _state.value = _state.value.copy(isLoadingList = true, offset = offset, errorMessage = null)
            when (
                val result = useCases.repository.listProducts(
                    query = snapshot.query.trim().ifBlank { null },
                    limit = snapshot.limit,
                    offset = offset,
                    active = snapshot.activeFilter.toQuery(),
                )
            ) {
                is AppResult.Success -> {
                    val emptyNotice = if (result.value.items.isEmpty()) "Sin productos para el filtro actual" else null
                    _state.value = _state.value.copy(
                        isLoadingList = false,
                        products = result.value.items,
                        total = result.value.total,
                        offset = result.value.offset,
                        notice = emptyNotice ?: _state.value.notice,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingList = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun selectProduct(productId: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoadingDetail = true, errorMessage = null, notice = null)
            when (val result = useCases.repository.getProduct(productId)) {
                is AppResult.Success -> {
                    applySelectedProduct(result.value)
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingDetail = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun startCreateProduct() {
        val defaultUnit = _state.value.units.firstOrNull()?.code ?: "EA"
        _state.value = _state.value.copy(
            selectedProduct = null,
            formMode = CatalogFormMode.CREATING,
            form = ProductFormState(baseUnit = defaultUnit, active = true),
            hasUnsavedChanges = false,
            newUomUnit = defaultUnit,
            newUomFactorInput = "1",
            newPriceInput = "",
            showAdvancedFields = false,
            notice = null,
            errorMessage = null,
        )
    }

    fun cancelForm() {
        _state.value = _state.value.copy(
            formMode = CatalogFormMode.IDLE,
            selectedProduct = null,
            form = ProductFormState(),
            hasUnsavedChanges = false,
            notice = null,
            errorMessage = null,
        )
    }

    fun onFormNameChange(value: String) = updateForm { copy(name = value) }
    fun onFormSkuChange(value: String) = updateForm { copy(sku = value) }
    fun onFormBarcodeChange(value: String) = updateForm { copy(barcode = value) }
    fun onFormBaseUnitChange(value: String) = updateForm { copy(baseUnit = value) }
    fun onFormActiveChange(value: Boolean) = updateForm { copy(active = value) }
    fun onFormInitialPriceChange(value: String) = updateForm { copy(initialPrice = value) }

    fun onFormLotTrackedChange(value: Boolean) {
        if (value && !_state.value.form.lotTracked) {
            _state.value = _state.value.copy(showLotTrackingWarning = true)
        } else {
            updateForm { copy(lotTracked = value) }
        }
    }

    fun confirmLotTracking() {
        _state.value = _state.value.copy(showLotTrackingWarning = false)
        updateForm { copy(lotTracked = true) }
    }

    fun dismissLotTrackingWarning() {
        _state.value = _state.value.copy(showLotTrackingWarning = false)
    }

    fun toggleAdvancedFields() {
        _state.value = _state.value.copy(showAdvancedFields = !_state.value.showAdvancedFields)
    }

    fun saveProduct() {
        val snapshot = _state.value
        val form = snapshot.form
        val validationError = validateProductForm(form)
        if (validationError != null) {
            _state.value = snapshot.copy(errorMessage = validationError)
            return
        }

        val initialPrice = form.initialPrice.toDoubleOrNull()
        val wantsInitialPrice = snapshot.selectedProduct == null && initialPrice != null && initialPrice > 0.0
        val priceBusinessUnitId = snapshot.selectedBusinessUnitId
        if (wantsInitialPrice && priceBusinessUnitId == null) {
            _state.value = snapshot.copy(errorMessage = BUSINESS_UNIT_REQUIRED)
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null, notice = null)
            val result = if (snapshot.selectedProduct == null) {
                useCases.repository.createProduct(
                    CreateProductInput(
                        name = form.name.trim(),
                        sku = form.sku.trim().ifBlank { null },
                        barcode = form.barcode.trim().ifBlank { null },
                        baseUnit = form.baseUnit.trim(),
                        lotTracked = form.lotTracked,
                        active = form.active,
                    )
                )
            } else {
                useCases.repository.updateProduct(
                    snapshot.selectedProduct.id,
                    UpdateProductInput(
                        name = form.name.trim(),
                        sku = form.sku.trim().ifBlank { null },
                        barcode = form.barcode.trim().ifBlank { null },
                        baseUnit = form.baseUnit.trim(),
                        lotTracked = form.lotTracked,
                        active = form.active,
                    )
                )
            }

            when (result) {
                is AppResult.Success -> {
                    val isCreating = snapshot.selectedProduct == null
                    val parsedPrice = initialPrice

                    if (isCreating && parsedPrice != null && parsedPrice > 0.0 && priceBusinessUnitId != null) {
                        when (useCases.repository.addPrice(result.value.id, priceBusinessUnitId, parsedPrice)) {
                            is AppResult.Success -> {
                                // Refresh product to get updated price
                                when (val refreshed = useCases.repository.getProduct(result.value.id)) {
                                    is AppResult.Success -> applySelectedProduct(refreshed.value)
                                    is AppResult.Failure -> applySelectedProduct(result.value)
                                }
                                _state.value = _state.value.copy(
                                    isSaving = false,
                                    notice = "Producto creado con precio $${"%.2f".format(parsedPrice)}",
                                )
                            }
                            is AppResult.Failure -> {
                                applySelectedProduct(result.value)
                                _state.value = _state.value.copy(
                                    isSaving = false,
                                    notice = "Producto creado",
                                    errorMessage = "No se pudo registrar el precio. Registralo manualmente.",
                                )
                            }
                        }
                    } else {
                        applySelectedProduct(result.value)
                        _state.value = _state.value.copy(
                            isSaving = false,
                            notice = if (isCreating) "Producto creado" else "Producto actualizado",
                        )
                    }
                    refreshList(resetOffset = false)
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onNewUomUnitChange(value: String) {
        _state.value = _state.value.copy(newUomUnit = value)
    }

    fun onNewUomFactorChange(value: String) {
        _state.value = _state.value.copy(newUomFactorInput = value)
    }

    fun addUomConversion() {
        val snapshot = _state.value
        val productId = snapshot.selectedProduct?.id
        if (productId == null) {
            _state.value = snapshot.copy(errorMessage = "Selecciona un producto para agregar UOM")
            return
        }
        val factor = snapshot.newUomFactorInput.toDoubleOrNull()
        if (factor == null || factor <= 0.0) {
            _state.value = snapshot.copy(errorMessage = "factor_to_base debe ser mayor a 0")
            return
        }
        val unit = snapshot.newUomUnit.trim()
        if (unit.isEmpty()) {
            _state.value = snapshot.copy(errorMessage = "Unidad requerida")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            when (val result = useCases.repository.addUom(productId, unit, factor)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        notice = "UOM agregada",
                        newUomFactorInput = "1",
                    )
                    refreshCurrentProduct()
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onNewPriceChange(value: String) {
        _state.value = _state.value.copy(newPriceInput = value)
    }

    fun addPriceEntry() {
        val snapshot = _state.value
        val productId = snapshot.selectedProduct?.id
        if (productId == null) {
            _state.value = snapshot.copy(errorMessage = "Selecciona un producto para registrar precio")
            return
        }
        val price = snapshot.newPriceInput.toDoubleOrNull()
        if (price == null || price <= 0.0) {
            _state.value = snapshot.copy(errorMessage = "Precio invalido")
            return
        }
        val businessUnitId = snapshot.selectedBusinessUnitId
        if (businessUnitId == null) {
            _state.value = snapshot.copy(errorMessage = BUSINESS_UNIT_REQUIRED)
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            when (val result = useCases.repository.addPrice(productId, businessUnitId, price)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        newPriceInput = "",
                        notice = "Precio registrado",
                    )
                    refreshCurrentProduct()
                    refreshList(resetOffset = false)
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    fun onBarcodeCheckInputChange(value: String) {
        _state.value = _state.value.copy(barcodeCheckInput = value)
    }

    fun checkBarcode() {
        val barcode = _state.value.barcodeCheckInput.trim()
        if (barcode.isEmpty()) return

        scope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            when (val result = useCases.repository.findByBarcode(barcode)) {
                is AppResult.Success -> {
                    val detail = result.value
                    if (detail != null) {
                        applySelectedProduct(detail)
                        _state.value = _state.value.copy(
                            isSaving = false,
                            barcodeCheckInput = "",
                            barcodeCheckResult = detail.toSummary(),
                            notice = "Producto encontrado: ${detail.name}",
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isSaving = false,
                            barcodeCheckResult = null,
                            notice = "Codigo de barras no encontrado",
                        )
                    }
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    // ── Quick-add dialog ──

    fun openQuickAdd() {
        val defaultUnit = _state.value.units.firstOrNull()?.code ?: "EA"
        _state.value = _state.value.copy(
            showQuickAddDialog = true,
            quickAddName = "",
            quickAddPrice = "",
            quickAddUnit = defaultUnit,
            errorMessage = null,
        )
    }

    fun closeQuickAdd() {
        _state.value = _state.value.copy(showQuickAddDialog = false, errorMessage = null)
    }

    fun onQuickAddNameChange(value: String) {
        _state.value = _state.value.copy(quickAddName = value)
    }

    fun onQuickAddPriceChange(value: String) {
        _state.value = _state.value.copy(quickAddPrice = value)
    }

    fun onQuickAddUnitChange(value: String) {
        _state.value = _state.value.copy(quickAddUnit = value)
    }

    fun submitQuickAdd() {
        val s = _state.value
        val name = s.quickAddName.trim()
        if (name.isEmpty()) {
            _state.value = s.copy(errorMessage = "Nombre requerido")
            return
        }
        val price = s.quickAddPrice.toDoubleOrNull()
        if (price == null || price <= 0.0) {
            _state.value = s.copy(errorMessage = "Precio invalido")
            return
        }
        val businessUnitId = s.selectedBusinessUnitId
        if (businessUnitId == null) {
            _state.value = s.copy(errorMessage = BUSINESS_UNIT_REQUIRED)
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isQuickAdding = true, errorMessage = null)
            val createResult = useCases.repository.createProduct(
                CreateProductInput(
                    name = name,
                    sku = null,
                    barcode = null,
                    baseUnit = s.quickAddUnit,
                    lotTracked = false,
                    active = true,
                )
            )
            when (createResult) {
                is AppResult.Success -> {
                    useCases.repository.addPrice(createResult.value.id, businessUnitId, price)
                    _state.value = _state.value.copy(
                        isQuickAdding = false,
                        showQuickAddDialog = false,
                        notice = "Producto \"$name\" creado con precio $${"%.2f".format(price)}",
                        errorMessage = null,
                    )
                    refreshList(resetOffset = false)
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isQuickAdding = false,
                        errorMessage = CatalogErrorText.from(createResult.error),
                    )
                }
            }
        }
    }

    private fun refreshCurrentProduct() {
        val selectedId = _state.value.selectedProduct?.id ?: return
        selectProduct(selectedId)
    }

    private fun loadUnits() {
        scope.launch {
            _state.value = _state.value.copy(isLoadingUnits = true, errorMessage = null)
            when (val result = useCases.repository.listUnits()) {
                is AppResult.Success -> {
                    val firstUnit = result.value.firstOrNull()?.code.orEmpty()
                    _state.value = _state.value.copy(
                        isLoadingUnits = false,
                        units = result.value,
                        newUomUnit = _state.value.newUomUnit.ifBlank { firstUnit },
                        form = _state.value.form.copy(
                            baseUnit = if (_state.value.form.baseUnit.isBlank()) firstUnit else _state.value.form.baseUnit,
                        ),
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingUnits = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    private fun loadBusinessUnits() {
        scope.launch {
            _state.value = _state.value.copy(isLoadingBusinessUnits = true)
            when (val result = useCases.repository.listBusinessUnits()) {
                is AppResult.Success -> {
                    val units = result.value
                    val current = _state.value.selectedBusinessUnitId
                    val selected = current?.takeIf { id -> units.any { it.id == id } } ?: units.firstOrNull()?.id
                    _state.value = _state.value.copy(
                        isLoadingBusinessUnits = false,
                        businessUnits = units,
                        selectedBusinessUnitId = selected,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoadingBusinessUnits = false,
                        errorMessage = CatalogErrorText.from(result.error),
                    )
                }
            }
        }
    }

    private fun debounceListReload() {
        listDebounceJob?.cancel()
        listDebounceJob = scope.launch {
            delay(300)
            _state.value = _state.value.copy(offset = 0)
            refreshList(resetOffset = false)
        }
    }

    private fun applySelectedProduct(detail: CatalogProductDetail) {
        _state.value = _state.value.copy(
            isLoadingDetail = false,
            formMode = CatalogFormMode.EDITING,
            selectedProduct = detail,
            form = ProductFormState(
                name = detail.name,
                sku = detail.sku.orEmpty(),
                barcode = detail.barcode.orEmpty(),
                baseUnit = detail.baseUnit,
                lotTracked = detail.lotTracked,
                active = detail.active,
            ),
            hasUnsavedChanges = false,
            newUomUnit = _state.value.newUomUnit.ifBlank { detail.baseUnit },
            newUomFactorInput = "1",
        )
    }

    private fun updateForm(block: ProductFormState.() -> ProductFormState) {
        val updated = _state.value.form.block()
        _state.value = _state.value.copy(form = updated, hasUnsavedChanges = true)
    }

    private fun validateProductForm(form: ProductFormState): String? {
        if (form.name.trim().isEmpty()) return "Nombre requerido"
        if (form.baseUnit.trim().isEmpty()) return "Unidad base requerida"
        return null
    }

    private fun ActiveFilter.toQuery(): Boolean? = when (this) {
        ActiveFilter.ALL -> null
        ActiveFilter.ACTIVE_ONLY -> true
        ActiveFilter.INACTIVE_ONLY -> false
    }

    private companion object {
        const val BUSINESS_UNIT_REQUIRED = "Selecciona la unidad de negocio para el precio"
    }

    private fun CatalogProductDetail.toSummary() = CatalogProductSummary(
        id = id,
        name = name,
        sku = sku,
        barcode = barcode,
        baseUnit = baseUnit,
        lotTracked = lotTracked,
        active = active,
        currentPrice = currentPrice,
    )
}
