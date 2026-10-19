package com.posfab.shared.features.shopping.ui

object QtyInput {
    private const val MAX_WHOLE_DIGITS = 6
    private const val MAX_DECIMALS = 3

    fun sanitize(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        if (dot < 0) return filtered.take(MAX_WHOLE_DIGITS)
        val whole = filtered.substring(0, dot).take(MAX_WHOLE_DIGITS)
        val decimals = filtered.substring(dot + 1).replace(".", "").take(MAX_DECIMALS)
        return "$whole.$decimals"
    }

    fun isValid(value: String): Boolean = value.isBlank() || toQty(value) != null

    fun toQty(value: String): Double? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed != sanitize(trimmed)) return null
        return trimmed.toDoubleOrNull()?.takeIf { it > 0.0 }
    }
}
