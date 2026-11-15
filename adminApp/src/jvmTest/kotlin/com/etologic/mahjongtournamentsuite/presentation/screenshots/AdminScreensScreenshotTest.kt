package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.screen.ActivityScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.ErrorsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.GlobalUsersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerBaseScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RankingStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RoundControlScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.SignInScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.StatisticsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TablesScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TeamsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TimerStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentsScreen
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

    @Test
    fun teams() = adminScreenshots("teams", ready = { waitForText("Red Dragons", substring = true) }) { nav -> TeamsScreen(nav, ONGOING) }

    @Test
    fun tables() = adminScreenshots("tables", ready = { waitForText("13(T4)", substring = true) }) { nav -> TablesScreen(nav, ONGOING) }

    @Test
    fun playerBase() = adminScreenshots("player-base", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav -> PlayerBaseScreen(nav) }

    @Test
    fun users() = adminScreenshots("users", ready = { waitForText("ada@example.test", substring = true) }) { nav -> GlobalUsersScreen(nav) }

    @Test
    fun statistics() = adminScreenshots("statistics", ready = { waitForText("Top players") }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = ONGOING))
    }

    @Test
    fun activity() = adminScreenshots("activity") { nav -> ActivityScreen(nav) }

    @Test
    fun errors() = adminScreenshots("errors") { nav -> ErrorsScreen(nav) }

    @Test
    fun timer() = adminScreenshots("timer", ready = { waitForText("2:00:00") }) { TimerStandaloneScreen(ONGOING, initialRound = 3) }

    @Test
    fun roundControl() = adminScreenshots("round-control", ready = { waitForText("2:00:00") }) { nav ->
        RoundControlScreen(nav, ONGOING, initialRound = 3)
    }

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

/** The tables of [ONGOING] repeated over 24 rounds, with the table numbers shuffled so every round differs. */
private class ManyRoundsTournamentRepository : ScreenshotTournamentRepository() {
    override suspend fun listTournamentTables(tournamentId: String, roundId: Int?, refreshMode: RefreshMode): AppResult<List<TournamentTable>> {
        val base = ScreenshotData.tables(tournamentId)
        val tables = (1..24).flatMap { round ->
            base.filter { it.roundId == (round - 1) % 4 + 1 }.map { table ->
                table.copy(roundId = round, tableId = (table.tableId * 7 + round * 5) % 40 + 1)
            }
        }
        return AppResult.Success(tables.filter { roundId == null || it.roundId == roundId })
    }
}
