package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.presentation.screenshots.adminModules
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

/**
 * The Players screen of a tournament (24-nov): Teams, ID cards, ID list and Invitations at the left of the top bar,
 * Import and Export at the right, and the tables of every round of a player folded until the organizer opens them.
 * The Teams screen opens from here and has no Import or Export of its own.
 */
@OptIn(ExperimentalTestApi::class)
class PlayersTopBarAndTablesUiTest {
    private fun ComposeUiTest.show(content: @Composable (NavHostController) -> Unit) {
        setContent {
            val koin = koinApplication {
                modules(module { single { Logger.withTag("ui-test") } })
                modules(adminModules())
            }.koin
            KoinContext(koin) {
                MtsTheme(useDarkTheme = false) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "screen") {
                        composable("screen") { content(navController) }
                    }
                }
            }
        }
    }

    private fun button(label: String): SemanticsMatcher = hasText(label) or hasContentDescription(label)

    private fun ComposeUiTest.count(label: String) = onAllNodes(button(label)).fetchSemanticsNodes().size

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aTeamTournamentHasTeamsIdCardsIdListAndInvitationsInTheTopBar() = runComposeUiTest {
        show { PlayersScreen(it, ScreenshotData.ONGOING) }
        waitForText("04010001")

        for (label in listOf("Teams", "ID cards", "ID list", "Invitations", "Import", "Export")) {
            check(count(label) >= 1) { "No $label button in the top bar of Players." }
        }
    }

    @Test
    fun anIndividualTournamentHasNoTeamsButton() = runComposeUiTest {
        show { PlayersScreen(it, ScreenshotData.INDIVIDUAL) }
        waitUntil(timeoutMillis = 10_000) { count("ID cards") > 0 }
        waitForIdle()

        check(count("Teams") == 0) { "An individual tournament offers Teams." }
    }

    @Test
    fun theTablesOfEveryRoundStartFoldedAndOpenPerPlayer() = runComposeUiTest {
        show { PlayersScreen(it, ScreenshotData.ONGOING) }
        waitForText("04010001")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(PlayerRoundTablesToggleTag).fetchSemanticsNodes().isNotEmpty() }

        onAllNodesWithTag(PlayerRoundTablesTag).assertCountEquals(0)
        check(onAllNodesWithText("Tables").fetchSemanticsNodes().isNotEmpty() || count("Tables") > 0) { "No Tables title over the buttons." }

        onAllNodesWithTag(PlayerRoundTablesToggleTag)[0].performClick()
        waitForIdle()
        onAllNodesWithTag(PlayerRoundTablesTag).assertCountEquals(1)

        onAllNodesWithTag(PlayerRoundTablesToggleTag)[1].performClick()
        waitForIdle()
        onAllNodesWithTag(PlayerRoundTablesTag).assertCountEquals(2)

        // The buttons ignore a second press within half a second (debounce).
        Thread.sleep(700)
        onAllNodesWithTag(PlayerRoundTablesToggleTag)[0].performClick()
        waitForIdle()
        onAllNodesWithTag(PlayerRoundTablesTag).assertCountEquals(1)
    }

    @Test
    fun theTeamsScreenHasNoImportOrExport() = runComposeUiTest {
        show { TeamsScreen(it, ScreenshotData.ONGOING) }
        waitForText("Red Dragons")

        check(count("Import") == 0) { "Teams still has Import." }
        check(count("Export") == 0) { "Teams still has Export." }
    }
}
