package com.posfab.shared.features.shell

import com.posfab.shared.auth.domain.UserSession

object RouteGuard {
    fun canAccess(session: UserSession, route: ShellRoute): Boolean =
        route.permission == null || route.permission in session.user.permissions

    fun allowedRoutes(session: UserSession): List<ShellRoute> =
        ShellRoute.entries.filter { canAccess(session, it) }

    fun modules(session: UserSession): List<ShellRoute> =
        allowedRoutes(session) - ShellRoute.HOME - ShellRoute.HELP
}
