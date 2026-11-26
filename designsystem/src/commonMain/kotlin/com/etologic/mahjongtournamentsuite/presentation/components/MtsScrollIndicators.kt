package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalMtsWindowWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max
import kotlin.math.min

/** The width of the strip of an always visible scrollbar, beside the list and outside it. */
val MtsScrollbarThickness: Dp = 10.dp

/** How deep the shadow of an edge with more content behind it is. */
val MtsFadingEdgeLength: Dp = 16.dp

/**
 * The shadow on each edge of a list that still has content to scroll to (the fading edge), and an always visible
 * scrollbar outside the list.
 *
 * The shadow shows also when the edge falls between two items, so a list cut in a gap still looks like a list.
 * It grows with the first pixels of the scroll, so it does not jump in.
 *
 * With [MtsScrollbar.Outside] (the default), the list is [MtsScrollbarThickness] narrower (or lower, for a horizontal list) and the scrollbar
 * takes that strip, on the end (or at the bottom), from the first time there is content to scroll to. The thumb can be dragged,
 * and a tap in the strip jumps there.
 *
 * Without [fadingEdges] it draws only the scrollbar: for a screen whose padding is outside its scroll, with a second
 * [scrollIndicators] without scrollbar inside the padding, so the shadows fall where the content is cut.
 *
 * On a phone (a compact window) the scrollbar does not stay: it shows over the end edge of the list only while the list
 * scrolls, like the scrollbars of the system, and fades out after it stops. The list keeps its whole width (Ernesto,
 * 2026-11-26: always visible scrollbars looked bad on a phone). The shadows of the edges stay.
 *
 * Put it before the scroll modifier (`Modifier.scrollIndicators(state).verticalScroll(state)`), or in the modifier of
 * a lazy list.
 */
@Composable
fun Modifier.scrollIndicators(
    state: ScrollState,
    orientation: Orientation = Orientation.Vertical,
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
    fadingEdges: Boolean = true,
): Modifier = scrollIndicators(remember(state) { ScrollStateIndicator(state) }, orientation, scrollbar, fadingEdges)

/** `verticalScroll(state)` with its [scrollIndicators]: the fading edges and the scrollbar. */
@Composable
fun Modifier.verticalScrollWithIndicators(
    state: ScrollState = rememberScrollState(),
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
): Modifier = scrollIndicators(state, Orientation.Vertical, scrollbar).verticalScroll(state)

/** `horizontalScroll(state)` with its [scrollIndicators]: the fading edges and the scrollbar under the row. */
@Composable
fun Modifier.horizontalScrollWithIndicators(
    state: ScrollState = rememberScrollState(),
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
): Modifier = scrollIndicators(state, Orientation.Horizontal, scrollbar).horizontalScroll(state)

/** [scrollIndicators] for a lazy list. The position of the thumb is an estimate when the items have different sizes. */
@Composable
fun Modifier.scrollIndicators(
    state: LazyListState,
    orientation: Orientation = Orientation.Vertical,
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
    fadingEdges: Boolean = true,
): Modifier = scrollIndicators(remember(state) { LazyListIndicator(state) }, orientation, scrollbar, fadingEdges)

/** [scrollIndicators] for a lazy grid. */
@Composable
fun Modifier.scrollIndicators(
    state: LazyGridState,
    orientation: Orientation = Orientation.Vertical,
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
    fadingEdges: Boolean = true,
): Modifier = scrollIndicators(remember(state) { LazyGridIndicator(state) }, orientation, scrollbar, fadingEdges)

@Composable
private fun Modifier.scrollIndicators(
    indicator: ScrollIndicator,
    orientation: Orientation,
    scrollbar: MtsScrollbar,
    fadingEdges: Boolean,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val colors = ScrollIndicatorColors(
        shadow = if (fadingEdges) Color.Black.copy(alpha = if (dark) 0.5f else 0.18f) else Color.Transparent,
        track = scheme.onSurface.copy(alpha = if (dark) 0.10f else 0.06f),
        thumb = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        dragged = scheme.primary,
    )
    val handle = remember(indicator) { ScrollIndicatorHandle(indicator) }
    val autoHide = scrollbar != MtsScrollbar.None && LocalMtsWindowWidth.current.isCompact
    // The scrollbar of a phone: shown while the list scrolls or the thumb is dragged, faded out a moment after.
    val shownScrollbar = if (autoHide) {
        handle.thumbAlpha = handle.thumbAlpha ?: Animatable(0f)
        LaunchedEffect(handle) {
            val alpha = handle.thumbAlpha ?: return@LaunchedEffect
            snapshotFlow { handle.indicator.state.isScrollInProgress || handle.dragging }.collectLatest { moving ->
                if (moving) {
                    alpha.animateTo(1f, tween(150))
                } else {
                    delay(800)
                    alpha.animateTo(0f, tween(400))
                }
            }
        }
        MtsScrollbar.Overlay
    } else {
        handle.thumbAlpha = null
        scrollbar
    }
    val drag = if (scrollbar != MtsScrollbar.None) {
        Modifier.pointerInput(handle, orientation) {
            val gutter = MtsScrollbarThickness.toPx()
            awaitEachGesture {
                // The strip takes the press before the content, which can be under it with an overlay scrollbar.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val vertical = orientation == Orientation.Vertical
                if (!handle.indicator.canScroll) return@awaitEachGesture
                // A hidden scrollbar of a phone takes no press: the content under it gets it.
                if ((handle.thumbAlpha?.value ?: 1f) == 0f) return@awaitEachGesture
                val inStrip = if (vertical) down.position.x >= size.width - gutter else down.position.y >= size.height - gutter
                if (!inStrip) return@awaitEachGesture
                val track = (if (vertical) size.height else size.width).toFloat()
                val along = { position: Offset -> if (vertical) position.y else position.x }
                val thumb = handle.indicator.thumb(track)
                down.consume()
                // A press outside the thumb takes the thumb there first, then the drag moves it from there.
                if (thumb != null && along(down.position) !in thumb.start..(thumb.start + thumb.size)) {
                    handle.indicator.scrollThumbBy(along(down.position) - (thumb.start + thumb.size / 2f), track)
                }
                handle.dragging = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = along(change.positionChange())
                        if (delta != 0f) handle.indicator.scrollThumbBy(delta, track)
                        change.consume()
                    }
                } finally {
                    handle.dragging = false
                }
            }
        }
    } else {
        Modifier
    }
    return this
        .then(if (shownScrollbar == MtsScrollbar.Margin) MarginBleedElement(handle, orientation) else Modifier)
        .then(drag)
        .then(ScrollIndicatorsElement(handle, orientation, shownScrollbar, colors))
}

/** Where the scrollbar of [scrollIndicators] goes. */
enum class MtsScrollbar {
    /** No scrollbar, only the shadows: lists that have their own, and short strips like chips. */
    None,

    /** In its own strip beside the list (or under it), outside the content. */
    Outside,

    /**
     * On the end edge of the container, over its padding: for a whole screen or a dialog whose content already keeps
     * a margin of at least [MtsScrollbarThickness], so the margins stay the same on both sides.
     */
    Overlay,

    /**
     * Like [Outside], but its strip goes past the end of the list (or under it), into the margin of the container, so
     * the list keeps the same width as the content above it and both sides look the same. The container must keep a
     * margin of at least [MtsScrollbarThickness] after the list and must not clip it: a single list on a whole screen.
     */
    Margin,
}

internal data class ScrollIndicatorColors(val shadow: Color, val track: Color, val thumb: Color, val dragged: Color)

/** The thumb of a scrollbar, in pixels along a track of a given length. */
internal data class ScrollThumb(val start: Float, val size: Float)

/** What the indicators need from a scrollable: how much is hidden on each edge, and where the thumb goes. */
internal interface ScrollIndicator {
    val state: ScrollableState

    /** Whether there is content to scroll to, so the scrollbar takes its strip. */
    val canScroll: Boolean get() = state.canScrollForward || state.canScrollBackward

    /** How much of [edge] pixels is hidden before the start, from 0 to [edge]. */
    fun hiddenBefore(edge: Float): Float

    /** How much of [edge] pixels is hidden after the end, from 0 to [edge]. */
    fun hiddenAfter(edge: Float): Float

    /** The thumb along [track] pixels, or null when the content fits. */
    fun thumb(track: Float): ScrollThumb?

    /** How many pixels of content one pixel of thumb moves, along [track]. */
    fun contentPerThumbPixel(track: Float): Float

    fun scrollThumbBy(thumbDelta: Float, track: Float) {
        val delta = thumbDelta * contentPerThumbPixel(track)
        if (delta != 0f) state.dispatchRawDelta(delta)
    }
}

internal fun thumbFor(position: Float, visibleFraction: Float, track: Float, minThumb: Float): ScrollThumb {
    val size = (visibleFraction * track).coerceIn(min(minThumb, track), track)
    return ScrollThumb(start = position.coerceIn(0f, 1f) * (track - size), size = size)
}

private const val MIN_THUMB_PX = 32f

internal class ScrollStateIndicator(override val state: ScrollState) : ScrollIndicator {
    // Before its first layout the state does not know its size yet (Int.MAX_VALUE): that does not count as scrollable.
    override val canScroll get() = state.maxValue in 1 until Int.MAX_VALUE

    override fun hiddenBefore(edge: Float) = min(state.value.toFloat(), edge)

    override fun hiddenAfter(edge: Float) = min((state.maxValue - state.value).toFloat(), edge).coerceAtLeast(0f)

    override fun thumb(track: Float): ScrollThumb? {
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return null
        val viewport = state.viewportSize.toFloat().takeIf { it > 0f } ?: track
        return thumbFor(state.value.toFloat() / max, viewport / (viewport + max), track, MIN_THUMB_PX)
    }

    override fun contentPerThumbPixel(track: Float): Float {
        val thumb = thumb(track) ?: return 0f
        val travel = track - thumb.size
        return if (travel <= 0f) 0f else state.maxValue / travel
    }
}

/**
 * Where a lazy list is, from 0 (top) to 1 (end), and how much of it shows, from the indexes of the visible items.
 * The items can have different sizes, so the values are an estimate. Null when the whole list fits.
 */
internal data class LazyScrollMetrics(val position: Float, val visibleFraction: Float)

internal data class LazyItemSpan(val index: Int, val offset: Int, val size: Int)

internal fun lazyScrollMetricsOf(
    totalItems: Int,
    visible: List<LazyItemSpan>,
    viewportStart: Int,
    viewportEnd: Int,
    firstIndex: Int,
    firstScrollOffset: Int,
    canScroll: Boolean,
): LazyScrollMetrics? {
    if (!canScroll || totalItems <= 0 || visible.isEmpty()) return null
    // The part of each visible item that is on screen.
    val shown = visible.sumOf { item ->
        val top = max(item.offset, viewportStart)
        val bottom = min(item.offset + item.size, viewportEnd)
        ((bottom - top).coerceAtLeast(0).toFloat() / item.size.coerceAtLeast(1)).toDouble()
    }.toFloat().coerceIn(0.01f, totalItems.toFloat())
    // The first item comes from the state, not from the visible items, so a sticky header does not count as first.
    val firstSize = visible.firstOrNull { it.index == firstIndex }?.size ?: 1
    val hidden = (firstScrollOffset.toFloat() / firstSize.coerceAtLeast(1)).coerceIn(0f, 1f)
    val scrollable = (totalItems - shown).coerceAtLeast(0.0001f)
    return LazyScrollMetrics(
        position = ((firstIndex + hidden) / scrollable).coerceIn(0f, 1f),
        visibleFraction = (shown / totalItems).coerceIn(0f, 1f),
    )
}

/** The common part of lazy lists and grids: the visible items and the viewport. */
internal abstract class LazyIndicator : ScrollIndicator {
    abstract val totalItems: Int
    abstract val items: List<LazyItemSpan>
    abstract val viewportStart: Int
    abstract val viewportEnd: Int
    abstract val afterContentPadding: Int
    abstract val beforeContentPadding: Int
    abstract val firstIndex: Int
    abstract val firstOffset: Int

    /** How many items share a line (the columns of a vertical grid). */
    open val itemsPerLine: Int = 1

    override fun hiddenBefore(edge: Float): Float {
        if (!state.canScrollBackward) return 0f
        if (firstIndex >= itemsPerLine) return edge
        return min(firstOffset.toFloat(), edge).coerceAtLeast(1f)
    }

    override fun hiddenAfter(edge: Float): Float {
        if (!state.canScrollForward) return 0f
        val last = items.maxByOrNull { it.index } ?: return edge
        if (last.index < totalItems - itemsPerLine) return edge
        val end = items.filter { it.index > totalItems - itemsPerLine - 1 }.maxOf { it.offset + it.size }
        return min((end + afterContentPadding - viewportEnd).toFloat(), edge).coerceAtLeast(1f)
    }

    private fun metrics(): LazyScrollMetrics? {
        val perLine = itemsPerLine.coerceAtLeast(1)
        // A grid counts by lines, so each line counts once.
        val lines = items.groupBy { it.index / perLine }.map { (line, spans) ->
            LazyItemSpan(line, spans.minOf { it.offset }, spans.maxOf { it.offset + it.size } - spans.minOf { it.offset })
        }
        return lazyScrollMetricsOf(
            totalItems = (totalItems + perLine - 1) / perLine,
            visible = lines,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            firstIndex = firstIndex / perLine,
            firstScrollOffset = firstOffset,
            canScroll = state.canScrollForward || state.canScrollBackward,
        )
    }

    override fun thumb(track: Float): ScrollThumb? {
        val metrics = metrics() ?: return null
        return thumbFor(metrics.position, metrics.visibleFraction, track, MIN_THUMB_PX)
    }

    override fun contentPerThumbPixel(track: Float): Float {
        val thumb = thumb(track) ?: return 0f
        val travel = track - thumb.size
        if (travel <= 0f || items.isEmpty()) return 0f
        // The size of the whole content, from the average size of the visible items.
        val perLine = itemsPerLine.coerceAtLeast(1)
        val lineCount = items.map { it.index / perLine }.distinct().size.coerceAtLeast(1)
        val averageLine = (items.maxOf { it.offset + it.size } - items.minOf { it.offset }).toFloat() / lineCount
        val totalLines = (totalItems + perLine - 1) / perLine
        val content = averageLine * totalLines + beforeContentPadding + afterContentPadding
        val viewport = (viewportEnd - viewportStart).toFloat()
        return ((content - viewport) / travel).coerceAtLeast(0f)
    }
}

internal class LazyListIndicator(override val state: LazyListState) : LazyIndicator() {
    override val totalItems get() = state.layoutInfo.totalItemsCount
    override val items get() = state.layoutInfo.visibleItemsInfo.map { LazyItemSpan(it.index, it.offset, it.size) }
    override val viewportStart get() = state.layoutInfo.viewportStartOffset
    override val viewportEnd get() = state.layoutInfo.viewportEndOffset
    override val afterContentPadding get() = state.layoutInfo.afterContentPadding
    override val beforeContentPadding get() = state.layoutInfo.beforeContentPadding
    override val firstIndex get() = state.firstVisibleItemIndex
    override val firstOffset get() = state.firstVisibleItemScrollOffset
}

internal class LazyGridIndicator(override val state: LazyGridState) : LazyIndicator() {
    private val vertical get() = state.layoutInfo.orientation == Orientation.Vertical
    override val totalItems get() = state.layoutInfo.totalItemsCount
    override val items
        get() = state.layoutInfo.visibleItemsInfo.map {
            if (vertical) LazyItemSpan(it.index, it.offset.y, it.size.height) else LazyItemSpan(it.index, it.offset.x, it.size.width)
        }
    override val viewportStart get() = state.layoutInfo.viewportStartOffset
    override val viewportEnd get() = state.layoutInfo.viewportEndOffset
    override val afterContentPadding get() = state.layoutInfo.afterContentPadding
    override val beforeContentPadding get() = state.layoutInfo.beforeContentPadding
    override val firstIndex get() = state.firstVisibleItemIndex
    override val firstOffset get() = state.firstVisibleItemScrollOffset
    override val itemsPerLine: Int
        get() = state.layoutInfo.visibleItemsInfo
            .groupBy { if (vertical) it.row else it.column }
            .maxOfOrNull { it.value.size } ?: 1
}

/** The indicator and whether the person is dragging its thumb, shared by the gesture and the drawing. */
internal class ScrollIndicatorHandle(val indicator: ScrollIndicator) {
    var dragging by mutableStateOf(false)

    /** Whether the strip of an outside scrollbar is in the layout. It comes once and stays (see the measure of the node). */
    var gutterShown = false

    /** How visible the scrollbar of a phone is, from 0 to 1. Null where the scrollbar always shows. */
    var thumbAlpha: Animatable<Float, *>? = null
}

/**
 * For [MtsScrollbar.Margin]: the list and its scrollbar strip measure [MtsScrollbarThickness] wider than the place
 * they get, so the strip falls in the margin after it. The gesture of the strip is inside this, so it takes presses there.
 */
private class MarginBleedElement(
    val handle: ScrollIndicatorHandle,
    val orientation: Orientation,
) : ModifierNodeElement<MarginBleedNode>() {
    override fun create() = MarginBleedNode(handle, orientation)

    override fun update(node: MarginBleedNode) {
        node.handle = handle
        node.orientation = orientation
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollbarMargin"
    }

    override fun equals(other: Any?) = other is MarginBleedElement && other.handle === handle && other.orientation == orientation

    override fun hashCode() = handle.hashCode() * 31 + orientation.hashCode()
}

private class MarginBleedNode(
    var handle: ScrollIndicatorHandle,
    var orientation: Orientation,
) : Modifier.Node(),
    LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        if (!handle.gutterShown && handle.indicator.canScroll) handle.gutterShown = true
        val bleed = if (handle.gutterShown) MtsScrollbarThickness.roundToPx() else 0
        val vertical = orientation == Orientation.Vertical
        if (bleed == 0 || (vertical && !constraints.hasBoundedWidth) || (!vertical && !constraints.hasBoundedHeight)) {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val wider = if (vertical) {
            constraints.copy(minWidth = constraints.minWidth + bleed, maxWidth = constraints.maxWidth + bleed)
        } else {
            constraints.copy(minHeight = constraints.minHeight + bleed, maxHeight = constraints.maxHeight + bleed)
        }
        val placeable = measurable.measure(wider)
        val width = if (vertical) constraints.constrainWidth(placeable.width - bleed) else constraints.constrainWidth(placeable.width)
        val height = if (vertical) constraints.constrainHeight(placeable.height) else constraints.constrainHeight(placeable.height - bleed)
        // Placed at the start: the part past the end is the strip, in the margin.
        return layout(width, height) { placeable.place(0, 0) }
    }
}

private class ScrollIndicatorsElement(
    val handle: ScrollIndicatorHandle,
    val orientation: Orientation,
    val scrollbar: MtsScrollbar,
    val colors: ScrollIndicatorColors,
) : ModifierNodeElement<ScrollIndicatorsNode>() {
    override fun create() = ScrollIndicatorsNode(handle, orientation, scrollbar, colors)

    override fun update(node: ScrollIndicatorsNode) {
        node.handle = handle
        node.orientation = orientation
        node.scrollbar = scrollbar
        node.colors = colors
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollIndicators"
        properties["orientation"] = orientation
        properties["scrollbar"] = scrollbar
    }

    override fun equals(other: Any?) = other is ScrollIndicatorsElement &&
        other.handle === handle &&
        other.orientation == orientation &&
        other.scrollbar == scrollbar &&
        other.colors == colors

    override fun hashCode() = ((handle.hashCode() * 31 + orientation.hashCode()) * 31 + scrollbar.hashCode()) * 31 + colors.hashCode()
}

private class ScrollIndicatorsNode(
    var handle: ScrollIndicatorHandle,
    var orientation: Orientation,
    var scrollbar: MtsScrollbar,
    var colors: ScrollIndicatorColors,
) : Modifier.Node(),
    LayoutModifierNode,
    DrawModifierNode {

    private val vertical get() = orientation == Orientation.Vertical

    private val usesGutter get() = scrollbar == MtsScrollbar.Outside || scrollbar == MtsScrollbar.Margin

    private var gutterShown
        get() = handle.gutterShown
        set(value) {
            handle.gutterShown = value
        }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        // The strip comes the first time the content can scroll, so a list that fits keeps its whole width. The state is
        // read here, so the layout comes again when it changes. Once there, the strip stays: content that gets shorter
        // when it is narrower (tiles that scale with the width) would otherwise fit without it, scroll with it, and the
        // layout would go back and forth forever.
        if (usesGutter && !gutterShown && handle.indicator.canScroll) gutterShown = true
        val gutter = if (usesGutter && gutterShown) MtsScrollbarThickness.roundToPx() else 0
        if (gutter == 0) {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val inner = if (vertical) {
            constraints.copy(
                minWidth = (constraints.minWidth - gutter).coerceAtLeast(0),
                maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - gutter).coerceAtLeast(0) else Constraints.Infinity,
            )
        } else {
            constraints.copy(
                minHeight = (constraints.minHeight - gutter).coerceAtLeast(0),
                maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - gutter).coerceAtLeast(0) else Constraints.Infinity,
            )
        }
        val placeable = measurable.measure(inner)
        val width = if (vertical) constraints.constrainWidth(placeable.width + gutter) else constraints.constrainWidth(placeable.width)
        val height = if (vertical) constraints.constrainHeight(placeable.height) else constraints.constrainHeight(placeable.height + gutter)
        return layout(width, height) { placeable.place(0, 0) }
    }

    private fun IntrinsicMeasureScope.gutter() = if (usesGutter && gutterShown) MtsScrollbarThickness.roundToPx() else 0

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.minIntrinsicWidth(height) + if (vertical) gutter() else 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.maxIntrinsicWidth(height) + if (vertical) gutter() else 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.minIntrinsicHeight(width) + if (vertical) 0 else gutter()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.maxIntrinsicHeight(width) + if (vertical) 0 else gutter()

    override fun ContentDrawScope.draw() {
        drawContent()
        val indicator = handle.indicator
        val gutter = if (usesGutter && gutterShown) MtsScrollbarThickness.toPx() else 0f
        val contentSize = if (vertical) Size(size.width - gutter, size.height) else Size(size.width, size.height - gutter)
        val edge = MtsFadingEdgeLength.toPx()
        val before = indicator.hiddenBefore(edge) / edge
        val after = indicator.hiddenAfter(edge) / edge
        val shadow = colors.shadow
        if (shadow.alpha > 0f) {
            drawShadows(contentSize, edge, before, after, shadow)
        }
        if (scrollbar == MtsScrollbar.None) return
        val strip = MtsScrollbarThickness.toPx()
        val track = if (vertical) size.height else size.width
        val thumb = indicator.thumb(track) ?: return
        // The scrollbar of a phone has no track, and its thumb fades in and out with the scroll.
        val fade = handle.thumbAlpha?.value
        if (fade == 0f) return
        val inset = 2.dp.toPx()
        val thickness = if (handle.dragging) strip - inset else 4.dp.toPx()
        val radius = CornerRadius(thickness / 2f)
        val stripStart = (if (vertical) size.width else size.height) - strip
        val across = stripStart + (strip - thickness) / 2f
        val trackThickness = 4.dp.toPx()
        val trackAcross = stripStart + (strip - trackThickness) / 2f
        val thumbColor = (if (handle.dragging) colors.dragged else colors.thumb).let { if (fade != null) it.copy(alpha = it.alpha * fade) else it }
        if (vertical) {
            if (fade == null) drawRoundRect(colors.track, Offset(trackAcross, 0f), Size(trackThickness, track), CornerRadius(trackThickness / 2f))
            drawRoundRect(thumbColor, Offset(across, thumb.start), Size(thickness, thumb.size), radius)
        } else {
            if (fade == null) drawRoundRect(colors.track, Offset(0f, trackAcross), Size(track, trackThickness), CornerRadius(trackThickness / 2f))
            drawRoundRect(thumbColor, Offset(thumb.start, across), Size(thumb.size, thickness), radius)
        }
    }

    private fun ContentDrawScope.drawShadows(contentSize: Size, edge: Float, before: Float, after: Float, shadow: Color) {
        clipRect(right = contentSize.width, bottom = contentSize.height) {
            if (before > 0f) {
                val color = shadow.copy(alpha = shadow.alpha * before)
                if (vertical) {
                    drawRect(Brush.verticalGradient(listOf(color, Color.Transparent), startY = 0f, endY = edge), size = Size(contentSize.width, edge))
                } else {
                    drawRect(Brush.horizontalGradient(listOf(color, Color.Transparent), startX = 0f, endX = edge), size = Size(edge, contentSize.height))
                }
            }
            if (after > 0f) {
                val color = shadow.copy(alpha = shadow.alpha * after)
                if (vertical) {
                    val top = contentSize.height - edge
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, color), startY = top, endY = contentSize.height),
                        topLeft = Offset(0f, top),
                        size = Size(contentSize.width, edge),
                    )
                } else {
                    val left = contentSize.width - edge
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, color), startX = left, endX = contentSize.width),
                        topLeft = Offset(left, 0f),
                        size = Size(edge, contentSize.height),
                    )
                }
            }
        }
    }
}
