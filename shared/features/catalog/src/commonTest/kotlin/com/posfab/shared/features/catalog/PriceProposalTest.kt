package com.posfab.shared.features.catalog

import com.posfab.shared.features.catalog.common.moneyInput
import com.posfab.shared.features.catalog.common.proposeListPrice
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceProposalTest {
    @Test
    fun rounds_up_to_next_whole_peso() {
        assertEquals(18.0, proposeListPrice(17.0, 5.0))
        assertEquals(12.0, proposeListPrice(10.50, 5.0))
        assertEquals(19.0, proposeListPrice(17.30, 5.0))
    }

    @Test
    fun zero_percent_still_rounds_up_but_keeps_whole_amounts() {
        assertEquals(18.0, proposeListPrice(17.30, 0.0))
        assertEquals(18.0, proposeListPrice(18.0, 0.0))
    }

    @Test
    fun money_input_uses_two_decimals_and_blank_for_null() {
        assertEquals("25.50", moneyInput(25.5))
        assertEquals("", moneyInput(null))
    }
}
