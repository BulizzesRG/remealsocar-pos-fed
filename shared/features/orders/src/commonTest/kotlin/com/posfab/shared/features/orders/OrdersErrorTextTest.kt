package com.posfab.shared.features.orders

import com.posfab.shared.core.result.AppError
import com.posfab.shared.features.orders.ui.OrdersErrorText
import com.posfab.shared.features.orders.ui.OrdersStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrdersErrorTextTest {
    @Test
    fun known_backend_messages_are_translated() {
        val table = mapOf(
            "Order not found" to OrdersStrings.ORDER_NOT_FOUND,
            "Order is not open" to OrdersStrings.ORDER_CLOSED,
            "notes are required" to OrdersStrings.NOTES_REQUIRED,
            "Customer is inactive" to OrdersStrings.CUSTOMER_INACTIVE,
            "phone must have 10 digits" to OrdersStrings.PHONE_INCOMPLETE,
            "paysWith is invalid" to OrdersStrings.PAYS_WITH_INVALID,
            "Use /orders/{id}/cancel" to OrdersStrings.HAS_LINES,
            "Draft line 9 not found" to OrdersStrings.LINE_NOT_FOUND,
        )
        table.forEach { (message, expected) ->
            assertEquals(expected, OrdersErrorText.from(AppError.Validation(message)))
        }
    }

    @Test
    fun unknown_messages_are_never_shown_raw() {
        assertEquals(OrdersStrings.FAILED, OrdersErrorText.from(AppError.Validation("boom")))
        assertEquals(OrdersStrings.FAILED, OrdersErrorText.from(AppError.Unknown("boom")))
        assertEquals(OrdersStrings.FAILED, OrdersErrorText.from(AppError.RateLimit))
    }

    @Test
    fun transport_errors() {
        assertEquals(OrdersStrings.NETWORK, OrdersErrorText.from(AppError.Network("x")))
        assertEquals(OrdersStrings.FORBIDDEN, OrdersErrorText.from(AppError.Forbidden))
        assertEquals(OrdersStrings.SIGN_IN_AGAIN, OrdersErrorText.from(AppError.Unauthorized))
        assertEquals(OrdersStrings.CONFLICT, OrdersErrorText.from(AppError.Conflict))
    }

    @Test
    fun load_errors_use_the_load_message() {
        assertEquals(OrdersStrings.LOAD_FAILED, OrdersErrorText.load(AppError.Validation("x")))
        assertEquals(OrdersStrings.NETWORK, OrdersErrorText.load(AppError.Network("x")))
    }

    @Test
    fun closed_detection() {
        assertTrue(OrdersErrorText.isClosed(AppError.Validation("Order is not open")))
        assertTrue(OrdersErrorText.isClosed(AppError.Validation("Order not found")))
        assertFalse(OrdersErrorText.isClosed(AppError.Validation("notes are required")))
        assertFalse(OrdersErrorText.isClosed(AppError.Conflict))
    }

    @Test
    fun customer_conflict_asks_to_check_the_phone() {
        assertEquals(OrdersStrings.CUSTOMER_SAVE_CONFLICT, OrdersErrorText.customer(AppError.Conflict))
        assertEquals(OrdersStrings.NETWORK, OrdersErrorText.customer(AppError.Network("x")))
    }
}
