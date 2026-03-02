package com.posfab.shared.core.model

enum class TerminalCode {
    POS1,
    POS2,
    ADMIN;

    companion object {
        fun fromRaw(value: String): TerminalCode? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
