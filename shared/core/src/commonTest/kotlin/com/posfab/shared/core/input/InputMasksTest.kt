package com.posfab.shared.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputMasksTest {

    private fun typeAll(vararg keys: Char): String = keys.fold("") { text, key -> DateMask.apply(text, "$text$key") }

    @Test
    fun phoneKeepsOnlyDigitsUpToTen() {
        assertEquals("5512345678", PhoneInput.sanitize("55-1234 5678 99a"))
        assertEquals("", PhoneInput.sanitize("abc"))
    }

    @Test
    fun phoneIsCompleteWhenEmptyOrTenDigits() {
        assertTrue(PhoneInput.isComplete(""))
        assertTrue(PhoneInput.isComplete("5512345678"))
        assertFalse(PhoneInput.isComplete("5512345"))
        assertFalse(PhoneInput.isComplete("55123456789"))
        assertFalse(PhoneInput.isComplete("55123a5678"))
    }

    @Test
    fun dayStartingAboveThreeIsPaddedRightAway() {
        assertEquals("04/", DateMask.apply("", "4"))
    }

    @Test
    fun loneLowDayDigitWaits() {
        assertEquals("1", DateMask.apply("", "1"))
    }

    @Test
    fun twoDayDigitsAppendTheSlash() {
        assertEquals("15/", DateMask.apply("1", "15"))
    }

    @Test
    fun dayAboveThirtyOnePadsAndShiftsTheDigitToTheMonth() {
        assertEquals("03/02/", DateMask.apply("3", "32"))
    }

    @Test
    fun monthStartingAboveOneIsPaddedRightAway() {
        assertEquals("01/02/", DateMask.apply("01/", "01/2"))
    }

    @Test
    fun loneLowMonthDigitWaits() {
        assertEquals("01/1", DateMask.apply("01/", "01/1"))
    }

    @Test
    fun monthAboveTwelvePadsAndShiftsTheDigitToTheYear() {
        assertEquals("01/01/3", DateMask.apply("01/1", "01/13"))
    }

    @Test
    fun deletingTheSlashDoesNotAddItBack() {
        assertEquals("01", DateMask.apply("01/", "01"))
        assertEquals("01/0", DateMask.apply("01", "010"))
    }

    @Test
    fun deletingAYearDigitKeepsTheTypedSlash() {
        assertEquals("01/02/", DateMask.apply("01/02/2", "01/02/"))
        assertEquals("01/02", DateMask.apply("01/02/", "01/02"))
    }

    @Test
    fun fullDateTypedKeyByKey() {
        assertEquals("01/02/2026", typeAll('0', '1', '0', '2', '2', '0', '2', '6'))
    }

    @Test
    fun paddedDigitsShiftIntoTheNextPart() {
        assertEquals("05/12/2026", typeAll('5', '1', '2', '2', '0', '2', '6'))
        assertEquals("05/12/2026", DateMask.apply("", "5122026"))
    }

    @Test
    fun extraDigitsAreIgnored() {
        assertEquals("01/02/2026", DateMask.apply("01/02/2026", "01/02/20261"))
    }

    @Test
    fun nonDigitsAreStripped() {
        assertEquals("01/02/20", DateMask.apply("", "0a1-0b2.20"))
    }
}
