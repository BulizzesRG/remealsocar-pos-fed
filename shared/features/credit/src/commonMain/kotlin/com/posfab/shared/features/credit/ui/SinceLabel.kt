package com.posfab.shared.features.credit.ui

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toLocalDateTime

fun sinceLabel(
    owesSince: String?,
    nowEpochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    val since = owesSince?.let(::parseInstant) ?: return null
    val sinceDate = since.toLocalDateTime(timeZone).date
    val today = Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(timeZone).date
    return when (val days = sinceDate.daysUntil(today)) {
        in Int.MIN_VALUE..0 -> CreditStrings.SINCE_TODAY
        1 -> CreditStrings.SINCE_YESTERDAY
        else -> CreditStrings.sinceDays(days)
    }
}

fun entryDateLabel(
    createdAt: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    withTime: Boolean = true,
): String? {
    val local = parseInstant(createdAt)?.toLocalDateTime(timeZone) ?: return null
    val month = CreditStrings.MONTHS[local.month.ordinal]
    if (!withTime) return "${local.day} $month ${local.year}"
    return "${local.day} $month, ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}

private fun parseInstant(value: String): Instant? = runCatching {
    DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(value).toInstantUsingOffset()
}.getOrNull()
