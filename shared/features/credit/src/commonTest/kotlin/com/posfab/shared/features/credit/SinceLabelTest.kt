package com.posfab.shared.features.credit

import com.posfab.shared.features.credit.ui.CreditStrings
import com.posfab.shared.features.credit.ui.entryDateLabel
import com.posfab.shared.features.credit.ui.sinceLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class SinceLabelTest {
    private val mexico = TimeZone.of("America/Mexico_City")
    private val now = Instant.parse("2026-10-15T18:00:00Z").toEpochMilliseconds()

    @Test
    fun same_day_is_today() {
        assertEquals(CreditStrings.SINCE_TODAY, sinceLabel("2026-10-15T08:00:00-06:00", now, mexico))
    }

    @Test
    fun previous_day_is_yesterday() {
        assertEquals(CreditStrings.SINCE_YESTERDAY, sinceLabel("2026-10-14T23:30:00-06:00", now, mexico))
    }

    @Test
    fun older_dates_count_days() {
        assertEquals("desde hace 12 días", sinceLabel("2026-10-03T10:00:00-06:00", now, mexico))
    }

    @Test
    fun utc_with_z_uses_the_local_day() {
        assertEquals(CreditStrings.SINCE_YESTERDAY, sinceLabel("2026-10-15T05:00:00Z", now, mexico))
        assertEquals(CreditStrings.SINCE_TODAY, sinceLabel("2026-10-15T07:00:00.123456Z", now, mexico))
    }

    @Test
    fun future_dates_read_as_today() {
        assertEquals(CreditStrings.SINCE_TODAY, sinceLabel("2026-10-20T10:00:00-06:00", now, mexico))
    }

    @Test
    fun garbage_or_missing_is_null() {
        assertNull(sinceLabel("ayer", now, mexico))
        assertNull(sinceLabel(null, now, mexico))
        assertNull(entryDateLabel("2026-13-45", mexico))
    }

    @Test
    fun entry_date_label_uses_spanish_month_and_local_time() {
        assertEquals("3 oct, 14:05", entryDateLabel("2026-10-03T20:05:00Z", mexico))
        assertEquals("1 ene, 9:07", entryDateLabel("2026-01-01T09:07:00-06:00", mexico))
    }
}
