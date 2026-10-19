package com.posfab.shared.features.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleRowsTest {
    private val all = ShellRoute.entries - ShellRoute.HOME - ShellRoute.HELP

    @Test
    fun six_modules_stay_in_one_row() {
        val six = all.take(6)
        assertEquals(listOf(six), moduleRows(six))
    }

    @Test
    fun seven_modules_wrap_into_two_rows_of_four_and_three() {
        val rows = moduleRows(all)
        assertEquals(2, rows.size)
        assertEquals(4, rows[0].size)
        assertEquals(3, rows[1].size)
        assertEquals(ShellRoute.SELL, rows[0].first())
        assertEquals(ShellRoute.SURTIDO, rows[1].last())
    }

    @Test
    fun cashier_modules_stay_in_one_row() {
        val cashier = listOf(ShellRoute.SELL, ShellRoute.CASH, ShellRoute.SURTIDO)
        assertEquals(listOf(cashier), moduleRows(cashier))
    }
}
