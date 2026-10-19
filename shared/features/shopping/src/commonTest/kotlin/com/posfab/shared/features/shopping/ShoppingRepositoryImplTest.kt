package com.posfab.shared.features.shopping

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
import com.posfab.shared.features.shopping.domain.ShoppingCurrent
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingListStatus
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.domain.ShoppingProduct
import com.posfab.shared.features.shopping.repository.ShoppingRepositoryImpl
import com.posfab.shared.network.catalog.BusinessConfigDto
import com.posfab.shared.network.catalog.BusinessUnitDto
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.CreateProductRequestDto
import com.posfab.shared.network.catalog.PriceHistoryResponseDto
import com.posfab.shared.network.catalog.ProductDetailDto
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.catalog.ProductListResponseDto
import com.posfab.shared.network.catalog.SetProductPricesRequestDto
import com.posfab.shared.network.catalog.UnitDto
import com.posfab.shared.network.catalog.UpdateProductRequestDto
import com.posfab.shared.network.pos.BarcodeLookupProductDto
import com.posfab.shared.network.pos.BarcodeLookupResponseDto
import com.posfab.shared.network.shopping.ShoppingApi
import com.posfab.shared.network.shopping.ShoppingCurrentDto
import com.posfab.shared.network.shopping.ShoppingHistoryDto
import com.posfab.shared.network.shopping.ShoppingItemDto
import com.posfab.shared.network.shopping.ShoppingItemRequestDto
import com.posfab.shared.network.shopping.ShoppingItemStatusRequestDto
import com.posfab.shared.network.shopping.ShoppingListDoneRequestDto
import com.posfab.shared.network.shopping.ShoppingListDto
import com.posfab.shared.network.shopping.ShoppingListSummaryDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class ShoppingRepositoryImplTest {
    @Test
    fun current_maps_lists_items_quantities_and_users() = runTest {
        val api = FakeShoppingApi(
            current = AppResult.Success(
                ShoppingCurrentDto(
                    open = listDto(12L, "OPEN", listOf(itemDto(31L, qty = "2.500", productId = 1L, productName = " Coca cola "))),
                    toShop = listDto(11L, "TO_SHOP", listOf(itemDto(21L, status = "BOUGHT", carriedFrom = 9L)), closedAt = "2026-10-17T22:00:00Z"),
                ),
            ),
        )

        val current = assertIs<AppResult.Success<ShoppingCurrent>>(repository(api).current()).value

        val open = requireNotNull(current.open)
        assertEquals(ShoppingListStatus.OPEN, open.status)
        assertEquals("gerente", open.createdBy)
        assertEquals(Instant.parse("2026-10-18T15:00:00Z"), open.createdAt)
        val item = open.items.single()
        assertEquals(2.5, item.qty)
        assertEquals(1L, item.productId)
        assertEquals("Coca cola", item.productName)
        assertEquals("cajero", item.createdBy)
        assertEquals(ShoppingItemStatus.PENDING, item.status)
        val toShop = requireNotNull(current.toShop)
        assertEquals(Instant.parse("2026-10-17T22:00:00Z"), toShop.closedAt)
        assertEquals(ShoppingItemStatus.BOUGHT, toShop.items.single().status)
        assertEquals(9L, toShop.items.single().carriedFrom)
        assertEquals(1, toShop.boughtCount)
    }

    @Test
    fun an_unknown_item_status_becomes_pending_and_bad_quantities_become_null() = runTest {
        val api = FakeShoppingApi(
            current = AppResult.Success(
                ShoppingCurrentDto(
                    open = listDto(
                        12L,
                        "OPEN",
                        listOf(itemDto(1L, status = "LOST"), itemDto(2L, qty = "abc"), itemDto(3L, qty = "0.000")),
                    ),
                ),
            ),
        )

        val items = requireNotNull(assertIs<AppResult.Success<ShoppingCurrent>>(repository(api).current()).value.open).items

        assertEquals(ShoppingItemStatus.PENDING, items[0].status)
        assertNull(items[1].qty)
        assertNull(items[2].qty)
    }

    @Test
    fun an_unknown_list_status_drops_the_list() = runTest {
        val api = FakeShoppingApi(
            current = AppResult.Success(ShoppingCurrentDto(open = listDto(12L, "ARCHIVED"), toShop = listDto(11L, "OPEN"))),
            history = AppResult.Success(ShoppingHistoryDto(listOf(summaryDto(4L, "DONE"), summaryDto(5L, "WEIRD")))),
            get = AppResult.Success(listDto(6L, "WEIRD")),
        )
        val repo = repository(api)

        val current = assertIs<AppResult.Success<ShoppingCurrent>>(repo.current()).value
        val history = assertIs<AppResult.Success<List<ShoppingListSummary>>>(repo.history(20)).value

        assertNull(current.open)
        assertNull(current.toShop)
        assertEquals(listOf(4L), history.map { it.id })
        assertIs<AppResult.Failure>(repo.list(6L))
    }

    @Test
    fun history_maps_counts_and_sends_the_limit() = runTest {
        val api = FakeShoppingApi(history = AppResult.Success(ShoppingHistoryDto(listOf(summaryDto(4L, "DONE")))))

        val history = assertIs<AppResult.Success<List<ShoppingListSummary>>>(repository(api).history(20)).value

        assertEquals(listOf(20), api.historyLimits)
        val summary = history.single()
        assertEquals(14, summary.itemCount)
        assertEquals(12, summary.boughtCount)
        assertEquals(2, summary.notFoundCount)
        assertEquals(Instant.parse("2026-10-17T23:00:00Z"), summary.doneAt)
    }

    @Test
    fun add_item_trims_and_formats_the_quantity() = runTest {
        val api = FakeShoppingApi()
        val repo = repository(api)

        repo.addItem("k1", "  jitomate ", null, 2.5)
        repo.addItem("k2", "Coca cola", 1L, 3.0)
        repo.addItem("k3", "sal", null, null)
        repo.addItem("k4", "queso", null, 0.125)

        assertEquals(
            listOf(
                "k1" to ShoppingItemRequestDto("jitomate", null, "2.5"),
                "k2" to ShoppingItemRequestDto("Coca cola", 1L, "3"),
                "k3" to ShoppingItemRequestDto("sal", null, null),
                "k4" to ShoppingItemRequestDto("queso", null, "0.125"),
            ),
            api.added,
        )
    }

    @Test
    fun update_status_and_delete_reach_the_api() = runTest {
        val api = FakeShoppingApi()
        val repo = repository(api)

        assertIs<AppResult.Success<ShoppingItem>>(repo.updateItem(31L, "coca", 1L, 2.0))
        assertIs<AppResult.Success<ShoppingItem>>(repo.setStatus(31L, ShoppingItemStatus.NOT_FOUND))
        assertIs<AppResult.Success<Unit>>(repo.deleteItem(31L))

        assertEquals(listOf(31L to ShoppingItemRequestDto("coca", 1L, "2")), api.updated)
        assertEquals(listOf(31L to "NOT_FOUND"), api.statuses)
        assertEquals(listOf(31L), api.deleted)
    }

    @Test
    fun finish_sends_the_carry_over_ids_without_duplicates() = runTest {
        val api = FakeShoppingApi()
        val repo = repository(api)

        val done = assertIs<AppResult.Success<ShoppingList>>(repo.finish("k9", 11L, listOf(21L, 22L, 21L))).value
        repo.close("k8", 12L)

        assertEquals(listOf(Triple("k9", 11L, listOf(21L, 22L))), api.finished)
        assertEquals(listOf("k8" to 12L), api.closed)
        assertEquals(ShoppingListStatus.DONE, done.status)
    }

    @Test
    fun barcode_not_found_is_null_and_other_errors_stay_errors() = runTest {
        val notFound = FakeCatalogApi(lookup = AppResult.Failure(AppError.Validation("Barcode not found")))
        val down = FakeCatalogApi(lookup = AppResult.Failure(AppError.Network("down")))
        val other = FakeCatalogApi(lookup = AppResult.Failure(AppError.Validation("barcode is required")))

        assertEquals(AppResult.Success(null), repository(FakeShoppingApi(), notFound).findProductByBarcode("7501"))
        assertEquals(AppResult.Failure(AppError.Network("down")), repository(FakeShoppingApi(), down).findProductByBarcode("7501"))
        assertIs<AppResult.Failure>(repository(FakeShoppingApi(), other).findProductByBarcode("7501"))
    }

    @Test
    fun barcode_found_maps_the_product_with_the_code() = runTest {
        val catalog = FakeCatalogApi(lookup = AppResult.Success(lookupDto(1L, "Coca cola")))

        val product = repository(FakeShoppingApi(), catalog).findProductByBarcode("1234567891234")

        assertEquals(AppResult.Success(ShoppingProduct(1L, "Coca cola", "1234567891234")), product)
    }

    @Test
    fun search_returns_only_active_products_and_respects_the_limit() = runTest {
        val catalog = FakeCatalogApi(
            products = listOf(
                productDto("1", "Coca cola", active = true, barcode = "1234567891234"),
                productDto("2", "Coca light", active = false),
                productDto("x", "Sin id", active = true),
                productDto("3", "Coca grande", active = true),
                productDto("4", "Coca mini", active = true),
            ),
        )

        val found = assertIs<AppResult.Success<List<ShoppingProduct>>>(
            repository(FakeShoppingApi(), catalog).searchProducts(" coca ", 2),
        ).value

        assertEquals(listOf<String?>("coca"), catalog.queries)
        assertEquals(listOf<Boolean?>(true), catalog.activeFlags)
        assertEquals(listOf(ShoppingProduct(1L, "Coca cola", "1234567891234"), ShoppingProduct(3L, "Coca grande", null)), found)
    }

    @Test
    fun a_failed_call_is_passed_through() = runTest {
        val api = FakeShoppingApi(current = AppResult.Failure(AppError.Forbidden))

        assertEquals(AppResult.Failure(AppError.Forbidden), repository(api).current())
    }

    @Test
    fun a_malformed_date_fails_instead_of_crashing() = runTest {
        val api = FakeShoppingApi(get = AppResult.Success(listDto(6L, "DONE").copy(createdAt = "ayer")))

        assertIs<AppResult.Failure>(repository(api).list(6L))
    }

    private suspend fun repository(api: FakeShoppingApi, catalog: FakeCatalogApi = FakeCatalogApi()): ShoppingRepositoryImpl {
        val manager = SessionManager(InMemorySessionStorage())
        manager.set(
            UserSession(
                tokens = AuthTokens("access", "refresh"),
                user = AuthUser("u1", "gerente", setOf(UserRole.MANAGER), emptySet()),
                terminalCode = "POS1",
            ),
        )
        return ShoppingRepositoryImpl(api, catalog, AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager))
    }
}

private fun itemDto(
    id: Long,
    status: String = "PENDING",
    qty: String? = null,
    productId: Long? = null,
    productName: String? = null,
    carriedFrom: Long? = null,
) = ShoppingItemDto(
    id = id,
    text = "renglón $id",
    productId = productId,
    productName = productName,
    qty = qty,
    status = status,
    carriedFrom = carriedFrom,
    createdBy = "cajero",
    createdAt = "2026-10-18T15:04:05Z",
)

private fun listDto(id: Long, status: String, items: List<ShoppingItemDto> = emptyList(), closedAt: String? = null) = ShoppingListDto(
    id = id,
    status = status,
    createdAt = "2026-10-18T15:00:00Z",
    createdBy = "gerente",
    closedAt = closedAt,
    itemCount = items.size,
    items = items,
)

private fun summaryDto(id: Long, status: String) = ShoppingListSummaryDto(
    id = id,
    status = status,
    createdAt = "2026-10-11T14:00:00Z",
    doneAt = "2026-10-17T23:00:00Z",
    itemCount = 14,
    boughtCount = 12,
    notFoundCount = 2,
)

private fun lookupDto(id: Long, name: String) = BarcodeLookupResponseDto(
    product = BarcodeLookupProductDto(
        id = id,
        sku = "SKU$id",
        name = name,
        baseUnitId = 1L,
        requiresLotTracking = false,
        active = true,
    ),
    matchedOn = "PRODUCT",
)

private fun productDto(id: String, name: String, active: Boolean, barcode: String? = null) = ProductListItemDto(
    id = id,
    barcode = barcode,
    name = name,
    baseUnit = "PZA",
    baseUnitId = 1L,
    lotTracked = false,
    active = active,
)

private class NoRefreshAuthRepository(private val manager: SessionManager) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unauthorized)

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeShoppingApi(
    val current: AppResult<ShoppingCurrentDto> = AppResult.Success(ShoppingCurrentDto()),
    val history: AppResult<ShoppingHistoryDto> = AppResult.Success(ShoppingHistoryDto()),
    val get: AppResult<ShoppingListDto> = AppResult.Success(listDto(6L, "DONE")),
) : ShoppingApi {
    val historyLimits = mutableListOf<Int>()
    val added = mutableListOf<Pair<String, ShoppingItemRequestDto>>()
    val updated = mutableListOf<Pair<Long, ShoppingItemRequestDto>>()
    val deleted = mutableListOf<Long>()
    val statuses = mutableListOf<Pair<Long, String>>()
    val closed = mutableListOf<Pair<String, Long>>()
    val finished = mutableListOf<Triple<String, Long, List<Long>>>()

    override suspend fun current(accessToken: String): AppResult<ShoppingCurrentDto> = current

    override suspend fun history(accessToken: String, limit: Int): AppResult<ShoppingHistoryDto> {
        historyLimits += limit
        return history
    }

    override suspend fun get(accessToken: String, id: Long): AppResult<ShoppingListDto> = get

    override suspend fun addItem(
        accessToken: String,
        idempotencyKey: String,
        request: ShoppingItemRequestDto,
    ): AppResult<ShoppingItemDto> {
        added += idempotencyKey to request
        return AppResult.Success(itemDto(1L))
    }

    override suspend fun updateItem(accessToken: String, id: Long, request: ShoppingItemRequestDto): AppResult<ShoppingItemDto> {
        updated += id to request
        return AppResult.Success(itemDto(id))
    }

    override suspend fun deleteItem(accessToken: String, id: Long): AppResult<Unit> {
        deleted += id
        return AppResult.Success(Unit)
    }

    override suspend fun setItemStatus(
        accessToken: String,
        id: Long,
        request: ShoppingItemStatusRequestDto,
    ): AppResult<ShoppingItemDto> {
        statuses += id to request.status
        return AppResult.Success(itemDto(id, status = request.status))
    }

    override suspend fun close(accessToken: String, idempotencyKey: String, id: Long): AppResult<ShoppingListDto> {
        closed += idempotencyKey to id
        return AppResult.Success(listDto(id, "TO_SHOP"))
    }

    override suspend fun finish(
        accessToken: String,
        idempotencyKey: String,
        id: Long,
        request: ShoppingListDoneRequestDto,
    ): AppResult<ShoppingListDto> {
        finished += Triple(idempotencyKey, id, request.carryOver)
        return AppResult.Success(listDto(id, "DONE"))
    }
}

private class FakeCatalogApi(
    private val products: List<ProductListItemDto> = emptyList(),
    private val lookup: AppResult<BarcodeLookupResponseDto> = AppResult.Failure(AppError.Validation("Barcode not found")),
) : CatalogApi {
    val queries = mutableListOf<String?>()
    val activeFlags = mutableListOf<Boolean?>()

    override suspend fun listProducts(
        accessToken: String,
        query: String?,
        limit: Int,
        offset: Int,
        active: Boolean?,
    ): AppResult<ProductListResponseDto> {
        queries += query
        activeFlags += active
        return AppResult.Success(ProductListResponseDto(products, products.size, limit, offset))
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

    override suspend fun getPriceHistory(accessToken: String, productId: String): AppResult<PriceHistoryResponseDto> =
        error("unused")

    override suspend fun assignInternalCode(accessToken: String, productId: String): AppResult<ProductDetailDto> =
        error("unused")

    override suspend fun getBusinessConfig(accessToken: String): AppResult<BusinessConfigDto> = error("unused")

    override suspend fun listUnits(accessToken: String): AppResult<List<UnitDto>> = error("unused")

    override suspend fun listBusinessUnits(accessToken: String): AppResult<List<BusinessUnitDto>> = error("unused")

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> = lookup
}
