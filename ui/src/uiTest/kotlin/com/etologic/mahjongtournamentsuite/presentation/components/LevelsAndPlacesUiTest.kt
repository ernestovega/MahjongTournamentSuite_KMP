package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** The level of a player with the levels on (rated, provisional, not rated yet) and the places chart with its windows. */
@OptIn(ExperimentalTestApi::class)
class LevelsAndPlacesUiTest {
    private val madrid = Tournament(id = "t-1", name = "Madrid", isTeams = false, numPlayers = 4, numRounds = 30, eventStartDate = "2025-05-01")

    private fun result(round: Int, handsPlayed: Int) = TableResult(
        tournamentId = madrid.id, roundId = round, tableId = 1, seat = 0, position = round % 4 + 1, score = 10,
        points = 2.0, hasHands = true, handsPlayed = handsPlayed, handsWon = handsPlayed / 4, selfDraws = 1, dealIns = 1,
        winningScoreSum = 40 * (handsPlayed / 4), bestHand = 24,
    )

    private fun player(tables: Int, handsPerTable: Int) = PlayerStatistics(1, (1..tables).map { result(it, handsPerTable) })

    private fun androidx.compose.ui.test.ComposeUiTest.show(content: @androidx.compose.runtime.Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalFeatureFlags provides FeatureFlags(levelsEnabled = true)) {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(900.dp).verticalScroll(rememberScrollState())) { content() }
            }
        }
    }

    @Test
    fun aRatedPlayerShowsTheLevelTheScoreAndTheLegend() = runComposeUiTest {
        val rated = player(tables = 12, handsPerTable = 16)
        show {
            SkillsBlock(rated, chartHeight = 240.dp)
            LevelMedalWithScore(rated)
        }
        assertTrue(onAllNodesWithText("Skill score", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("Provisional").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aPlayerWithFewHandsIsProvisionalOrNotRated() = runComposeUiTest {
        show {
            SkillsBlock(player(tables = 2, handsPerTable = 8), chartHeight = 240.dp)
            SkillsBlock(player(tables = 1, handsPerTable = 4), chartHeight = 240.dp)
            LevelMedalWithScore(player(tables = 1, handsPerTable = 4))
        }
        onNodeWithText("Provisional").performScrollTo().assertIsDisplayed()
        onNodeWithText("Level: not rated yet").performScrollTo().assertIsDisplayed()
        onNodeWithText("Needs 8 hands").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun thePlacesChartShowsTheLastTablesOrAll() = runComposeUiTest {
        show { PlacesCard(player(tables = 25, handsPerTable = 16), listOf(madrid), Modifier) }
        onNodeWithText("Last 10").performClick()
        waitForIdle()
        onNodeWithText("Last 20").performClick()
        waitForIdle()
        onNodeWithText("ALL(25)").performClick()
        waitForIdle()
        onNodeWithText("ALL(25)").assertIsDisplayed()
    }

    @Test
    fun onePlayedTableHasNoWindows() = runComposeUiTest {
        show { PlacesCard(player(tables = 1, handsPerTable = 16), listOf(madrid), Modifier) }
        assertTrue(onAllNodesWithText("ALL(1)").fetchSemanticsNodes().isEmpty())
    }
}
