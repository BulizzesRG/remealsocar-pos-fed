package com.posfab.shared.core.model

fun terminalDisplayName(code: String): String = when (code.uppercase()) {
    "POS1" -> "Caja 1"
    "POS2" -> "Caja 2"
    "COCINA" -> "Cocina"
    else -> code
}
