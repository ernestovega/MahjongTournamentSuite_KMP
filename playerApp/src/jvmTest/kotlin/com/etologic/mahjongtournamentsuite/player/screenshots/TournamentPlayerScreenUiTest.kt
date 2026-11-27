package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.player.PlayerTab
import com.etologic.mahjongtournamentsuite.player.TableRoute
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerRoute
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerScreen
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import kotlin.test.Test

/** The screen of a player: a player that the tournament no longer has, the choice of another tournament and a tap on a table. */
@OptIn(ExperimentalTestApi::class)
class TournamentPlayerScreenUiTest {
    private fun ComposeUiTest.show(content: @Composable (NavHostController) -> Unit) = setContent {
        PlayerFrame(
            modules = { playerModules() },
            dark = false,
            destinations = {
                composable<TableRoute> { Text("Table ${it.toRoute<TableRoute>().roundId}-${it.toRoute<TableRoute>().tableId}") }
                composable<TournamentPlayerRoute> { Text("Player in ${it.toRoute<TournamentPlayerRoute>().tournamentId}") }
            },
            content = content,
        )
    }

    private fun ComposeUiTest.choose(field: String, option: String) {
        onAllNodesWithText(field).onFirst().performClick()
        waitForText(option)
        onAllNodesWithText(option).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun aPlayerThatTheTournamentNoLongerHasSaysSo() = runComposeUiTest {
        show { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 99)) }
        waitForText(playerText("tables_player_empty_message"))
    }

    @Test
    fun aTapOnATableOpensIt() = runComposeUiTest {
        show { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1)) }
        waitForText("EMA 04010001", substring = true)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasClickAction() and hasText("Round 1 - Table 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasClickAction() and hasText("Round 1 - Table 1", substring = true)).onFirst().performClick()
        waitForText("Table 1-1")
    }

    @Test
    fun theTournamentChoiceGoesBackFromAllAndOpensTheOtherTournament() = runComposeUiTest {
        show { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1, tab = PlayerTab.STATISTICS)) }
        waitForText(uiText("stats_totals"))
        val all = playerText("player_tournaments_all")
        choose("Madrid Open 2026", all)
        waitForText(uiText("stat_tournaments"))
        choose(all, "Madrid Open 2026")
        waitForText(uiText("stats_totals"))
        choose("Madrid Open 2026", "Lisbon Cup 2025")
        waitForText("Player in lisbon-cup-2025")
    }
}
