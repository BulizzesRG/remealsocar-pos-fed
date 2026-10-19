package com.posfab.shared.features.shopping

import com.posfab.shared.core.result.AppError
import com.posfab.shared.features.shopping.ui.ShoppingErrorText
import com.posfab.shared.features.shopping.ui.ShoppingStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShoppingErrorTextTest {
    @Test
    fun known_server_messages_are_translated() {
        val cases = mapOf(
            "List is empty" to ShoppingStrings.LIST_EMPTY,
            "List is not open" to ShoppingStrings.LIST_CLOSED,
            "List is not ready to shop" to ShoppingStrings.LIST_FINISHED,
            "Item not found" to ShoppingStrings.ITEM_NOT_FOUND,
            "text is required" to ShoppingStrings.TEXT_REQUIRED,
            "text too long" to ShoppingStrings.TEXT_TOO_LONG,
            "qty is invalid" to ShoppingStrings.QTY_INVALID,
            "Product not found" to ShoppingStrings.PRODUCT_GONE,
            "Product is inactive" to ShoppingStrings.PRODUCT_GONE,
            "carryOver item does not belong to the list" to ShoppingStrings.RELOAD_AND_RETRY,
            "carryOver item was bought" to ShoppingStrings.RELOAD_AND_RETRY,
        )

        cases.forEach { (message, expected) ->
            assertEquals(expected, ShoppingErrorText.from(AppError.Validation(message)), message)
        }
    }

    @Test
    fun not_ready_to_shop_is_not_confused_with_not_open() {
        assertEquals(ShoppingStrings.LIST_FINISHED, ShoppingErrorText.from(AppError.Validation("List is not ready to shop")))
    }

    @Test
    fun unknown_messages_never_leak_to_the_screen() {
        assertEquals(ShoppingStrings.FAILED, ShoppingErrorText.from(AppError.Validation("SQL error at line 3")))
        assertEquals(ShoppingStrings.FAILED, ShoppingErrorText.from(AppError.Unknown("boom")))
        assertEquals(ShoppingStrings.FAILED, ShoppingErrorText.from(AppError.RateLimit))
    }

    @Test
    fun transport_errors_have_fixed_texts() {
        assertEquals(ShoppingStrings.FORBIDDEN, ShoppingErrorText.from(AppError.Forbidden))
        assertEquals(ShoppingStrings.NETWORK, ShoppingErrorText.from(AppError.Network("timeout")))
        assertEquals(ShoppingStrings.CONFLICT, ShoppingErrorText.from(AppError.Conflict))
        assertEquals(ShoppingStrings.SIGN_IN_AGAIN, ShoppingErrorText.from(AppError.Unauthorized))
    }

    @Test
    fun load_errors_are_generic_except_network_and_session() {
        assertEquals(ShoppingStrings.LOAD_FAILED, ShoppingErrorText.load(AppError.Validation("x")))
        assertEquals(ShoppingStrings.LOAD_FAILED, ShoppingErrorText.load(AppError.Forbidden))
        assertEquals(ShoppingStrings.LOAD_FAILED, ShoppingErrorText.load(AppError.Conflict))
        assertEquals(ShoppingStrings.NETWORK, ShoppingErrorText.load(AppError.Network("x")))
        assertEquals(ShoppingStrings.SIGN_IN_AGAIN, ShoppingErrorText.load(AppError.Unauthorized))
    }

    @Test
    fun history_and_detail_errors_have_their_own_texts() {
        assertEquals(ShoppingStrings.HISTORY_FAILED, ShoppingErrorText.history(AppError.Validation("x")))
        assertEquals(ShoppingStrings.NETWORK, ShoppingErrorText.history(AppError.Network("x")))
        assertEquals(ShoppingStrings.DETAIL_FAILED, ShoppingErrorText.detail(AppError.Validation("List not found")))
        assertEquals(ShoppingStrings.NETWORK, ShoppingErrorText.detail(AppError.Network("x")))
    }

    @Test
    fun item_gone_is_detected_only_for_item_not_found() {
        assertTrue(ShoppingErrorText.isItemGone(AppError.Validation("Item not found")))
        assertFalse(ShoppingErrorText.isItemGone(AppError.Validation("List not found")))
        assertFalse(ShoppingErrorText.isItemGone(AppError.Conflict))
    }
}
