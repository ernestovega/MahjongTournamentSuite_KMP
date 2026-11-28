package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.player.TableScreen
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerRoute
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import kotlin.test.Test

/** A tap on a player of a finished table opens the screen of that player. */
@OptIn(ExperimentalTestApi::class)
class TableScreenNavigationUiTest {
    private fun ComposeUiTest.show(roundId: Int) {
        setContent {
            PlayerFrame(
                modules = { playerModules() },
                dark = false,
                destinations = {
                    composable<TournamentPlayerRoute> { Text("Player ${it.toRoute<TournamentPlayerRoute>().slotId}") }
                },
            ) { nav -> TableScreen(nav, ONGOING, roundId = roundId, tableId = 1) }
        }
    }

    private fun ComposeUiTest.tap(text: String) {
        val matcher = hasText(text, substring = true, ignoreCase = true) and hasClickAction()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(matcher).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun aPlayerOfAFinishedTableOpensItsScreen() = runComposeUiTest {
        show(roundId = 1)
        tap("Jonas Becker")
        waitForText("Player ", substring = true)
    }
}
