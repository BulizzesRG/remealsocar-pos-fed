package com.posfab.shared.auth

import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.CajaAccess
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CajaAccessTest {
    @Test
    fun stocker_permissions_do_not_allow_caja() {
        assertFalse(CajaAccess.allows(user(Permission.VIEW_PRODUCTS, Permission.COUNT_STOCK)))
    }

    @Test
    fun only_sell_allows_caja() {
        assertTrue(CajaAccess.allows(user(Permission.SELL)))
    }

    @Test
    fun orders_role_permissions_allow_caja() {
        assertTrue(CajaAccess.allows(user(Permission.VIEW_PRODUCTS, Permission.MANAGE_ORDERS)))
    }

    @Test
    fun empty_permissions_do_not_allow_caja() {
        assertFalse(CajaAccess.allows(user()))
    }

    @Test
    fun every_caja_permission_allows_caja_on_its_own() {
        CajaAccess.permissions.forEach { assertTrue(CajaAccess.allows(user(it)), it.name) }
    }

    private fun user(vararg permissions: Permission) = AuthUser(
        id = "1",
        username = "x",
        roles = setOf(UserRole.CASHIER),
        permissions = permissions.toSet(),
    )
}
