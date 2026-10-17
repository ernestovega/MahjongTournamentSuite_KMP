package com.etologic.mahjongtournamentsuite.presentation.util

/**
 * Formats an ISO date (yyyy-MM-dd) in the language and style of the device, with the short day of the week
 * when [withWeekday] is true. The year is left out when [withYear] is false. Returns the input when it is not a date.
 */
fun formatLocalizedDate(isoDate: String, withWeekday: Boolean = false, withYear: Boolean = true): String {
    val parts = isoDate.trim().split("-")
    if (parts.size != 3) return isoDate
    val year = parts[0].toIntOrNull() ?: return isoDate
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return isoDate
    val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return isoDate
    return platformFormatDate(year, month, day, withWeekday, withYear)
}

/** Formats a date range such as `12 Oct 2026 – 14 Oct 2026`. A one-day range shows one date. Returns null when there is no date. */
fun formatLocalizedDateRange(start: String?, end: String?): String? =
    listOfNotNull(start, end).map(String::trim).filter { it.isNotEmpty() }.distinct()
        .joinToString(" - ") { formatLocalizedDate(it) }
        .ifBlank { null }

/** Formats a local time (HH:mm) in the style of the device. Returns the input when it is not a time. */
fun formatLocalizedTime(time: String): String {
    val parts = time.trim().split(":")
    if (parts.size < 2) return time
    val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return time
    val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return time
    return platformFormatTime(hour, minute)
}

internal expect fun platformFormatDate(year: Int, month: Int, day: Int, withWeekday: Boolean, withYear: Boolean): String

internal expect fun platformFormatTime(hour: Int, minute: Int): String
