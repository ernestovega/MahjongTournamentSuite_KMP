package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/**
 * The arrow of a dropdown field that the user can also type in.
 * A tap on the arrow only opens or closes the full menu.
 * It does not give the focus to the text, so the keyboard stays closed and the text is not selected.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DropdownArrow(expanded: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        currentOnClick()
                    }
                }
            },
    ) {
        ExposedDropdownMenuDefaults.TrailingIcon(expanded)
    }
}

/** Calls [onClick] each time the user taps the text area of a field. A tap on the arrow does not count. */
@Composable
fun OnFieldTextClick(interactionSource: InteractionSource, onClick: () -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnClick() }
    }
}
