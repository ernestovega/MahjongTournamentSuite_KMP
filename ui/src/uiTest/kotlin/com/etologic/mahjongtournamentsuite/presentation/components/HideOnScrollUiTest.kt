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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

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
    fun scrollingUpFarEnoughShowsTheRowsAgain() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-200f)
        assertFalse(state.visible)
        state.endDrag()
        state.connection.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput)
        assertFalse(state.visible, "A short move back keeps the rows hidden.")
        state.connection.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }

    @Test
    fun aSmallMoveBackAtTheEndOfALongDragDoesNotShowTheRows() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-200f)
        state.endDrag()
        // A long drag up the screen, as far as the top bar, with the finger shaking a little on the way and as it lifts.
        repeat(20) {
            state.scroll(-40f)
            state.connection.onPreScroll(Offset(0f, 3f), NestedScrollSource.UserInput)
            state.connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        }
        assertFalse(state.visible, "The shaking finger does not stop the rows from hiding.")
        state.scroll(-40f)
        repeat(5) { state.connection.onPreScroll(Offset(0f, 8f), NestedScrollSource.UserInput) }
        assertFalse(state.visible)
    }

    @Test
    fun theRowsThatShowInADragDoNotHideAgainInTheSameDrag() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-200f)
        state.endDrag()
        state.connection.onPreScroll(Offset(0f, 120f), NestedScrollSource.UserInput)
        assertTrue(state.visible)
        // The rows push the content down while they show, which reads as a scroll down under the finger.
        state.connection.onPreScroll(Offset(0f, -150f), NestedScrollSource.UserInput)
        state.scroll(-150f)
        assertTrue(state.visible, "The rows stay until the finger lifts.")
        state.endDrag()
        state.scroll(-150f)
        assertFalse(state.visible, "A new drag down hides them again.")
    }

    @Test
    fun theRowsThatHideInADragDoNotShowAgainInTheSameDrag() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-120f)
        assertFalse(state.visible)
        // The content moves up under the finger while the rows hide.
        state.connection.onPreScroll(Offset(0f, 150f), NestedScrollSource.UserInput)
        assertFalse(state.visible)
    }

    @Test
    fun aChangeHoldsOnlyWhileTheRowsMove() {
        // A mouse wheel or a trackpad never lifts a finger: the rows react again once they stop moving.
        val time = TestTimeSource()
        val state = HideOnScrollState(hideDistance, time)
        state.scroll(-200f)
        assertFalse(state.visible)
        state.connection.onPreScroll(Offset(0f, 150f), NestedScrollSource.UserInput)
        assertFalse(state.visible, "The rows are still hiding.")
        time += 1.seconds
        state.connection.onPreScroll(Offset(0f, 150f), NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }

    @Test
    fun aShortShakeBackDoesNotStartTheDistanceAgain() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-200f)
        state.endDrag()
        state.connection.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput)
        state.connection.onPreScroll(Offset(0f, -4f), NestedScrollSource.UserInput)
        state.connection.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }

    @Test
    fun theRowsShowWhenTheContentIsBackAtTheTop() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-200f)
        state.endDrag()
        // A fling up that reaches the top: the content takes 20 px and 30 px are left over.
        state.connection.onPostScroll(Offset(0f, 20f), Offset(0f, 30f), NestedScrollSource.SideEffect)
        assertTrue(state.visible)
    }

    @Test
    fun aFlingUpShowsTheRows() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-200f)
        state.endDrag()
        repeat(4) { state.connection.onPreScroll(Offset(0f, 40f), NestedScrollSource.SideEffect) }
        assertTrue(state.visible)
        // A fling does not lock the rows: the next drag down hides them.
        state.scroll(-150f)
        assertFalse(state.visible)
    }

    @Test
    fun aDirectionChangeStartsTheDistanceAgain() {
        val state = HideOnScrollState(hideDistance)
        state.scroll(-80f)
        state.connection.onPreScroll(Offset(0f, 120f), NestedScrollSource.UserInput)
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
    fun contentThatFitsOnceTheRowsHideShowsThemAgain() = runComposeUiTest {
        // The content is a little taller than the screen with the rows, and fits without them, like the statistics
        // of all tournaments in a wide window. A mouse wheel then has nothing to scroll up.
        setContent {
            val state = rememberHideOnScrollState()
            CompositionLocalProvider(LocalHideOnScroll provides state) {
                Column(Modifier.height(400.dp).hideOnScroll(state)) {
                    HideOnScrollTabs {
                        Box(Modifier.height(100.dp).testTag("tabs")) { Text("Tabs") }
                    }
                    val scroll = rememberScrollState()
                    ShowTabsAtTop(scroll)
                    Column(Modifier.weight(1f).verticalScroll(scroll).testTag("content")) {
                        repeat(9) { Text("Row $it", Modifier.height(40.dp)) }
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
