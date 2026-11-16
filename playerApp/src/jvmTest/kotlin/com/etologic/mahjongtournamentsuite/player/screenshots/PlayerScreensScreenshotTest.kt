package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.etologic.mahjongtournamentsuite.player.COMBINATION_DIALOG_TAG
import com.etologic.mahjongtournamentsuite.player.CombinationDialog
import com.etologic.mahjongtournamentsuite.player.Combinations
import com.etologic.mahjongtournamentsuite.player.CombinationsScreen
import com.etologic.mahjongtournamentsuite.player.CreditsDialog
import com.etologic.mahjongtournamentsuite.player.MyTableButton
import com.etologic.mahjongtournamentsuite.player.MyTableReminderDialog
import com.etologic.mahjongtournamentsuite.player.PenaltiesScreen
import com.etologic.mahjongtournamentsuite.player.PlayerTab
import com.etologic.mahjongtournamentsuite.player.RoundMoment
import com.etologic.mahjongtournamentsuite.player.SignInScreen
import com.etologic.mahjongtournamentsuite.player.StatisticsRoute
import com.etologic.mahjongtournamentsuite.player.StatisticsScreen
import com.etologic.mahjongtournamentsuite.player.TableScreen
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerRoute
import com.etologic.mahjongtournamentsuite.player.TournamentPlayerScreen
import com.etologic.mahjongtournamentsuite.player.TournamentScreen
import com.etologic.mahjongtournamentsuite.player.TournamentsScreen
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.screenshots.componentVariants
import com.etologic.mahjongtournamentsuite.screenshots.dialogVariants
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import java.util.Locale
import kotlin.test.Test

/** Every screen and tab of the player app with the data of ScreenshotData, signed in as Lucía (player 1). */
@OptIn(ExperimentalTestApi::class)
class PlayerScreensScreenshotTest {
    /** Opens a main tab of the tournament by its title, once the tournament is loaded. */
    private fun ComposeUiTest.openTab(vararg titles: String) {
        waitForText("Madrid Open 2026", substring = true)
        waitUntil(timeoutMillis = 10_000) { titles.any { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } }
        val title = titles.first { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(title).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun myTableReminder() = playerScreenshots(
        "dialogs/my-table-reminder",
        variants = dialogVariants,
        ready = { waitForText(if (Locale.getDefault().language == "es") "Ir a mi mesa" else "Go to my table") },
    ) {
        MyTableReminderDialog("Madrid Open 2026", roundId = 3, tableId = 7, moment = RoundMoment.StartedAgo(12 * 60_000L), onOpen = {}, onDismiss = {})
    }

    @Test
    fun combinationsCredits() = playerScreenshots(
        "dialogs/combinations-credits",
        variants = dialogVariants,
        ready = { waitForText("www.martinpersson.org") },
    ) {
        CreditsDialog(onDismiss = {})
    }

    /** The dialog of a combination: its tiles, its detail and the examples of the Green Book. */
    private fun combinationDialog(id: String) = playerScreenshots(
        "dialogs/combination-${id.replace('_', '-')}",
        variants = dialogVariants,
        ready = { waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(COMBINATION_DIALOG_TAG).fetchSemanticsNodes().isNotEmpty() } },
    ) {
        CombinationDialog(Combinations.first { it.id == id }, onDismiss = {})
    }

    @Test
    fun combinationWithExamples() = combinationDialog("big_four_winds")

    @Test
    fun combinationWithoutExamples() = combinationDialog("pure_double_chow")

    @Test
    fun myTableButton() = playerScreenshots("components/my-table-button", variants = componentVariants, ready = { waitForText("Go to my table") }) {
        Box(Modifier.fillMaxSize()) { MyTableButton(onClick = {}, modifier = Modifier.align(Alignment.BottomEnd)) }
    }

    @Test
    fun signIn() = playerScreenshots("sign-in", modules = { playerModules(account = null) }) { nav -> SignInScreen(nav, null) }

    @Test
    fun tournaments() = playerScreenshots("tournaments", ready = { waitForText("Madrid Open 2026", substring = true) }) { nav ->
        TournamentsScreen(nav)
    }

    @Test
    fun tournamentsGuest() = playerScreenshots(
        "tournaments-guest",
        modules = { playerModules(account = null) },
        ready = { waitForText("Madrid Open 2026", substring = true) },
    ) { nav -> TournamentsScreen(nav) }

    @Test
    fun tournamentTables() = playerScreenshots("tournament-tables", ready = { openTab("Tables") }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentRanking() = playerScreenshots("tournament-ranking", ready = { openTab("Ranking") }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentPlayers() = playerScreenshots("tournament-players", ready = { openTab("Players") }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentPlayerTables() = playerScreenshots("tournament-player-tables", ready = { waitForText("EMA 04010001", substring = true) }) { nav ->
        TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1))
    }

    @Test
    fun tournamentPlayerStatistics() = playerScreenshots(
        "tournament-player-statistics",
        ready = { waitForText("EMA 04010001", substring = true) },
    ) { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1, tab = PlayerTab.STATISTICS)) }

    @Test
    fun tournamentPlayerAllTournaments() = playerScreenshots(
        "tournament-player-all-tournaments",
        ready = {
            waitForText("Totals")
            onAllNodesWithText("Madrid Open 2026").onFirst().performClick()
            waitForIdle()
            onAllNodesWithText("All").onFirst().performClick()
            waitForText("Tournaments")
        },
    ) { nav -> TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1, tab = PlayerTab.STATISTICS)) }

    @Test
    fun tournamentTeams() = playerScreenshots("tournament-teams", ready = { openTab("Teams") }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentStatistics() = playerScreenshots("tournament-statistics", ready = { openTab("Stats", "Statistics") }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tournamentInfo() = playerScreenshots("tournament-info", ready = { openTab("Info") }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun tableCompleted() = playerScreenshots("table-completed", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav ->
        TableScreen(nav, ONGOING, roundId = 1, tableId = 1)
    }

    @Test
    fun tableInPlay() = playerScreenshots("table-in-play", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav ->
        TableScreen(nav, ONGOING, roundId = 3, tableId = 1)
    }

    @Test
    fun combinations() = playerScreenshots("combinations", ready = { waitForText("Pure double chow") }) { nav -> CombinationsScreen(nav) }

    @Test
    fun combinationsFolded() = playerScreenshots(
        "combinations-folded",
        ready = {
            waitForText("Pure double chow")
            onNode(hasText("Fold all") or hasContentDescription("Fold all")).performClick()
            waitForIdle()
        },
    ) { nav -> CombinationsScreen(nav) }

    @Test
    fun combinationsOneValue() = playerScreenshots(
        "combinations-one-value",
        ready = {
            waitForText("Pure double chow")
            onNode(hasText("Fold all") or hasContentDescription("Fold all")).performClick()
            onNode(hasText("24 points (9)")).performScrollTo().performClick()
            waitForIdle()
        },
    ) { nav -> CombinationsScreen(nav) }

    @Test
    fun penalties() = playerScreenshots("penalties", ready = { waitForText("Dead hand") }) { nav -> PenaltiesScreen(nav) }

    @Test
    fun statisticsTournament() = playerScreenshots("statistics-tournament", ready = { waitForText("Madrid Open 2026", substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = ONGOING))
    }

    @Test
    fun statisticsPlayer() = playerScreenshots("statistics-player", ready = { waitForText("EMA 04010001", substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PLAYER.name, tournamentId = ONGOING, slotId = 1))
    }

    @Test
    fun statisticsPerson() = playerScreenshots("statistics-person", ready = { waitForText("Skills", substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PERSON.name))
    }

    /** The place history of a person with several tournaments: each tournament has a band of its own color. */
    @Test
    fun statisticsPersonPlaces() = playerScreenshots(
        "statistics-person-places",
        ready = {
            waitForText("Skills", substring = true)
            onAllNodesWithText("Place in the last", substring = true, ignoreCase = true).onFirst().performScrollTo()
            // The chart is under its title. A swipe brings it into view.
            onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = centerY + 160f, endY = centerY - 160f) }
            waitForIdle()
        },
    ) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PERSON.name))
    }
}
