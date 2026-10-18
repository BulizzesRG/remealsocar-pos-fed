package com.posfab.shared.features.orders

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
import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.Order
import com.posfab.shared.features.orders.domain.OrderCustomer
import com.posfab.shared.features.orders.domain.OrderFields
import com.posfab.shared.features.orders.domain.OrderKitchenKind
import com.posfab.shared.features.orders.domain.OrderLineInput
import com.posfab.shared.features.orders.domain.OrderMutationResult
import com.posfab.shared.features.orders.domain.OrderPriceType
import com.posfab.shared.features.orders.domain.OrderProduct
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.domain.OrderStatus
import com.posfab.shared.features.orders.repository.OrdersRepositoryImpl
import com.posfab.shared.network.catalog.BusinessConfigDto
import com.posfab.shared.network.catalog.BusinessUnitDto
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.CreateProductRequestDto
import com.posfab.shared.network.catalog.PriceHistoryResponseDto
import com.posfab.shared.network.catalog.ProductDetailDto
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.catalog.ProductListResponseDto
import com.posfab.shared.network.catalog.ProductPricesDto
import com.posfab.shared.network.catalog.SetProductPricesRequestDto
import com.posfab.shared.network.catalog.UnitDto
import com.posfab.shared.network.catalog.UpdateProductRequestDto
import com.posfab.shared.network.customers.CreateCustomerRequestDto
import com.posfab.shared.network.customers.CustomerDto
import com.posfab.shared.network.customers.CustomerLedgerDto
import com.posfab.shared.network.customers.CustomerPaymentRequestDto
import com.posfab.shared.network.customers.CustomerPaymentResponseDto
import com.posfab.shared.network.customers.CustomersApi
import com.posfab.shared.network.customers.DebtorDto
import com.posfab.shared.network.customers.OpeningBalanceRequestDto
import com.posfab.shared.network.customers.OpeningBalanceResponseDto
import com.posfab.shared.network.customers.UpdateCustomerRequestDto
import com.posfab.shared.network.orders.OrderCancelRequestDto
import com.posfab.shared.network.orders.OrderCreateRequestDto
import com.posfab.shared.network.orders.OrderDto
import com.posfab.shared.network.orders.OrderListItemDto
import com.posfab.shared.network.orders.OrderListResponseDto
import com.posfab.shared.network.orders.OrderStageRequestDto
import com.posfab.shared.network.orders.OrderUpdateRequestDto
import com.posfab.shared.network.orders.OrdersApi
import com.posfab.shared.network.pos.AddLineRequestDto
import com.posfab.shared.network.pos.BarcodeLookupResponseDto
import com.posfab.shared.network.pos.CancelDraftRequestDto
import com.posfab.shared.network.pos.CheckoutRequestDto
import com.posfab.shared.network.pos.CheckoutResponseDto
import com.posfab.shared.network.pos.DraftChannelRequestDto
import com.posfab.shared.network.pos.DraftCreateRequestDto
import com.posfab.shared.network.pos.DraftCreateResponseDto
import com.posfab.shared.network.pos.DraftDto
import com.posfab.shared.network.pos.DraftLineDto
import com.posfab.shared.network.pos.DraftListDto
import com.posfab.shared.network.pos.DraftTotalsDto
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.ResolveLotsResponseDto
import com.posfab.shared.network.pos.UpdateLineRequestDto
import com.posfab.shared.network.pos.ValidateDraftResponseDto
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class OrdersRepositoryImplTest {
    @Test
    fun full_order_maps_customer_lines_money_and_negative_change() = runTest {
        val orders = FakeOrdersApi(getResults = mutableListOf(AppResult.Success(orderDto(paysWith = "50.00", change = "-47.00"))))

        val order = assertIs<AppResult.Success<Order>>(repository(orders).order(7L)).value

        assertEquals(7L, order.id)
        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals(OrderStage.ANOTADO, order.stage)
        assertEquals("Lupe Hernández (Doña Lupe)", order.customer?.displayName)
        assertEquals("5551122896", order.customer?.phone)
        assertEquals("Calle 5 #12", order.address)
        assertEquals(50.0, order.paysWith)
        assertEquals(-47.0, order.change)
        assertEquals(97.0, order.total)
        assertEquals(Instant.parse("2026-10-16T01:15:00Z"), order.createdAt)
        val line = order.lines.single()
        assertEquals(OrderPriceType.WEIGHT, line.priceType)
        assertEquals(0.25, line.qty)
        assertEquals("KG", line.unitCode)
        assertEquals(1, order.lineCount)
    }

    @Test
    fun total_is_the_cash_total_and_falls_back_to_total() = runTest {
        val withCash = OrderListItemDto(
            id = 1L, version = 1, status = "OPEN", createdAt = "2026-10-16T01:15:00Z", updatedAt = "2026-10-16T01:15:00Z",
            terminalId = 1L, lineCount = 1, totals = totals(total = "105.00", cash = "100.00"),
        )
        val withoutCash = withCash.copy(id = 2L, totals = totals(total = "42.00", cash = null))
        val orders = FakeOrdersApi(listResult = AppResult.Success(OrderListResponseDto(listOf(withCash, withoutCash))))

        val result = assertIs<AppResult.Success<List<Order>>>(repository(orders).openOrders()).value

        assertEquals(listOf(100.0, 42.0), result.map { it.total })
    }

    @Test
    fun unknown_status_is_dropped_from_lists_and_fails_a_detail() = runTest {
        val orders = FakeOrdersApi(
            listResult = AppResult.Success(OrderListResponseDto(listOf(listItem(id = 1L), listItem(id = 2L, status = "ARCHIVED")))),
            getResults = mutableListOf(AppResult.Success(orderDto(status = "ARCHIVED"))),
        )
        val repo = repository(orders)

        val listed = assertIs<AppResult.Success<List<Order>>>(repo.openOrders()).value

        assertEquals(listOf(1L), listed.map { it.id })
        assertIs<AppResult.Failure>(repo.order(7L))
    }

    @Test
    fun unknown_price_type_drops_the_product_and_keeps_the_line_read_only() = runTest {
        val catalog = FakeCatalogApi(listOf(productDto("1", "Combo", "COMBO", "10.00"), productDto("2", "Torta", "PIECE", "50.00")))
        val orders = FakeOrdersApi(
            getResults = mutableListOf(AppResult.Success(orderDto(lines = listOf(lineDto().copy(priceType = "COMBO", unitCode = "CAJA", qty = "2"))))),
        )
        val repo = repository(orders, catalog = catalog)

        val found = assertIs<AppResult.Success<List<OrderProduct>>>(repo.searchProducts("o", 8)).value
        val line = assertIs<AppResult.Success<Order>>(repo.order(7L)).value.lines.single()

        assertEquals(listOf("Torta"), found.map { it.name })
        assertEquals(OrderPriceType.OTHER, line.priceType)
        assertEquals(31.5, line.lineTotal)
    }

    @Test
    fun unknown_stage_and_reason_stay_null() = runTest {
        val item = listItem().copy(stage = "EN_CAMINO", cancelReason = "LLUVIA")
        val orders = FakeOrdersApi(listResult = AppResult.Success(OrderListResponseDto(listOf(item))))

        val order = assertIs<AppResult.Success<List<Order>>>(repository(orders).openOrders()).value.single()

        assertNull(order.stage)
        assertNull(order.cancelReason)
    }

    @Test
    fun list_items_keep_line_count_and_cancel_reason() = runTest {
        val item = listItem(id = 3L, status = "CANCELLED", cancelReason = "OTRO", cancelNotes = "Se fue", lineCount = 4)
        val orders = FakeOrdersApi(listResult = AppResult.Success(OrderListResponseDto(listOf(item))))

        val result = repository(orders).ordersOfDay(OrderStatus.CANCELLED, LocalDate(2026, 10, 16))

        val order = assertIs<AppResult.Success<List<Order>>>(result).value.single()
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals(CancelReason.OTRO, order.cancelReason)
        assertEquals("Se fue", order.cancelNotes)
        assertEquals(4, order.lineCount)
        assertEquals(listOf<Pair<String, String?>>("CANCELLED" to "2026-10-16"), orders.listCalls)
    }

    @Test
    fun open_orders_send_no_date_and_paid_maps_to_checked_out() = runTest {
        val orders = FakeOrdersApi(listResult = AppResult.Success(OrderListResponseDto(listOf(listItem(status = "CHECKED_OUT")))))
        val repo = repository(orders)

        repo.openOrders()
        val paid = repo.ordersOfDay(OrderStatus.PAID, LocalDate(2026, 10, 16))

        assertEquals(listOf<Pair<String, String?>>("OPEN" to null, "CHECKED_OUT" to "2026-10-16"), orders.listCalls)
        assertEquals(OrderStatus.PAID, assertIs<AppResult.Success<List<Order>>>(paid).value.single().status)
    }

    @Test
    fun unparsable_created_at_fails_instead_of_inventing_a_time() = runTest {
        val orders = FakeOrdersApi(listResult = AppResult.Success(OrderListResponseDto(listOf(listItem(createdAt = "ayer")))))

        assertIs<AppResult.Failure>(repository(orders).openOrders())
    }

    @Test
    fun create_sends_the_idempotency_key_with_an_empty_body() = runTest {
        val orders = FakeOrdersApi(createResult = AppResult.Success(orderDto(customerId = null, lines = emptyList())))

        val created = assertIs<AppResult.Success<Order>>(repository(orders).createOrder("k-1")).value

        assertEquals(listOf("k-1" to OrderCreateRequestDto()), orders.createCalls)
        assertNull(created.customer)
    }

    @Test
    fun update_sends_two_decimal_pays_with_and_blank_notes_to_clear() = runTest {
        val orders = FakeOrdersApi(updateResults = mutableListOf(AppResult.Success(orderDto())))

        repository(orders).updateOrder(7L, 3, OrderFields(customerId = 13L, address = " Calle 5 ", notes = "", paysWith = 500.0))

        assertEquals(OrderUpdateRequestDto(3, customerId = 13L, address = "Calle 5", notes = "", paysWith = "500.00"), orders.updateCalls.single())
    }

    @Test
    fun update_conflict_refetches_and_retries_once_with_the_fresh_version() = runTest {
        val orders = FakeOrdersApi(
            updateResults = mutableListOf(AppResult.Failure(AppError.Conflict), AppResult.Success(orderDto(version = 6))),
            getResults = mutableListOf(AppResult.Success(orderDto(version = 5))),
        )

        val result = repository(orders).updateOrder(7L, 3, OrderFields(13L, "Calle 5", null, null))

        assertEquals(6, assertIs<AppResult.Success<Order>>(result).value.version)
        assertEquals(listOf(3, 5), orders.updateCalls.map { it.expectedVersion })
    }

    @Test
    fun stage_conflict_on_a_closed_order_reports_not_open() = runTest {
        val orders = FakeOrdersApi(
            stageResults = mutableListOf(AppResult.Failure(AppError.Conflict)),
            getResults = mutableListOf(AppResult.Success(orderDto(status = "CHECKED_OUT"))),
        )

        val result = repository(orders).setStage(7L, OrderStage.ENVIADO, 3)

        val error = assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error)
        assertEquals("Order is not open", error.message)
        assertEquals(1, orders.stageCalls.size)
    }

    @Test
    fun cancel_sends_reason_and_trimmed_notes() = runTest {
        val orders = FakeOrdersApi(cancelResult = AppResult.Success(orderDto(status = "CANCELLED")))

        repository(orders).cancelOrder(7L, CancelReason.OTRO, "  Se fue ", 4)
        repository(orders).cancelOrder(7L, CancelReason.NO_CONTESTO, "   ", 4)

        assertEquals(
            listOf(OrderCancelRequestDto("OTRO", "Se fue", 4), OrderCancelRequestDto("NO_CONTESTO", null, 4)),
            orders.cancelCalls,
        )
    }

    @Test
    fun cancel_empty_uses_the_pos_draft_cancel() = runTest {
        val pos = FakePosApi()

        assertIs<AppResult.Success<Unit>>(repository(FakeOrdersApi(), pos).cancelEmpty(7L, 2))

        assertEquals(listOf(7L to 2), pos.cancelCalls)
    }

    @Test
    fun add_lines_format_kilos_and_open_prices() = runTest {
        val pos = FakePosApi()
        val repo = repository(FakeOrdersApi(), pos)

        repo.addLine(7L, product(type = OrderPriceType.WEIGHT, unitId = 2L), OrderLineInput.Qty(0.25), 1)
        repo.addLine(7L, product(type = OrderPriceType.OPEN), OrderLineInput.OpenPrice(20.0), 2)
        repo.addLine(7L, product(), OrderLineInput.Qty(2.0), 3)

        assertEquals(
            listOf(
                AddLineRequestDto(productId = 1L, unitId = 2L, qty = "0.250", expectedVersion = 1),
                AddLineRequestDto(productId = 1L, unitId = 1L, qty = "1", unitPrice = "20.00", expectedVersion = 2),
                AddLineRequestDto(productId = 1L, unitId = 1L, qty = "2", expectedVersion = 3),
            ),
            pos.addCalls,
        )
    }

    @Test
    fun line_conflict_refetches_and_retries_with_the_order_version() = runTest {
        val pos = FakePosApi(addResults = mutableListOf(AppResult.Failure(AppError.Conflict), AppResult.Success(draftDto(version = 9))))
        val orders = FakeOrdersApi(getResults = mutableListOf(AppResult.Success(orderDto(version = 8))))

        val result = repository(orders, pos).addLine(7L, product(), OrderLineInput.Qty(1.0), 2)

        assertEquals(9, assertIs<OrderMutationResult.Success>(result).cart.version)
        assertEquals(listOf(2, 8), pos.addCalls.map { it.expectedVersion })
    }

    @Test
    fun line_conflict_twice_returns_the_refetched_cart() = runTest {
        val pos = FakePosApi(addResults = mutableListOf(AppResult.Failure(AppError.Conflict), AppResult.Failure(AppError.Conflict)))
        val orders = FakeOrdersApi(getResults = mutableListOf(AppResult.Success(orderDto(version = 8))))

        val result = repository(orders, pos).addLine(7L, product(), OrderLineInput.Qty(1.0), 2)

        assertEquals(8, assertIs<OrderMutationResult.ConflictRefetched>(result).cart.version)
    }

    @Test
    fun line_conflict_on_a_closed_order_is_closed() = runTest {
        val pos = FakePosApi(updateResults = mutableListOf(AppResult.Failure(AppError.Conflict)))
        val orders = FakeOrdersApi(getResults = mutableListOf(AppResult.Success(orderDto(status = "CANCELLED"))))

        val result = repository(orders, pos).updateLineQty(7L, 70L, 3.0, 2)

        assertIs<OrderMutationResult.Closed>(result)
        assertEquals(listOf("3"), pos.updateCalls.map { it.qty })
    }

    @Test
    fun products_keep_priced_piece_and_weight_and_every_open() = runTest {
        val catalog = FakeCatalogApi(
            listOf(
                productDto("1", "Torta", "PIECE", "50.00", kitchen = "TORTERIA"),
                productDto("2", "Arroz", "PIECE", null),
                productDto("3", "Jamón", "WEIGHT", "126.00"),
                productDto("4", "Cargo extra", "OPEN", null),
                productDto("5", "Comida", "PIECE", "75.00", kitchen = "FONDA"),
            ),
        )
        val repo = repository(FakeOrdersApi(), catalog = catalog)

        val found = assertIs<AppResult.Success<List<OrderProduct>>>(repo.searchProducts(" to ", 8)).value
        val kitchen = assertIs<AppResult.Success<List<OrderProduct>>>(repo.kitchenProducts()).value

        assertEquals(listOf("Torta", "Jamón", "Cargo extra", "Comida"), found.map { it.name })
        assertEquals(126.0, found[1].listPrice)
        assertEquals(listOf("Comida", "Torta"), kitchen.map { it.name })
        assertEquals(OrderKitchenKind.FONDA, kitchen.first().kitchenKind)
        assertEquals("to", catalog.queries.first())
    }

    @Test
    fun products_carry_the_cash_price_for_cards() = runTest {
        val catalog = FakeCatalogApi(
            listOf(
                productDto("50", "Torta de milanesa", "PIECE", "52.50", kitchen = "TORTERIA", cash = "50.00"),
                productDto("13", "Jamón", "WEIGHT", "126.00", cash = "120.00"),
                productDto("1", "Coca", "PIECE", "18.00", cash = "17.00"),
            ),
        )

        val found = assertIs<AppResult.Success<List<OrderProduct>>>(repository(FakeOrdersApi(), catalog = catalog).searchProducts("a", 8)).value

        assertEquals(listOf(50.0, 120.0, 17.0), found.map { it.cashPrice })
        assertEquals(listOf(50.0, 120.0, 17.0), found.map { it.shownPrice })
        assertEquals(52.5, found.first().listPrice)
        assertEquals(OrderProduct(1L, "P", 1L, OrderPriceType.PIECE, 10.0).shownPrice, 10.0)
    }

    @Test
    fun customers_search_skips_inactive_and_create_sends_address() = runTest {
        val customers = FakeCustomersApi(
            searchResult = listOf(
                CustomerDto(13L, name = "Lupe Hernández", nickname = "Doña Lupe", phone = "5551122896", address = "Calle 5", balance = "0"),
                CustomerDto(14L, name = "Lupe Baja", active = false, balance = "0"),
            ),
        )
        val repo = repository(FakeOrdersApi(), customers = customers)

        val found = assertIs<AppResult.Success<List<OrderCustomer>>>(repo.searchCustomers("lup", 5)).value
        val created = assertIs<AppResult.Success<OrderCustomer>>(repo.createCustomer(" Ana ", "", " Av. 3 ")).value

        assertEquals(listOf(OrderCustomer(13L, "Lupe Hernández", "Doña Lupe", "5551122896", "Calle 5")), found)
        assertEquals(CreateCustomerRequestDto(name = "Ana", phone = null, address = "Av. 3"), customers.created.single())
        assertEquals("Ana", created.name)
    }

    private suspend fun repository(
        orders: FakeOrdersApi,
        pos: FakePosApi = FakePosApi(),
        catalog: FakeCatalogApi = FakeCatalogApi(emptyList()),
        customers: FakeCustomersApi = FakeCustomersApi(),
    ): OrdersRepositoryImpl {
        val manager = SessionManager(InMemorySessionStorage())
        manager.set(
            UserSession(
                tokens = AuthTokens("access", "refresh"),
                user = AuthUser("u1", "tortera", setOf(UserRole.ORDERS), emptySet()),
                terminalCode = "POS1",
            ),
        )
        return OrdersRepositoryImpl(orders, pos, catalog, customers, AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager))
    }
}

private fun product(type: OrderPriceType = OrderPriceType.PIECE, unitId: Long = 1L) =
    OrderProduct(id = 1L, name = "P", unitId = unitId, priceType = type, listPrice = 10.0)

private fun totals(total: String = "102.00", cash: String? = "97.00") = DraftTotalsDto(amount = total, subtotal = total, tax = "0", total = total, cashTotal = cash)

private fun lineDto() = DraftLineDto(
    id = 70L,
    lineNo = 1,
    productId = 13L,
    unitId = 2L,
    qty = "0.250",
    unitPrice = "126.00",
    productName = "Jamón",
    unitCode = "KG",
    lineTotal = "31.50",
    priceType = "WEIGHT",
)

private fun orderDto(
    version: Int = 3,
    status: String = "OPEN",
    customerId: Long? = 13L,
    paysWith: String? = null,
    change: String? = null,
    lines: List<DraftLineDto> = listOf(lineDto()),
) = OrderDto(
    id = 7L,
    version = version,
    status = status,
    stage = "ANOTADO".takeIf { status == "OPEN" },
    customerId = customerId,
    customerName = "Lupe Hernández".takeIf { customerId != null },
    customerNickname = "Doña Lupe".takeIf { customerId != null },
    phone = "5551122896".takeIf { customerId != null },
    address = "Calle 5 #12".takeIf { customerId != null },
    paysWith = paysWith,
    change = change,
    createdAt = "2026-10-16T01:15:00Z",
    updatedAt = "2026-10-16T01:15:00Z",
    terminalId = 1L,
    lines = lines,
    totals = totals(),
)

private fun listItem(
    id: Long = 7L,
    status: String = "OPEN",
    cancelReason: String? = null,
    cancelNotes: String? = null,
    lineCount: Int = 2,
    createdAt: String = "2026-10-16T01:15:00Z",
) = OrderListItemDto(
    id = id,
    version = 1,
    status = status,
    cancelReason = cancelReason,
    cancelNotes = cancelNotes,
    createdAt = createdAt,
    updatedAt = createdAt,
    terminalId = 1L,
    lineCount = lineCount,
    totals = totals(),
)

private fun draftDto(version: Int) = DraftDto(
    id = 7L,
    type = "DELIVERY",
    status = "OPEN",
    terminalId = 1L,
    version = version,
    lines = listOf(lineDto()),
    totals = totals(),
)

private fun productDto(
    id: String,
    name: String,
    type: String,
    list: String?,
    kitchen: String? = null,
    cash: String? = list,
) = ProductListItemDto(
    id = id,
    name = name,
    baseUnit = "PZA",
    baseUnitId = 1L,
    lotTracked = false,
    active = true,
    priceType = type,
    kitchenKind = kitchen,
    prices = list?.let { ProductPricesDto(cashTakeaway = cash ?: it, listTakeaway = it) },
)

private class NoRefreshAuthRepository(private val manager: SessionManager) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unauthorized)

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeOrdersApi(
    var createResult: AppResult<OrderDto> = AppResult.Failure(AppError.Unknown("unused")),
    var listResult: AppResult<OrderListResponseDto> = AppResult.Success(OrderListResponseDto()),
    val getResults: MutableList<AppResult<OrderDto>> = mutableListOf(),
    val updateResults: MutableList<AppResult<OrderDto>> = mutableListOf(),
    val stageResults: MutableList<AppResult<OrderDto>> = mutableListOf(),
    var cancelResult: AppResult<OrderDto> = AppResult.Failure(AppError.Unknown("unused")),
) : OrdersApi {
    val createCalls = mutableListOf<Pair<String, OrderCreateRequestDto>>()
    val listCalls = mutableListOf<Pair<String, String?>>()
    val updateCalls = mutableListOf<OrderUpdateRequestDto>()
    val stageCalls = mutableListOf<OrderStageRequestDto>()
    val cancelCalls = mutableListOf<OrderCancelRequestDto>()

    override suspend fun create(accessToken: String, idempotencyKey: String, request: OrderCreateRequestDto): AppResult<OrderDto> {
        createCalls += idempotencyKey to request
        return createResult
    }

    override suspend fun list(accessToken: String, status: String, date: String?): AppResult<OrderListResponseDto> {
        listCalls += status to date
        return listResult
    }

    override suspend fun get(accessToken: String, id: Long): AppResult<OrderDto> =
        getResults.removeFirstOrNull() ?: AppResult.Failure(AppError.Validation("Order not found"))

    override suspend fun update(accessToken: String, id: Long, request: OrderUpdateRequestDto): AppResult<OrderDto> {
        updateCalls += request
        return updateResults.removeFirstOrNull() ?: AppResult.Success(orderDto())
    }

    override suspend fun setStage(accessToken: String, id: Long, request: OrderStageRequestDto): AppResult<OrderDto> {
        stageCalls += request
        return stageResults.removeFirstOrNull() ?: AppResult.Success(orderDto())
    }

    override suspend fun cancel(accessToken: String, id: Long, request: OrderCancelRequestDto): AppResult<OrderDto> {
        cancelCalls += request
        return cancelResult
    }
}

private class FakePosApi(
    val addResults: MutableList<AppResult<DraftDto>> = mutableListOf(),
    val updateResults: MutableList<AppResult<DraftDto>> = mutableListOf(),
) : PosApi {
    val addCalls = mutableListOf<AddLineRequestDto>()
    val updateCalls = mutableListOf<UpdateLineRequestDto>()
    val cancelCalls = mutableListOf<Pair<Long, Int>>()

    override suspend fun currentCashSession(accessToken: String, terminalId: Long): AppResult<Boolean> = error("unused")

    override suspend fun listOpenDrafts(accessToken: String, type: String): AppResult<DraftListDto> = error("unused")

    override suspend fun createDraft(accessToken: String, request: DraftCreateRequestDto): AppResult<DraftCreateResponseDto> =
        error("unused")

    override suspend fun getDraft(accessToken: String, draftId: Long): AppResult<DraftDto> = error("unused")

    override suspend fun addDraftLine(accessToken: String, draftId: Long, request: AddLineRequestDto): AppResult<DraftDto> {
        addCalls += request
        return addResults.removeFirstOrNull() ?: AppResult.Success(draftDto(request.expectedVersion + 1))
    }

    override suspend fun updateDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        request: UpdateLineRequestDto,
    ): AppResult<DraftDto> {
        updateCalls += request
        return updateResults.removeFirstOrNull() ?: AppResult.Success(draftDto(request.expectedVersion + 1))
    }

    override suspend fun removeDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        expectedVersion: Int,
    ): AppResult<DraftDto> = AppResult.Success(draftDto(expectedVersion + 1))

    override suspend fun validateDraft(accessToken: String, draftId: Long): AppResult<ValidateDraftResponseDto> =
        error("unused")

    override suspend fun resolveLots(accessToken: String, draftId: Long): AppResult<ResolveLotsResponseDto> = error("unused")

    override suspend fun checkoutDraft(
        accessToken: String,
        draftId: Long,
        request: CheckoutRequestDto,
    ): AppResult<CheckoutResponseDto> = error("unused")

    override suspend fun setDraftChannel(
        accessToken: String,
        draftId: Long,
        request: DraftChannelRequestDto,
    ): AppResult<DraftDto> = error("unused")

    override suspend fun cancelDraft(accessToken: String, draftId: Long, request: CancelDraftRequestDto): AppResult<Unit> {
        cancelCalls += draftId to request.expectedVersion
        return AppResult.Success(Unit)
    }

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> =
        error("unused")
}

private class FakeCatalogApi(private val products: List<ProductListItemDto>) : CatalogApi {
    val queries = mutableListOf<String?>()

    override suspend fun listProducts(
        accessToken: String,
        query: String?,
        limit: Int,
        offset: Int,
        active: Boolean?,
    ): AppResult<ProductListResponseDto> {
        queries += query
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

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> =
        error("unused")
}

private class FakeCustomersApi(private val searchResult: List<CustomerDto> = emptyList()) : CustomersApi {
    val created = mutableListOf<CreateCustomerRequestDto>()

    override suspend fun search(
        accessToken: String,
        query: String?,
        limit: Int,
        includeInactive: Boolean,
    ): AppResult<List<CustomerDto>> =
        AppResult.Success(searchResult)

    override suspend fun get(accessToken: String, customerId: Long): AppResult<CustomerDto> = error("unused")

    override suspend fun findByCode(accessToken: String, code: String): AppResult<CustomerDto> = error("unused")

    override suspend fun create(accessToken: String, request: CreateCustomerRequestDto): AppResult<CustomerDto> {
        created += request
        return AppResult.Success(
            CustomerDto(id = 20L, name = request.name, phone = request.phone, address = request.address, balance = "0"),
        )
    }

    override suspend fun update(
        accessToken: String,
        customerId: Long,
        request: UpdateCustomerRequestDto,
    ): AppResult<CustomerDto> = error("unused")

    override suspend fun updateAddress(accessToken: String, customerId: Long, address: String): AppResult<CustomerDto> =
        error("unused")

    override suspend fun recordPayment(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: CustomerPaymentRequestDto,
    ): AppResult<CustomerPaymentResponseDto> = error("unused")

    override suspend fun loadOpeningBalance(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: OpeningBalanceRequestDto,
    ): AppResult<OpeningBalanceResponseDto> = error("unused")

    override suspend fun debtors(accessToken: String): AppResult<List<DebtorDto>> = error("unused")

    override suspend fun ledger(accessToken: String, customerId: Long, limit: Int): AppResult<CustomerLedgerDto> =
        error("unused")
}
