package com.etologic.mahjongtournamentsuite.presentation.sync

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Marks the user as active on every pointer, scroll or key event inside this node. It never consumes an event. */
fun Modifier.trackActivity(activity: ActivityTracker): Modifier = this
    .pointerInput(activity) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                activity.markActive()
            }
        }
    }
    .onPreviewKeyEvent {
        activity.markActive()
        false
    }
