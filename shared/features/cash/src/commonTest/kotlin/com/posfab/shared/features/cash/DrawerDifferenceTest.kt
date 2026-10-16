package com.posfab.shared.features.cash

import com.posfab.shared.features.cash.domain.DrawerDifference
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawerDifferenceTest {
    @Test
    fun equal_amounts_cuadra() {
        assertEquals(DrawerDifference.Cuadra, DrawerDifference.of(expected = 1500.0, counted = 1500.0))
    }

    @Test
    fun differences_below_a_cent_cuadra() {
        assertEquals(DrawerDifference.Cuadra, DrawerDifference.of(expected = 0.1 + 0.2, counted = 0.3))
        assertEquals(DrawerDifference.Cuadra, DrawerDifference.of(expected = 1500.004, counted = 1500.0))
    }

    @Test
    fun more_counted_sobran() {
        assertEquals(DrawerDifference.Sobran(100.5), DrawerDifference.of(expected = 1500.0, counted = 1600.5))
    }

    @Test
    fun less_counted_faltan() {
        assertEquals(DrawerDifference.Faltan(20.0), DrawerDifference.of(expected = 1500.0, counted = 1480.0))
        assertEquals(DrawerDifference.Faltan(1500.0), DrawerDifference.of(expected = 1500.0, counted = 0.0))
    }
}
