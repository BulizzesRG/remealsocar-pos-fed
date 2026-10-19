package com.posfab.shared.features.shopping

import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.ui.historyRowLabel
import com.posfab.shared.features.shopping.ui.listTitle
import com.posfab.shared.features.shopping.ui.shortListDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ListTitleTest {
    private val zone = TimeZone.of("America/Mexico_City")
    private val today = LocalDate(2026, 10, 18)

    @Test
    fun title_uses_spanish_weekday_and_month() {
        assertEquals("Lista del dom 18 oct", listTitle(Instant.parse("2026-10-18T15:00:00Z"), zone, today))
    }

    @Test
    fun a_different_year_is_shown() {
        assertEquals("Lista del sáb 18 oct 2025", listTitle(Instant.parse("2025-10-18T15:00:00Z"), zone, today))
    }

    @Test
    fun the_business_time_zone_decides_the_day() {
        val lateFridayNight = Instant.parse("2026-10-17T05:30:00Z")

        assertEquals("Lista del vie 16 oct", listTitle(lateFridayNight, zone, today))
        assertEquals("Lista del sáb 17 oct", listTitle(lateFridayNight, TimeZone.UTC, today))
    }

    @Test
    fun every_month_and_weekday_has_an_abbreviation() {
        assertEquals("lun 5 ene", shortListDate(Instant.parse("2026-01-05T18:00:00Z"), zone, today))
        assertEquals("dom 27 dic", shortListDate(Instant.parse("2026-12-27T18:00:00Z"), zone, today))
        assertEquals("mié 1 jul", shortListDate(Instant.parse("2026-07-01T18:00:00Z"), zone, today))
    }

    @Test
    fun history_row_reads_date_and_progress() {
        val summary = ShoppingListSummary(4L, Instant.parse("2026-10-11T18:00:00Z"), null, 14, 12, 2)

        assertEquals("dom 11 oct · 12 de 14 comprados", historyRowLabel(summary, zone, today))
    }
}
