package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** The test tag of the thumb of a [DraggableScrollbar]. */
const val DRAGGABLE_SCROLLBAR_TAG = "draggable-scrollbar"

/**
 * Where a list is, from 0 (top) to 1 (end), and how much of it shows, from the indexes of the visible items.
 * The items can have different heights, so the values are an estimate. Null when the whole list fits.
 */
internal data class ScrollbarMetrics(val position: Float, val visibleFraction: Float)

internal fun scrollbarMetricsOf(
    totalItems: Int,
    visible: List<LazyListItemInfo>,
    viewportStart: Int,
    viewportEnd: Int,
    firstIndex: Int,
    firstScrollOffset: Int,
    canScroll: Boolean,
): ScrollbarMetrics? {
    if (!canScroll || totalItems <= 0 || visible.isEmpty()) return null
    // The part of each visible item that is on screen.
    val shown = visible.sumOf { item ->
        val top = maxOf(item.offset, viewportStart)
        val bottom = minOf(item.offset + item.size, viewportEnd)
        ((bottom - top).coerceAtLeast(0).toFloat() / item.size.coerceAtLeast(1)).toDouble()
    }.toFloat().coerceIn(0.01f, totalItems.toFloat())
    // The first item comes from the state, not from the visible items, so a sticky header does not count as first.
    val firstSize = visible.firstOrNull { it.index == firstIndex }?.size ?: 1
    val hidden = (firstScrollOffset.toFloat() / firstSize.coerceAtLeast(1)).coerceIn(0f, 1f)
    val scrollable = (totalItems - shown).coerceAtLeast(0.0001f)
    return ScrollbarMetrics(
        position = ((firstIndex + hidden) / scrollable).coerceIn(0f, 1f),
        visibleFraction = (shown / totalItems).coerceIn(0f, 1f),
    )
}

/**
 * The index to scroll to so the list is at [fraction] (0 is the top, 1 is the end).
 */
internal fun scrollbarTargetIndex(fraction: Float, totalItems: Int, visibleItems: Float): Int {
    if (totalItems <= 0) return 0
    val scrollable = (totalItems - visibleItems).coerceAtLeast(0f)
    return (fraction.coerceIn(0f, 1f) * scrollable).roundToInt().coerceIn(0, totalItems - 1)
}

/**
 * A scrollbar that always shows while the list can scroll, on every platform. The thumb is big enough for a finger:
 * a drag on it, or anywhere in its strip, moves the list fast. A tap in the strip jumps there.
 * While the person drags, [label] can show a text next to the thumb, for example the round of the first visible item.
 * Put it in the same box as the list, aligned to the end. It takes the full height and a narrow strip.
 */
@Composable
fun DraggableScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    label: ((LazyListItemInfo) -> String?)? = null,
) {
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            scrollbarMetricsOf(
                totalItems = info.totalItemsCount,
                visible = info.visibleItemsInfo,
                viewportStart = info.viewportStartOffset,
                viewportEnd = info.viewportEndOffset,
                firstIndex = state.firstVisibleItemIndex,
                firstScrollOffset = state.firstVisibleItemScrollOffset,
                canScroll = state.canScrollForward || state.canScrollBackward,
            )
        }
    }
    val current = metrics ?: return
    var dragging by remember { mutableStateOf(false) }
    // While the person drags, the thumb follows the finger, not the list, so it does not jump.
    var dragFraction by remember { mutableStateOf(0f) }
    var target by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(state) {
        snapshotFlow { target }.collectLatest { fraction ->
            if (fraction == null) return@collectLatest
            val info = state.layoutInfo
            val visibleItems = (metrics?.visibleFraction ?: 0f) * info.totalItemsCount
            state.scrollToItem(scrollbarTargetIndex(fraction, info.totalItemsCount, visibleItems))
        }
    }
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val thumbWidth by animateDpAsState(if (dragging) 10.dp else 6.dp)
    BoxWithConstraints(modifier.fillMaxHeight().width(TouchWidth)) {
        val trackPx = with(density) { maxHeight.toPx() }
        val minThumbPx = with(density) { MinThumbHeight.toPx() }
        val thumbPx = (current.visibleFraction * trackPx).coerceIn(minThumbPx.coerceAtMost(trackPx), trackPx)
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)
        val fraction = if (dragging) dragFraction else current.position
        val thumbTop = fraction * travel
        fun fractionAt(y: Float) = ((y - thumbPx / 2f) / travel).coerceIn(0f, 1f)
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(state, travel, thumbPx) {
                    detectTapGestures { offset -> target = fractionAt(offset.y) }
                }
                .pointerInput(state, travel, thumbPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            // A drag that starts on the thumb keeps the finger where it took the thumb.
                            val position = metrics?.position ?: 0f
                            val top = position * travel
                            dragFraction = if (offset.y in top..(top + thumbPx)) position else fractionAt(offset.y)
                            dragging = true
                            target = dragFraction
                        },
                        onDragEnd = {
                            dragging = false
                            target = null
                        },
                        onDragCancel = {
                            dragging = false
                            target = null
                        },
                    ) { change, amount ->
                        change.consume()
                        dragFraction = (dragFraction + amount / travel).coerceIn(0f, 1f)
                        target = dragFraction
                    }
                },
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop.roundToInt()) }
                .padding(end = 4.dp)
                .width(thumbWidth)
                .height(with(density) { thumbPx.toDp() })
                .clip(RoundedCornerShape(50))
                .background(if (dragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.55f))
                .testTag(DRAGGABLE_SCROLLBAR_TAG),
        )
        if (label != null) {
            val text = if (dragging) {
                // A sticky header is before the first item, so it does not give the label.
                val first = state.firstVisibleItemIndex
                state.layoutInfo.visibleItemsInfo.filter { it.index >= first }.firstNotNullOfOrNull { item -> label(item) }
            } else {
                null
            }
            AnimatedVisibility(
                visible = text != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // The bubble is wider than the strip, so it grows to the left, out of the strip.
                    .wrapContentWidth(align = Alignment.End, unbounded = true)
                    .offset {
                        // The bubble sits on the left of the strip, centered on the thumb.
                        val bubbleHalf = with(density) { 18.dp.roundToPx() }
                        IntOffset(-with(density) { TouchWidth.roundToPx() }, (thumbTop + thumbPx / 2f).roundToInt() - bubbleHalf)
                    },
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text.orEmpty(),
                        Modifier.widthIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The strip that takes the drags. It covers the right padding of the lists, so the rows keep their clicks. */
private val TouchWidth = 20.dp

/** The shortest thumb, so a finger can take it also in a very long list. */
private val MinThumbHeight = 48.dp
