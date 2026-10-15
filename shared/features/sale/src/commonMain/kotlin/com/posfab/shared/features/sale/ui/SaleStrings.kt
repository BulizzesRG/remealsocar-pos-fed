package com.posfab.shared.features.sale.ui

import kotlin.math.abs
import kotlin.math.round

internal object SaleStrings {
    const val LOADING = "Cargando venta…"
    const val CASH_CLOSED_TITLE = "La caja está cerrada"
    const val CASH_CLOSED_BODY = "Abre el día para empezar a vender"
    const val OPEN_DAY = "Abrir día"
    const val RETRY = "Reintentar"
    const val CART_TITLE = "Venta"
    const val EMPTY_CART = "Escanea o busca un producto"
    const val TOTAL = "Total"
    const val CLEAR = "Vaciar"
    const val REMOVE = "Quitar"
    const val QTY_LABEL = "Cantidad"
    const val DECREASE = "Quitar uno"
    const val INCREASE = "Agregar uno"
    const val SEARCH_LABEL = "Escanea o busca"
    const val KITCHEN_FONDA = "Fonda"
    const val KITCHEN_TORTERIA = "Tortería"
    const val CHARGE_QUESTION = "¿Con cuánto paga?"
    const val EXACT = "Exacto"
    const val PAID_LABEL = "Paga con"
    const val CHANGE = "Cambio"
    const val DONE_CONFIRM = "Listo"
    const val BACK = "Volver"
    const val CHARGED = "Cobrado"
    const val EXACT_PAYMENT = "Pago exacto"
    const val NEXT = "Siguiente"
    const val CLEAR_TITLE = "¿Vaciar la venta?"
    const val CLEAR_MESSAGE = "Se quitarán todos los productos"
    const val CLEAR_CONFIRM = "Vaciar"
    const val CLEAR_CANCEL = "Seguir"
    const val NOT_SELLABLE_YET = "Este producto todavía no se puede vender desde aquí"
    const val KITCHEN_UNAVAILABLE = "No se pudo cargar el tablero de cocina"
    const val SALE_CHANGED = "La venta cambió, se volvió a cargar"
    const val CASH_SESSION_REQUIRED = "La caja está cerrada. Ábrela para cobrar"
    const val NO_PRICE = "Este producto no tiene precio"
    const val NOTHING_TO_CHARGE = "No hay nada que cobrar"
    const val INSUFFICIENT_STOCK = "No hay existencia suficiente"
    const val NO_CONNECTION = "Sin conexión. Revisa la red y vuelve a intentar"
    const val SESSION_EXPIRED = "Tu sesión terminó. Vuelve a entrar"
    const val NOT_ALLOWED = "No tienes permiso para hacer esto"
    const val TOO_MANY_REQUESTS = "Demasiados intentos. Espera unos segundos"
    const val FINISH_CHARGE_FIRST = "Termina el cobro primero"
    const val CHECKOUT_CONFLICT = "No se pudo cobrar. La venta se volvió a cargar, revísala y cobra de nuevo"
    const val POSSIBLY_CHARGED = "Es posible que la venta ya se haya cobrado. Revisa Ventas del día"
    const val RETRY_WITH_DONE = "Vuelve a intentar con Listo"
    val QUICK_BILLS: List<Int> = listOf(20, 50, 100, 200, 500)

    fun notFoundWithCode(code: String): String = "No hay ningún producto con el código $code"

    fun chargeLabel(total: Double): String = "Cobrar ${money(total)}"

    fun added(name: String): String = "Agregado: $name"

    fun notAdded(name: String): String = "No se agregó $name, vuelve a escanear"

    fun missing(amount: Double): String = "Falta ${money(amount)}"

    fun quickBill(amount: Int): String = "$$amount"

    fun qtyTimes(qty: Double): String = "×${qtyText(qty)}"

    fun folio(folio: String): String = "Folio $folio"

    fun couldNotComplete(message: String): String = "No se pudo completar: $message"

    fun money(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign$$whole.${(absolute % 100).toString().padStart(2, '0')}"
    }

    fun qtyText(qty: Double): String {
        val whole = round(qty)
        return if (abs(qty - whole) < 1e-9) whole.toLong().toString() else qty.toString()
    }
}
