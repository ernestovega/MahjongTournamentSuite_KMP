package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared screen layouts: the screen column and its full-bleed rows and lists, the scroll reset, a plain table row. */
@OptIn(ExperimentalTestApi::class)
class ScreenLayoutsUiTest {
    @Test
    fun aTableRowWithoutAnActionOnlyShowsItsContent() = runDesktopComposeUiTest(width = 600, height = 400) {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    DataTableHeaderRow { Text("Name") }
                    DataTableDivider()
                    DataTableRow { Text("Ana") }
                }
            }
        }
        onNodeWithText("Name").assertExists()
        onNodeWithText("Ana").assertExists()
    }

    @Test
    fun theScrollGoesBackToTheTopOnlyWhenTheKeyChanges() = runDesktopComposeUiTest(width = 400, height = 300) {
        var key by mutableStateOf("ana")
        lateinit var state: ScrollState
        lateinit var scrollTo: (Int) -> Unit
        setContent {
            state = rememberScrollState()
            val scope = rememberCoroutineScope()
            scrollTo = { value -> scope.launch { state.scrollTo(value) } }
            ResetScrollOnChange(state, key)
            // Without a scroll state there is nothing to reset.
            ResetScrollOnChange(null, key)
            Column(Modifier.verticalScroll(state)) { Box(Modifier.size(100.dp, 2000.dp)) }
        }
        scrollTo(500)
        waitForIdle()
        assertEquals(500, state.value, "The first key keeps the restored position.")

        key = "bea"
        waitForIdle()
        assertEquals(0, state.value)
    }

    @Test
    fun aFullBleedRowReachesTheEdgesAndTheWheelScrollsItSideways() = runDesktopComposeUiTest(width = 400, height = 600) {
        setContent {
            MtsTheme(useDarkTheme = false) {
                ScreenColumn(scrollable = true, contentPadding = PaddingValues(24.dp)) {
                    FullBleedRow(Modifier.testTag("row")) {
                        repeat(10) { Box(Modifier.size(120.dp, 40.dp).testTag("item$it")) }
                    }
                    LazyRow(
                        modifier = Modifier.screenFullBleed().height(40.dp).testTag("list"),
                        contentPadding = screenListPadding(top = 4.dp, bottom = 4.dp),
                    ) {
                        items(10) { Text("Card $it", Modifier.size(120.dp, 32.dp)) }
                    }
                    Box(Modifier.size(100.dp, 2000.dp))
                }
            }
        }
        val row = onNodeWithTag("row").getUnclippedBoundsInRoot()
        val list = onNodeWithTag("list").getUnclippedBoundsInRoot()
        assertEquals(24.dp, row.left, "The row keeps the width of the content in the layout...")
        assertEquals(24.dp, onNodeWithTag("item0").getUnclippedBoundsInRoot().left, "...and its first item lines up with the content.")
        assertEquals(0.dp, list.left, "The list reaches the left edge.")
        assertEquals(400.dp, list.right, "The list reaches the right edge.")

        // At the start, the wheel up does not move the row: it cannot scroll back.
        onNodeWithTag("row").performMouseInput { moveTo(center); scroll(-3f) }
        waitForIdle()
        assertEquals(24.dp, onNodeWithTag("item0").getUnclippedBoundsInRoot().left)

        // The wheel down scrolls the row without the Shift key: the items leave through the screen edge.
        onNodeWithTag("row").performMouseInput { moveTo(center); scroll(3f) }
        waitForIdle()
        val afterWheel = onNodeWithTag("item0").getUnclippedBoundsInRoot().left
        assertTrue(afterWheel < 0.dp, "After the wheel the first item is at $afterWheel.")
        assertEquals(0.dp, onNodeWithText("Card 0").getUnclippedBoundsInRoot().top - list.top - 4.dp, "The screen did not scroll down.")

        // A sideways wheel is left to the row itself, which scrolls it further.
        onNodeWithTag("row").performMouseInput { moveTo(center); scroll(3f, ScrollWheel.Horizontal) }
        // The wheel animation of the row ends before the test ends, else it stops after the screen is gone.
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        assertTrue(onNodeWithTag("item0").getUnclippedBoundsInRoot().left < afterWheel)
    }

    @Test
    fun aScreenColumnWithTheDefaultsShowsItsContent() = runDesktopComposeUiTest(width = 400, height = 300) {
        var count by mutableStateOf(0)
        setContent {
            MtsTheme(useDarkTheme = false) {
                Text("Count $count")
                // The parent draws again, and the column with the same parameters is skipped.
                ScreenColumn { Text("Content") }
            }
        }
        onNodeWithText("Content").assertExists()
        count = 1
        waitForIdle()
        onNodeWithText("Count 1").assertExists()
        onNodeWithText("Content").assertExists()
    }
}
