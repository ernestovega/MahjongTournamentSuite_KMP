package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerRoute
import com.etologic.mahjongtournamentsuite.player.TournamentScreen
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import kotlin.test.Test

/** A tap on a player in the ranking, the players or a team opens the screen of that player. */
@OptIn(ExperimentalTestApi::class)
class TournamentScreenNavigationUiTest {
    private fun ComposeUiTest.show() {
        setContent {
            PlayerFrame(
                modules = { playerModules() },
                dark = false,
                destinations = {
                    composable<TournamentPlayerRoute> {
                        val route = it.toRoute<TournamentPlayerRoute>()
                        Text("Player ${route.slotId} tab ${route.tab}")
                    }
                },
            ) { nav -> TournamentScreen(nav, ONGOING) }
        }
        waitForText("Madrid Open 2026", substring = true)
    }

    private fun ComposeUiTest.tap(text: String) {
        val matcher = hasText(text, substring = true, ignoreCase = true) and hasClickAction()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(matcher).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun aPlayerOfTheRankingOpensItsScreen() = runComposeUiTest {
        show()
        tap("Ranking")
        tap("Lucía Martín")
        waitForText("Player ", substring = true)
    }

    @Test
    fun aPlayerOfATeamOpensItsScreen() = runComposeUiTest {
        show()
        tap("Teams")
        tap("Red Dragons")
        tap("Lucía Martín")
        waitForText("Player ", substring = true)
    }

    @Test
    fun aTeamShowsItsStatistics() = runComposeUiTest {
        show()
        tap("Teams")
        tap("Red Dragons")
        val stats = hasText("Stats") and hasClickAction()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(stats).fetchSemanticsNodes().size > 1 }
        onAllNodes(stats).onLast().performClick()
        waitForText(uiText("stats_totals"))
    }
}
