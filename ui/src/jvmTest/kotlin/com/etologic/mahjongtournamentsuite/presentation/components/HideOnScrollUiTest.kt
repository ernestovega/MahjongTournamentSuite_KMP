package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HideOnScrollUiTest {
    private val hideDistance = 100f

    private fun HideOnScrollState.scroll(consumedY: Float) {
        connection.onPostScroll(Offset(0f, consumedY), Offset.Zero, NestedScrollSource.UserInput)
    }

    @Test
    fun scrollingDownFarEnoughHidesTheRows() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-60f)
        assertTrue(state.visible, "A short scroll keeps the rows.")
        state.scroll(-60f)
        assertFalse(state.visible, "The rows hide after the distance.")
    }

    @Test
    fun scrollingUpShowsTheRowsAgain() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-200f)
        assertFalse(state.visible)
        state.connection.onPreScroll(Offset(0f, 10f), NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }

    @Test
    fun aDirectionChangeStartsTheDistanceAgain() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-80f)
        state.connection.onPreScroll(Offset(0f, 5f), NestedScrollSource.UserInput)
        state.scroll(-80f)
        assertTrue(state.visible, "The first 80 px do not count after the user scrolled up.")
    }

    @Test
    fun contentThatDoesNotMoveNeverHidesTheRows() {
        val state = HideOnScrollState(hideDistance)
        // The finger moves down the screen but the content cannot scroll, so nothing is consumed.
        repeat(10) { state.connection.onPreScroll(Offset(0f, -80f), NestedScrollSource.UserInput) }
        assertTrue(state.visible)
    }

    @Test
    fun showResetsTheDistance() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-90f)
        state.show()
        state.scroll(-90f)
        assertTrue(state.visible)
    }

    @Test
    fun theRowsLeaveTheScreenWhenTheContentScrollsDownAndComeBackWhenItScrollsUp() = runComposeUiTest {
        setContent {
            val state = rememberHideOnScrollState()
            CompositionLocalProvider(LocalHideOnScroll provides state) {
                Column(Modifier.hideOnScroll(state)) {
                    HideOnScrollTabs {
                        Box(Modifier.height(48.dp).testTag("tabs")) { Text("Tabs") }
                    }
                    Column(Modifier.height(300.dp).verticalScroll(rememberScrollState()).testTag("content")) {
                        repeat(40) { Text("Row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        onNodeWithText("Tabs").assertIsDisplayed()
        onNodeWithTag("content").performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f) }
        waitForIdle()
        onNodeWithTag("tabs").assertDoesNotExistOrIsGone()
        onNodeWithTag("content").performTouchInput { swipeDown(startY = top + 10f, endY = bottom - 10f) }
        waitForIdle()
        onNodeWithText("Tabs").assertIsDisplayed()
    }

    @Test
    fun theRowsStayWhenTheContentIsShorterThanTheScreen() = runComposeUiTest {
        setContent {
            val state = rememberHideOnScrollState()
            CompositionLocalProvider(LocalHideOnScroll provides state) {
                Column(Modifier.hideOnScroll(state)) {
                    HideOnScrollTabs {
                        Box(Modifier.height(48.dp)) { Text("Tabs") }
                    }
                    Column(Modifier.height(300.dp).verticalScroll(rememberScrollState()).testTag("content")) {
                        repeat(3) { Text("Row $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        onNodeWithTag("content").performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f) }
        waitForIdle()
        onNodeWithText("Tabs").assertIsDisplayed()
    }

    @Test
    fun theRowsAlwaysShowWithoutAHideOnScrollState() = runComposeUiTest {
        setContent { HideOnScrollTabs { Text("Tabs") } }
        onNodeWithText("Tabs").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistOrIsGone() {
    val gone = runCatching { assertDoesNotExist() }.isSuccess
    if (!gone) {
        // The row can stay in the tree for an instant with a height of zero.
        val height = fetchSemanticsNode().size.height
        assertTrue(height == 0, "The rows are still on the screen, height $height.")
    }
}
