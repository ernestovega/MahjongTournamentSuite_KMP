package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Timer of a tournament follows the clock of the round on the server. Play starts the round for every Timer and
 * for the players, and the screen tells the organizer what happens.
 */
@OptIn(ExperimentalTestApi::class)
class TimerScreenUiTest {
    private val now = 1_000_000_000L
    private val min = 60_000L

    private fun server(vararg clocks: RoundClock) = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 3, matchDurationMinutes = 120))
        roundClocks = clocks.toList()
        serverNowMs = now
    }

    private fun ComposeUiTest.show(server: FakeTournamentRepository, round: Int = 1): RoundTimerController {
        val controller = RoundTimerController(server, "t1", round, deviceNowMs = { now })
        setContent {
            MtsTheme(useDarkTheme = false) {
                TimerContent(controller = controller, themePreference = ThemePreference.Light, onToggleTheme = {}, deviceNowMs = { now })
            }
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        return controller
    }

    @Test
    fun aRoundWithoutClockWaitsForTheGongAndShowsTheWholeMatch() = runComposeUiTest {
        show(server())

        onNodeWithText("Press Play when the starting gong sounds", substring = true).assertExists()
        onNodeWithText("2:00:00").assertExists()
        onNodeWithContentDescription("Reset timer").assertIsNotEnabled()
        onNodeWithContentDescription("The round started earlier").assertIsEnabled()
    }

    @Test
    fun playStartsTheRoundOnTheServer() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithContentDescription("Start timer").performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()

        assertEquals(listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.Start()), server.clockActions.toList())
        onNodeWithText("Press Play when the starting gong sounds", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Pause timer").assertExists()
    }

    @Test
    fun aRoundStartedOnAnotherTimerShowsItsRemainingTime() = runComposeUiTest {
        show(server(RoundClock(roundId = 1, startedAtMs = now - 30 * min)))

        onNodeWithText("1:30:00").assertExists()
        onNodeWithContentDescription("Pause timer").assertExists()
    }

    @Test
    fun aPausedRoundSaysThatThePlayersCanStillAddHands() = runComposeUiTest {
        show(server(RoundClock(roundId = 1, startedAtMs = now - 40 * min, pausedAtMs = now - 10 * min)))

        onNodeWithText("Paused. The players can still add hands.").assertExists()
        // 30 minutes of play: the 10 minutes of the pause do not count.
        onNodeWithText("1:30:00").assertExists()
        onNodeWithContentDescription("Set time").assertIsEnabled()
    }

    @Test
    fun anEndedRoundSaysThatTheTimeIsUp() = runComposeUiTest {
        show(server(RoundClock(roundId = 1, startedAtMs = now - 130 * min)))

        onNodeWithText("Time is up.").assertExists()
        onNodeWithText("0:00:00").assertExists()
    }

    @Test
    fun resettingARoundWithHandsAsksFirst() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 30 * min, pausedAtMs = now - 5 * min)).apply {
            roundsWithHands = setOf(1)
        }
        show(server)

        onNodeWithContentDescription("Reset timer").performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        onNodeWithText("Reset the round clock?").assertExists()
        onNodeWithText("Reset").performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()

        assertEquals(RoundClockAction.Reset(force = true), server.clockActions.last().second)
        assertEquals(emptyList<RoundClock>(), server.roundClocks)
        onNodeWithText("Press Play when the starting gong sounds", substring = true).assertExists()
    }

    @Test
    fun theStartedAgoDialogStartsTheRoundInThePast() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithContentDescription("The round started earlier").performClick()
        mainClock.advanceTimeBy(300)
        waitForIdle()
        onNodeWithText("When did the round start?").assertExists()
    }

    @Test
    fun withoutNetworkPlayShowsTheRoundAtOnceAndSaysItIsOffline() = runComposeUiTest {
        val server = server()
        show(server)
        server.clockError = AppError.Network

        onNodeWithContentDescription("Start timer").performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()

        onNodeWithText("No connection", substring = true).assertExists()
        onNodeWithContentDescription("Pause timer").assertExists()

        server.clockError = null
        mainClock.advanceTimeBy(5_500)
        waitForIdle()

        assertEquals(listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.Start()), server.clockActions.toList())
        onNodeWithText("No connection", substring = true).assertDoesNotExist()
    }
}
