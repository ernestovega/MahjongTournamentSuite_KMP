package com.etologic.mahjongtournamentsuite.presentation.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A backend timestamp (ISO-8601, UTC) as local date and time in the device language, or null when it is not a timestamp. */
fun localizedDateTimeOrNull(isoTimestamp: String?, zone: TimeZone = TimeZone.currentSystemDefault()): String? {
    val raw = isoTimestamp?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val local = runCatching { Instant.parse(raw).toLocalDateTime(zone) }.getOrNull() ?: return raw
    val date = "${local.year}-${local.month.ordinal + 1}-${local.day}"
    val time = "${local.hour}:${local.minute}"
    return "${formatLocalizedDate(date)} ${formatLocalizedTime(time)}"
}

fun String?.toUiIsoDateTimeOrDash(): String = localizedDateTimeOrNull(this) ?: "—"
