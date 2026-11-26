package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MtsScrollIndicatorsMathTest {
    @Test
    fun theThumbGoesFromTheTopToTheEnd() {
        val top = thumbFor(position = 0f, visibleFraction = 0.25f, track = 400f, minThumb = 32f)
        val end = thumbFor(position = 1f, visibleFraction = 0.25f, track = 400f, minThumb = 32f)
        assertEquals(ScrollThumb(0f, 100f), top)
        assertEquals(ScrollThumb(300f, 100f), end)
    }

    @Test
    fun aVeryLongListKeepsAThumbBigEnoughToTake() {
        assertEquals(32f, thumbFor(position = 0.5f, visibleFraction = 0.001f, track = 400f, minThumb = 32f).size)
    }

    @Test
    fun aLazyListThatFitsHasNoMetrics() {
        val items = listOf(LazyItemSpan(0, 0, 40), LazyItemSpan(1, 40, 40))
        assertNull(lazyScrollMetricsOf(2, items, 0, 400, 0, 0, canScroll = false))
    }

    @Test
    fun aLazyListInTheMiddleIsHalfWay() {
        // 100 items of 40 px, 10 visible from item 45: 45 of the 90 that can go out of the screen.
        val items = (45 until 55).map { LazyItemSpan(it, (it - 45) * 40, 40) }
        val metrics = assertNotNull(lazyScrollMetricsOf(100, items, 0, 400, 45, 0, canScroll = true))
        assertEquals(0.5f, metrics.position, 0.001f)
        assertEquals(0.1f, metrics.visibleFraction, 0.001f)
    }

    @Test
    fun withoutAPinnedHeaderTheShadowStartsAtTheTop() {
        val items = (0 until 10).map { LazyItemSpan(it, it * 40, 40) }
        assertEquals(0f, pinnedStartOf(items, firstIndex = 0, firstOffset = 0, viewportStart = 0))
        val scrolled = (3 until 13).map { LazyItemSpan(it, (it - 3) * 40 - 10, 40) }
        assertEquals(0f, pinnedStartOf(scrolled, firstIndex = 3, firstOffset = 10, viewportStart = 0))
    }

    @Test
    fun aPinnedHeaderMovesTheShadowUnderIt() {
        // The header (item 0, 30 px) stays pinned at the top while row 5 is the first one scrolled in.
        val items = listOf(LazyItemSpan(0, 0, 30)) + (5 until 15).map { LazyItemSpan(it, (it - 5) * 40 - 12, 40) }
        assertEquals(30f, pinnedStartOf(items, firstIndex = 5, firstOffset = 12, viewportStart = 0))
        // The first item itself pinned while the list has only scrolled a little.
        val first = listOf(LazyItemSpan(0, 0, 30)) + (1 until 10).map { LazyItemSpan(it, it * 40 - 20, 40) }
        assertEquals(30f, pinnedStartOf(first, firstIndex = 0, firstOffset = 8, viewportStart = 0))
    }
}

@OptIn(ExperimentalTestApi::class)
class MtsScrollIndicatorsUiTest {
    @Test
    fun theScrollbarNeverTakesAStripBecauseItShowsOnlyWhileScrolling() = runComposeUiTest {
        var lines by mutableStateOf(2)
        val state = ScrollState(0)
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(200.dp)) {
                    Column(Modifier.fillMaxWidth().scrollIndicators(state).verticalScroll(state).testTag("content")) {
                        repeat(lines) { Text("Line $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        val fits = onNodeWithTag("content").getBoundsInRoot()
        assertEquals(300.dp, fits.right - fits.left)

        lines = 20
        waitForIdle()
        val scrolls = onNodeWithTag("content").getBoundsInRoot()
        // The scrollbar shows over the end of the list while it scrolls, so the list keeps its whole width (2026-11-26).
        assertEquals(300.dp, scrolls.right - scrolls.left)
    }

    @Test
    fun theOverlayScrollbarKeepsTheWholeWidth() = runComposeUiTest {
        val state = ScrollState(0)
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(200.dp)) {
                    Column(
                        Modifier.fillMaxWidth().scrollIndicators(state, scrollbar = MtsScrollbar.Overlay).verticalScroll(state).testTag("content"),
                    ) {
                        repeat(20) { Text("Line $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        val bounds = onNodeWithTag("content").getBoundsInRoot()
        assertEquals(300.dp, bounds.right - bounds.left)
    }

    @Test
    fun theMarginScrollbarKeepsTheWidthAndIsDraggedOnTheEndOfTheList() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            MtsTheme(useDarkTheme = false) {
                // A screen with a 24 dp margin on each side, and a list in the middle.
                Box(Modifier.width(348.dp).height(400.dp).testTag("screen")) {
                    Box(Modifier.padding(horizontal = 24.dp).width(300.dp).height(400.dp)) {
                        LazyColumn(
                            Modifier.fillMaxWidth().scrollIndicators(state, scrollbar = MtsScrollbar.Margin).testTag("list"),
                            state = state,
                        ) {
                            items((1..200).toList(), key = { it }) { Text("Row $it", Modifier.fillMaxWidth().height(40.dp).testTag("row$it")) }
                        }
                    }
                }
            }
        }
        waitForIdle()
        // The rows keep the whole width of the place of the list: the strip is past its end.
        val row = onNodeWithTag("row1").getBoundsInRoot()
        assertEquals(24.dp, row.left)
        assertEquals(324.dp, row.right)
        onNodeWithTag("screen").performMouseInput {
            // A mouse down on the hidden thumb, on the end of the list, and a drag to the bottom.
            val x = (324.dp - MtsScrollbarThickness / 2).toPx()
            dragAndDrop(Offset(x, 10f), Offset(x, height - 1f), durationMillis = 500)
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex > 150, "The list is at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun draggingTheStripScrollsTheList() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(400.dp).testTag("box")) {
                    LazyColumn(Modifier.fillMaxWidth().scrollIndicators(state), state = state) {
                        items((1..200).toList(), key = { it }) { Text("Row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("box").performMouseInput {
            // A mouse down on the thumb, at the top of the strip on the end, and a drag to the bottom. The mouse takes
            // the thumb also while it is hidden.
            val x = width - 3f
            dragAndDrop(Offset(x, 10f), Offset(x, height - 1f), durationMillis = 500)
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex > 150, "The list is at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun aFingerOnTheHiddenStripScrollsTheListNormally() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(400.dp).testTag("box")) {
                    LazyColumn(Modifier.fillMaxWidth().scrollIndicators(state), state = state) {
                        items((1..200).toList(), key = { it }) { Text("Row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("box").performTouchInput {
            val x = width - 3f
            swipe(Offset(x, height - 20f), Offset(x, 20f), durationMillis = 300)
        }
        waitForIdle()
        // The hidden scrollbar does not take the finger: the list moves by about the length of the swipe.
        assertTrue(state.firstVisibleItemIndex in 5..30, "The list is at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun aDragOnTheContentStillScrollsTheListNormally() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(400.dp).testTag("box")) {
                    LazyColumn(Modifier.fillMaxWidth().scrollIndicators(state), state = state) {
                        items((1..200).toList(), key = { it }) { Text("Row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("box").performTouchInput {
            swipe(Offset(centerX, height - 20f), Offset(centerX, 20f), durationMillis = 300)
        }
        waitForIdle()
        // A finger on the rows moves the list by about the length of the swipe, not to the end.
        assertTrue(state.firstVisibleItemIndex in 5..30, "The list is at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun theTopShadowOfAListWithAStickyHeaderGoesUnderTheHeader() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(200.dp)) {
                    LazyColumn(
                        state = state,
                        modifier = Modifier.fillMaxWidth().scrollIndicators(state),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                    ) {
                        stickyHeader { Text("Header", Modifier.fillMaxWidth().height(30.dp)) }
                        items(50) { Text("Row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        val indicator = LazyListIndicator(state)
        assertEquals(0f, indicator.pinnedStart())

        runOnIdle { kotlinx.coroutines.runBlocking { state.scrollToItem(10, 15) } }
        waitForIdle()
        val headerEnd = runOnIdle { indicator.pinnedStart() }
        val header = runOnIdle { state.layoutInfo.visibleItemsInfo.first { it.index == 0 } }
        assertEquals((header.offset + header.size - state.layoutInfo.viewportStartOffset).toFloat(), headerEnd)
        assertTrue(headerEnd >= 30f * density.density - 1f, "The shadow starts at $headerEnd, under a 30 dp header")
    }
}
