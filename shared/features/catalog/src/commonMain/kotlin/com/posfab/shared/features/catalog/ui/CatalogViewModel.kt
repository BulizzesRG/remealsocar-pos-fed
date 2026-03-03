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
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(CatalogState(isLoadingList = true, isLoadingUnits = true))
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var listDebounceJob: Job? = null

    init {
        refreshList(resetOffset = true)
        loadUnits()
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
                    _state.value = _state.value.copy(
                        isLoadingList = false,
                        products = result.value.items,
                        total = result.value.total,
                        offset = result.value.offset,
                        notice = if (result.value.items.isEmpty()) "Sin productos para el filtro actual" else null,
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
            form = ProductFormState(baseUnit = defaultUnit, active = true),
            hasUnsavedChanges = false,
            newUomUnit = defaultUnit,
            newUomFactorInput = "1",
            newPriceInput = "",
            notice = "Capturando nuevo producto",
            errorMessage = null,
        )
    }

    fun onFormNameChange(value: String) = updateForm { copy(name = value) }
    fun onFormSkuChange(value: String) = updateForm { copy(sku = value) }
    fun onFormBarcodeChange(value: String) = updateForm { copy(barcode = value) }
    fun onFormBaseUnitChange(value: String) = updateForm { copy(baseUnit = value) }
    fun onFormLotTrackedChange(value: Boolean) = updateForm { copy(lotTracked = value) }
    fun onFormActiveChange(value: Boolean) = updateForm { copy(active = value) }

    fun saveProduct() {
        val snapshot = _state.value
        val form = snapshot.form
        val validationError = validateProductForm(form)
        if (validationError != null) {
            _state.value = snapshot.copy(errorMessage = validationError)
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
                    applySelectedProduct(result.value)
                    _state.value = _state.value.copy(
                        isSaving = false,
                        notice = if (snapshot.selectedProduct == null) "Producto creado" else "Producto actualizado",
                    )
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

        scope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            when (val result = useCases.repository.addPrice(productId, price)) {
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
                    _state.value = _state.value.copy(
                        isSaving = false,
                        barcodeCheckResult = detail?.toSummary(),
                        notice = if (detail == null) "Codigo no encontrado" else "Codigo encontrado: ${detail.name}",
                    )
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
