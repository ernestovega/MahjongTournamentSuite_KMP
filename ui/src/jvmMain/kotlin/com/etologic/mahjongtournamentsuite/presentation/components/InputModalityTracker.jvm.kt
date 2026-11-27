package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsInputModality
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

private object AwtInputModality {
    private val modifierKeys = setOf(KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_ALT_GRAPH)

    /** Added once for the whole app: every window and every dialog. */
    val installed: Boolean = runCatching {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            { event ->
                when (event.id) {
                    KeyEvent.KEY_PRESSED ->
                        if ((event as KeyEvent).keyCode !in modifierKeys) MtsInputModality.keyboard = true
                    MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_WHEEL ->
                        MtsInputModality.keyboard = false
                }
            },
            AWTEvent.KEY_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK,
        )
    }.isSuccess
}

@Composable
internal actual fun InputModalityTracker() {
    AwtInputModality.installed
}
