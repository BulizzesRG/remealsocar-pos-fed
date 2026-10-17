package com.posfab.shared.features.orders

import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.OrderKitchenKind
import com.posfab.shared.features.orders.domain.OrderPriceType
import com.posfab.shared.features.orders.domain.OrderStage
import com.posfab.shared.features.orders.ui.OrdersStrings
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class OrdersStringsTest {
    private val zone = TimeZone.of("America/Mexico_City")

    @Test
    fun money_uses_thousands_and_two_decimals() {
        assertEquals("$1,234.50", OrdersStrings.money(1234.5))
        assertEquals("$97.00", OrdersStrings.money(97.0))
        assertEquals("Total $97.00", OrdersStrings.total(97.0))
    }

    @Test
    fun pays_with_line_shows_change_or_what_is_missing() {
        assertEquals("Paga con $500.00 · Cambio $403.00", OrdersStrings.paysWithLine(500.0, 403.0))
        assertEquals("Paga con $50.00 · Le falta $47.00", OrdersStrings.paysWithLine(50.0, -47.0))
        assertEquals("Paga con $97.00 · Cambio $0.00", OrdersStrings.paysWithLine(97.0, 0.0))
        assertEquals("Paga con $500.00", OrdersStrings.paysWithLine(500.0, null))
    }

    @Test
    fun time_label_is_twelve_hour() {
        assertEquals("7:15 pm", OrdersStrings.timeLabel(Instant.parse("2026-10-17T01:15:00Z"), zone))
        assertEquals("12:05 am", OrdersStrings.timeLabel(Instant.parse("2026-10-16T06:05:00Z"), zone))
        assertEquals("12:00 pm", OrdersStrings.timeLabel(Instant.parse("2026-10-16T18:00:00Z"), zone))
    }

    @Test
    fun quantity_labels() {
        assertEquals("2 pz", OrdersStrings.qtyLabel(2.0, "PZA", OrderPriceType.PIECE))
        assertEquals("0.250 kg", OrdersStrings.qtyLabel(0.25, "KG", OrderPriceType.WEIGHT))
        assertEquals("1.000 kg", OrdersStrings.qtyLabel(1.0, "KG", OrderPriceType.WEIGHT))
        assertEquals("", OrdersStrings.qtyLabel(1.0, "PZA", OrderPriceType.OPEN))
    }

    @Test
    fun reasons_stages_and_sections() {
        assertEquals(
            listOf("No contestó", "Dirección equivocada", "Se arrepintió", "No tenía dinero", "Otro"),
            CancelReason.entries.map(OrdersStrings::reasonLabel),
        )
        assertEquals("Anotado", OrdersStrings.stageLabel(OrderStage.ANOTADO))
        assertEquals("Enviado", OrdersStrings.stageLabel(OrderStage.ENVIADO))
        assertEquals("Pagados hoy (2)", OrdersStrings.paidToday(2))
        assertEquals("Cancelados hoy (0)", OrdersStrings.cancelledToday(0))
        assertEquals("Otro · Se fue", OrdersStrings.cancelledLine(CancelReason.OTRO, "Se fue"))
        assertEquals("No contestó", OrdersStrings.cancelledLine(CancelReason.NO_CONTESTO, null))
    }

    @Test
    fun titles_and_buttons() {
        assertEquals("Pedido de Lupe", OrdersStrings.orderOf("Lupe"))
        assertEquals("Agregar $31.50", OrdersStrings.addButton(31.5))
        assertEquals("Agregar", OrdersStrings.addButton(null))
        assertEquals("$126.00 / kg", OrdersStrings.perKilo(126.0))
        assertEquals("Tortería", OrdersStrings.kitchenLabel(OrderKitchenKind.TORTERIA))
    }

    @Test
    fun unknown_price_type_shows_qty_and_unit() {
        assertEquals("2 caja", OrdersStrings.qtyLabel(2.0, "CAJA", OrderPriceType.OTHER))
    }

    @Test
    fun cancel_subtitle_and_open_section() {
        assertEquals("Pedido de Lupe · $97.00", OrdersStrings.cancelSubtitle("Lupe", 97.0))
        assertEquals("Pedido de Sin nombre · $0.00", OrdersStrings.cancelSubtitle(null, 0.0))
        assertEquals("Abiertos (3)", OrdersStrings.openSection(3))
        assertEquals("Escribe por qué", OrdersStrings.NOTES_REQUIRED)
    }
}
