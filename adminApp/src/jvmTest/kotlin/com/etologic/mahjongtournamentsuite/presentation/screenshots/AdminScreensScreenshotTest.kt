package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.screen.ActivityScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.ErrorsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.GlobalUsersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerBaseScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerRoundTablesTag
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RankingStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.SignInScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.StatisticsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TeamsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TimerStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentsScreen
import com.etologic.mahjongtournamentsuite.screenshots.ScreenSize
import com.etologic.mahjongtournamentsuite.screenshots.Variant
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotAuthRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotTournamentRepository
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

/** Every screen of the admin app with the data of ScreenshotData. */
@OptIn(ExperimentalTestApi::class)
class AdminScreensScreenshotTest {
    @Test
    fun signIn() = adminScreenshots("sign-in", modules = { adminModules(auth = ScreenshotAuthRepository(profile = null)) }) { nav -> SignInScreen(nav) }

    @Test
    fun tournaments() = adminScreenshots("tournaments", ready = { waitForText("Madrid Open 2026") }) { nav -> TournamentsScreen(nav) }

    /** The DEV menu of an administrator, open. On a phone it is an entry of a three-dots menu with its own menu. */
    @Test
    fun tournamentsDevMenu() = adminScreenshots(
        "tournaments-dev-menu",
        variants = listOf(ScreenSize.Desktop, ScreenSize.Phone).flatMap { listOf(Variant(it, false), Variant(it, true)) },
        ready = {
            waitForText("Madrid Open 2026")
            val menus = onAllNodes(hasContentDescription("Menu") and hasClickAction())
            var index = 0
            while (onAllNodesWithText("DEV").fetchSemanticsNodes().isEmpty() && index < menus.fetchSemanticsNodes().size) {
                menus[index++].performClick()
                waitForIdle()
            }
            onAllNodesWithText("DEV").onFirst().performClick()
            waitForText("Staging (test site)")
        },
    ) { nav -> TournamentsScreen(nav) }

    @Test
    fun tournament() = adminScreenshots("tournament", ready = { waitForText("Madrid Open 2026") }) { nav -> TournamentScreen(nav, ONGOING) }

    @Test
    fun players() = adminScreenshots("players", ready = { waitForText("04010001", substring = true) }) { nav -> PlayersScreen(nav, ONGOING) }

    /** A long tournament: the round and table strip under each player scrolls sideways when the rounds do not fit. */
    @Test
    fun playersManyRounds() = adminScreenshots(
        "players-many-rounds",
        modules = { adminModules(tournaments = ManyRoundsTournamentRepository()) },
        ready = { waitForText("04010001", substring = true) },
    ) { nav -> PlayersScreen(nav, ONGOING) }

    /** The 24 rounds after a swipe: the strips are scrolled to the middle and go past both edges of the row. */
    @Test
    fun playersManyRoundsScrolled() = adminScreenshots(
        "players-many-rounds-scrolled",
        modules = { adminModules(tournaments = ManyRoundsTournamentRepository()) },
        ready = {
            waitForText("04010001", substring = true)
            onAllNodesWithTag(PlayerRoundTablesTag).fetchSemanticsNodes().indices.forEach { index ->
                // A slow swipe, so it does not fling to the end.
                onAllNodesWithTag(PlayerRoundTablesTag)[index].performTouchInput {
                    swipeLeft(startX = right * 0.8f, endX = right * 0.5f, durationMillis = 1_000)
                }
            }
            waitForIdle()
        },
    ) { nav -> PlayersScreen(nav, ONGOING) }

    /** Seven rounds, the most usual length: the strip fits without scrolling. */
    @Test
    fun playersSevenRounds() = adminScreenshots(
        "players-seven-rounds",
        modules = { adminModules(tournaments = ManyRoundsTournamentRepository(rounds = 7)) },
        ready = { waitForText("04010001", substring = true) },
    ) { nav -> PlayersScreen(nav, ONGOING) }

    @Test
    fun teams() = adminScreenshots("teams", ready = { waitForText("Red Dragons", substring = true) }) { nav -> TeamsScreen(nav, ONGOING) }

    @Test
    fun playerBase() = adminScreenshots("player-base", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav -> PlayerBaseScreen(nav) }

    @Test
    fun users() = adminScreenshots("users", ready = { waitForText("ada@example.test", substring = true) }) { nav -> GlobalUsersScreen(nav) }

    /** An admin chose some EMA players: the bar offers to delete their accounts. */
    @Test
    fun playerBaseSelected() = adminScreenshots(
        "player-base-selected",
        ready = {
            waitForText("LUCÍA MARTÍN", substring = true)
            onAllNodesWithText(adminText("select_all_shown", 0).substringBefore("(").trim(), substring = true).onFirst().performClick()
            waitForIdle()
        },
    ) { nav -> PlayerBaseScreen(nav) }

    /** An admin chose two accounts: the bar offers to delete them. */
    @Test
    fun usersSelected() = adminScreenshots(
        "users-selected",
        ready = {
            waitForText("ada@example.test", substring = true)
            onAllNodes(hasContentDescription("ada@example.test") and hasClickAction()).onFirst().performClick()
            waitForIdle()
        },
    ) { nav -> GlobalUsersScreen(nav) }

    @Test
    fun statistics() = adminScreenshots("statistics", ready = { waitForText(uiText("stats_leaders")) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = ONGOING))
    }

    /** The statistics of one person: the second tab row is as wide as the first one. */
    @Test
    fun statisticsPerson() = adminScreenshots("statistics-person", ready = { waitForText(uiText("stats_skills"), substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PERSON.name, emaId = ScreenshotData.playerAccount.emaId))
    }

    /** One player in one tournament, opened from the tournament statistics: standing, then the cards with By round. */
    @Test
    fun statisticsPlayer() = adminScreenshots("statistics-player", ready = { waitForText(uiText("stats_skills"), substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.PLAYER.name, tournamentId = ONGOING, slotId = 1))
    }

    @Test
    fun activity() = adminScreenshots("activity") { nav -> ActivityScreen(nav) }

    @Test
    fun errors() = adminScreenshots("errors") { nav -> ErrorsScreen(nav) }

    @Test
    fun timer() = adminScreenshots("timer", ready = { waitForText("2:00:00") }) { TimerStandaloneScreen(ONGOING, initialRound = 3) }

    @Test
    fun timerIndependent() = adminScreenshots("timer-independent") { TimerStandaloneScreen(null) }

    @Test
    fun rankingStandalone() = adminScreenshots("ranking-standalone", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) {
        RankingStandaloneScreen(ONGOING, clock = FixedClock)
    }
}

/** A "loaded at" time that does not change between runs. */
private object FixedClock : Clock {
    override fun now(): Instant = Instant.parse("2026-05-16T10:00:00Z")
}

/** The tables of [ONGOING] repeated over [rounds] rounds, with the table numbers shuffled so every round differs. */
private class ManyRoundsTournamentRepository(private val rounds: Int = 24) : ScreenshotTournamentRepository() {
    override suspend fun listTournamentTables(tournamentId: String, roundId: Int?, refreshMode: RefreshMode): AppResult<List<TournamentTable>> {
        val base = ScreenshotData.tables(tournamentId)
        val tables = (1..rounds).flatMap { round ->
            base.filter { it.roundId == (round - 1) % 4 + 1 }.map { table ->
                table.copy(roundId = round, tableId = (table.tableId * 7 + round * 5) % 40 + 1)
            }
        }
        return AppResult.Success(tables.filter { roundId == null || it.roundId == roundId })
    }
}
