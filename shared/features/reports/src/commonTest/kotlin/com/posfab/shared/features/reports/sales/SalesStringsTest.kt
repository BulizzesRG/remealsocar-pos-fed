package com.posfab.shared.features.reports.sales

import kotlin.test.Test
import kotlin.test.assertEquals

class SalesStringsTest {
    @Test
    fun money_has_thousands_separator_and_two_decimals() {
        assertEquals("$0.00", SalesStrings.money(0.0))
        assertEquals("$17.50", SalesStrings.money(17.5))
        assertEquals("$1,234.00", SalesStrings.money(1234.0))
        assertEquals("$1,234,567.89", SalesStrings.money(1234567.891))
        assertEquals("-$5.00", SalesStrings.money(-5.0))
    }

    @Test
    fun minus_money_only_for_positive_amounts() {
        assertEquals("−$6.30", SalesStrings.minusMoney(6.3))
        assertEquals("$0.00", SalesStrings.minusMoney(0.0))
    }

    @Test
    fun sale_count_is_singular_for_one() {
        assertEquals("0 ventas", SalesStrings.saleCount(0))
        assertEquals("1 venta", SalesStrings.saleCount(1))
        assertEquals("12 ventas", SalesStrings.saleCount(12))
    }

    @Test
    fun payment_text_names_the_credit_customer() {
        assertEquals("Efectivo", SalesStrings.paymentText(SalePayment.CASH, null))
        assertEquals("Fiado", SalesStrings.paymentText(SalePayment.CREDIT, null))
        assertEquals("Fiado a Juan Pérez", SalesStrings.paymentText(SalePayment.CREDIT, "Juan Pérez"))
        assertEquals("Tarjeta", SalesStrings.paymentText(SalePayment.CARD, "Juan Pérez"))
        assertEquals("Venta S-000123", SalesStrings.saleTitle("S-000123"))
    }
}
