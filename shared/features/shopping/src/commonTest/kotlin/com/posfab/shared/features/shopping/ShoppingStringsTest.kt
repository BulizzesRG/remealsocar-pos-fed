package com.posfab.shared.features.shopping

import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.plainQty
import com.posfab.shared.features.shopping.ui.QtyInput
import com.posfab.shared.features.shopping.ui.ShoppingStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShoppingStringsTest {
    @Test
    fun item_line_without_quantity_is_the_text() {
        assertEquals("Coca cola 600 ml", ShoppingStrings.itemLine(shoppingItem(1L, "Coca cola 600 ml")))
    }

    @Test
    fun item_line_with_quantity_drops_useless_decimals() {
        assertEquals("Coca cola 600 ml × 3", ShoppingStrings.itemLine(shoppingItem(1L, "Coca cola 600 ml", qty = 3.0)))
        assertEquals("jitomate × 2.5", ShoppingStrings.itemLine(shoppingItem(1L, "jitomate", qty = 2.5)))
        assertEquals("queso × 0.125", ShoppingStrings.itemLine(shoppingItem(1L, "queso", qty = 0.125)))
    }

    @Test
    fun plain_quantity_rounds_to_three_decimals() {
        assertEquals("3", plainQty(3.0))
        assertEquals("2.5", plainQty(2.5))
        assertEquals("1.235", plainQty(1.2349999))
        assertEquals("10", plainQty(9.99999))
    }

    @Test
    fun progress_count_and_author_lines() {
        assertEquals("5 de 8 comprados", ShoppingStrings.progress(5, 8))
        assertEquals("1 renglón", ShoppingStrings.itemCount(1))
        assertEquals("3 renglones", ShoppingStrings.itemCount(3))
        assertEquals("anotó gerente", ShoppingStrings.byLine("gerente"))
        assertEquals("", ShoppingStrings.byLine(" "))
    }

    @Test
    fun notices_carry_the_text() {
        assertEquals("Se anotó: jitomate", ShoppingStrings.added("jitomate"))
        assertEquals("Se quitó: jitomate", ShoppingStrings.removed("jitomate"))
        assertEquals("No hay ningún producto con el código 7501", ShoppingStrings.notFoundWithCode("7501"))
    }

    @Test
    fun history_status_reads_what_happened() {
        assertEquals("Comprado", ShoppingStrings.historyStatus(ShoppingItemStatus.BOUGHT))
        assertEquals("No había", ShoppingStrings.historyStatus(ShoppingItemStatus.NOT_FOUND))
        assertEquals("Quedó pendiente", ShoppingStrings.historyStatus(ShoppingItemStatus.PENDING))
        assertEquals("Listas anteriores (2)", ShoppingStrings.historyToggle(2))
        assertEquals("Listas anteriores", ShoppingStrings.historyToggle(null))
    }

    @Test
    fun quantity_input_accepts_positive_decimals_only() {
        assertEquals(2.5, QtyInput.toQty("2.5"))
        assertEquals(3.0, QtyInput.toQty("3"))
        assertEquals(0.5, QtyInput.toQty(".5"))
        assertNull(QtyInput.toQty("0"))
        assertNull(QtyInput.toQty("0.000"))
        assertNull(QtyInput.toQty("."))
        assertNull(QtyInput.toQty("1.2345"))
        assertNull(QtyInput.toQty("abc"))
        assertTrue(QtyInput.isValid(""))
        assertFalse(QtyInput.isValid("0"))
        assertEquals("123456", QtyInput.sanitize("1234567"))
    }
}
