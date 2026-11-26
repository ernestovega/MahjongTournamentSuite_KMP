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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Tells if the tab rows show. The rows hide when the content scrolls down and show when it scrolls up, or when the
 * content is back at the top. Use [hideOnScroll] on the parent of the scrolling content and [HideOnScrollTabs] around
 * each tab row.
 */
class HideOnScrollState(private val hideDistancePx: Float) {
    var visible by mutableStateOf(true)
        private set

    /** The distance that the content scrolled down since the rows last showed. */
    private var scrolledDown = 0f

    /** The distance that the finger or the content moved up the list since they last moved down. */
    private var scrolledUp = 0f

    /** The distance that the finger moved back down the list while [scrolledUp] counts. A small one is a shake. */
    private var movedBack = 0f

    /**
     * True after the rows showed or hid during a drag, until the finger lifts. The rows change the height of the
     * content while they show or hide, so the content moves under the finger and reads as a scroll the other way:
     * without this, the rows that just showed could hide again at once, and stay hidden.
     */
    private var changedInThisDrag = false

    fun show() {
        visible = true
        scrolledDown = 0f
        scrolledUp = 0f
        movedBack = 0f
    }

    private fun hide() {
        visible = false
        scrolledUp = 0f
        movedBack = 0f
    }

    private fun countUp(distance: Float, source: NestedScrollSource) {
        scrolledUp += distance
        movedBack = 0f
        if (scrolledUp > hideDistancePx) {
            val wasHidden = !visible
            show()
            if (wasHidden && source == NestedScrollSource.UserInput) changedInThisDrag = true
        }
    }

    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (changedInThisDrag) return Offset.Zero
            // A drag down shows the rows, also at the top, where the content does not move. A small move back, as the
            // finger stops or lifts at the end of a long drag up, does not: the rows show after the same distance
            // that hides them. A short shake the other way on the way does not start the distance again.
            when {
                available.y > 0f -> countUp(available.y, source)
                available.y < 0f -> {
                    movedBack -= available.y
                    if (movedBack > hideDistancePx / ShakeFraction) scrolledUp = 0f
                }
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (changedInThisDrag) return Offset.Zero
            // Only a content that really moved hides the rows. A short list that does not move keeps them.
            if (consumed.y < 0f && visible) {
                scrolledDown -= consumed.y
                if (scrolledDown > hideDistancePx) {
                    hide()
                    if (source == NestedScrollSource.UserInput) changedInThisDrag = true
                }
            }
            // Back at the top, with more scroll up left over, the rows always show.
            if (available.y > 0f && !visible) {
                show()
                if (source == NestedScrollSource.UserInput) changedInThisDrag = true
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            endDrag()
            return Velocity.Zero
        }
    }

    /** The finger lifted: the next scroll counts again. */
    internal fun endDrag() {
        changedInThisDrag = false
    }

    private companion object {
        /** A move back shorter than this part of the hide distance is a shake of the finger. */
        const val ShakeFraction = 3f
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
