package com.etologic.mahjongtournamentsuite.presentation.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, withWeekday: Boolean, withYear: Boolean): String {
    val calendar = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
    val format = DateFormat.getDateInstance(DateFormat.MEDIUM)
    if (!withYear && format is SimpleDateFormat) {
        // Remove the year and the separator next to it from the device pattern.
        val pattern = format.toPattern().replace(Regex("[\\s,./-]*y+[\\s,./-]*(?=[^y]|$)"), " ").trim()
        format.applyPattern(pattern.trim(' ', ',', '.', '/', '-'))
    }
    val date = format.format(calendar.time)
    if (!withWeekday) return date
    return SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time) + ", " + date
}

internal actual fun platformFormatTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply { clear(); set(2000, 0, 1, hour, minute) }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
}
