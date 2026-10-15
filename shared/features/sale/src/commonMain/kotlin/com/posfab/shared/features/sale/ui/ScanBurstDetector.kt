package com.posfab.shared.features.sale.ui

internal class ScanBurstDetector(
    private val gapMs: Long = 60,
    private val enterGapMs: Long = 150,
    private val minLength: Int = 6,
) {
    data class Scan(val code: String, val valueBeforeBurst: String)

    private var base: String? = null
    private val digits = StringBuilder()
    private var lastAt: Long? = null

    fun onChange(previous: String, value: String, atMs: Long) {
        val suffix = value.drop(previous.length)
        val appended = value.length > previous.length && value.startsWith(previous) && suffix.all(Char::isDigit)
        val alive = base != null && lastAt?.let { atMs - it < gapMs } == true
        when {
            appended && alive -> digits.append(suffix)
            appended -> {
                base = previous
                digits.clear()
                digits.append(suffix)
            }
            else -> clearBurst()
        }
        lastAt = atMs
    }

    fun scanOnConfirm(atMs: Long): Scan? {
        val start = base
        val last = lastAt
        val scan = if (start != null && last != null && digits.length >= minLength && atMs - last < enterGapMs) {
            Scan(digits.toString(), start)
        } else {
            null
        }
        reset()
        return scan
    }

    fun reset() {
        clearBurst()
        lastAt = null
    }

    private fun clearBurst() {
        base = null
        digits.clear()
    }
}
