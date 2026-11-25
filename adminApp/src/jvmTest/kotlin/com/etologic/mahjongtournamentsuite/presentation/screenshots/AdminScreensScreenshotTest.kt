package com.etologic.mahjongtournamentsuite.presentation.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.screen.ActivityScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.ErrorsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.GlobalUsersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.OrganizationsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerBaseScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerRoundTablesTag
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayerRoundTablesToggleTag
import com.etologic.mahjongtournamentsuite.presentation.screen.PlayersScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RankingStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RegistrationsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RolesScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RoundControlContent
import com.etologic.mahjongtournamentsuite.presentation.screen.RoundControlScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.RoundTimerController
import com.etologic.mahjongtournamentsuite.presentation.screen.SignInScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.StatisticsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TeamsScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TimerContent
import com.etologic.mahjongtournamentsuite.presentation.screen.TimerStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TournamentsScreen
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.screenshots.ScreenSize
import com.etologic.mahjongtournamentsuite.screenshots.Variant
import com.etologic.mahjongtournamentsuite.screenshots.componentVariants
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotAuthRepository
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData.ONGOING
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotTournamentRepository
import org.koin.compose.koinInject
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

    /** The "More" menu of the top bar of a tournament: Players, Tables IDs, Stats, EMA report and Activity. */
    @Test
    fun tournamentMoreMenu() = adminScreenshots(
        "tournament-more-menu",
        variants = listOf(ScreenSize.Desktop, ScreenSize.Phone).flatMap { listOf(Variant(it, false), Variant(it, true)) },
        ready = {
            waitForText("Madrid Open 2026")
            // On a phone the options are in the three-dots menu of the top bar; on a wide screen in "More".
            if (onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty()) {
                onAllNodesWithText("More").onFirst().performClick()
            } else {
                onAllNodes(hasContentDescription("Menu") and hasClickAction()).onLast().performClick()
            }
            waitForIdle()
            waitForText("EMA report")
        },
    ) { nav -> TournamentScreen(nav, ONGOING) }

    /** Round control, the plan B of the Timer, on the round in play. */
    @Test
    fun roundControl() = adminScreenshots("round-control", ready = { waitForText("2:00:00") }) { nav ->
        RoundControlScreen(ONGOING, initialRound = 3, onBack = { nav.popBackStack() })
    }

    /** Round 2 is over (round 3 is in play) but two of its tables stayed open: badge on its chip and the close button. */
    @Test
    fun roundControlOpenTables() = adminScreenshots("round-control-open-tables", ready = { waitForText("2:00:00") }) {
        val repository = koinInject<TournamentRepository>()
        val controller = remember { RoundTimerController(repository, ONGOING, initialRound = 3) }
        val tables = remember {
            ScreenshotData.tables(ONGOING).map { if (it.roundId == 2 && it.tableId in setOf(1, 3)) it.copy(isCompleted = false) else it }
        }
        RoundControlContent(
            controller = controller,
            loadTables = { AppResult.Success(tables) },
            loadRoundHands = { roundId -> AppResult.Success(ScreenshotData.tables(ONGOING).filter { it.roundId == roundId }.map { ScreenshotData.table(ONGOING, roundId, it.tableId) }) },
            onBack = {},
        )
    }

    @Test
    fun players() = adminScreenshots("players", ready = { waitForText("04010001", substring = true) }) { nav -> PlayersScreen(nav, ONGOING) }

    /** A long tournament: the round and table strip under each player scrolls sideways when the rounds do not fit. */
    @Test
    fun playersManyRounds() = adminScreenshots(
        "players-many-rounds",
        modules = { adminModules(tournaments = ManyRoundsTournamentRepository()) },
        ready = {
            waitForText("04010001", substring = true)
            openRoundTables()
        },
    ) { nav -> PlayersScreen(nav, ONGOING) }

    /** The 24 rounds after a swipe: the strips are scrolled to the middle and go past both edges of the row. */
    @Test
    fun playersManyRoundsScrolled() = adminScreenshots(
        "players-many-rounds-scrolled",
        modules = { adminModules(tournaments = ManyRoundsTournamentRepository()) },
        ready = {
            waitForText("04010001", substring = true)
            openRoundTables()
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
        ready = {
            waitForText("04010001", substring = true)
            openRoundTables()
        },
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

    /**
     * The registrations of a tournament before its draw: the settings, changed on the screen, and the list with
     * places, waiting list, a person to confirm, paid and food. A phone shows the settings first.
     */
    @Test
    fun registrations() = adminScreenshots("registrations", ready = { waitForText("bs99", substring = true) }) { nav ->
        RegistrationsScreen(nav, ScreenshotData.UPCOMING)
    }

    /**
     * The registrations as an editor sees them: the editor manages the people, but only who configures the tournament
     * changes the settings and makes the draw, so the screen has no Change, no Close registrations and no draw.
     */
    @Test
    fun registrationsEditor() = adminScreenshots(
        "registrations-editor",
        modules = { adminModules(role = GlobalUserRole.EDITOR) },
        variants = listOf(Variant(ScreenSize.Desktop, false), Variant(ScreenSize.Phone, false)),
        ready = {
            waitForText("bs99", substring = true)
            waitForIdle()
        },
    ) { nav -> RegistrationsScreen(nav, ScreenshotData.UPCOMING) }

    /** The list of the registrations on a phone, scrolled past the settings. */
    @Test
    fun registrationsList() = adminScreenshots(
        "registrations-list",
        variants = componentVariants,
        ready = {
            waitForText("bs99", substring = true)
            // A slow swipe of the height of the settings, so it does not fling to the end.
            onNodeWithTag("registrations-list").performTouchInput {
                swipeUp(startY = bottom - 60f, endY = bottom - 620f, durationMillis = 1_000)
            }
            waitForText("04010001", substring = true)
        },
    ) { nav -> RegistrationsScreen(nav, ScreenshotData.UPCOMING) }

    @Test
    fun roles() = adminScreenshots("roles", ready = { waitForText("Head referee") }) { nav -> RolesScreen(nav) }

    @Test
    fun organizations() = adminScreenshots("organizations", ready = { waitForText("Madrid Mahjong Club") }) { nav -> OrganizationsScreen(nav) }

    @Test
    fun errors() = adminScreenshots("errors") { nav -> ErrorsScreen(nav) }

    @Test
    fun timer() = adminScreenshots("timer", ready = { waitForText("2:00:00") }) { TimerStandaloneScreen(ONGOING, initialRound = 3) }

    @Test
    fun timerRunning() = adminScreenshots("timer-running", ready = { waitForText("1:30:00") }) {
        // Round 3 started 30 minutes ago: Pause and Stop stay faint so the clock is what the room sees.
        val repository = remember { RunningClockTournamentRepository() }
        val controller = remember { RoundTimerController(repository, ONGOING, initialRound = 3, deviceNowMs = { RUNNING_NOW_MS }) }
        Surface(modifier = Modifier.fillMaxSize()) {
            TimerContent(controller = controller, themePreference = ThemePreference.System, onToggleTheme = {}, deviceNowMs = { RUNNING_NOW_MS })
        }
    }

    @Test
    fun timerIndependent() = adminScreenshots("timer-independent") { TimerStandaloneScreen(null) }

    @Test
    fun rankingStandalone() = adminScreenshots("ranking-standalone", ready = { waitForText("LUCÍA MARTÍN", substring = true) }) {
        RankingStandaloneScreen(ONGOING, clock = FixedClock)
    }
}

/** The device and server time of the running Timer capture. */
private const val RUNNING_NOW_MS = 1_779_000_000_000L

/** Round 3 of [ONGOING] has run for 30 minutes. */
private class RunningClockTournamentRepository : ScreenshotTournamentRepository() {
    override suspend fun getRoundClocks(tournamentId: String): AppResult<RoundClocks> = AppResult.Success(
        RoundClocks(clocks = listOf(RoundClock(roundId = 3, startedAtMs = RUNNING_NOW_MS - 30 * 60_000L)), serverNowMs = RUNNING_NOW_MS),
    )
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

/** The tables per round start closed under each player. The captures that show them open every visible one. */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.openRoundTables() {
    // An open strip pushes the rows below it out of the screen, so the count is read again after each click.
    var index = 0
    while (index < onAllNodesWithTag(PlayerRoundTablesToggleTag).fetchSemanticsNodes().size) {
        onAllNodesWithTag(PlayerRoundTablesToggleTag)[index++].performClick()
        waitForIdle()
    }
}
