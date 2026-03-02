package com.posfab.shared.features.shell

import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.core.model.TerminalCode

object RouteGuard {
    fun canAccess(session: UserSession, route: ShellRoute): Boolean {
        val hasRole = route.requiredRoles.any { it in session.user.roles }
        if (!hasRole) return false

        return when (route) {
            ShellRoute.ADMIN -> session.terminal == TerminalCode.ADMIN
            else -> true
        }
    }

    fun allowedRoutes(session: UserSession): List<ShellRoute> =
        ShellRoute.entries.filter { canAccess(session, it) }
}
