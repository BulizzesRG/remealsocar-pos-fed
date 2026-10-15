package com.posfab.shared.core.model

enum class UserRole {
    CASHIER,
    ADMIN,
    MANAGER,
    STOCKER;

    companion object {
        fun fromRaw(value: String): UserRole? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
