package com.posfab.shared.core.diagnostics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashReporterTest {
    @AfterTest
    fun uninstall() {
        CrashReporter.install(null, "dev")
    }

    @Test
    fun report_appends_to_sink_and_updates_last_crash() {
        val sink = InMemoryCrashSink()
        CrashReporter.install(sink, "9.9.9")

        val record = CrashReporter.report(IllegalArgumentException("reporter-sink"), screen = "Fiado")

        assertEquals(listOf(record), sink.records)
        assertEquals(record, CrashReporter.lastCrash.value)
        assertEquals("9.9.9", record.version)
        assertEquals("Fiado", record.screen)
        assertEquals("IllegalArgumentException", record.type)
        assertEquals(listOf(record), CrashReporter.recent(5))
    }

    @Test
    fun failing_sink_does_not_break_report() {
        CrashReporter.install(FailingCrashSink(), "dev")

        val record = CrashReporter.report(IllegalStateException("reporter-failing"), screen = "Caja")

        assertEquals("reporter-failing", record.message)
        assertEquals(record, CrashReporter.lastCrash.value)
        assertEquals(listOf(record), CrashReporter.recent(3))
    }

    @Test
    fun works_without_install_and_uses_screen_tracker() {
        val previous = ScreenTracker.current
        ScreenTracker.current = "Productos"
        try {
            val record = CrashReporter.report(IllegalStateException("reporter-no-sink"))

            assertEquals("Productos", record.screen)
            assertEquals(listOf(record), CrashReporter.recent(1))
            assertTrue(CrashReporter.recent(0).isEmpty())
        } finally {
            ScreenTracker.current = previous
        }
    }
}
