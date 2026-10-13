package com.etologic.mahjongtournamentsuite.presentation.util

import kotlin.math.floor
import kotlin.math.round

/** Points without a decimal part when the value is whole: `12.0` is `12`, `12.5` stays `12.5`. */
fun formatPoints(value: Double): String {
    val rounded = round(value * 100) / 100
    return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

/** The same for a text that holds a number. Text that is not a number stays as it is. */
fun formatPoints(text: String): String = text.trim().toDoubleOrNull()?.let(::formatPoints) ?: text
