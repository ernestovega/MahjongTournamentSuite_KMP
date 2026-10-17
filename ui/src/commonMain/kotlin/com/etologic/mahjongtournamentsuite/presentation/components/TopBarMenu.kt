package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Arrangement

/**
 * Set to true by the admin app. [AppScaffold] then keeps the title between the buttons on wide screens.
 * On narrow screens it puts the buttons left of the title in one three-dots menu next to the back
 * button, and the buttons right of the title in one three-dots menu at the top right.
 * The default is false: the bar keeps one row and the screen owns its own menu.
 */
val LocalAdaptiveTopBar = staticCompositionLocalOf { false }

/** The width in dp under which the adaptive top bar uses the three-dots menu. */
internal const val TopBarCompactWidthDp = 900

/** Set while the top bar buttons are drawn inside the three-dots menu. [dismiss] closes the menu. */
class TopBarMenuScope(val dismiss: () -> Unit)

val LocalTopBarMenu = staticCompositionLocalOf<TopBarMenuScope?> { null }

/** Lays out top bar buttons in a row, or in a column when they are inside the three-dots menu. */
@Composable
fun TopBarButtonsLayout(content: @Composable () -> Unit) {
    if (LocalTopBarMenu.current != null) {
        Column { content() }
    } else {
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) { content() }
    }
}

/**
 * Asks for focus and ignores the error when the control is not on screen.
 * A top bar button is not on screen while the three-dots menu is closed.
 */
fun FocusRequester.requestFocusIfAttached() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
    }
}
