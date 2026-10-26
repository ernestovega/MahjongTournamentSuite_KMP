package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.dropdown_show_all
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val showAll = stringResource(Res.string.dropdown_show_all)
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .semantics { contentDescription = showAll }
            .padding(horizontal = 6.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures {
                        currentOnClick()
                        // The text field also sees the tap and asks for the focus after this handler. Take it back.
                        scope.launch {
                            repeat(3) { withFrameNanos { } }
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
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
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnClick() }
    }
}

/** The height of one item of a Material 3 dropdown menu. */
private val MENU_ITEM_HEIGHT = 48.dp

/**
 * The scroll state of a dropdown menu. Each time the menu opens, it scrolls to the item at [selectedIndex].
 * Without a selected item, it scrolls to [defaultIndex]. The default is the start of the menu.
 */
@Composable
fun rememberMenuScrollState(expanded: Boolean, selectedIndex: Int?, defaultIndex: Int = 0): ScrollState {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentDefault by rememberUpdatedState(defaultIndex)
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        val target = currentSelected?.takeIf { it >= 0 } ?: currentDefault
        scroll.scrollTo(with(density) { (MENU_ITEM_HEIGHT * target).roundToPx() })
    }
    return scroll
}
