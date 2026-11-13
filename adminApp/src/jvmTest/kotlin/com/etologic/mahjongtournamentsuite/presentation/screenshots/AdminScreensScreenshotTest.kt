package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.screen.ActivityScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.ErrorsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.GlobalUsersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerBaseScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RankingStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.SignInScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.StatisticsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TablesScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TeamsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TimerStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentsScreen
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotAuthRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
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

    @Test
    fun teams() = adminScreenshots("teams", ready = { waitForText("Red Dragons", substring = true) }) { nav -> TeamsScreen(nav, ONGOING) }

    @Test
    fun tables() = adminScreenshots("tables", ready = { waitForText("13(T4)", substring = true) }) { nav -> TablesScreen(nav, ONGOING) }

    @Test
    fun playerBase() = adminScreenshots("player-base", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav -> PlayerBaseScreen(nav) }

    @Test
    fun users() = adminScreenshots("users", ready = { waitForText("ada@example.test", substring = true) }) { nav -> GlobalUsersScreen(nav) }

    @Test
    fun statistics() = adminScreenshots("statistics", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) { nav ->
        StatisticsScreen(nav, StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = ONGOING))
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
