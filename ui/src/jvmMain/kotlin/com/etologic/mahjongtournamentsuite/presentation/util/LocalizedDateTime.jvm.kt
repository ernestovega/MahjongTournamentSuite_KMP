package com.etologic.mahjongtournamentsuite.presentation.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, withWeekday: Boolean, withYear: Boolean, fullNames: Boolean): String {
    val calendar = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
    var format = DateFormat.getDateInstance(if (fullNames) DateFormat.LONG else DateFormat.MEDIUM)
    if (!withYear && !fullNames && format is SimpleDateFormat && "MMM" !in format.toPattern()) {
        // Some languages (German) have a medium date with the month as a number: without the year it reads "16.05",
        // so the month is written short from the long date ("16. Mai").
        val long = DateFormat.getDateInstance(DateFormat.LONG)
        if (long is SimpleDateFormat) format = long.apply { applyPattern(toPattern().replace("MMMM", "MMM")) }
    }
    if (!withYear && format is SimpleDateFormat) {
        // Remove the year, the separator next to it and a word after it (Russian "г.") from the device pattern.
        val pattern = format.toPattern().replace(Regex("[\\s,./-]*('[^']*'[\\s,./-]*)?y+([\\s\\p{Zs}]*'[^']*'\\.?)?[\\s,./-]*(?=[^y]|$)"), " ").trim()
        format.applyPattern(pattern.trim(' ', ',', '.', '/', '-'))
    }
    val date = format.format(calendar.time)
    if (!withWeekday) return date
    return SimpleDateFormat(if (fullNames) "EEEE" else "EEE", Locale.getDefault()).format(calendar.time) + ", " + date
}
