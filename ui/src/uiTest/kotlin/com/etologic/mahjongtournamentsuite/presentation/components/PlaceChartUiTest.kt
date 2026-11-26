package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/**
 * The small chart of the place after each round (23-24 nov): under the table of the Results card of the player app
 * and under the numbers of the Standing card of the statistics. One place is no line, so it shows from two places.
 */
@OptIn(ExperimentalTestApi::class)
class PlaceChartUiTest {
    private fun row(round: Int, place: Int?) = ResultsSummaryRow(
        round = round,
        table = round,
        wind = null,
        points = 2.0,
        score = 10,
        totalPoints = 2.0 * round,
        totalScore = 10 * round,
        place = place,
    )

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent { MtsTheme(useDarkTheme = false) { content() } }

    private fun ComposeUiTest.charts(count: Int) = onAllNodesWithTag(RESULTS_PLACE_CHART_TAG).assertCountEquals(count)

    @Test
    fun theResultsCardDrawsTheChartFromTwoPlaces() = runComposeUiTest {
        show { ResultsSummaryCard("Open", "Ana", "ES", "10990001", listOf(row(1, 5), row(2, 3), row(3, null)), playerCount = 16) }
        charts(1)
    }

    @Test
    fun theResultsCardHasNoChartWithOnePlaceOrWithoutTheNumberOfPlayers() = runComposeUiTest {
        show {
            ResultsSummaryCard("Open", "Ana", "ES", null, listOf(row(1, 5), row(2, null)), playerCount = 16)
            ResultsSummaryCard("Open", "Ana", "ES", null, listOf(row(1, 5), row(2, 3)), playerCount = 0)
        }
        charts(0)
    }

    @Test
    fun theStandingCardDrawsTheChartOfThePlacesWithAGapForARoundWithoutPlace() = runComposeUiTest {
        show { PlayerStandingCard(position = 2, points = 7.0, score = 30, places = mapOf(1 to 9, 3 to 2), of = 16) }
        charts(1)
    }

    @Test
    fun theStandingCardHasNoChartWithOnePlaceOrNone() = runComposeUiTest {
        show {
            PlayerStandingCard(position = 2, points = 7.0, score = 30, places = mapOf(1 to 9), of = 16)
            PlayerStandingCard(position = 2, points = 7.0, score = 30)
        }
        charts(0)
    }
}
