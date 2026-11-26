package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tells if the tab rows show. The rows hide when the content scrolls down and show when it scrolls up, or when the
 * content is back at the top. Use [hideOnScroll] on the parent of the scrolling content and [HideOnScrollTabs] around
 * each tab row.
 */
class HideOnScrollState(
    private val hideDistancePx: Float,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    var visible by mutableStateOf(true)
        private set

    /** The distance that the content scrolled down since the rows last showed. */
    private var scrolledDown = 0f

    /** The distance that the finger or the content moved up the list since they last moved down. */
    private var scrolledUp = 0f

    /** The distance that the finger moved back down the list while [scrolledUp] counts. A small one is a shake. */
    private var movedBack = 0f

    /**
     * When the rows last showed or hid during a scroll. The rows change the height of the content while they show or
     * hide, so the content moves under the finger and reads as a scroll the other way: without this, the rows that
     * just showed could hide again at once, and stay hidden. A change holds while the rows move, or until the finger
     * lifts. It does not wait for the lift, because a mouse wheel or a trackpad never lifts.
     */
    private var changedAt: TimeMark? = null

    private val changedInThisDrag: Boolean
        get() = changedAt?.let { it.elapsedNow() < ChangeHold } ?: false

    private fun markChange() {
        changedAt = timeSource.markNow()
    }

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
            if (wasHidden && source == NestedScrollSource.UserInput) markChange()
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
                    if (source == NestedScrollSource.UserInput) markChange()
                }
            }
            // Back at the top, with more scroll up left over, the rows always show.
            if (available.y > 0f && !visible) {
                show()
                if (source == NestedScrollSource.UserInput) markChange()
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
        changedAt = null
    }

    private companion object {
        /** A move back shorter than this part of the hide distance is a shake of the finger. */
        const val ShakeFraction = 3f

        /** A little longer than the rows take to show or hide. */
        val ChangeHold = 500.milliseconds
    }
}

/** The state of the closest [hideOnScroll]. Null means that the tab rows always show. */
val LocalHideOnScroll = compositionLocalOf<HideOnScrollState?> { null }

@Composable
fun rememberHideOnScrollState(): HideOnScrollState {
    val hideDistancePx = with(LocalDensity.current) { 48.dp.toPx() }
    return remember(hideDistancePx) { HideOnScrollState(hideDistancePx) }
}

/**
 * Shows the tab rows of the closest [hideOnScroll] whenever [scrollable] is at its top. Without it, content that is a
 * little taller than the screen can fit once the rows hide: it then cannot scroll up any more, and a mouse wheel or
 * a trackpad sends nothing that could show the rows again.
 */
@Composable
fun ShowTabsAtTop(scrollable: ScrollableState) {
    val state = LocalHideOnScroll.current ?: return
    LaunchedEffect(state, scrollable) {
        snapshotFlow { scrollable.canScrollBackward }.collect { canScrollUp -> if (!canScrollUp) state.show() }
    }
}

/** Lets the scrolling content inside this node control [state]. */
fun Modifier.hideOnScroll(state: HideOnScrollState): Modifier = nestedScroll(state.connection)

/**
 * A screen whose filters and search fields hide like the tab rows: the rows in [HideOnScrollTabs] inside hide when a
 * list inside scrolls down, and show when it scrolls up or is back at its top ([ShowTabsAtTop] on the list).
 */
@Composable
fun HideOnScrollArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberHideOnScrollState()
    CompositionLocalProvider(LocalHideOnScroll provides state) {
        Box(modifier.fillMaxSize().hideOnScroll(state)) { content() }
    }
}

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
