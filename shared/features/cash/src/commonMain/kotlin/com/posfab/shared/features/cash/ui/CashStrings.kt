package com.posfab.shared.features.cash.ui

import com.posfab.shared.features.cash.domain.CashPayout
import com.posfab.shared.features.cash.domain.DrawerDifference
import com.posfab.shared.features.cash.domain.PayoutReason
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object CashStrings {
    const val LOADING = "Consultando la caja"
    const val CLOSED_TITLE = "La caja está cerrada"
    const val OPENING_LABEL = "¿Con cuánto empiezas?"
    const val FIRST_TIME = "Primera vez que abres la caja"
    const val OPEN_DAY = "Abrir día"
    const val EXPECTED_HEADLINE = "Debe haber en el cajón"
    const val STARTED_WITH = "Empezaste con"
    const val CASH_SALES = "Ventas en efectivo"
    const val CREDIT_PAYMENTS = "Abonos de clientes"
    const val PAYOUTS = "Retiros"
    const val OTHER_OUT = "Compras en efectivo"
    const val NOT_IN_DRAWER = "No entran al cajón"
    const val TRANSFERS = "Ventas por transferencia"
    const val CARD = "Ventas con tarjeta"
    const val CREDIT = "Fiado de hoy"
    const val PAYOUTS_TODAY = "Retiros de hoy"
    const val TAKE_MONEY = "Sacar dinero"
    const val CLOSE_DAY = "Cerrar día"
    const val REFRESH = "Actualizar"
    const val REFRESHING = "Actualizando…"
    const val PESO_SIGN = "$"
    const val NO_MANAGE = "Para sacar dinero o cerrar el día, entra con la cuenta del gerente"
    const val PAYOUT_AMOUNT_LABEL = "¿Cuánto sacas?"
    const val PAYOUT_REASON_LABEL = "¿Para qué?"
    const val PAYOUT_NOTES_LABEL = "¿En qué?"
    const val PAYOUT_NOTES_REQUIRED = "Escribe en qué se usa el dinero"
    const val PAYOUT_CONFIRM = "Sacar"
    const val PAYOUT_RETRY = "Retiro en curso: vuelve a tocar Sacar para reintentar"
    const val NOT_ENOUGH = "No hay tanto en el cajón"
    const val CANCEL = "Cancelar"
    const val COUNTED_LABEL = "¿Cuánto hay en el cajón?"
    const val CUADRA = "Cuadra"
    const val COUNT_PLACEHOLDER = "Escribe lo que contaste"
    const val DAY_CLOSED = "Día cerrado"
    const val EXPECTED = "Debía haber"
    const val COUNTED = "Contaste"
    const val DONE = "Listo"
    const val UNAVAILABLE_TITLE = "No se pudo consultar la caja"
    const val RETRY = "Reintentar"
    const val INPUT_REJECTED = "El valor no es válido, vuelve a escribirlo"
    const val ALREADY_OPEN = "La caja ya estaba abierta"
    const val CLOSED_NOTICE = "La caja está cerrada"
    const val OPEN_ACCOUNTS_BLOCK = "Tienes cuentas abiertas en Vender. Cóbralas o vacíalas antes de cerrar"
    const val FORBIDDEN = "Solo Admin y Gerente pueden hacer esto"
    const val UNAUTHORIZED = "Tu sesión terminó, vuelve a entrar"
    const val NETWORK = "Sin conexión. Revisa la red e intenta de nuevo"
    const val RATE_LIMIT = "Demasiados intentos. Espera unos segundos"
    const val CONFLICT = "La caja cambió. Toca Actualizar"
    const val FAILED_PREFIX = "No se pudo completar: "
    const val SEPARATOR = " · "

    private const val MINUS = "−"
    private const val AM = "am"
    private const val PM = "pm"
    private const val HOURS_PER_HALF_DAY = 12

    fun money(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign$$whole.${(absolute % 100).toString().padStart(2, '0')}"
    }

    fun minusMoney(amount: Double): String = if (round(amount * 100.0) > 0.0) "$MINUS${money(amount)}" else money(amount)

    fun closeButton(amount: Double?): String = amount?.let { "$CLOSE_DAY con ${money(it)}" } ?: CLOSE_DAY

    fun lastClosing(amount: Double): String = "La última vez cerraste con ${money(amount)}"

    fun timeLabel(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val local = instant.toLocalDateTime(timeZone)
        val hour = (local.hour % HOURS_PER_HALF_DAY).let { if (it == 0) HOURS_PER_HALF_DAY else it }
        val suffix = if (local.hour < HOURS_PER_HALF_DAY) AM else PM
        return "$hour:${local.minute.toString().padStart(2, '0')} $suffix"
    }

    fun openSince(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
        "Abierta desde ${timeLabel(instant, timeZone)}"

    fun reasonLabel(reason: PayoutReason): String = when (reason) {
        PayoutReason.SURTIDO -> "Surtido"
        PayoutReason.PERSONAL -> "Personal"
        PayoutReason.OTRO -> "Otro"
    }

    fun payoutLine(payout: CashPayout, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
        listOf(timeLabel(payout.createdAt, timeZone), reasonLabel(payout.reason), minusMoney(payout.amount))
            .joinToString(SEPARATOR)

    fun payoutButton(amount: Double?): String =
        amount?.takeIf { it > 0.0 }?.let { "$PAYOUT_CONFIRM ${money(it)}" } ?: PAYOUT_CONFIRM

    fun payoutSaved(reason: PayoutReason, amount: Double): String =
        "Retiro guardado: ${reasonLabel(reason)}$SEPARATOR${money(amount)}"

    fun openAccounts(count: Int): String =
        if (count == 1) "Tienes 1 cuenta abierta en Vender" else "Tienes $count cuentas abiertas en Vender"

    fun closeBlocked(count: Int): String = "${openAccounts(count)}. Cóbralas o vacíalas antes de cerrar"

    fun difference(difference: DrawerDifference): String = when (difference) {
        DrawerDifference.Cuadra -> CUADRA
        is DrawerDifference.Sobran -> "Sobran ${money(difference.amount)}"
        is DrawerDifference.Faltan -> "Faltan ${money(difference.amount)}"
    }
}
