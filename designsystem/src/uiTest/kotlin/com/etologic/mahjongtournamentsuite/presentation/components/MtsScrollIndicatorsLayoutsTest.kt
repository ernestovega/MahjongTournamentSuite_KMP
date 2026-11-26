package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** The scrollbar of a grid, of a row that scrolls sideways, and of a list inside a layout that asks for its intrinsic size. */
@OptIn(ExperimentalTestApi::class)
class MtsScrollIndicatorsLayoutsTest {
    @Test
    fun draggingTheStripOfAGridScrollsIt() = runComposeUiTest {
        val state = LazyGridState()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(400.dp).testTag("box")) {
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().scrollIndicators(state), state = state) {
                        items((1..300).toList(), key = { it }) { Text("Cell $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("box").performMouseInput {
            val x = width - 3f
            dragAndDrop(Offset(x, 10f), Offset(x, height - 1f), durationMillis = 500)
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex > 200, "The grid is at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun aPressOnTheStripAwayFromTheThumbTakesTheThumbThere() = runComposeUiTest {
        val state = LazyGridState()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).height(400.dp).testTag("box")) {
                    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxWidth().scrollIndicators(state), state = state) {
                        items((1..400).toList(), key = { it }) { Text("Cell $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("box").performMouseInput { click(Offset(width - 3f, height - 2f)) }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex > 300, "The grid is at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun aRowThatScrollsSidewaysHasItsStripUnderIt() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.width(300.dp).testTag("box")) {
                    Row(Modifier.horizontalScrollWithIndicators(rememberScrollState(), scrollbar = MtsScrollbar.Margin).testTag("row")) {
                        repeat(30) { Text("Chip $it", Modifier.width(80.dp).height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        assertTrue(onNodeWithTag("row").getBoundsInRoot().let { it.bottom - it.top } >= 40.dp)
    }

    @Test
    fun aListInsideAnIntrinsicLayoutCountsItsStrip() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Row(Modifier.height(IntrinsicSize.Max).width(IntrinsicSize.Max).testTag("intrinsic")) {
                    Column(Modifier.height(200.dp).scrollIndicators(rememberScrollState(), Orientation.Vertical)) {
                        repeat(40) { Text("Line $it", Modifier.height(40.dp)) }
                    }
                    Column(Modifier.width(200.dp).horizontalScrollWithIndicators()) { Text("Wide") }
                }
            }
        }
        waitForIdle()
        assertTrue(onNodeWithTag("intrinsic").getBoundsInRoot().let { it.right - it.left } > 0.dp)
    }
}
