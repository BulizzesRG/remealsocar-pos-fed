package com.posfab.shared.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportBundleTest {
    private fun crash(message: String) = CrashRecord(
        at = "2026-10-17T17:05:00Z",
        version = "dev",
        screen = "Vender",
        type = "IllegalStateException",
        message = message,
        stack = listOf("java.lang.IllegalStateException: $message"),
    )

    @Test
    fun sections_come_in_order_after_the_header() {
        val bundle = SupportBundle.build(
            header = listOf("version" to "dev", "screen" to "Vender"),
            logs = listOf("log one", "log two"),
            crashes = listOf(crash("first"), crash("second")),
        )

        val lines = bundle.lines()
        assertEquals("version=dev", lines[0])
        assertEquals("screen=Vender", lines[1])
        assertEquals(SupportBundle.ERRORS_SECTION, lines[2])
        assertTrue(bundle.indexOf("message=first") < bundle.indexOf("message=second"))
        assertTrue(bundle.indexOf("message=second") < bundle.indexOf(SupportBundle.LOGS_SECTION))
        assertTrue(bundle.endsWith("log one\nlog two"))
    }

    @Test
    fun no_crashes_keeps_both_sections() {
        val bundle = SupportBundle.build(header = emptyList(), logs = emptyList(), crashes = emptyList())

        assertTrue(bundle.contains(SupportBundle.ERRORS_SECTION))
        assertTrue(bundle.contains(SupportBundle.LOGS_SECTION))
        assertTrue(!bundle.contains("type="))
    }
}
