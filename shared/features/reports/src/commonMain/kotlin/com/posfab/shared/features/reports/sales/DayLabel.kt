package com.posfab.shared.features.reports.sales

import com.posfab.shared.core.model.terminalDisplayName
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private const val HOURS_PER_HALF_DAY = 12
private const val AM = "am"
private const val PM = "pm"
private const val DATE_PARTS = 3
private const val YEAR_DIGITS = 4
private const val MAX_DAY_OR_MONTH_DIGITS = 2
private const val QTY_DECIMALS = 1000.0
private const val MIN_YEAR = 2000

fun dayTitle(date: LocalDate, today: LocalDate): String {
    val short = shortDate(date, today)
    return when (relativeDay(date, today)) {
        RelativeDay.TODAY -> "${SalesStrings.TODAY}, $short"
        RelativeDay.YESTERDAY -> "${SalesStrings.YESTERDAY}, $short"
        RelativeDay.OTHER -> short
    }
}

fun listTimeLabel(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = instant.toLocalDateTime(timeZone)
    val hour = (local.hour % HOURS_PER_HALF_DAY).let { if (it == 0) HOURS_PER_HALF_DAY else it }
    val suffix = if (local.hour < HOURS_PER_HALF_DAY) AM else PM
    return "$hour:${local.minute.toString().padStart(2, '0')} $suffix"
}

fun detailTimeLabel(
    instant: Instant,
    today: LocalDate,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val date = instant.toLocalDateTime(timeZone).date
    val time = listTimeLabel(instant, timeZone)
    return when (relativeDay(date, today)) {
        RelativeDay.TODAY -> "${SalesStrings.TODAY} $time"
        RelativeDay.YESTERDAY -> "${SalesStrings.YESTERDAY} $time"
        RelativeDay.OTHER -> "${shortDate(date, today)}, $time"
    }
}

fun detailSubtitle(
    detail: SaleDetail,
    today: LocalDate,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = listOf(
    detailTimeLabel(detail.soldAt, today, timeZone),
    terminalDisplayName(detail.terminalCode),
    SalesStrings.paymentText(detail.payment, detail.customerName),
    SalesStrings.channelLabel(detail.channel),
).joinToString(SalesStrings.SEPARATOR)

fun qtyLabel(qty: Double, unitCode: String, priceType: SalePriceType): String = when (priceType) {
    SalePriceType.OPEN -> ""
    SalePriceType.WEIGHT -> "${thousandths(qty, trim = false)} ${SalesStrings.KILOS}"
    SalePriceType.PIECE -> "${thousandths(qty, trim = true)} ${pieceUnit(unitCode)}"
    SalePriceType.OTHER -> listOf(thousandths(qty, trim = true), unitCode.trim().lowercase())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

fun parseTypedDate(text: String): LocalDate? {
    val parts = text.trim().split('/')
    if (parts.size != DATE_PARTS) return null
    val (day, month, year) = parts
    if (day.length !in 1..MAX_DAY_OR_MONTH_DIGITS || month.length !in 1..MAX_DAY_OR_MONTH_DIGITS) return null
    if (year.length != YEAR_DIGITS || parts.any { part -> part.any { it !in '0'..'9' } }) return null
    if (year.toInt() < MIN_YEAR) return null
    return try {
        LocalDate(year.toInt(), month.toInt(), day.toInt())
    } catch (invalid: IllegalArgumentException) {
        null
    }
}

private enum class RelativeDay { TODAY, YESTERDAY, OTHER }

private fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when (date) {
    today -> RelativeDay.TODAY
    today.minus(1, DateTimeUnit.DAY) -> RelativeDay.YESTERDAY
    else -> RelativeDay.OTHER
}

private fun shortDate(date: LocalDate, today: LocalDate): String {
    val weekday = SalesStrings.WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]
    val month = SalesStrings.MONTHS[date.month.ordinal]
    val base = "$weekday ${date.day} $month"
    return if (date.year == today.year) base else "$base ${date.year}"
}

private fun pieceUnit(unitCode: String): String = when (val code = unitCode.trim().uppercase()) {
    "", "PZA", "PZ" -> SalesStrings.PIECES
    else -> code.lowercase()
}

private fun thousandths(qty: Double, trim: Boolean): String {
    val scaled = round(qty * QTY_DECIMALS).toLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = abs(scaled)
    val whole = absolute / QTY_DECIMALS.toLong()
    val fraction = (absolute % QTY_DECIMALS.toLong()).toString().padStart(3, '0')
    val shown = if (trim) fraction.trimEnd('0') else fraction
    return if (shown.isEmpty()) "$sign$whole" else "$sign$whole.$shown"
}
