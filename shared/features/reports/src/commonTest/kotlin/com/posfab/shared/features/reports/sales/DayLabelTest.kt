package com.posfab.shared.features.reports.sales

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class DayLabelTest {
    private val zone = TimeZone.of("America/Mexico_City")
    private val today = LocalDate(2026, 10, 16)

    @Test
    fun day_title_names_today_and_yesterday() {
        assertEquals("Hoy, vie 16 oct", dayTitle(today, today))
        assertEquals("Ayer, jue 15 oct", dayTitle(LocalDate(2026, 10, 15), today))
        assertEquals("mié 14 oct", dayTitle(LocalDate(2026, 10, 14), today))
    }

    @Test
    fun day_title_adds_the_year_only_when_it_differs() {
        assertEquals("mié 31 dic 2025", dayTitle(LocalDate(2025, 12, 31), today))
        assertEquals("Ayer, mié 31 dic 2025", dayTitle(LocalDate(2025, 12, 31), LocalDate(2026, 1, 1)))
        assertEquals("dom 1 mar", dayTitle(LocalDate(2026, 3, 1), today))
    }

    @Test
    fun list_time_uses_twelve_hours_in_the_business_zone() {
        assertEquals("7:15 pm", listTimeLabel(Instant.parse("2026-10-17T01:15:00Z"), zone))
        assertEquals("12:05 am", listTimeLabel(Instant.parse("2026-10-16T06:05:00Z"), zone))
        assertEquals("12:00 pm", listTimeLabel(Instant.parse("2026-10-16T18:00:00Z"), zone))
        assertEquals("9:07 am", listTimeLabel(Instant.parse("2026-10-16T15:07:00Z"), zone))
    }

    @Test
    fun detail_time_names_the_day() {
        assertEquals("Hoy 7:15 pm", detailTimeLabel(Instant.parse("2026-10-17T01:15:00Z"), today, zone))
        assertEquals("Ayer 7:15 pm", detailTimeLabel(Instant.parse("2026-10-16T01:15:00Z"), today, zone))
        assertEquals("mié 14 oct, 7:15 pm", detailTimeLabel(Instant.parse("2026-10-15T01:15:00Z"), today, zone))
        assertEquals("mié 31 dic 2025, 10:00 am", detailTimeLabel(Instant.parse("2025-12-31T16:00:00Z"), today, zone))
    }

    @Test
    fun detail_subtitle_joins_time_terminal_payment_and_channel() {
        val cash = detail(SalePayment.CASH, SaleChannel.TAKEAWAY, null)
        val credit = detail(SalePayment.CREDIT, SaleChannel.ONSITE, "Lupe Hernández (Doña Lupe)")

        assertEquals("Hoy 7:15 pm · Caja 1 · Efectivo · Normal", detailSubtitle(cash, today, zone))
        assertEquals("Hoy 7:15 pm · Caja 1 · Fiado a Lupe Hernández (Doña Lupe) · Corral", detailSubtitle(credit, today, zone))
    }

    @Test
    fun qty_label_by_price_type() {
        assertEquals("2 pz", qtyLabel(2.0, "PZA", SalePriceType.PIECE))
        assertEquals("1.5 pz", qtyLabel(1.5, "PZA", SalePriceType.PIECE))
        assertEquals("0.250 kg", qtyLabel(0.25, "KG", SalePriceType.WEIGHT))
        assertEquals("1.000 kg", qtyLabel(1.0, "KG", SalePriceType.WEIGHT))
        assertEquals("", qtyLabel(1.0, "PZA", SalePriceType.OPEN))
        assertEquals("3 paq", qtyLabel(3.0, "PAQ", SalePriceType.OTHER))
        assertEquals("1.5", qtyLabel(1.5, "", SalePriceType.OTHER))
    }

    @Test
    fun parse_typed_date_accepts_only_complete_real_dates() {
        assertEquals(LocalDate(2026, 10, 16), parseTypedDate("16/10/2026"))
        assertEquals(LocalDate(2026, 2, 28), parseTypedDate(" 28/02/2026 "))
        assertNull(parseTypedDate("31/02/2026"))
        assertNull(parseTypedDate("16/10/26"))
        assertNull(parseTypedDate("16/10/"))
        assertNull(parseTypedDate(""))
        assertNull(parseTypedDate("aa/10/2026"))
        assertNull(parseTypedDate("2026-10-16"))
        assertNull(parseTypedDate("16/10/1999"))
        assertEquals(LocalDate(2000, 1, 1), parseTypedDate("01/01/2000"))
    }

    private fun detail(payment: SalePayment, channel: SaleChannel, customer: String?) = SaleDetail(
        id = 1L,
        folio = "S-000123",
        soldAt = Instant.parse("2026-10-17T01:15:00Z"),
        terminalCode = "POS1",
        businessUnitCode = "TIENDA",
        payment = payment,
        channel = channel,
        subtotal = 100.0,
        cashDiscount = 0.0,
        total = 100.0,
        customerName = customer,
        lines = emptyList(),
    )
}
