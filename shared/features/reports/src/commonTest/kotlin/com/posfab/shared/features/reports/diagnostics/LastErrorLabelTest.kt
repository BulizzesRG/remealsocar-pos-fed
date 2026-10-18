package com.posfab.shared.features.reports.diagnostics

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LastErrorLabelTest {
    private val mexico = TimeZone.of("America/Mexico_City")

    @Test
    fun formats_day_month_and_24_hour_time_in_local_zone() {
        assertEquals("17 oct, 11:05", lastErrorLabel("2026-10-17T17:05:00Z", mexico))
        assertEquals("3 ene, 21:40", lastErrorLabel("2026-01-04T03:40:00Z", mexico))
    }

    @Test
    fun null_reads_none() {
        assertEquals(DiagnosticsStrings.NO_ERRORS, lastErrorLabel(null, mexico))
    }

    @Test
    fun unparseable_reads_none() {
        assertEquals(DiagnosticsStrings.NO_ERRORS, lastErrorLabel("not a date", mexico))
    }
}
