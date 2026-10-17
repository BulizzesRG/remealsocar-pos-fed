package com.posfab.shared.features.catalog.common

import com.posfab.shared.features.catalog.domain.PriceHistoryEntry
import com.posfab.shared.features.catalog.ui.CatalogStrings
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toLocalDateTime

private const val SEPARATOR = " · "
private const val NOON = 12

fun priceHistoryDateLabel(iso: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): String? {
    val local = parseInstant(iso)?.toLocalDateTime(timeZone) ?: return null
    val month = CatalogStrings.MONTHS[local.month.ordinal]
    val hour = local.hour % NOON
    val shownHour = if (hour == 0) NOON else hour
    val minute = local.minute.toString().padStart(2, '0')
    val period = if (local.hour < NOON) CatalogStrings.AM else CatalogStrings.PM
    return "${local.day} $month, $shownHour:$minute $period"
}

fun priceHistoryLine(entry: PriceHistoryEntry, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val prices = entry.prices
    val until = priceHistoryDateLabel(entry.validUntil, timeZone) ?: CatalogStrings.UNKNOWN_DATE
    val takeaway = if (prices.suggestedPrice) {
        "${moneyLabel(prices.cashTakeaway)} (${CatalogStrings.HISTORY_SUGGESTED})"
    } else {
        "${moneyLabel(prices.cashTakeaway)} (${CatalogStrings.HISTORY_WITH_CARD} ${moneyLabel(prices.listTakeaway)})"
    }
    val onsite = prices.cashOnsite?.let { cash ->
        val list = prices.listOnsite.takeUnless { prices.suggestedPrice }
        val listPart = list?.let { " (${moneyLabel(it)})" }.orEmpty()
        "${CatalogStrings.HISTORY_ONSITE} ${moneyLabel(cash)}$listPart"
    }
    return listOfNotNull(
        "${CatalogStrings.HISTORY_UNTIL} $until",
        takeaway,
        onsite,
        "${CatalogStrings.HISTORY_CHANGED_BY} ${entry.changedBy}",
    ).joinToString(SEPARATOR)
}

fun moneyLabel(value: Double): String {
    val cents = (abs(value) * 100).roundToLong()
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val fraction = (cents % 100).toString().padStart(2, '0')
    val sign = if (value < 0 && cents != 0L) "-" else ""
    return "$sign$$whole.$fraction"
}

private fun parseInstant(value: String): Instant? = runCatching {
    DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(value).toInstantUsingOffset()
}.getOrNull()
