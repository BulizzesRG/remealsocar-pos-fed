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
    const val LOAD_LIBRETA = "Cargar libreta"
    const val LIBRETA_HELP =
        "Anota a un cliente a la vez, como en tu cuaderno. Aparecerá como «Saldo de la libreta», no como una venta."
    const val NAME_LABEL = "Nombre"
    const val NICKNAME_LABEL = "Apodo (opcional)"
    const val PHONE_LABEL = "Teléfono (opcional)"
    const val OWED_FIELD_LABEL = "Debe"
    const val SINCE_LABEL = "Desde cuándo (opcional)"
    const val SINCE_EXAMPLE = "ej. 03/10/2025"
    const val SAVE_NEXT = "Guardar y siguiente"
    const val FINISH = "Terminar"
    const val CHANGE_CUSTOMER = "Otro cliente"
    const val EXISTING_CUSTOMER_HINT = "Cliente que ya existe. Escribe lo que debe."
    const val SUGGESTIONS_HINT = "¿Ya está en la lista? Tócalo para cargarle deuda"
    const val CORRECT = "Corregir"
    const val OPENING_BALANCE = "Saldo de la libreta"
    const val NO_DEBT = "Sin deuda"
    const val PESO_SIGN = "$"
    const val NAME_REQUIRED = "Escribe el nombre del cliente"
    const val AMOUNT_REQUIRED = "Escribe cuánto debe"
    const val INPUT_REJECTED = "El valor no es válido, vuelve a escribirlo"
    const val PHONE_INVALID = "El teléfono debe tener 10 números"
    const val DATE_INVALID = "La fecha no es válida, usa día/mes/año"
    const val DATE_FUTURE = "La fecha no puede ser futura"
    const val HAS_MOVEMENTS =
        "Este cliente ya tiene compras o abonos en el sistema. Su deuda cambia desde Vender, con Fiar o Abonar."
    const val NICKNAME_TOO_LONG = "El apodo es muy largo"
    const val NO_PERMISSION = "Tu cuenta no puede cargar la libreta. Sal y vuelve a entrar, o pide al administrador"
    const val INACTIVE_CUSTOMER = "Este cliente está dado de baja"
    const val NOT_FOUND = "No se encontró el cliente"
    const val SAVE_FAILED = "No se pudo guardar, intenta de nuevo"
    const val NETWORK = "No hay conexión con el servidor"

    val MONTHS: List<String> = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic",
    )

    fun customerNumber(code: String): String = "Número $code"

    fun loadedToday(count: Int): String = "Cargados hoy ($count)"

    fun savedNotice(name: String, amount: Double): String = "Guardado: $name$SEPARATOR${money(amount)}"

    fun owes(balance: Double): String = "debe ${money(balance)}"

    fun withPhone(name: String, phone: String?): String = listOfNotNull(name, phone).joinToString(SEPARATOR)

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
