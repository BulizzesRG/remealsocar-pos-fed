package com.posfab.app

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class CrashTimeLabelTest {
    private val mexico = ZoneId.of("America/Mexico_City")

    @Test
    fun formats_local_hour_and_padded_minutes() {
        assertEquals("11:05", crashTimeLabel("2026-10-17T17:05:00Z", mexico))
        assertEquals("9:07", crashTimeLabel("2026-10-17T15:07:30.123Z", mexico))
    }

    @Test
    fun unparseable_value_is_returned_as_is() {
        assertEquals("not a date", crashTimeLabel("not a date", mexico))
    }

    @Test
    fun occurred_sentence_matches_agreed_text() {
        assertEquals("Ocurrió a las 11:05 en Vender", CrashStrings.occurred("11:05", "Vender"))
    }
}
