package com.posfab.shared.features.shell

import com.posfab.shared.core.model.UserRole

enum class ShellRoute(val title: String, val requiredRoles: Set<UserRole>) {
    POS("POS", setOf(UserRole.CASHIER, UserRole.MANAGER, UserRole.ADMIN)),
    CASH("Cash", setOf(UserRole.CASHIER, UserRole.MANAGER, UserRole.ADMIN)),
    HISTORY("History", setOf(UserRole.CASHIER, UserRole.MANAGER, UserRole.ADMIN)),
    CATALOG("Catalog", setOf(UserRole.MANAGER, UserRole.ADMIN)),
    OPERATIONS("Operations", setOf(UserRole.MANAGER, UserRole.ADMIN)),
    CREDIT("Credit", setOf(UserRole.CASHIER, UserRole.MANAGER, UserRole.ADMIN)),
    REPORTS("Reports", setOf(UserRole.MANAGER, UserRole.ADMIN)),
    ADMIN("Admin", setOf(UserRole.ADMIN));
}
