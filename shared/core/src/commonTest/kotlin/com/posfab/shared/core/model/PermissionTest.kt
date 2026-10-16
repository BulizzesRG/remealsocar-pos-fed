package com.posfab.shared.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionTest {
    @Test
    fun valid_value_maps_to_permission() {
        assertEquals(Permission.OPEN_CASH, Permission.fromRaw("OPEN_CASH"))
        assertEquals(Permission.MANAGE_USERS, Permission.fromRaw("MANAGE_USERS"))
        assertEquals(Permission.MANAGE_CASH, Permission.fromRaw("MANAGE_CASH"))
    }

    @Test
    fun lowercase_value_maps_to_permission() {
        assertEquals(Permission.SELL, Permission.fromRaw("sell"))
    }

    @Test
    fun unknown_value_maps_to_null() {
        assertNull(Permission.fromRaw("BOGUS"))
        assertNull(Permission.fromRaw(""))
    }

    @Test
    fun every_entry_round_trips_through_its_name() {
        Permission.entries.forEach { assertEquals(it, Permission.fromRaw(it.name)) }
    }
}
