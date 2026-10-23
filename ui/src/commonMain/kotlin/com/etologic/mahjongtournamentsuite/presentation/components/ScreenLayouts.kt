package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/** The scroll state of a [ScreenColumn] and the top of its viewport in the window. A section uses them to stick or to scroll. */
class ScreenScroll(val state: ScrollState) {
    var viewportTop by mutableFloatStateOf(0f)
}

/** The [ScreenScroll] of the closest scrolling [ScreenColumn]. Null when the screen does not scroll. */
val LocalScreenScroll = compositionLocalOf<ScreenScroll?> { null }

/**
 * Scrolls [state] to the top when [key] changes, for example when the screen shows another player.
 * The first value does nothing, so a scroll position restored after a page refresh stays.
 */
@Composable
fun ResetScrollOnChange(state: ScrollState?, key: Any?) {
    var previous by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (previous != key) {
            previous = key
            state?.scrollTo(0)
        }
    }
}

/** The space between the content and the two edges of a [ScreenColumn]. A [FullBleedRow] uses it to reach the edges. */
class ScreenBleed(val start: Dp, val end: Dp)

val LocalScreenBleed = compositionLocalOf { ScreenBleed(0.dp, 0.dp) }

/**
 * A row that scrolls sideways and reaches both edges of the [ScreenColumn].
 * At the start, the first item lines up with the content. When the row scrolls, the items leave through the screen edge.
 * The mouse wheel scrolls the row without the Shift key.
 */
@Composable
fun FullBleedRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val bleed = LocalScreenBleed.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val startPx = with(density) { bleed.start.roundToPx() }
    val endPx = with(density) { bleed.end.roundToPx() }
    Row(
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + startPx + endPx),
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(-startPx, 0) }
            }
            .pointerInput(scrollState) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val delta = event.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
                        if (delta.x != 0f || delta.y == 0f) continue
                        val canScroll = if (delta.y > 0f) scrollState.canScrollForward else scrollState.canScrollBackward
                        if (!canScroll) continue
                        scope.launch { scrollState.scrollBy(delta.y * WheelStepPx * density.density) }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .horizontalScroll(scrollState)
            .padding(start = bleed.start, end = bleed.end),
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}

private const val WheelStepPx = 24f

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 900.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollable: Boolean = false,
    /** When true, the padding scrolls with the content. The content then goes under the top bar without a gap. */
    paddingInsideScroll: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val scrollState = rememberPersistentScrollState("screen")
        val layoutDirection = LocalLayoutDirection.current
        // The column fills the width. The side space keeps the content inside maxWidth and lets a full-bleed row use it.
        val sideSpace = ((this.maxWidth - maxWidth) / 2).coerceAtLeast(0.dp)
        val sidePadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + sideSpace,
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection) + sideSpace,
            bottom = contentPadding.calculateBottomPadding(),
        )
        val bleed = ScreenBleed(
            start = contentPadding.calculateStartPadding(layoutDirection) + sideSpace,
            end = contentPadding.calculateEndPadding(layoutDirection) + sideSpace,
        )
        val screenScroll = remember(scrollState) { ScreenScroll(scrollState) }
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalScreenScroll provides if (scrollable) screenScroll else null,
                LocalScreenBleed provides bleed,
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { screenScroll.viewportTop = it.positionInRoot().y }
                    .then(
                        if (scrollable && paddingInsideScroll) {
                            Modifier.verticalScroll(scrollState).padding(sidePadding)
                        } else {
                            Modifier.padding(sidePadding).then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                        },
                    ),
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
            }
            if (scrollable) {
                PlatformVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(PlatformScrollbarThickness),
                )
            }
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun ScreenColumnPreview() {
    MtsTheme(useDarkTheme = false) {
        ScreenColumn(
            maxWidth = 700.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "One") { Text("First item") }
            SectionCard(title = "Two") { Text("Second item") }
        }
    }
}
