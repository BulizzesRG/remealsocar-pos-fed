package com.posfab.shared.auth.domain

import com.posfab.shared.core.model.Permission

object CajaAccess {
    val permissions: Set<Permission> = setOf(
        Permission.SELL,
        Permission.OPEN_CASH,
        Permission.VIEW_SALES,
        Permission.MANAGE_PRODUCTS,
        Permission.MANAGE_ORDERS,
    )

    fun allows(user: AuthUser): Boolean = user.permissions.any { it in permissions }
}
