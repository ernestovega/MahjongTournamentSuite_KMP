package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsInputModality

/** On a phone or a tablet Compose already knows the mode: touch, or a hardware keyboard. */
@Composable
internal actual fun InputModalityTracker() {
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    SideEffect { MtsInputModality.keyboard = keyboard }
}
