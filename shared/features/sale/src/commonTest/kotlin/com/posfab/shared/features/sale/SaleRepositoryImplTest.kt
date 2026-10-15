package com.posfab.shared.features.sale

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.session.InMemorySessionStorage
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.repository.SaleRepositoryImpl
import com.posfab.shared.network.catalog.BusinessConfigDto
import com.posfab.shared.network.catalog.BusinessUnitDto
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.CreateProductRequestDto
import com.posfab.shared.network.catalog.ProductDetailDto
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.catalog.ProductListResponseDto
import com.posfab.shared.network.catalog.ProductPricesDto
import com.posfab.shared.network.catalog.SetProductPricesRequestDto
import com.posfab.shared.network.catalog.UnitDto
import com.posfab.shared.network.catalog.UpdateProductRequestDto
import com.posfab.shared.network.pos.AddLineRequestDto
import com.posfab.shared.network.pos.BarcodeLookupProductDto
import com.posfab.shared.network.pos.BarcodeLookupResponseDto
import com.posfab.shared.network.pos.BarcodeLookupUomDto
import com.posfab.shared.network.pos.CancelDraftRequestDto
import com.posfab.shared.network.pos.CheckoutRequestDto
import com.posfab.shared.network.pos.CheckoutResponseDto
import com.posfab.shared.network.pos.DraftCreateRequestDto
import com.posfab.shared.network.pos.DraftCreateResponseDto
import com.posfab.shared.network.pos.DraftDto
import com.posfab.shared.network.pos.DraftLineDto
import com.posfab.shared.network.pos.DraftTotalsDto
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.ResolveLotsResponseDto
import com.posfab.shared.network.pos.UpdateLineRequestDto
import com.posfab.shared.network.pos.ValidateDraftResponseDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaleRepositoryImplTest {
    private suspend fun repository(posApi: FakePosApi, catalogApi: FakeCatalogApi = FakeCatalogApi()): SaleRepositoryImpl {
        val manager = SessionManager(InMemorySessionStorage())
        manager.set(
            UserSession(
                tokens = AuthTokens("access", "refresh"),
                user = AuthUser("u1", "cajero", setOf(UserRole.CASHIER), emptySet()),
                terminalCode = "POS1",
            ),
        )
        val executor = AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager)
        return SaleRepositoryImpl(posApi, catalogApi, TERMINAL_ID, executor)
    }

    @Test
    fun current_or_new_draft_returns_the_open_draft_without_creating() = runTest {
        val posApi = FakePosApi().apply { currentDraft = AppResult.Success(draftDto(id = 7, cashTotal = "40.00")) }
        val result = repository(posApi).currentOrNewDraft()

        val draft = assertIs<AppResult.Success<SaleDraft>>(result).value
        assertEquals(7L, draft.id)
        assertEquals(40.0, draft.totals.cashTotal)
        assertEquals(0, posApi.createCalls.size)
    }

    @Test
    fun current_or_new_draft_creates_only_when_there_is_no_open_draft() = runTest {
        val posApi = FakePosApi().apply {
            currentDraft = AppResult.Failure(AppError.Validation("No OPEN draft for terminal=3 type=SALE"))
        }
        val result = repository(posApi).currentOrNewDraft()

        assertEquals(99L, assertIs<AppResult.Success<SaleDraft>>(result).value.id)
        assertEquals(1, posApi.createCalls.size)
        assertEquals("SALE", posApi.createCalls.single().type)
        assertEquals(TERMINAL_ID, posApi.createCalls.single().terminalId)
        assertNull(posApi.createCalls.single().businessUnitId)
        assertEquals(listOf(99L), posApi.getDraftCalls)
    }

    @Test
    fun current_or_new_draft_passes_other_failures_through() = runTest {
        val failures = listOf(
            AppError.Network("down"),
            AppError.Validation("Request rejected: 400"),
            AppError.Forbidden,
        )
        failures.forEach { error ->
            val posApi = FakePosApi().apply { currentDraft = AppResult.Failure(error) }
            val result = repository(posApi).currentOrNewDraft()

            assertEquals(error, assertIs<AppResult.Failure>(result).error)
            assertEquals(0, posApi.createCalls.size)
        }
    }

    @Test
    fun new_draft_creates_then_reads_the_draft() = runTest {
        val posApi = FakePosApi()
        val result = repository(posApi).newDraft()

        assertEquals(99L, assertIs<AppResult.Success<SaleDraft>>(result).value.id)
        assertEquals(listOf("create", "get:99"), posApi.log)
    }

    @Test
    fun search_keeps_piece_products_with_prices_and_limits_after_filtering() = runTest {
        val items = listOf(
            listItem(id = "1", name = "Kilo", priceType = "WEIGHT", cash = "90.00"),
            listItem(id = "2", name = "Sin precio", cash = null),
            listItem(id = "3", name = "Coca", cash = "18.00"),
            listItem(id = "4", name = "Abierto", priceType = "OPEN", cash = "10.00"),
            listItem(id = "5", name = "Chicle", cash = "2.00"),
            listItem(id = "6", name = "Galletas", cash = "15.00"),
        )
        val catalogApi = FakeCatalogApi(items)
        val result = repository(FakePosApi(), catalogApi).searchProducts("a", 2)

        val products = assertIs<AppResult.Success<List<SaleProduct>>>(result).value
        assertEquals(listOf(3L, 5L), products.map { it.id })
        assertEquals(18.0, products.first().cashPrice)
        assertEquals(50, catalogApi.lastLimit)
        assertEquals("a", catalogApi.lastQuery)
    }

    @Test
    fun kitchen_products_keep_only_kitchen_items_sorted_by_kind_and_name() = runTest {
        val items = listOf(
            listItem(id = "1", name = "Torta cubana", cash = "60.00", kitchenKind = "TORTERIA"),
            listItem(id = "2", name = "Coca", cash = "18.00"),
            listItem(id = "3", name = "Sopa", cash = "40.00", kitchenKind = "FONDA"),
            listItem(id = "4", name = "Comida", cash = "63.00", kitchenKind = "FONDA"),
            listItem(id = "5", name = "Guisado kilo", priceType = "WEIGHT", cash = "200.00", kitchenKind = "FONDA"),
        )
        val catalogApi = FakeCatalogApi(items)
        val result = repository(FakePosApi(), catalogApi).kitchenProducts()

        val products = assertIs<AppResult.Success<List<SaleProduct>>>(result).value
        assertEquals(listOf("Comida", "Sopa", "Torta cubana"), products.map { it.name })
        assertEquals(
            listOf(SaleKitchenKind.FONDA, SaleKitchenKind.FONDA, SaleKitchenKind.TORTERIA),
            products.map { it.kitchenKind },
        )
        assertEquals(200, catalogApi.lastLimit)
        assertNull(catalogApi.lastQuery)
    }

    @Test
    fun barcode_not_found_maps_to_null() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Failure(AppError.Validation("Barcode not found")) }
        val result = repository(posApi).findProductByBarcode("7501")

        assertNull(assertIs<AppResult.Success<SaleProduct?>>(result).value)
    }

    @Test
    fun barcode_other_failures_pass_through() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Failure(AppError.Network("down")) }
        val result = repository(posApi).findProductByBarcode("7501")

        assertIs<AppResult.Failure>(result)
    }

    @Test
    fun inactive_barcode_product_maps_to_null() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Success(lookup(active = false)) }
        val result = repository(posApi).findProductByBarcode("7501")

        assertNull(assertIs<AppResult.Success<SaleProduct?>>(result).value)
    }

    @Test
    fun barcode_uses_the_matched_uom_unit_and_cash_price() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Success(lookup(matchedUnitId = 4L)) }
        val product = assertIs<AppResult.Success<SaleProduct?>>(repository(posApi).findProductByBarcode("7501")).value!!

        assertEquals(4L, product.unitId)
        assertEquals(21.0, product.cashPrice)
        assertEquals(SaleKitchenKind.FONDA, product.kitchenKind)
        assertTrue(product.isSellable)

        val noUom = FakePosApi().apply { barcode = AppResult.Success(lookup(matchedUnitId = null)) }
        val base = assertIs<AppResult.Success<SaleProduct?>>(repository(noUom).findProductByBarcode("7501")).value!!
        assertEquals(1L, base.unitId)
    }

    @Test
    fun checkout_cash_sends_paid_cash_amount_and_key_and_maps_total() = runTest {
        val posApi = FakePosApi()
        val result = repository(posApi).checkoutCash(draftId = 7L, paidAmount = 100.0, idempotencyKey = "key-1")

        val request = posApi.checkoutRequests.single()
        assertEquals("PAID", request.paymentStatus)
        assertEquals("CASH", request.paymentType)
        assertEquals("100.00", request.paidAmount)
        assertEquals("key-1", request.idempotencyKey)
        val charged = assertIs<AppResult.Success<CashCheckoutResult>>(result).value
        assertEquals(63.0, charged.cashTotal)
        assertEquals(37.0, charged.change)
        assertEquals("F-1", charged.folio)
    }

    private fun lookup(active: Boolean = true, matchedUnitId: Long? = null) = BarcodeLookupResponseDto(
        product = BarcodeLookupProductDto(
            id = 10L,
            sku = "SKU",
            name = "Cigarro",
            baseUnitId = 1L,
            requiresLotTracking = false,
            active = active,
            priceType = "PIECE",
            kitchenKind = "FONDA",
            prices = ProductPricesDto(cashTakeaway = "21.00", listTakeaway = "22.10"),
        ),
        matchedOn = if (matchedUnitId == null) "PRODUCT" else "UOM",
        matchedUom = matchedUnitId?.let { BarcodeLookupUomDto(id = 1L, unitId = it, factorToBase = "1", barcode = "7501") },
    )

    private fun listItem(
        id: String,
        name: String,
        cash: String?,
        priceType: String = "PIECE",
        kitchenKind: String? = null,
    ) = ProductListItemDto(
        id = id,
        name = name,
        baseUnit = "PZA",
        baseUnitId = 1L,
        lotTracked = false,
        active = true,
        priceType = priceType,
        kitchenKind = kitchenKind,
        prices = cash?.let { ProductPricesDto(cashTakeaway = it, listTakeaway = it) },
    )

    private companion object {
        const val TERMINAL_ID = 3L
    }
}

internal fun draftDto(id: Long, cashTotal: String = "0", lines: List<DraftLineDto> = emptyList()) = DraftDto(
    id = id,
    type = "SALE",
    status = "OPEN",
    terminalId = 3L,
    version = 1,
    lines = lines,
    totals = DraftTotalsDto(amount = cashTotal, subtotal = cashTotal, tax = "0", total = cashTotal, cashTotal = cashTotal),
)

private class NoRefreshAuthRepository(private val manager: SessionManager) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unauthorized)

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakePosApi : PosApi {
    var currentDraft: AppResult<DraftDto> = AppResult.Failure(AppError.Validation("No OPEN draft"))
    var barcode: AppResult<BarcodeLookupResponseDto> = AppResult.Failure(AppError.Validation("Barcode not found"))
    val createCalls = mutableListOf<DraftCreateRequestDto>()
    val getDraftCalls = mutableListOf<Long>()
    val checkoutRequests = mutableListOf<CheckoutRequestDto>()
    val log = mutableListOf<String>()

    override suspend fun currentCashSession(accessToken: String, terminalId: Long): AppResult<Boolean> =
        AppResult.Success(true)

    override suspend fun getCurrentSaleDraft(accessToken: String): AppResult<DraftDto> = currentDraft

    override suspend fun createDraft(accessToken: String, request: DraftCreateRequestDto): AppResult<DraftCreateResponseDto> {
        createCalls += request
        log += "create"
        return AppResult.Success(DraftCreateResponseDto(id = 99L, version = 1))
    }

    override suspend fun getDraft(accessToken: String, draftId: Long): AppResult<DraftDto> {
        getDraftCalls += draftId
        log += "get:$draftId"
        return AppResult.Success(draftDto(id = draftId))
    }

    override suspend fun addDraftLine(accessToken: String, draftId: Long, request: AddLineRequestDto): AppResult<DraftDto> =
        error("unused")

    override suspend fun updateDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        request: UpdateLineRequestDto,
    ): AppResult<DraftDto> = error("unused")

    override suspend fun removeDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        expectedVersion: Int,
    ): AppResult<DraftDto> = error("unused")

    override suspend fun validateDraft(accessToken: String, draftId: Long): AppResult<ValidateDraftResponseDto> =
        error("unused")

    override suspend fun resolveLots(accessToken: String, draftId: Long): AppResult<ResolveLotsResponseDto> =
        error("unused")

    override suspend fun checkoutDraft(
        accessToken: String,
        draftId: Long,
        request: CheckoutRequestDto,
    ): AppResult<CheckoutResponseDto> {
        checkoutRequests += request
        return AppResult.Success(CheckoutResponseDto(id = 1L, folio = "F-1", totalAmount = "63.00"))
    }

    override suspend fun cancelDraft(accessToken: String, draftId: Long, request: CancelDraftRequestDto): AppResult<Unit> =
        error("unused")

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> =
        this.barcode
}

private class FakeCatalogApi(private val items: List<ProductListItemDto> = emptyList()) : CatalogApi {
    var lastQuery: String? = null
    var lastLimit: Int? = null

    override suspend fun listProducts(
        accessToken: String,
        query: String?,
        limit: Int,
        offset: Int,
        active: Boolean?,
    ): AppResult<ProductListResponseDto> {
        lastQuery = query
        lastLimit = limit
        return AppResult.Success(ProductListResponseDto(items = items.take(limit), total = items.size, limit = limit, offset = offset))
    }

    override suspend fun createProduct(accessToken: String, request: CreateProductRequestDto): AppResult<ProductDetailDto> =
        error("unused")

    override suspend fun getProduct(accessToken: String, productId: String): AppResult<ProductDetailDto> = error("unused")

    override suspend fun updateProduct(
        accessToken: String,
        productId: String,
        request: UpdateProductRequestDto,
    ): AppResult<ProductDetailDto> = error("unused")

    override suspend fun setPrices(
        accessToken: String,
        productId: String,
        request: SetProductPricesRequestDto,
    ): AppResult<ProductDetailDto> = error("unused")

    override suspend fun assignInternalCode(accessToken: String, productId: String): AppResult<ProductDetailDto> =
        error("unused")

    override suspend fun getBusinessConfig(accessToken: String): AppResult<BusinessConfigDto> = error("unused")

    override suspend fun listUnits(accessToken: String): AppResult<List<UnitDto>> = error("unused")

    override suspend fun listBusinessUnits(accessToken: String): AppResult<List<BusinessUnitDto>> = error("unused")

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> =
        error("unused")
}
