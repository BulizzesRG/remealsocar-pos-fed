package com.posfab.shared.features.orders

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
import com.posfab.shared.features.orders.repository.OrdersRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

val CREATED_AT: Instant = Instant.parse("2026-10-16T01:15:00Z")

val LUPE = OrderCustomer(13L, "Lupe Hernández", "Doña Lupe", "5551122896", "Calle 5 #12")
val ROSA = OrderCustomer(6L, "Rosa Martínez", null, "5550001111", null)

val TORTA = OrderProduct(50L, "Torta de milanesa", 1L, OrderPriceType.PIECE, 50.0, OrderKitchenKind.TORTERIA)
val COMIDA = OrderProduct(75L, "Comida", 1L, OrderPriceType.PIECE, 75.0, OrderKitchenKind.FONDA)
val JAMON = OrderProduct(13L, "Jamón", 2L, OrderPriceType.WEIGHT, 126.0)
val CARGO = OrderProduct(90L, "Cargo extra", 1L, OrderPriceType.OPEN, null)

fun sampleOrder(
    id: Long = 7L,
    version: Int = 1,
    status: OrderStatus = OrderStatus.OPEN,
    stage: OrderStage? = OrderStage.ANOTADO,
    customer: OrderCustomer? = null,
    address: String? = null,
    lines: List<OrderLine> = emptyList(),
    paysWith: Double? = null,
    cancelReason: CancelReason? = null,
) = Order(
    id = id,
    version = version,
    status = status,
    stage = stage,
    customer = customer,
    phone = customer?.phone,
    address = address,
    notes = null,
    paysWith = paysWith,
    change = null,
    cancelReason = cancelReason,
    cancelNotes = null,
    createdAt = CREATED_AT,
    lines = lines,
    total = roundMoney(lines.sumOf { it.lineTotal }),
    lineCount = lines.size,
)

class FakeOrdersRepository(
    var openResult: AppResult<List<Order>> = AppResult.Success(emptyList()),
    var paidResult: AppResult<List<Order>> = AppResult.Success(emptyList()),
    var cancelledResult: AppResult<List<Order>> = AppResult.Success(emptyList()),
    val orders: MutableMap<Long, Order> = mutableMapOf(),
    var createResults: MutableList<AppResult<Order>> = mutableListOf(),
    var customers: List<OrderCustomer> = listOf(LUPE, ROSA),
    var kitchen: List<OrderProduct> = listOf(COMIDA, TORTA),
    var products: List<OrderProduct> = listOf(TORTA, JAMON, CARGO),
) : OrdersRepository {
    val createKeys = mutableListOf<String>()
    val dayCalls = mutableListOf<Pair<OrderStatus, LocalDate>>()
    val updates = mutableListOf<Triple<Long, Int, OrderFields>>()
    val stages = mutableListOf<Triple<Long, OrderStage, Int>>()
    val cancels = mutableListOf<Pair<CancelReason, String?>>()
    val cancelEmpties = mutableListOf<Pair<Long, Int>>()
    val mutationLog = mutableListOf<String>()
    val customerQueries = mutableListOf<String>()
    val productQueries = mutableListOf<String>()
    val createdCustomers = mutableListOf<Triple<String, String?, String?>>()
    var updateResult: AppResult<Order>? = null
    var stageResult: AppResult<Order>? = null
    var cancelResult: AppResult<Order>? = null
    var createCustomerResult: AppResult<OrderCustomer>? = null
    var nextMutation: OrderMutationResult? = null
    var mutationGate: CompletableDeferred<Unit>? = null
    var openCalls = 0
    val openGates = ArrayDeque<CompletableDeferred<Unit>>()
    val openResults = ArrayDeque<AppResult<List<Order>>>()
    var orderGate: CompletableDeferred<Unit>? = null
    var cancelEmptyResult: AppResult<Unit> = AppResult.Success(Unit)
    var customerSearchResult: AppResult<List<OrderCustomer>>? = null
    val createCustomerResults = ArrayDeque<AppResult<OrderCustomer>>()
    private var nextLineId = 100L

    override suspend fun openOrders(): AppResult<List<Order>> {
        openCalls += 1
        val result = openResults.removeFirstOrNull() ?: openResult
        openGates.removeFirstOrNull()?.await()
        return result
    }

    override suspend fun ordersOfDay(status: OrderStatus, date: LocalDate): AppResult<List<Order>> {
        dayCalls += status to date
        return if (status == OrderStatus.PAID) paidResult else cancelledResult
    }

    override suspend fun order(id: Long): AppResult<Order> {
        orderGate?.await()
        return orders[id]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Validation("Order not found"))
    }

    override suspend fun createOrder(idempotencyKey: String): AppResult<Order> {
        createKeys += idempotencyKey
        val result = createResults.removeFirstOrNull() ?: AppResult.Success(sampleOrder())
        if (result is AppResult.Success) orders[result.value.id] = result.value
        return result
    }

    override suspend fun updateOrder(id: Long, expectedVersion: Int, fields: OrderFields): AppResult<Order> {
        updates += Triple(id, expectedVersion, fields)
        return updateResult ?: AppResult.Success(sampleOrder(id = id, version = expectedVersion + 1))
    }

    override suspend fun setStage(id: Long, stage: OrderStage, expectedVersion: Int): AppResult<Order> {
        stages += Triple(id, stage, expectedVersion)
        return stageResult ?: AppResult.Success(sampleOrder(id = id, version = expectedVersion + 1, stage = stage))
    }

    override suspend fun cancelOrder(id: Long, reason: CancelReason, notes: String?, expectedVersion: Int): AppResult<Order> {
        cancels += reason to notes
        return cancelResult ?: AppResult.Success(sampleOrder(id = id, status = OrderStatus.CANCELLED, cancelReason = reason))
    }

    override suspend fun cancelEmpty(id: Long, expectedVersion: Int): AppResult<Unit> {
        cancelEmpties += id to expectedVersion
        return cancelEmptyResult
    }

    override suspend fun addLine(
        orderId: Long,
        product: OrderProduct,
        input: OrderLineInput,
        expectedVersion: Int,
    ): OrderMutationResult {
        mutationLog += "add:${product.id}:$input:v$expectedVersion"
        mutationGate?.await()
        nextMutation?.let { nextMutation = null; return it }
        val current = cartOf(orderId)
        val line = when (input) {
            is OrderLineInput.Qty -> OrderLine(nextLineId++, product.id, product.name, input.qty, unitOf(product), product.priceType, product.listPrice ?: 0.0, roundMoney(input.qty * (product.listPrice ?: 0.0)))
            is OrderLineInput.OpenPrice -> OrderLine(nextLineId++, product.id, product.name, 1.0, "PZA", product.priceType, input.amount, input.amount)
        }
        return store(orderId, expectedVersion, current.lines + line)
    }

    override suspend fun updateLineQty(orderId: Long, lineId: Long, qty: Double, expectedVersion: Int): OrderMutationResult {
        mutationLog += "qty:$lineId:$qty:v$expectedVersion"
        mutationGate?.await()
        nextMutation?.let { nextMutation = null; return it }
        val lines = cartOf(orderId).lines.map { if (it.id == lineId) it.copy(qty = qty, lineTotal = roundMoney(qty * it.unitPrice)) else it }
        return store(orderId, expectedVersion, lines)
    }

    override suspend fun removeLine(orderId: Long, lineId: Long, expectedVersion: Int): OrderMutationResult {
        mutationLog += "remove:$lineId:v$expectedVersion"
        mutationGate?.await()
        nextMutation?.let { nextMutation = null; return it }
        return store(orderId, expectedVersion, cartOf(orderId).lines.filterNot { it.id == lineId })
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<OrderProduct>> {
        productQueries += query
        return AppResult.Success(products.filter { it.name.contains(query, ignoreCase = true) }.take(limit))
    }

    override suspend fun kitchenProducts(): AppResult<List<OrderProduct>> = AppResult.Success(kitchen)

    override suspend fun searchCustomers(query: String, limit: Int): AppResult<List<OrderCustomer>> {
        customerQueries += query
        customerSearchResult?.let { return it }
        return AppResult.Success(
            customers.filter { it.name.contains(query, ignoreCase = true) || it.phone?.contains(query) == true }.take(limit),
        )
    }

    override suspend fun createCustomer(name: String, phone: String?, address: String?): AppResult<OrderCustomer> {
        createdCustomers += Triple(name, phone, address)
        createCustomerResults.removeFirstOrNull()?.let { return it }
        return createCustomerResult ?: AppResult.Success(OrderCustomer(30L, name, null, phone, address))
    }

    private fun cartOf(orderId: Long): OrderCart = (orders[orderId] ?: sampleOrder(id = orderId)).cart

    private fun store(orderId: Long, expectedVersion: Int, lines: List<OrderLine>): OrderMutationResult {
        val updated = (orders[orderId] ?: sampleOrder(id = orderId)).copy(
            version = expectedVersion + 1,
            lines = lines,
            total = roundMoney(lines.sumOf { it.lineTotal }),
            lineCount = lines.size,
        )
        orders[orderId] = updated
        return OrderMutationResult.Success(updated.cart)
    }

    private fun unitOf(product: OrderProduct): String = if (product.priceType == OrderPriceType.WEIGHT) "KG" else "PZA"
}
