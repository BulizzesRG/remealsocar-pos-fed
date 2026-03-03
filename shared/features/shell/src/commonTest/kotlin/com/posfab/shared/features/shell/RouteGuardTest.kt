package com.posfab.shared.features.shell

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.model.UserRole
import kotlin.test.Test
import kotlin.test.assertFalse

class RouteGuardTest {
    @Test
    fun denies_admin_route_when_role_missing() {
        val session = UserSession(
            tokens = AuthTokens("a", "r"),
            user = AuthUser("1", "cashier", setOf(UserRole.CASHIER)),
            terminal = TerminalCode.POS1,
        )

        val allowed = RouteGuard.canAccess(session, ShellRoute.ADMIN)

        assertFalse(allowed)
    }

    @Test
    fun denies_reports_route_for_cashier() {
        val session = UserSession(
            tokens = AuthTokens("a", "r"),
            user = AuthUser("1", "cashier", setOf(UserRole.CASHIER)),
            terminal = TerminalCode.POS1,
        )

        val allowed = RouteGuard.canAccess(session, ShellRoute.REPORTS)

        assertFalse(allowed)
    }

    @Test
    fun denies_catalog_route_for_cashier() {
        val session = UserSession(
            tokens = AuthTokens("a", "r"),
            user = AuthUser("1", "cashier", setOf(UserRole.CASHIER)),
            terminal = TerminalCode.POS1,
        )

        val allowed = RouteGuard.canAccess(session, ShellRoute.CATALOG)

        assertFalse(allowed)
    }
}
