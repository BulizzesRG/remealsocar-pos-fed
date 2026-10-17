package com.posfab.shared.features.orders.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.Order
import com.posfab.shared.features.orders.domain.OrderCart
import com.posfab.shared.features.orders.domain.OrderCustomer
import com.posfab.shared.features.orders.domain.OrderFields
import com.posfab.shared.features.orders.domain.OrderKitchenKind
import com.posfab.shared.features.orders.domain.OrderLine
import com.posfab.shared.features.orders.domain.OrderLineInput
import com.posfab.shared.features.orders.domain.OrderMutationResult
import com.posfab.shared.features.orders.domain.OrderPriceType
import com.posfab.shared.features.orders.domain.OrderProduct
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.domain.OrderStatus
import com.posfab.shared.features.orders.domain.roundMoney
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.customers.CreateCustomerRequestDto
import com.posfab.shared.network.customers.CustomerDto
import com.posfab.shared.network.customers.CustomersApi
import com.posfab.shared.network.orders.OrderCancelRequestDto
import com.posfab.shared.network.orders.OrderCreateRequestDto
import com.posfab.shared.network.orders.OrderDto
import com.posfab.shared.network.orders.OrderListItemDto
import com.posfab.shared.network.orders.OrderStageRequestDto
import com.posfab.shared.network.orders.OrderUpdateRequestDto
import com.posfab.shared.network.orders.OrdersApi
import com.posfab.shared.network.pos.AddLineRequestDto
import com.posfab.shared.network.pos.CancelDraftRequestDto
import com.posfab.shared.network.pos.DraftDto
import com.posfab.shared.network.pos.DraftLineDto
import com.posfab.shared.network.pos.DraftTotalsDto
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.UpdateLineRequestDto
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class OrdersRepositoryImpl(
    private val ordersApi: OrdersApi,
    private val posApi: PosApi,
    private val catalogApi: CatalogApi,
    private val customersApi: CustomersApi,
    private val executor: AuthorizedApiExecutor,
) : OrdersRepository {
    override suspend fun openOrders(): AppResult<List<Order>> = list(STATUS_OPEN, null)

    override suspend fun ordersOfDay(status: OrderStatus, date: LocalDate): AppResult<List<Order>> =
        list(status.wireName(), date.toString())

    override suspend fun order(id: Long): AppResult<Order> = authorized { ordersApi.get(it, id) }.mapOrder()

    override suspend fun createOrder(idempotencyKey: String): AppResult<Order> =
        authorized { ordersApi.create(it, idempotencyKey, OrderCreateRequestDto()) }.mapOrder()

    override suspend fun updateOrder(id: Long, expectedVersion: Int, fields: OrderFields): AppResult<Order> =
        withVersionRetry(id, expectedVersion) { token, version ->
            ordersApi.update(
                token,
                id,
                OrderUpdateRequestDto(
                    expectedVersion = version,
                    customerId = fields.customerId,
                    address = fields.address?.trim(),
                    notes = fields.notes?.trim().orEmpty(),
                    paysWith = fields.paysWith?.let(::formatMoney),
                ),
            )
        }

    override suspend fun setStage(id: Long, stage: OrderStage, expectedVersion: Int): AppResult<Order> =
        withVersionRetry(id, expectedVersion) { token, version ->
            ordersApi.setStage(token, id, OrderStageRequestDto(stage.name, version))
        }

    override suspend fun cancelOrder(
        id: Long,
        reason: CancelReason,
        notes: String?,
        expectedVersion: Int,
    ): AppResult<Order> = withVersionRetry(id, expectedVersion) { token, version ->
        ordersApi.cancel(
            token,
            id,
            OrderCancelRequestDto(reason.name, notes?.trim()?.takeIf { it.isNotEmpty() }, version),
        )
    }

    override suspend fun cancelEmpty(id: Long, expectedVersion: Int): AppResult<Unit> =
        authorized { posApi.cancelDraft(it, id, CancelDraftRequestDto(expectedVersion)) }

    override suspend fun addLine(
        orderId: Long,
        product: OrderProduct,
        input: OrderLineInput,
        expectedVersion: Int,
    ): OrderMutationResult = cartMutation(orderId, expectedVersion) { token, version ->
        val request = when (input) {
            is OrderLineInput.Qty -> AddLineRequestDto(
                productId = product.id,
                unitId = product.unitId,
                qty = formatQty(input.qty),
                expectedVersion = version,
            )
            is OrderLineInput.OpenPrice -> AddLineRequestDto(
                productId = product.id,
                unitId = product.unitId,
                qty = OPEN_PRICE_QTY,
                unitPrice = formatMoney(input.amount),
                expectedVersion = version,
            )
        }
        posApi.addDraftLine(token, orderId, request)
    }

    override suspend fun updateLineQty(
        orderId: Long,
        lineId: Long,
        qty: Double,
        expectedVersion: Int,
    ): OrderMutationResult = cartMutation(orderId, expectedVersion) { token, version ->
        posApi.updateDraftLine(token, orderId, lineId, UpdateLineRequestDto(qty = formatQty(qty), expectedVersion = version))
    }

    override suspend fun removeLine(orderId: Long, lineId: Long, expectedVersion: Int): OrderMutationResult =
        cartMutation(orderId, expectedVersion) { token, version ->
            posApi.removeDraftLine(token, orderId, lineId, version)
        }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<OrderProduct>> =
        when (val result = authorized { catalogApi.listProducts(it, query.trim(), SEARCH_FETCH_LIMIT, 0, true) }) {
            is AppResult.Success -> AppResult.Success(result.value.items.mapNotNull { it.toOrderProduct() }.take(limit))
            is AppResult.Failure -> result
        }

    override suspend fun kitchenProducts(): AppResult<List<OrderProduct>> =
        when (val result = authorized { catalogApi.listProducts(it, null, KITCHEN_LIMIT, 0, true) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.items
                    .mapNotNull { it.toOrderProduct() }
                    .filter { it.kitchenKind != null }
                    .sortedWith(compareBy<OrderProduct> { it.kitchenKind?.ordinal }.thenBy { it.name.lowercase() }),
            )
            is AppResult.Failure -> result
        }

    override suspend fun searchCustomers(query: String, limit: Int): AppResult<List<OrderCustomer>> {
        val trimmed = query.trim().takeIf { it.isNotEmpty() }
        return when (val result = authorized { customersApi.search(it, trimmed, limit) }) {
            is AppResult.Success -> AppResult.Success(result.value.filter { it.active }.map { it.toCustomer() })
            is AppResult.Failure -> result
        }
    }

    override suspend fun createCustomer(name: String, phone: String?, address: String?): AppResult<OrderCustomer> {
        val request = CreateCustomerRequestDto(
            name = name.trim(),
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            address = address?.trim()?.takeIf { it.isNotEmpty() },
        )
        return when (val result = authorized { customersApi.create(it, request) }) {
            is AppResult.Success -> AppResult.Success(result.value.toCustomer())
            is AppResult.Failure -> result
        }
    }

    private suspend fun list(status: String, date: String?): AppResult<List<Order>> =
        when (val result = authorized { ordersApi.list(it, status, date) }) {
            is AppResult.Success -> {
                val known = result.value.items.filter { OrderStatus.fromRaw(it.status) != null }
                val mapped = known.map { it.toDomain() ?: return malformed() }
                AppResult.Success(mapped)
            }
            is AppResult.Failure -> result
        }

    private suspend fun withVersionRetry(
        id: Long,
        expectedVersion: Int,
        block: suspend (String, Int) -> AppResult<OrderDto>,
    ): AppResult<Order> {
        val first = authorized { block(it, expectedVersion) }
        if (first !is AppResult.Failure || first.error !is AppError.Conflict) return first.mapOrder()
        val refetched = order(id)
        if (refetched !is AppResult.Success) return first
        if (refetched.value.status != OrderStatus.OPEN) return AppResult.Failure(AppError.Validation(ORDER_NOT_OPEN))
        return authorized { block(it, refetched.value.version) }.mapOrder()
    }

    private suspend fun cartMutation(
        orderId: Long,
        expectedVersion: Int,
        block: suspend (String, Int) -> AppResult<DraftDto>,
    ): OrderMutationResult {
        val first = authorized { block(it, expectedVersion) }
        val conflict = when (first) {
            is AppResult.Success -> return OrderMutationResult.Success(first.value.toCart())
            is AppResult.Failure -> first.error
        }
        if (conflict !is AppError.Conflict) return OrderMutationResult.Failure(conflict)
        val refetched = when (val result = order(orderId)) {
            is AppResult.Success -> result.value.cart
            is AppResult.Failure -> return OrderMutationResult.Failure(conflict)
        }
        if (!refetched.isOpen) return OrderMutationResult.Closed(refetched)
        return when (val retry = authorized { block(it, refetched.version) }) {
            is AppResult.Success -> OrderMutationResult.Success(retry.value.toCart())
            is AppResult.Failure ->
                if (retry.error is AppError.Conflict) {
                    OrderMutationResult.ConflictRefetched(refetched)
                } else {
                    OrderMutationResult.Failure(retry.error)
                }
        }
    }

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private fun AppResult<OrderDto>.mapOrder(): AppResult<Order> = when (this) {
        is AppResult.Success -> value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
        is AppResult.Failure -> this
    }

    private fun malformed(): AppResult.Failure = AppResult.Failure(AppError.Unknown(MALFORMED))

    private fun OrderDto.toDomain(): Order? {
        val created = parseInstant(createdAt) ?: return null
        val parsedStatus = OrderStatus.fromRaw(status) ?: return null
        val mappedLines = lines.map { it.toDomain() }
        return Order(
            id = id,
            version = version,
            status = parsedStatus,
            stage = OrderStage.fromRaw(stage),
            customer = customerOf(customerId, customerName, customerNickname, phone),
            phone = phone?.takeIf { it.isNotBlank() },
            address = address?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
            paysWith = paysWith?.toDoubleOrNull(),
            change = change?.toDoubleOrNull(),
            cancelReason = CancelReason.fromRaw(cancelReason),
            cancelNotes = cancelNotes?.takeIf { it.isNotBlank() },
            createdAt = created,
            lines = mappedLines,
            total = totals.totalAmount(),
            lineCount = mappedLines.size,
            folio = checkoutFolio?.takeIf { it.isNotBlank() },
        )
    }

    private fun OrderListItemDto.toDomain(): Order? {
        val created = parseInstant(createdAt) ?: return null
        val parsedStatus = OrderStatus.fromRaw(status) ?: return null
        return Order(
            id = id,
            version = version,
            status = parsedStatus,
            stage = OrderStage.fromRaw(stage),
            customer = customerOf(customerId, customerName, customerNickname, phone),
            phone = phone?.takeIf { it.isNotBlank() },
            address = address?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
            paysWith = paysWith?.toDoubleOrNull(),
            change = change?.toDoubleOrNull(),
            cancelReason = CancelReason.fromRaw(cancelReason),
            cancelNotes = cancelNotes?.takeIf { it.isNotBlank() },
            createdAt = created,
            lines = emptyList(),
            total = totals.totalAmount(),
            lineCount = lineCount,
            folio = checkoutFolio?.takeIf { it.isNotBlank() },
        )
    }

    private fun customerOf(id: Long?, name: String?, nickname: String?, phone: String?): OrderCustomer? {
        if (id == null) return null
        return OrderCustomer(
            id = id,
            name = name?.trim().orEmpty(),
            nickname = nickname?.trim()?.takeIf { it.isNotEmpty() },
            phone = phone?.takeIf { it.isNotBlank() },
        )
    }

    private fun DraftDto.toCart(): OrderCart = OrderCart(
        orderId = id,
        version = version,
        isOpen = status.equals(STATUS_OPEN, ignoreCase = true),
        lines = lines.map { it.toDomain() },
        total = totals.totalAmount(),
    )

    private fun DraftTotalsDto.totalAmount(): Double = (cashTotal ?: total).toDoubleOrNull() ?: 0.0

    private fun DraftLineDto.toDomain(): OrderLine {
        val quantity = qty.toDoubleOrNull() ?: 0.0
        val price = unitPrice?.toDoubleOrNull() ?: cashUnitPrice?.toDoubleOrNull() ?: 0.0
        return OrderLine(
            id = id,
            productId = productId,
            productName = productName,
            qty = quantity,
            unitCode = unitCode,
            priceType = OrderPriceType.fromRaw(priceType),
            unitPrice = price,
            lineTotal = lineTotal.toDoubleOrNull() ?: roundMoney(quantity * price),
        )
    }

    private fun CustomerDto.toCustomer(): OrderCustomer = OrderCustomer(
        id = id,
        name = name,
        nickname = nickname?.trim()?.takeIf { it.isNotEmpty() },
        phone = phone?.takeIf { it.isNotBlank() },
        address = address?.trim()?.takeIf { it.isNotEmpty() },
    )

    private fun ProductListItemDto.toOrderProduct(): OrderProduct? {
        val productId = id.toLongOrNull() ?: return null
        val type = OrderPriceType.fromRaw(priceType)
        if (!type.isSellable) return null
        val listPrice = prices?.listTakeaway?.toDoubleOrNull()
        val cashPrice = prices?.cashTakeaway?.toDoubleOrNull() ?: listPrice
        if (type != OrderPriceType.OPEN && cashPrice == null) return null
        return OrderProduct(
            id = productId,
            name = name,
            unitId = baseUnitId,
            priceType = type,
            listPrice = listPrice,
            kitchenKind = OrderKitchenKind.fromRaw(kitchenKind),
            cashPrice = cashPrice,
        )
    }

    private fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

    private fun OrderStatus.wireName(): String = when (this) {
        OrderStatus.OPEN -> STATUS_OPEN
        OrderStatus.PAID -> STATUS_CHECKED_OUT
        OrderStatus.CANCELLED -> STATUS_CANCELLED
    }

    private fun formatQty(qty: Double): String {
        val whole = round(qty)
        return if (abs(qty - whole) < QTY_EPSILON) whole.toLong().toString() else formatKilos(qty)
    }

    private fun formatKilos(qty: Double): String {
        val grams = round(qty * 1000.0).toLong()
        val sign = if (grams < 0) "-" else ""
        val absolute = abs(grams)
        return "$sign${absolute / 1000}.${(absolute % 1000).toString().padStart(3, '0')}"
    }

    private fun formatMoney(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CHECKED_OUT = "CHECKED_OUT"
        const val STATUS_CANCELLED = "CANCELLED"
        const val ORDER_NOT_OPEN = "Order is not open"
        const val MALFORMED = "order"
        const val KITCHEN_LIMIT = 200
        const val SEARCH_FETCH_LIMIT = 50
        const val QTY_EPSILON = 1e-9
        const val OPEN_PRICE_QTY = "1"
    }
}
