package com.posfab.shared.features.shell

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toLocalDateTime

fun openedAtTimeLabel(openedAt: String): String? = runCatching {
    val instant = DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(openedAt).toInstantUsingOffset()
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    "${local.hour}:${local.minute.toString().padStart(2, '0')}"
}.getOrNull()

internal fun moneyLabel(amount: Double): String {
    val cents = (amount * 100.0).roundToLong()
    val whole = abs(cents) / 100
    val fraction = (abs(cents) % 100).toString().padStart(2, '0')
    val sign = if (cents < 0) "-" else ""
    return "$sign$$whole.$fraction"
}

internal fun openCashLabel(status: HomeCashStatus.Open): String = buildString {
    append(ShellStrings.CASH_OPEN_PREFIX)
    openedAtTimeLabel(status.openedAt)?.let { append(ShellStrings.CASH_SINCE).append(it) }
    append(ShellStrings.CASH_FLOAT).append(moneyLabel(status.openingAmount))
}
