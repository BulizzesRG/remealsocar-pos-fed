package com.posfab.shared.features.catalog

import com.posfab.shared.features.catalog.common.moneyLabel
import com.posfab.shared.features.catalog.common.priceHistoryDateLabel
import com.posfab.shared.features.catalog.common.priceHistoryLine
import com.posfab.shared.features.catalog.domain.PriceHistoryEntry
import com.posfab.shared.features.catalog.domain.ProductPrices
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PriceHistoryLabelTest {
    private val utc = TimeZone.UTC
    private val mexico = TimeZone.of("America/Mexico_City")

    private fun entry(
        cash: Double = 17.0,
        list: Double = 18.0,
        cashOnsite: Double? = null,
        listOnsite: Double? = null,
        suggested: Boolean = false,
        validUntil: String = "2026-10-15T18:40:00Z",
        changedBy: String = "gerente",
    ) = PriceHistoryEntry(
        prices = ProductPrices(cash, list, cashOnsite, listOnsite, suggested),
        validFrom = "2026-10-01T12:00:00Z",
        validUntil = validUntil,
        changedBy = changedBy,
    )

    @Test
    fun afternoon_reads_pm_in_twelve_hours() {
        assertEquals("15 oct, 6:40 pm", priceHistoryDateLabel("2026-10-15T18:40:00Z", utc))
    }

    @Test
    fun morning_reads_am() {
        assertEquals("3 ene, 9:05 am", priceHistoryDateLabel("2026-01-03T09:05:00Z", utc))
    }

    @Test
    fun midnight_reads_twelve_am() {
        assertEquals("1 dic, 12:05 am", priceHistoryDateLabel("2026-12-01T00:05:00Z", utc))
    }

    @Test
    fun noon_reads_twelve_pm() {
        assertEquals("20 jun, 12:30 pm", priceHistoryDateLabel("2026-06-20T12:30:00Z", utc))
    }

    @Test
    fun fractional_seconds_are_accepted() {
        assertEquals("17 oct, 4:48 pm", priceHistoryDateLabel("2026-10-17T16:48:47.123456Z", utc))
    }

    @Test
    fun business_timezone_moves_day_and_hour() {
        assertEquals("16 oct, 1:30 am", priceHistoryDateLabel("2026-10-16T01:30:00Z", utc))
        assertEquals("15 oct, 7:30 pm", priceHistoryDateLabel("2026-10-16T01:30:00Z", mexico))
    }

    @Test
    fun unparseable_date_is_null() {
        assertNull(priceHistoryDateLabel("ayer", utc))
        assertNull(priceHistoryDateLabel("", utc))
    }

    @Test
    fun line_with_onsite_prices() {
        assertEquals(
            "Hasta 15 oct, 6:40 pm · $17.00 (con tarjeta $18.00) · corral $20.00 ($21.00) · lo cambió gerente",
            priceHistoryLine(entry(cashOnsite = 20.0, listOnsite = 21.0), utc),
        )
    }

    @Test
    fun line_without_onsite_prices() {
        assertEquals(
            "Hasta 15 oct, 6:40 pm · $17.00 (con tarjeta $18.00) · lo cambió admin",
            priceHistoryLine(entry(changedBy = "admin"), utc),
        )
    }

    @Test
    fun suggested_price_line_drops_card_prices() {
        assertEquals(
            "Hasta 15 oct, 6:40 pm · $17.00 (precio sugerido) · corral $20.00 · lo cambió gerente",
            priceHistoryLine(entry(list = 17.0, cashOnsite = 20.0, listOnsite = 20.0, suggested = true), utc),
        )
    }

    @Test
    fun suggested_price_line_without_onsite() {
        assertEquals(
            "Hasta 15 oct, 6:40 pm · $17.00 (precio sugerido) · lo cambió gerente",
            priceHistoryLine(entry(list = 17.0, suggested = true), utc),
        )
    }

    @Test
    fun line_uses_thousands_separator() {
        assertEquals(
            "Hasta 15 oct, 6:40 pm · $1,234.50 (con tarjeta $1,299.47) · lo cambió gerente",
            priceHistoryLine(entry(cash = 1234.5, list = 1299.47), utc),
        )
    }

    @Test
    fun line_with_bad_date_reads_unknown() {
        assertEquals(
            "Hasta — · $17.00 (con tarjeta $18.00) · lo cambió gerente",
            priceHistoryLine(entry(validUntil = "nunca"), utc),
        )
    }

    @Test
    fun line_follows_the_business_timezone() {
        assertEquals(
            "Hasta 15 oct, 7:30 pm · $17.00 (con tarjeta $18.00) · lo cambió gerente",
            priceHistoryLine(entry(validUntil = "2026-10-16T01:30:00Z"), mexico),
        )
    }

    @Test
    fun money_rounds_to_two_decimals_with_separators() {
        assertEquals("$0.00", moneyLabel(0.0))
        assertEquals("$17.00", moneyLabel(17.0))
        assertEquals("$0.10", moneyLabel(0.1))
        assertEquals("$999.99", moneyLabel(999.994))
        assertEquals("$1,000.00", moneyLabel(999.995))
        assertEquals("$1,234,567.89", moneyLabel(1234567.891))
        assertEquals("-$5.25", moneyLabel(-5.25))
    }

    @Test
    fun line_with_onsite_cash_but_no_onsite_list_omits_the_parentheses() {
        assertEquals(
            "Hasta 15 oct, 6:40 pm · $17.00 (con tarjeta $18.00) · corral $20.00 · lo cambió gerente",
            priceHistoryLine(entry(cashOnsite = 20.0, listOnsite = null), utc),
        )
    }

    @Test
    fun line_with_onsite_list_but_no_onsite_cash_omits_the_onsite_part() {
        assertEquals(
            "Hasta 15 oct, 6:40 pm · $17.00 (con tarjeta $18.00) · lo cambió gerente",
            priceHistoryLine(entry(cashOnsite = null, listOnsite = 21.0), utc),
        )
    }
}
