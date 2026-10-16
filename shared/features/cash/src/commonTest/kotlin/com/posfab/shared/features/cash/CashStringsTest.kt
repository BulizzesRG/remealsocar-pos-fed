package com.posfab.shared.features.cash

import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.DrawerDifference
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.features.cash.ui.CashStrings
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CashStringsTest {
    @Test
    fun money_uses_thousands_separator() {
        assertEquals("$1,250.00", CashStrings.money(1250.0))
        assertEquals("$0.50", CashStrings.money(0.5))
        assertEquals("$123,456.78", CashStrings.money(123456.78))
    }

    @Test
    fun minus_money_only_when_positive() {
        assertEquals("−$500.00", CashStrings.minusMoney(500.0))
        assertEquals("$0.00", CashStrings.minusMoney(0.0))
    }

    @Test
    fun time_label_is_twelve_hour() {
        assertEquals("8:15 am", CashStrings.timeLabel(Instant.parse("2026-10-16T08:15:00Z"), TimeZone.UTC))
        assertEquals("12:05 am", CashStrings.timeLabel(Instant.parse("2026-10-16T00:05:00Z"), TimeZone.UTC))
        assertEquals("12:30 pm", CashStrings.timeLabel(Instant.parse("2026-10-16T12:30:00Z"), TimeZone.UTC))
        assertEquals("6:40 pm", CashStrings.timeLabel(Instant.parse("2026-10-16T18:40:00Z"), TimeZone.UTC))
        assertEquals("Abierta desde 8:15 am", CashStrings.openSince(Instant.parse("2026-10-16T14:15:00Z"), TimeZone.of("America/Mexico_City")))
    }

    @Test
    fun payout_line_joins_time_reason_and_amount() {
        val payout = CashPayout(1L, 500.0, PayoutReason.SURTIDO, null, Instant.parse("2026-10-16T10:30:00Z"))

        assertEquals("10:30 am · Surtido · −$500.00", CashStrings.payoutLine(payout, TimeZone.UTC))
    }

    @Test
    fun open_accounts_singular_and_plural() {
        assertEquals("Tienes 1 cuenta abierta en Vender", CashStrings.openAccounts(1))
        assertEquals("Tienes 3 cuentas abiertas en Vender", CashStrings.openAccounts(3))
        assertEquals(
            "Tienes 2 cuentas abiertas en Vender. Cóbralas o vacíalas antes de cerrar",
            CashStrings.closeBlocked(2),
        )
    }

    @Test
    fun difference_labels() {
        assertEquals("Cuadra", CashStrings.difference(DrawerDifference.Cuadra))
        assertEquals("Sobran $10.00", CashStrings.difference(DrawerDifference.Sobran(10.0)))
        assertEquals("Faltan $1,000.00", CashStrings.difference(DrawerDifference.Faltan(1000.0)))
    }

    @Test
    fun close_button_shows_counted_amount_when_present() {
        assertEquals("Cerrar día", CashStrings.closeButton(null))
        assertEquals("Cerrar día con $1,480.00", CashStrings.closeButton(1480.0))
    }

    @Test
    fun breakdown_labels_use_the_agreed_wording() {
        assertEquals("Abonos de clientes", CashStrings.CREDIT_PAYMENTS)
        assertEquals("Ventas por transferencia", CashStrings.TRANSFERS)
        assertEquals("Ventas con tarjeta", CashStrings.CARD)
        assertEquals("Fiado de hoy", CashStrings.CREDIT)
        assertEquals("Primera vez que abres la caja", CashStrings.FIRST_TIME)
    }

    @Test
    fun payout_button_shows_amount_when_present() {
        assertEquals("Sacar", CashStrings.payoutButton(null))
        assertEquals("Sacar $500.00", CashStrings.payoutButton(500.0))
    }
}
