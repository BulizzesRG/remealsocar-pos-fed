package com.posfab.shared.features.credit

import com.posfab.shared.features.credit.ui.CreditStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomerLineTest {
    @Test
    fun all_three_parts_join_with_the_separator() {
        assertEquals(
            "Número 30000117 · 5551122896 · Av juarez 5",
            CreditStrings.customerLine("30000117", "5551122896", "Av juarez 5"),
        )
    }

    @Test
    fun missing_parts_are_left_out() {
        assertEquals("Número 30000117 · Av juarez 5", CreditStrings.customerLine("30000117", null, "Av juarez 5"))
        assertEquals("5551122896 · Av juarez 5", CreditStrings.customerLine(null, "5551122896", "Av juarez 5"))
    }

    @Test
    fun a_single_part_has_no_separator() {
        assertEquals("Número 30000025", CreditStrings.customerLine("30000025", null, null))
        assertEquals("Calle 1", CreditStrings.customerLine(null, null, "Calle 1"))
    }

    @Test
    fun nothing_gives_an_empty_line() {
        assertEquals("", CreditStrings.customerLine(null, null, null))
    }

    @Test
    fun blank_parts_count_as_missing_and_are_trimmed() {
        assertEquals("Número 30000025 · Calle 1", CreditStrings.customerLine(" 30000025 ", "  ", " Calle 1 "))
        assertEquals("", CreditStrings.customerLine("", " ", ""))
    }
}
