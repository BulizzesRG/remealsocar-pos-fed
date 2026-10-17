package com.posfab.shared.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserRoleTest {
    @Test
    fun orders_role_parses_in_any_case() {
        assertEquals(UserRole.ORDERS, UserRole.fromRaw("ORDERS"))
        assertEquals(UserRole.ORDERS, UserRole.fromRaw("orders"))
    }

    @Test
    fun unknown_role_maps_to_null() {
        assertNull(UserRole.fromRaw("COOK"))
        assertNull(UserRole.fromRaw(""))
    }

    @Test
    fun every_entry_round_trips_through_its_name() {
        UserRole.entries.forEach { assertEquals(it, UserRole.fromRaw(it.name)) }
    }
}
