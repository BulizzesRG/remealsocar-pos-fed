package com.posfab.shared.features.shopping.ui

import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

private val WEEKDAYS: List<String> = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

private val MONTHS: List<String> = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

fun listTitle(createdAt: Instant, timeZone: TimeZone, today: LocalDate): String =
    "${ShoppingStrings.LIST_TITLE_PREFIX} ${shortListDate(createdAt, timeZone, today)}"

fun shortListDate(instant: Instant, timeZone: TimeZone, today: LocalDate): String {
    val date = instant.toLocalDateTime(timeZone).date
    val base = "${WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]} ${date.day} ${MONTHS[date.month.ordinal]}"
    return if (date.year == today.year) base else "$base ${date.year}"
}

fun historyRowLabel(summary: ShoppingListSummary, timeZone: TimeZone, today: LocalDate): String =
    "${shortListDate(summary.createdAt, timeZone, today)} · ${ShoppingStrings.progress(summary.boughtCount, summary.itemCount)}"
