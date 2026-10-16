package com.posfab.shared.features.credit.ui

import kotlin.math.abs
import kotlin.math.round

object CreditStrings {
    const val SEARCH_LABEL = "Buscar cliente"
    const val REFRESH = "Actualizar"
    const val BACK = "Volver"
    const val EMPTY = "Nadie te debe"
    const val NO_MATCHES = "Ningún cliente con ese nombre"
    const val OWED_LABEL = "Te deben"
    const val HOW_TO_PAY = "Para registrar un abono, ve a Vender y toca Abonar"
    const val PURCHASE = "Compra"
    const val PAYMENT = "Abono"
    const val BALANCE = "Debe"
    const val NO_MOVEMENTS = "Sin movimientos"
    const val INACTIVE = "dado de baja"
    const val SINCE_TODAY = "desde hoy"
    const val SINCE_YESTERDAY = "desde ayer"
    const val SEPARATOR = " · "

    val MONTHS: List<String> = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic",
    )

    fun sinceDays(days: Int): String = "desde hace $days días"

    fun owedSummary(total: Double, count: Int): String =
        "$OWED_LABEL ${money(total)}$SEPARATOR${clientCount(count)}"

    fun clientCount(count: Int): String = "$count ${if (count == 1) "cliente" else "clientes"}"

    fun entryTitle(label: String, folio: String?): String =
        listOfNotNull(label, folio).joinToString(SEPARATOR)

    fun paymentAmount(amount: Double): String = "\u2212${money(amount)}"

    fun money(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign$$whole.${(absolute % 100).toString().padStart(2, '0')}"
    }
}
