package com.etologic.mahjongtournamentsuite.presentation.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTestApi::class)
class TrackActivityUiTest {
    @Test
    fun aClickCountsAsActivityAndStillReachesTheButton() = runComposeUiTest {
        val clock = TestTimeSource()
        val tracker = ActivityTracker(clock)
        var clicks = 0
        setContent {
            Box(Modifier.trackActivity(tracker)) {
                Text("Save", Modifier.size(80.dp).testTag("save").clickable { clicks++ })
            }
        }
        clock += 90.seconds
        assertEquals(90.seconds, tracker.idleFor())

        onNodeWithTag("save").performClick()

        assertEquals(1, clicks, "the tracker must not consume the click")
        assertTrue(tracker.idleFor() < 1.seconds, "idle time is reset")
    }

    @Test
    fun withoutInputTheIdleTimeKeepsGrowing() = runComposeUiTest {
        val clock = TestTimeSource()
        val tracker = ActivityTracker(clock)
        setContent { Box(Modifier.trackActivity(tracker)) { Text("Nothing happens") } }

        clock += 3.seconds

        assertEquals(3.seconds, tracker.idleFor())
        assertTrue(tracker.idleFor() > Duration.ZERO)
    }
}
