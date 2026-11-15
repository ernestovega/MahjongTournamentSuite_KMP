package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long a scrollbar stays after the list stops and the pointer leaves it. */
internal const val SCROLLBAR_HIDE_DELAY_MILLIS = 1_000L

/**
 * Whether a scrollbar shows: only while its list scrolls, or while [held] (dragged or under the mouse), and a moment
 * after. A list at rest shows no scrollbar.
 */
@Composable
internal fun rememberScrollbarShown(scrolling: Boolean, held: Boolean = false): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, held) {
        if (scrolling || held) {
            shown = true
        } else if (shown) {
            delay(SCROLLBAR_HIDE_DELAY_MILLIS)
            shown = false
        }
    }
    return shown
}

/** The opacity of a scrollbar that fades in and out with [shown]. */
@Composable
internal fun scrollbarAlpha(shown: Boolean): State<Float> = animateFloatAsState(if (shown) 1f else 0f)
