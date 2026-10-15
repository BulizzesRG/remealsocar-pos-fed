package com.posfab.shared.features.sale

import com.posfab.shared.features.sale.ui.ScanBurstDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScanBurstDetectorTest {
    private var clock = 0L

    private fun ScanBurstDetector.type(start: String, text: String, stepMs: Long): String {
        var value = start
        text.forEach { char ->
            clock += stepMs
            val next = value + char
            onChange(value, next, clock)
            value = next
        }
        return value
    }

    @Test
    fun fast_burst_of_thirteen_digits_is_a_scan_on_an_empty_field() {
        val detector = ScanBurstDetector()
        clock = 1_000
        detector.type("", "1234567891234", stepMs = 5)

        assertEquals(ScanBurstDetector.Scan("1234567891234", ""), detector.scanOnConfirm(clock + 5))
    }

    @Test
    fun burst_keeps_the_value_typed_before_it() {
        val detector = ScanBurstDetector()
        val typed = detector.type("", "0.5", stepMs = 120)
        clock += 500
        detector.type(typed, "1234567891234", stepMs = 5)

        assertEquals(ScanBurstDetector.Scan("1234567891234", "0.5"), detector.scanOnConfirm(clock + 5))
    }

    @Test
    fun human_typing_is_not_a_scan() {
        val detector = ScanBurstDetector()
        detector.type("", "1234567891234", stepMs = 120)

        assertNull(detector.scanOnConfirm(clock + 5))
    }

    @Test
    fun five_fast_digits_are_not_a_scan() {
        val detector = ScanBurstDetector()
        detector.type("", "12345", stepMs = 5)

        assertNull(detector.scanOnConfirm(clock + 5))
    }

    @Test
    fun late_enter_is_not_a_scan() {
        val detector = ScanBurstDetector()
        detector.type("", "1234567891234", stepMs = 5)

        assertNull(detector.scanOnConfirm(clock + 400))
    }

    @Test
    fun backspace_breaks_the_burst() {
        val detector = ScanBurstDetector()
        val typed = detector.type("", "1234567", stepMs = 5)
        clock += 5
        detector.onChange(typed, typed.dropLast(1), clock)

        assertNull(detector.scanOnConfirm(clock + 5))
    }

    @Test
    fun confirm_always_clears_the_burst() {
        val detector = ScanBurstDetector()
        detector.type("", "1234567", stepMs = 5)

        assertEquals("1234567", detector.scanOnConfirm(clock + 5)?.code)
        assertNull(detector.scanOnConfirm(clock + 10))
    }

    @Test
    fun two_digits_landing_in_one_change_stay_in_the_burst() {
        val detector = ScanBurstDetector()
        detector.onChange("", "12", 1_000)
        detector.onChange("12", "1234", 1_005)
        detector.onChange("1234", "1234567", 1_010)

        assertEquals(ScanBurstDetector.Scan("1234567", ""), detector.scanOnConfirm(1_015))
    }

    @Test
    fun non_digit_prefix_breaks_the_burst() {
        val detector = ScanBurstDetector()
        detector.onChange("", "a", 1_000)
        detector.type("a", "1234567891234", stepMs = 5)

        assertEquals(ScanBurstDetector.Scan("1234567891234", "a"), detector.scanOnConfirm(clock + 5))
    }

    @Test
    fun gap_at_the_limit_breaks_the_burst() {
        val fast = ScanBurstDetector()
        fast.type("", "1234567", stepMs = 59)
        assertEquals("1234567", fast.scanOnConfirm(clock + 5)?.code)

        clock = 0
        val slow = ScanBurstDetector()
        slow.type("", "1234567", stepMs = 60)
        assertNull(slow.scanOnConfirm(clock + 5))
    }

    @Test
    fun reset_clears_the_burst() {
        val detector = ScanBurstDetector()
        detector.type("", "1234567891234", stepMs = 5)
        detector.reset()

        assertNull(detector.scanOnConfirm(clock + 5))
    }
}
