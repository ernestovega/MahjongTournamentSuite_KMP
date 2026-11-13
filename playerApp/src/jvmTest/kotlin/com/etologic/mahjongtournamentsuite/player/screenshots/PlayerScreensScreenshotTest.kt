package com.etologic.mahjongtournamentsuite.player.screenshots

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.etologic.mahjongtournamentsuite.player.MyTableReminderDialog
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
import com.etologic.mahjongtournamentsuite.screenshots.dialogVariants
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
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
    fun myTableReminder() = playerScreenshots("dialogs/my-table-reminder", variants = dialogVariants, ready = { waitForText("Go to my table") }) {
        MyTableReminderDialog("Madrid Open 2026", roundId = 3, tableId = 7, moment = RoundMoment.StartedAgo(12 * 60_000L), onOpen = {}, onDismiss = {})
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
    fun tournamentPlayerTables() = playerScreenshots("tournament-player-tables", ready = { waitForText("Madrid Open 2026", substring = true) }) { nav ->
        TournamentPlayerScreen(nav, TournamentPlayerRoute(ONGOING, slotId = 1))
    }

    @Test
    fun tournamentPlayerStatistics() = playerScreenshots(
        "tournament-player-statistics",
        ready = { waitForText("Madrid Open 2026", substring = true) },
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
}
