package com.posfab.shared.core.input

object PhoneInput {
    const val LENGTH = 10

    fun sanitize(value: String): String = value.filter { it in '0'..'9' }.take(LENGTH)

    fun isComplete(value: String): Boolean = value.isEmpty() || (value.length == LENGTH && value.all { it in '0'..'9' })
}

object DateMask {
    private const val SEPARATOR = '/'
    private const val MAX_DAY = 31
    private const val MAX_MONTH = 12
    private const val YEAR_DIGITS = 4

    fun apply(previous: String, typed: String): String {
        val digits = typed.filter { it in '0'..'9' }
        val masked = build(digits)
        val deleting = typed.length < previous.length
        return if (deleting && !typed.endsWith(SEPARATOR)) masked.removeSuffix(SEPARATOR.toString()) else masked
    }

    private fun build(digits: String): String {
        val day = part(digits, from = 0, max = MAX_DAY)
        if (!day.complete) return day.text
        val month = part(digits, from = day.consumed, max = MAX_MONTH)
        if (!month.complete) return "${day.text}$SEPARATOR${month.text}"
        val year = digits.drop(day.consumed + month.consumed).take(YEAR_DIGITS)
        return "${day.text}$SEPARATOR${month.text}$SEPARATOR$year"
    }

    private fun part(digits: String, from: Int, max: Int): Part {
        val first = digits.getOrNull(from) ?: return Part("", consumed = 0, complete = false)
        if (first.digitToInt() > max / 10) return Part("0$first", consumed = 1, complete = true)
        val second = digits.getOrNull(from + 1) ?: return Part("$first", consumed = 1, complete = false)
        val value = first.digitToInt() * 10 + second.digitToInt()
        return if (value > max) {
            Part("0$first", consumed = 1, complete = true)
        } else {
            Part("$first$second", consumed = 2, complete = true)
        }
    }

    private data class Part(val text: String, val consumed: Int, val complete: Boolean)
}
