package com.etologic.mahjongtournamentsuite.presentation.util

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, withWeekday: Boolean, withYear: Boolean): String =
    when {
        withWeekday && withYear -> browserDateWithWeekday(year, month, day)
        withWeekday -> browserDateWithWeekdayNoYear(year, month, day)
        withYear -> browserDate(year, month, day)
        else -> browserDateNoYear(year, month, day)
    }

internal actual fun platformFormatTime(hour: Int, minute: Int): String = browserTime(hour, minute)

private fun browserDate(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(undefined, { dateStyle: 'medium' })")

private fun browserDateNoYear(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })")

private fun browserDateWithWeekdayNoYear(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })")

private fun browserDateWithWeekday(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })")

private fun browserTime(hour: Int, minute: Int): String =
    js("new Date(2000, 0, 1, hour, minute).toLocaleTimeString(undefined, { timeStyle: 'short' })")
