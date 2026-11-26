package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The seats of a table as a list before the round, with the leading content alone, with new scores, and its small icons. */
@OptIn(ExperimentalTestApi::class)
class TableSeatsViewUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun seats(scores: List<Int> = listOf(0, 0, 0, 0)) = listOf("Ana", "Bea", "Carla", "Dora").mapIndexed { index, name ->
        SeatDisplay(
            name = name,
            score = scores[index].toString(),
            points = "",
            slotId = index + 1,
            country = "ES",
            level = if (index == 0) PlayerLevel.EXPERT else null,
            skillScore = if (index == 0) 62.0 else null,
            levelProvisional = false,
        )
    }

    @Test
    fun beforeTheRoundTheSeatsAreAListThatOpensAPlayer() = runDesktopComposeUiTest {
        val opened = mutableListOf<Int>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    TableSeatsView(seats(), handNumber = 1, centerLabel = "", showPoints = false, asList = true, onPlayerClick = { opened += it })
                    TableSeatsView(seats(), handNumber = 1, centerLabel = "", showPoints = false, asList = true, windsKnown = false, diffsAvailable = false)
                }
            }
        }
        onAllNodes(androidx.compose.ui.test.hasTestTag(SEATS_LIST_TAG)).assertCountEquals(2)
        onAllNodesWithText("Carla").fetchSemanticsNodes().let { assertEquals(2, it.size) }
        onAllNodesWithText("Carla")[0].performClick()
        assertEquals(listOf(3), opened)
    }

    @Test
    fun withoutTheDiffsTheLeadingContentIsAlone() = runDesktopComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                TableSeatsView(seats(), handNumber = 2, centerLabel = "", showPoints = false, diffsAvailable = false, leading = { Text("12:00 left") })
            }
        }
        onNodeWithText("12:00 left").assertIsDisplayed()
        onNodeWithText("Dora").assertIsDisplayed()
    }

    @Test
    fun aNewScoreCountsFromTheOldOne() = runDesktopComposeUiTest {
        var scores by mutableStateOf(listOf(0, 0, 0, 0))
        setContent {
            MtsTheme(useDarkTheme = false) {
                TableSeatsView(seats(scores), handNumber = 1, centerLabel = "E1", showPoints = false, animateScoreChanges = true)
            }
        }
        waitForIdle()
        scores = listOf(24, -8, -8, -8)
        mainClock.advanceTimeBy(5_000)
        waitForIdle()
        onNodeWithText("Ana").assertIsDisplayed()
    }

    @Test
    fun theSmallIconsOfATable() = runDesktopComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    ChickenHandMark()
                    SelfDrawIcon()
                    HandLabel(6)
                    RankTrophy(1)
                    RankTrophy(5)
                    PositionTrophy(4)
                    LastPlaceIcon()
                    WindIcon(SeatWind.NORTH)
                }
            }
        }
        onAllNodesWithContentDescription("Position 1").assertCountEquals(1)
        onAllNodesWithContentDescription("Position 5").assertCountEquals(1)
        onAllNodesWithContentDescription("Position 4").assertCountEquals(1)
        onNodeWithText("2").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithText(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text))
}
