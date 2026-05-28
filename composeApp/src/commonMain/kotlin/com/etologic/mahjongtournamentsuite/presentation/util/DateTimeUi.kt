package com.etologic.mahjongtournamentsuite.presentation.util

fun String?.toUiIsoDateTimeOrDash(): String {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return "—"

    // Common backend format: 2026-04-17T10:22:33.123Z
    // Show a compact value without milliseconds/timezone when possible.
    val tIndex = raw.indexOf('T')
    if (tIndex > 0 && raw.length >= tIndex + 6) {
        val date = raw.substring(0, tIndex)
        val time = raw.substring(tIndex + 1).take(8)
        if (date.length == 10 && time.length == 8) return "$date $time"
    }

    return raw
}

