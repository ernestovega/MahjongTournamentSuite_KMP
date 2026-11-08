package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.level_info
import mahjongtournamentsuite.ui.generated.resources.rivals_info
import mahjongtournamentsuite.ui.generated.resources.skill_deal_in_rate_info
import org.jetbrains.compose.resources.stringResource

/**
 * The tooltips with the most text, near each edge of a small window.
 * Every tooltip must stay inside the window, so the reader can see all of its text.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipLayoutUiTest {
    private val corners = listOf(
        Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd, Alignment.Center,
        Alignment.TopCenter, Alignment.BottomCenter, Alignment.CenterStart, Alignment.CenterEnd,
    )

    /** Hovers the info icon, then checks that the tooltip that starts with [firstLine] is inside the window. */
    private fun DesktopComposeUiTest.checkTooltip(width: Int, height: Int, firstLine: String) {
        onNodeWithContentDescription("More information").performTouchInput { longClick() }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val bounds = onNodeWithText(firstLine, substring = true).fetchSemanticsNode().boundsInRoot
        val windowWidth = density.density * width
        val windowHeight = density.density * height
        assertTrue(bounds.left >= 0f, "Tooltip starts left of the window: $bounds")
        assertTrue(bounds.top >= 0f, "Tooltip starts above the window: $bounds")
        assertTrue(bounds.right <= windowWidth, "Tooltip ends right of the window: $bounds, window $windowWidth")
        assertTrue(bounds.bottom <= windowHeight, "Tooltip ends under the window: $bounds, window $windowHeight")
    }

    private fun check(width: Int, height: Int, alignment: Alignment, firstLine: String, text: @androidx.compose.runtime.Composable () -> String) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                MtsTheme(useDarkTheme = false) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(alignment)) { InfoTip(text()) }
                    }
                }
            }
            checkTooltip(width, height, firstLine)
        }

    private val levelInfoText: @androidx.compose.runtime.Composable () -> String =
        { stringResource(Res.string.level_info, 40, 50, 25, 8, 32, 30, 100) }
    private val rivalsInfoText: @androidx.compose.runtime.Composable () -> String =
        { stringResource(Res.string.rivals_info, 3, 4, 2, 1) }
    private val dealInText: @androidx.compose.runtime.Composable () -> String =
        { stringResource(Res.string.skill_deal_in_rate_info) }

    @Test
    fun levelInfoFitsInASmallPhoneAtEveryEdge() =
        corners.forEach { check(320, 568, it, "The skill score goes from 0 to 100", levelInfoText) }

    @Test
    fun rivalsInfoFitsInASmallPhoneAtEveryEdge() =
        corners.forEach { check(320, 568, it, "Rivals are ordered by a score", rivalsInfoText) }

    @Test
    fun levelInfoFitsInALandscapePhone() =
        corners.forEach { check(640, 320, it, "The skill score goes from 0 to 100", levelInfoText) }

    @Test
    fun shortTooltipFitsInATinyWindow() =
        corners.forEach { check(240, 240, it, "This is how often you discard", dealInText) }

    @Test
    fun levelMedalTooltipShowsTheProvisionalNoteOnItsOwnLine() = runDesktopComposeUiTest(320, 568) {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopEnd)) {
                        LevelMedalTag(PlayerLevel.entries.first(), skillScore = 42.0, provisional = true, size = 40.dp)
                    }
                }
            }
        }
        onNodeWithContentDescription("Level Beginner").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val tooltip = onNodeWithText("skill score 42", substring = true).fetchSemanticsNode()
        val text = tooltip.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        assertTrue(text.contains(".\nIt is provisional"), "The provisional note is not on its own line: $text")
        assertTrue(tooltip.boundsInRoot.right <= density.density * 320, "Tooltip ends right of the window: ${tooltip.boundsInRoot}")
    }
}
