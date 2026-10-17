package com.posfab.shared.features.orders.repository

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
import kotlinx.datetime.LocalDate

interface OrdersRepository {
    suspend fun openOrders(): AppResult<List<Order>>
    suspend fun ordersOfDay(status: OrderStatus, date: LocalDate): AppResult<List<Order>>
    suspend fun order(id: Long): AppResult<Order>
    suspend fun createOrder(idempotencyKey: String): AppResult<Order>
    suspend fun updateOrder(id: Long, expectedVersion: Int, fields: OrderFields): AppResult<Order>
    suspend fun setStage(id: Long, stage: OrderStage, expectedVersion: Int): AppResult<Order>
    suspend fun cancelOrder(id: Long, reason: CancelReason, notes: String?, expectedVersion: Int): AppResult<Order>
    suspend fun cancelEmpty(id: Long, expectedVersion: Int): AppResult<Unit>
    suspend fun addLine(orderId: Long, product: OrderProduct, input: OrderLineInput, expectedVersion: Int): OrderMutationResult
    suspend fun updateLineQty(orderId: Long, lineId: Long, qty: Double, expectedVersion: Int): OrderMutationResult
    suspend fun removeLine(orderId: Long, lineId: Long, expectedVersion: Int): OrderMutationResult
    suspend fun searchProducts(query: String, limit: Int): AppResult<List<OrderProduct>>
    suspend fun kitchenProducts(): AppResult<List<OrderProduct>>
    suspend fun searchCustomers(query: String, limit: Int): AppResult<List<OrderCustomer>>
    suspend fun createCustomer(name: String, phone: String?, address: String?): AppResult<OrderCustomer>
}
