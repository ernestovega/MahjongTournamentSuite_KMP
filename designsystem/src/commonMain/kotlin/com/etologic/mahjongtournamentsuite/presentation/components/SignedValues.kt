package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/** A value with its sign: "+12" for a gain, "-12" for a loss and "0". */
fun signedText(value: Long): String = if (value > 0) "+$value" else "$value"

/** A value with its sign: "+12" for a gain, "-12" for a loss and "0". */
fun signedText(value: Int): String = signedText(value.toLong())

/** A value with its sign: "+1.5" for a gain, "-1.5" for a loss and "0.0". */
fun signedText(value: Double): String = if (value > 0) "+$value" else "$value"

/** The color of a gain or a loss: green above zero, red below zero, and [neutral] for zero. */
@Composable
fun signedColor(value: Double, neutral: Color = MaterialTheme.colorScheme.onSurface): Color = when {
    value > 0 -> MtsTheme.colors.gain
    value < 0 -> MaterialTheme.colorScheme.error
    else -> neutral
}

/** The color of a gain or a loss: green above zero, red below zero, and [neutral] for zero. */
@Composable
fun signedColor(value: Long, neutral: Color = MaterialTheme.colorScheme.onSurface): Color = signedColor(value.toDouble(), neutral)

/** The color of a gain or a loss: green above zero, red below zero, and [neutral] for zero. */
@Composable
fun signedColor(value: Int, neutral: Color = MaterialTheme.colorScheme.onSurface): Color = signedColor(value.toDouble(), neutral)
