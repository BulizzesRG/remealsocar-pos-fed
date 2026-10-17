package com.posfab.shared.features.reports.sales

import kotlin.math.abs
import kotlin.math.round

object SalesStrings {
    const val TITLE = "Ventas del día"
    const val PREVIOUS_DAY = "Día anterior"
    const val NEXT_DAY = "Día siguiente"
    const val GO_TO_DATE = "Ir a fecha"
    const val GO = "Ir"
    const val REFRESH = "Actualizar"
    const val SOLD = "Vendido"
    const val BY_PAYMENT = "Por forma de pago"
    const val CASH = "Efectivo"
    const val TRANSFER = "Transferencia"
    const val CARD = "Tarjeta"
    const val CREDIT = "Fiado"
    const val BY_UNIT = "Por unidad"
    const val TIENDA = "Tienda"
    const val FONDA = "Fonda"
    const val TORTERIA = "Tortería"
    const val ONSITE = "Aquí"
    const val TAKEAWAY = "Para llevar"
    const val BACK = "Volver"
    const val SUBTOTAL = "Subtotal"
    const val CASH_DISCOUNT = "Descuento en efectivo"
    const val TOTAL = "Total"
    const val COLUMN_PRODUCT = "Producto"
    const val COLUMN_QTY = "Cantidad"
    const val COLUMN_PRICE = "Precio"
    const val COLUMN_AMOUNT = "Importe"
    const val EMPTY_TODAY = "Todavía no hay ventas hoy"
    const val EMPTY_DAY = "No hubo ventas este día"
    const val DATE_INVALID = "Escribe la fecha como dd/mm/aaaa"
    const val DATE_FUTURE = "Ese día todavía no llega"
    const val LOADING = "Cargando…"
    const val NO_VALUE = "—"
    const val TODAY = "Hoy"
    const val YESTERDAY = "Ayer"
    const val SALE_NOT_FOUND = "Esa venta ya no está"
    const val NETWORK = "Sin conexión. Revisa la red y vuelve a intentar"
    const val FORBIDDEN = "Solo Admin y Gerente pueden ver las ventas"
    const val SIGN_IN_AGAIN = "Vuelve a entrar"
    const val LOAD_FAILED = "No se pudieron cargar las ventas"
    const val SEPARATOR = " · "
    const val PIECES = "pz"
    const val KILOS = "kg"
    private const val MINUS = "−"

    val WEEKDAYS: List<String> = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

    val MONTHS: List<String> = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    fun saleCount(count: Int): String = if (count == 1) "1 venta" else "$count ventas"

    fun saleTitle(folio: String): String = "Venta $folio"

    fun creditTo(name: String): String = "Fiado a $name"

    fun paymentLabel(payment: SalePayment): String = when (payment) {
        SalePayment.CASH -> CASH
        SalePayment.TRANSFER -> TRANSFER
        SalePayment.CARD -> CARD
        SalePayment.CREDIT -> CREDIT
    }

    fun paymentText(payment: SalePayment, customerName: String?): String =
        if (payment == SalePayment.CREDIT && !customerName.isNullOrBlank()) creditTo(customerName) else paymentLabel(payment)

    fun channelLabel(channel: SaleChannel): String = when (channel) {
        SaleChannel.TAKEAWAY -> TAKEAWAY
        SaleChannel.ONSITE -> ONSITE
    }

    fun money(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign$$whole.${(absolute % 100).toString().padStart(2, '0')}"
    }

    fun minusMoney(amount: Double): String = if (round(amount * 100.0) > 0.0) "$MINUS${money(amount)}" else money(amount)
}
