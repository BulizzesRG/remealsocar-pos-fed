package com.posfab.shared.core.model

enum class Permission {
    VIEW_PRODUCTS,
    SELL,
    OPEN_CASH,
    MANAGE_PRODUCTS,
    MANAGE_INVENTORY,
    COUNT_STOCK,
    VIEW_SALES,
    VIEW_CREDIT,
    MANAGE_CREDIT,
    MANAGE_USERS;

    companion object {
        fun fromRaw(value: String): Permission? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
