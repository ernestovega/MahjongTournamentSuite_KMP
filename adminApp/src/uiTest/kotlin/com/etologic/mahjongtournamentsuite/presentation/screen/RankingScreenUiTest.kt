package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The ranking screen stays still while it is open. It loads once and asks the server nothing more. */
@OptIn(ExperimentalTestApi::class)
class RankingScreenUiTest {
    private fun server() = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 1))
        players = (1..4).map { TournamentPlayer(id = it, name = "Player $it", team = 0) }
        seed(syncTable(version = 1, roundId = 1, tableId = 1))
    }

    /** A tournament played today with one table scored, so the ranking has pages. */
    private fun playedToday() = FakeTournamentRepository().apply {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        tournaments = listOf(
            Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 1, eventStartDate = today, eventEndDate = today),
        )
        players = (1..4).map { TournamentPlayer(id = it, name = "Player $it", team = 0) }
        seed(
            syncTable(version = 1, roundId = 1, tableId = 1, eastScore = "48")
                .copy(playerSouthScore = "-16", playerWestScore = "-16", playerNorthScore = "-16"),
            syncHands(score = "24").mapIndexed { index, hand ->
                if (index == 0) hand.copy(playerWinnerId = "1", playerLooserId = "2", isDone = true, isChickenHand = true) else hand
            },
        )
    }

    /** A screen that is on: the check for new hands waits while the screen is hidden. */
    private class ShownScreen : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry

        /** Hides the screen (a tab in the background) or shows it again. */
        fun shown(value: Boolean) {
            registry.currentState = if (value) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        }
    }

    private var screen = ShownScreen()

    /** The buttons ignore a second press within a short time; the tests move this clock past it. */
    private val pressClock = TestTimeSource()

    private fun androidx.compose.ui.test.ComposeUiTest.show(server: FakeTournamentRepository, compact: Boolean = false) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { server }
                            single<PlayerRepository> { NoPlayersRepository() }
                            factory { RankingPresenter(get(), get(), CalculateTournamentRankingsUseCase(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController, LocalCompactLayout provides compact, LocalLifecycleOwner provides screen, LocalPressTimeSource provides pressClock) {
                    RankingStandaloneScreen(tournamentId = "t1")
                }
            }
        }
    }

    @Test
    fun loadsAllTablesOnceWhenItOpens() = runComposeUiTest {
        val server = server()

        show(server)
        waitForIdle()

        assertEquals(1, server.calls.count { it.startsWith("all") })
        assertEquals("all CACHED", server.calls.first { it.startsWith("all") })
    }

    @Test
    fun showsWhichDataItHoldsAndWhenItLoaded() = runComposeUiTest {
        val server = server()
        show(server)
        waitForIdle()

        onNodeWithText("Results r1 · loaded ", substring = true).assertExists()
    }

    @Test
    fun makesNoRequestAtAllWhileItStaysOpen() = runComposeUiTest {
        val server = server()
        show(server)
        waitForIdle()
        val callsAfterOpening = server.calls.toList()

        mainClock.advanceTimeBy(60_000)
        waitForIdle()

        assertEquals(callsAfterOpening, server.calls, "no table, manifest or list request after the first load")
        assertTrue(server.calls.none { it == "manifest" })
    }

    @Test
    fun otherUsersSavesDoNotChangeAnOpenRanking() = runComposeUiTest {
        val server = server()
        show(server)
        waitForIdle()
        val callsAfterOpening = server.calls.toList()

        server.otherUserSaves(1, 1, handScore = "24")
        mainClock.advanceTimeBy(30_000)
        waitForIdle()

        assertEquals(callsAfterOpening, server.calls)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.press(description: String) {
        onAllNodesWithContentDescription(description).onFirst().performClick()
        mainClock.advanceTimeBy(600)
        waitForIdle()
    }

    @Test
    fun theControlsPageTheListAndChangeItsRows() = runComposeUiTest {
        show(server())
        waitForIdle()

        press("Start automatic paging")
        // The countdown runs while the list pages by itself.
        mainClock.advanceTimeBy(31_000)
        waitForIdle()
        press("Pause automatic paging")
        press("Next ranking list")
        press("Previous ranking list")
        press("Show more rows")
        press("Show fewer rows")
        onAllNodesWithText("Show first 3").onFirst().performClick()
        waitForIdle()

        onNodeWithText("Results r1 · loaded ", substring = true).assertExists()
    }

    @Test
    fun aFailedLoadShowsTheError() = runComposeUiTest {
        show(server().apply { failPlayers = true })
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("OK").onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun aRankingWithResultsPagesByItself() = runComposeUiTest {
        val server = playedToday()
        show(server)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Points").fetchSemanticsNodes().isNotEmpty() }

        press("Start automatic paging")
        repeat(4) {
            mainClock.advanceTimeBy(16_000)
            waitForIdle()
        }
        press("Pause automatic paging")
        onNodeWithText("Results r1 · loaded ", substring = true).assertExists()
    }

    @Test
    fun thePrizeGivingRevealsTheTopThreeOneByOneFromTheThird() = runComposeUiTest {
        show(playedToday())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Points").fetchSemanticsNodes().isNotEmpty() }
        fun shown(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

        // The winner and the next two hide behind "???" until the organizer reveals them.
        assertTrue(!shown("PLAYER 1"))
        assertTrue(!shown("PLAYER 2") && shown("PLAYER 4"))
        onNodeWithText("Reveal 3rd place").performClick()
        pressClock += 1.seconds
        waitForIdle()
        onNodeWithText("Reveal 2nd place").performClick()
        pressClock += 1.seconds
        waitForIdle()
        assertTrue(!shown("PLAYER 1"), "the winner is the last one")
        onNodeWithText("Reveal 1st place").performClick()
        pressClock += 1.seconds
        waitForIdle()

        assertTrue(shown("PLAYER 1"))
        assertTrue(!shown("Reveal 1st place") && !shown("Reveal 3rd place"))
    }

    @Test
    fun onAPhoneTheSettingsOpenInASheet() = runComposeUiTest {
        show(playedToday(), compact = true)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Points").fetchSemanticsNodes().isNotEmpty() }

        press("Settings")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Show first 3").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun FakeTournamentRepository.checks() = calls.count { it == "tournaments IF_CHANGED" }

    private fun androidx.compose.ui.test.ComposeUiTest.pass(seconds: Int) {
        mainClock.advanceTimeBy(seconds * 1_000L)
        waitForIdle()
    }

    @Test
    fun onTheDayOfTheTournamentTheRankingChecksForNewHandsEvery15Seconds() = runComposeUiTest {
        val server = playedToday()
        show(server)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Points").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, server.checks())

        pass(14)
        assertEquals(0, server.checks(), "a check before 15 s: ${server.calls}")
        pass(2)
        assertEquals(1, server.checks(), "no check after 15 s: ${server.calls}")
        pass(15)
        assertEquals(2, server.checks())
    }

    @Test
    fun aHiddenRankingDoesNotCheckAndChecksAtOnceWhenItShowsAgain() = runComposeUiTest {
        val server = playedToday()
        show(server)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Points").fetchSemanticsNodes().isNotEmpty() }

        runOnIdle { screen.shown(false) }
        pass(60)
        assertEquals(0, server.checks(), "a hidden screen checked: ${server.calls}")

        runOnIdle { screen.shown(true) }
        pass(1)
        assertEquals(1, server.checks())
    }

    @Test
    fun anotherDayTheRankingChecksEveryFiveMinutes() = runComposeUiTest {
        val server = server()
        show(server)
        waitForIdle()

        pass(4 * 60 + 50)
        assertEquals(0, server.checks())
        pass(20)
        assertEquals(1, server.checks(), "no check after 5 minutes: ${server.calls}")
    }

    @Test
    fun aRankingHiddenFromThePublicAsksBeforeItShows() = runComposeUiTest {
        val server = playedToday().apply { tournaments = tournaments.map { it.copy(hideResultsFromRound = 1, resultsHiddenFromRound = 1) } }
        show(server)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("The results are hidden from the public").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Points").assertCountEquals(0)

        onNodeWithText("Show here anyway").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Points").fetchSemanticsNodes().isNotEmpty() }
    }
}
