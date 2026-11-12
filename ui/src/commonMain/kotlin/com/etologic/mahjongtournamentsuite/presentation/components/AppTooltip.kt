package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The widest tooltip. A narrower window makes it narrower. */
val TooltipMaxWidth = 320.dp

/** The free space between a tooltip and the edge of the window. */
val TooltipWindowMargin = 16.dp

/**
 * The tooltip of both apps. The standard tooltip text is small (12 sp), so the text here uses the body size (14 sp).
 * The tooltip is never wider or taller than the window.
 * Write a long text as short lines: one sentence per line, and a bullet "• " for each case of a list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipScope.AppPlainTooltip(modifier: Modifier = Modifier, maxWidth: Dp = TooltipMaxWidth, content: @Composable () -> Unit) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidth = with(density) { window.width.toDp() }
    val windowHeight = with(density) { window.height.toDp() }
    val width = if (window.width > 0) minOf(maxWidth, windowWidth - TooltipWindowMargin * 2) else maxWidth
    val height = if (window.height > 0) windowHeight - TooltipWindowMargin * 2 else Dp.Unspecified
    PlainTooltip(modifier.heightIn(max = height), maxWidth = width) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
    }
}

/**
 * Puts the tooltip above the anchor, or under it when there is no room above. The tooltip never leaves the window.
 * It is centered on the anchor, or ends at the end edge of the anchor when [alignEnd] is true.
 * When it fits on neither side, it moves over the anchor, on the side with more room.
 */
class AppTooltipPositionProvider(
    private val spacing: Int,
    private val margin: Int,
    private val alignEnd: Boolean = false,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val wantedX = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.center.x - popupContentSize.width / 2
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val above = anchorBounds.top - popupContentSize.height - spacing
        val below = anchorBounds.bottom + spacing
        val wantedY = when {
            above >= margin -> above
            below <= maxY -> below
            anchorBounds.top >= windowSize.height - anchorBounds.bottom -> above
            else -> below
        }
        return IntOffset(wantedX.coerceIn(margin, maxX), wantedY.coerceIn(margin, maxY))
    }
}

@Composable
fun rememberAppTooltipPositionProvider(alignEnd: Boolean = false): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density, alignEnd) {
        with(density) { AppTooltipPositionProvider(spacing = 4.dp.roundToPx(), margin = TooltipWindowMargin.roundToPx(), alignEnd = alignEnd) }
    }
}

/**
 * The anchor of [AppTooltipBox]. A button of the anchor calls [toggleTooltip] on click, so the keyboard and the mouse
 * can also open the tooltip. A click that ends a touch does nothing: the finger already showed the tooltip, and lifting
 * the finger hides it.
 */
@OptIn(ExperimentalMaterial3Api::class)
class AppTooltipAnchorScope internal constructor(
    private val state: TooltipState,
    private val scope: CoroutineScope,
) {
    internal var lastTouchUp: TimeMark? = null

    fun toggleTooltip() {
        if (lastTouchUp?.let { it.elapsedNow() < TouchClickWindow } == true) return
        if (state.isVisible) state.dismiss() else scope.launch { state.show() }
    }
}

/** A click that comes this soon after a touch ends belongs to that touch. */
private val TouchClickWindow = 500.milliseconds

/**
 * The tooltip box of both apps.
 * - Touch: the tooltip shows as soon as the finger touches the anchor, with no long press, and hides when the finger lifts.
 * - Mouse: the tooltip shows while the pointer is over the anchor.
 * Taps and clicks still reach the anchor, so a button inside still works.
 * When [enabled] is false, the anchor shows no tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltipBox(
    tooltip: @Composable TooltipScope.() -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    enabled: Boolean = true,
    content: @Composable AppTooltipAnchorScope.() -> Unit,
) {
    // Persistent, so a long touch keeps the tooltip until the finger lifts.
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val anchorScope = remember(state, scope) { AppTooltipAnchorScope(state, scope) }
    TooltipBox(
        positionProvider = rememberAppTooltipPositionProvider(alignEnd),
        tooltip = tooltip,
        state = state,
        modifier = if (enabled) modifier.showTooltipWhileTouched(state, scope, anchorScope).hideTooltipOnMouseExit(state) else modifier,
        enableUserInput = enabled,
    ) { anchorScope.content() }
}

/** [AppTooltipBox] with a plain [text]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltipBox(
    text: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    enabled: Boolean = true,
    content: @Composable AppTooltipAnchorScope.() -> Unit,
) = AppTooltipBox(
    tooltip = { AppPlainTooltip { Text(text) } },
    modifier = modifier,
    alignEnd = alignEnd,
    enabled = enabled,
    content = content,
)

/**
 * Shows the tooltip while a finger or a pen touches the anchor. It watches the events before the anchor does and
 * consumes none of them, so the anchor and its parents still get the taps and the scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.showTooltipWhileTouched(
    state: TooltipState,
    scope: CoroutineScope,
    anchorScope: AppTooltipAnchorScope,
): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type == PointerType.Mouse) return@awaitEachGesture
        scope.launch { state.show() }
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            anchorScope.lastTouchUp = TimeSource.Monotonic.markNow()
            state.dismiss()
        }
    }
}

/**
 * Hides the tooltip when the mouse leaves the anchor. The tooltip state is persistent, for the long touches,
 * and the hover of a persistent tooltip does not hide it on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.hideTooltipOnMouseExit(state: TooltipState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Exit && event.changes.any { it.type == PointerType.Mouse }) state.dismiss()
        }
    }
}
