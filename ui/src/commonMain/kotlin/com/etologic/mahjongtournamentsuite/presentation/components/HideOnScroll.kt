package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Tells if the tab rows show. The rows hide when the content scrolls down and show when it scrolls up.
 * Use [hideOnScroll] on the parent of the scrolling content and [HideOnScrollTabs] around each tab row.
 */
class HideOnScrollState(private val hideDistancePx: Float) {
    var visible by mutableStateOf(true)
        private set

    /** The distance that the content scrolled down since the last change of direction. */
    private var scrolledDown = 0f

    fun show() {
        visible = true
        scrolledDown = 0f
    }

    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // A drag down shows the rows, also at the top, where the content does not move.
            if (available.y > 0f) show()
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Only a content that really moved hides the rows. A short list that does not move keeps them.
            if (consumed.y < 0f) {
                scrolledDown -= consumed.y
                if (scrolledDown > hideDistancePx) visible = false
            }
            return Offset.Zero
        }
    }
}

/** The state of the closest [hideOnScroll]. Null means that the tab rows always show. */
val LocalHideOnScroll = compositionLocalOf<HideOnScrollState?> { null }

@Composable
fun rememberHideOnScrollState(): HideOnScrollState {
    val hideDistancePx = with(LocalDensity.current) { 48.dp.toPx() }
    return remember(hideDistancePx) { HideOnScrollState(hideDistancePx) }
}

/** Lets the scrolling content inside this node control [state]. */
fun Modifier.hideOnScroll(state: HideOnScrollState): Modifier = nestedScroll(state.connection)

/**
 * Shows [content] while the closest [hideOnScroll] state is visible.
 * The rows show again when [resetKey] changes, for example when the user chooses another tab.
 */
@Composable
fun HideOnScrollTabs(resetKey: Any? = null, content: @Composable () -> Unit) {
    val state = LocalHideOnScroll.current
    LaunchedEffect(resetKey) { state?.show() }
    AnimatedVisibility(
        visible = state?.visible ?: true,
        enter = expandVertically(),
        exit = shrinkVertically(),
        content = { content() },
    )
}
