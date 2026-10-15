package com.posfab.shared.features.shell

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteGuardTest {
    private val manager = session(
        UserRole.MANAGER,
        Permission.entries.toSet() - Permission.MANAGE_USERS,
    )
    private val cashier = session(
        UserRole.CASHIER,
        setOf(Permission.VIEW_PRODUCTS, Permission.SELL, Permission.OPEN_CASH),
    )
    private val stocker = session(
        UserRole.STOCKER,
        setOf(Permission.VIEW_PRODUCTS, Permission.COUNT_STOCK),
    )

    @Test
    fun manager_modules_follow_permissions() {
        assertEquals(
            listOf(ShellRoute.SELL, ShellRoute.CASH, ShellRoute.DAILY_SALES, ShellRoute.PRODUCTS),
            RouteGuard.modules(manager),
        )
    }

    @Test
    fun cashier_modules_are_sell_and_cash() {
        assertEquals(listOf(ShellRoute.SELL, ShellRoute.CASH), RouteGuard.modules(cashier))
    }

    @Test
    fun stocker_has_no_modules() {
        assertEquals(emptyList(), RouteGuard.modules(stocker))
    }

    @Test
    fun home_and_help_are_allowed_for_everyone() {
        listOf(manager, cashier, stocker).forEach {
            assertTrue(RouteGuard.canAccess(it, ShellRoute.HOME))
            assertTrue(RouteGuard.canAccess(it, ShellRoute.HELP))
        }
    }

    @Test
    fun cashier_is_denied_daily_sales_and_products() {
        assertFalse(RouteGuard.canAccess(cashier, ShellRoute.DAILY_SALES))
        assertFalse(RouteGuard.canAccess(cashier, ShellRoute.PRODUCTS))
    }

    @Test
    fun stocker_is_denied_sell_and_cash() {
        assertFalse(RouteGuard.canAccess(stocker, ShellRoute.SELL))
        assertFalse(RouteGuard.canAccess(stocker, ShellRoute.CASH))
    }

    @Test
    fun allowed_routes_keep_enum_order_and_include_home_and_help() {
        assertEquals(
            listOf(ShellRoute.HOME, ShellRoute.SELL, ShellRoute.CASH, ShellRoute.HELP),
            RouteGuard.allowedRoutes(cashier),
        )
        assertEquals(listOf(ShellRoute.HOME, ShellRoute.HELP), RouteGuard.allowedRoutes(stocker))
    }

    @Test
    fun access_depends_on_permissions_and_not_on_role() {
        val roleOnly = session(UserRole.ADMIN, emptySet())
        assertEquals(emptyList(), RouteGuard.modules(roleOnly))
    }

    private fun session(role: UserRole, permissions: Set<Permission>) = UserSession(
        tokens = AuthTokens("a", "r"),
        user = AuthUser("1", "user", setOf(role), permissions),
        terminalCode = "POS1",
    )
}
