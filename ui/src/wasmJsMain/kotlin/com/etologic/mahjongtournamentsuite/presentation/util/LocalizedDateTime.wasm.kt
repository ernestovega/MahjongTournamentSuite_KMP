package com.etologic.mahjongtournamentsuite.presentation.util

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, withWeekday: Boolean, withYear: Boolean): String =
    when {
        withWeekday && withYear -> browserDateWithWeekday(year, month, day)
        withWeekday -> browserDateWithWeekdayNoYear(year, month, day)
        withYear -> browserDate(year, month, day)
        else -> browserDateNoYear(year, month, day)
    }

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun browserDate(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(navigator.language, { dateStyle: 'medium' })")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun browserDateNoYear(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(navigator.language, { day: 'numeric', month: 'short' })")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun browserDateWithWeekdayNoYear(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(navigator.language, { weekday: 'short', day: 'numeric', month: 'short' })")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun browserDateWithWeekday(year: Int, month: Int, day: Int): String =
    js("new Date(year, month - 1, day).toLocaleDateString(navigator.language, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })")
