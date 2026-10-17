package com.posfab.shared.features.orders.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.Order
import com.posfab.shared.features.orders.domain.OrderCustomer
import com.posfab.shared.features.orders.domain.OrderFields
import com.posfab.shared.features.orders.domain.OrderLineInput
import com.posfab.shared.features.orders.domain.OrderMutationResult
import com.posfab.shared.features.orders.domain.OrderProduct
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.domain.OrderStatus
import com.posfab.shared.features.orders.repository.OrdersRepository
import kotlinx.datetime.LocalDate

class OrdersUseCases(private val repository: OrdersRepository) {
    suspend fun openOrders(): AppResult<List<Order>> = repository.openOrders()

    suspend fun ordersOfDay(status: OrderStatus, date: LocalDate): AppResult<List<Order>> =
        repository.ordersOfDay(status, date)

    suspend fun order(id: Long): AppResult<Order> = repository.order(id)

    suspend fun createOrder(idempotencyKey: String): AppResult<Order> = repository.createOrder(idempotencyKey)

    suspend fun updateOrder(id: Long, expectedVersion: Int, fields: OrderFields): AppResult<Order> =
        repository.updateOrder(id, expectedVersion, fields)

    suspend fun setStage(id: Long, stage: OrderStage, expectedVersion: Int): AppResult<Order> =
        repository.setStage(id, stage, expectedVersion)

    suspend fun cancelOrder(id: Long, reason: CancelReason, notes: String?, expectedVersion: Int): AppResult<Order> =
        repository.cancelOrder(id, reason, notes, expectedVersion)

    suspend fun cancelEmpty(id: Long, expectedVersion: Int): AppResult<Unit> = repository.cancelEmpty(id, expectedVersion)

    suspend fun addLine(
        orderId: Long,
        product: OrderProduct,
        input: OrderLineInput,
        expectedVersion: Int,
    ): OrderMutationResult = repository.addLine(orderId, product, input, expectedVersion)

    suspend fun updateLineQty(orderId: Long, lineId: Long, qty: Double, expectedVersion: Int): OrderMutationResult =
        repository.updateLineQty(orderId, lineId, qty, expectedVersion)

    suspend fun removeLine(orderId: Long, lineId: Long, expectedVersion: Int): OrderMutationResult =
        repository.removeLine(orderId, lineId, expectedVersion)

    suspend fun searchProducts(query: String, limit: Int): AppResult<List<OrderProduct>> =
        repository.searchProducts(query, limit)

    suspend fun kitchenProducts(): AppResult<List<OrderProduct>> = repository.kitchenProducts()

    suspend fun searchCustomers(query: String, limit: Int): AppResult<List<OrderCustomer>> =
        repository.searchCustomers(query, limit)

    suspend fun createCustomer(name: String, phone: String?, address: String?): AppResult<OrderCustomer> =
        repository.createCustomer(name, phone, address)
}
