package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which input the person used last: the keyboard or a pointer (mouse, finger). Only one mark shows at a time
 * (Ernesto, 27-nov): with the mouse, the hover; with the keyboard, the pink focus ring. Pressing a key turns the
 * keyboard on; moving or pressing the mouse turns it off. The `ui` module listens to the platform and sets it.
 */
object MtsInputModality {
    var keyboard: Boolean by mutableStateOf(false)
}
