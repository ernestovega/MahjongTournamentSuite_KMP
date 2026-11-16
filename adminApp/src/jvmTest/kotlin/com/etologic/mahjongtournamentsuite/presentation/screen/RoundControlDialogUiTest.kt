package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundControl
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Round control, the plan B of the Timer: an admin runs the clock of a round without the Timer and decides by hand
 * what the players can do, for the round or for one table.
 */
@OptIn(ExperimentalTestApi::class)
class RoundControlDialogUiTest {
    private val now = 1_000_000_000L
    private val min = 60_000L

    private fun server(vararg clocks: RoundClock, numRounds: Int = 3) = FakeTournamentRepository().apply {
        tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = numRounds, matchDurationMinutes = 120))
        roundClocks = clocks.toList()
        serverNowMs = now
        seed(syncTable(tableId = 1))
        seed(syncTable(tableId = 2, isCompleted = true))
    }

    private fun ComposeUiTest.show(server: FakeTournamentRepository, round: Int = 1): RoundTimerController {
        val controller = RoundTimerController(server, "t1", round, deviceNowMs = { now })
        setContent {
            MtsTheme(useDarkTheme = false) {
                RoundControlDialogContent(
                    controller = controller,
                    loadTables = { mode -> server.listTournamentTables("t1", null, mode) },
                    onDismiss = {},
                    deviceNowMs = { now },
                )
            }
        }
        settle()
        return controller
    }

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(600)
        waitForIdle()
    }

    @Test
    fun aRoundThatHasNotStartedOffersStartAndShowsItsTables() = runComposeUiTest {
        show(server())

        onNodeWithText("Not started").assertExists()
        onNodeWithText("2:00:00").assertExists()
        onNodeWithText("Start").assertExists()
        onNodeWithText("Table 1").assertExists()
        onNodeWithText("Table 2").assertExists()
        onNodeWithTag("hands_auto").assertIsSelected()
        onNodeWithTag("seats_auto").assertIsSelected()
        onNodeWithText("This round is in manual mode", substring = true).assertDoesNotExist()
    }

    @Test
    fun openingOnALateRoundScrollsTheRoundStripToIt() = runComposeUiTest {
        show(server(numRounds = 30), round = 28)

        onNodeWithTag("round_chip_28").assertIsDisplayed().assertIsSelected()
        onNodeWithTag("round_chip_1").assertDoesNotExist()
    }

    @Test
    fun startRunsTheClockOfTheRoundWithoutTheTimer() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithText("Start").performClick()
        settle()

        assertEquals(listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.Start()), server.clockActions.toList())
        onNodeWithText("Running").assertExists()
        onNodeWithText("Pause timer").assertExists()
    }

    @Test
    fun stopAsksFirstAndThenEndsTheRound() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 30 * min))
        show(server)

        onNodeWithText("Stop the round").performClick()
        settle()
        onNodeWithText("Stop the round?").assertExists()
        onNodeWithText("Stop").performClick()
        settle()

        assertEquals(listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.Stop), server.clockActions.toList())
        onNodeWithText("Time is over").assertExists()
    }

    @Test
    fun closingTheHandEntryOfTheRoundWarnsThatTheRoundIsManual() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithTag("hands_closed").performClick()
        settle()

        assertEquals(listOf(1 to RoundControlChange(hands = ControlMode.CLOSED)), server.controlChanges.toList())
        onNodeWithTag("hands_closed").assertIsSelected()
        onNodeWithText("This round is in manual mode", substring = true).assertExists()

        onNodeWithTag("hands_auto").performClick()
        settle()
        onNodeWithText("This round is in manual mode", substring = true).assertDoesNotExist()
    }

    @Test
    fun aTableTakesItsOwnMode() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithTag("table_1_open").performScrollTo().performClick()
        settle()

        assertEquals(listOf(1 to RoundControlChange(table = 1 to ControlMode.OPEN)), server.controlChanges.toList())
        onNodeWithTag("table_1_open").assertIsSelected()
        onNodeWithTag("table_2_auto").assertIsSelected()
    }

    @Test
    fun inPlayReopensACompletedTableLikeTheSwitchOfTheEditor() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 130 * min))
        show(server)

        onNodeWithTag("table_2_completed").assertIsSelected()
        onNodeWithTag("table_2_in_play").performScrollTo().performClick()
        settle()

        assertEquals(listOf(1 to RoundControlChange(reopenTables = listOf(2))), server.controlChanges.toList())
        assertFalse(server.tables.getValue(TableKey(1, 2)).first.isCompleted)
        onNodeWithTag("table_2_in_play").assertIsSelected()
    }

    @Test
    fun completedMarksATableAsCompleted() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithTag("table_1_completed").performScrollTo().performClick()
        settle()

        assertEquals(listOf(1 to RoundControlChange(completeTables = listOf(1))), server.controlChanges.toList())
        assertTrue(server.tables.getValue(TableKey(1, 1)).first.isCompleted)
        onNodeWithTag("table_1_completed").assertIsSelected()
    }

    @Test
    fun anEndedRoundAndTheRoundInPlayAreShown() = runComposeUiTest {
        val controller = show(server(RoundClock(roundId = 1, startedAtMs = now - 130 * min), RoundClock(roundId = 2, startedAtMs = now - 10 * min)))
        // The dialog opens at the round in play.
        onNodeWithTag("round_chip_2").assertIsSelected()
        onNodeWithContentDescription("This is the round in play.", useUnmergedTree = true).assertExists()
        controller.selectRound(1)
        settle()

        onNodeWithText("Time is over").assertExists()
        onNodeWithTag("round_chip_1").assertIsSelected()
    }

    @Test
    fun theManualModeOfARoundDoesNotReachTheNextRound() = runComposeUiTest {
        val server = server().apply { roundControls = listOf(RoundControl(roundId = 1, seats = ControlMode.OPEN)) }
        show(server)

        onNodeWithText("This round is in manual mode", substring = true).assertExists()
        onNodeWithTag("round_chip_2").performClick()
        settle()

        onNodeWithTag("round_chip_2").assertIsSelected()
        onNodeWithText("This round is in manual mode", substring = true).assertDoesNotExist()
        onNodeWithTag("seats_auto").assertIsSelected()
    }

    @Test
    fun seatsOpenAndClosedChangeTheSeatsOfTheRound() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithTag("seats_open").performClick()
        settle()
        onNodeWithTag("seats_open").assertIsSelected()
        onNodeWithText("This round is in manual mode", substring = true).assertExists()

        onNodeWithTag("seats_closed").performClick()
        settle()
        onNodeWithTag("seats_closed").assertIsSelected()

        assertEquals(
            listOf(1 to RoundControlChange(seats = ControlMode.OPEN), 1 to RoundControlChange(seats = ControlMode.CLOSED)),
            server.controlChanges.toList(),
        )
        assertEquals(ControlMode.CLOSED, server.roundControls.single().seats)
    }

    @Test
    fun theRoundStartedEarlierStartsTheClockInThePast() = runComposeUiTest {
        val server = server()
        show(server)

        onNodeWithText("The round started earlier").performClick()
        settle()
        onNodeWithText("When did the round start?").assertExists()
        onNode(hasSetTextAction()).performTextReplacement("10")
        onNodeWithText("Set").performClick()
        settle()

        assertEquals(listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.Start(agoSeconds = 600)), server.clockActions.toList())
        onNodeWithText("1:50:00").assertExists()
    }

    @Test
    fun pauseAndResumeTheClockOfTheRound() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 30 * min))
        show(server)

        onNodeWithText("Pause timer").performClick()
        settle()
        onNodeWithText("Resume").performClick()
        settle()

        assertEquals(
            listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.Pause(), 1 to RoundClockAction.Resume()),
            server.clockActions.toList(),
        )
        onNodeWithText("Pause timer").assertExists()
    }

    @Test
    fun setTimeChangesTheRemainingTime() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 30 * min))
        show(server)

        onNodeWithText("Set time").performClick()
        settle()
        onNode(hasSetTextAction()).performTextReplacement("45")
        onNodeWithText("Set").performClick()
        settle()

        assertEquals(listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.SetRemaining(2_700L)), server.clockActions.toList())
        onNodeWithText("0:45:00").assertExists()
    }

    @Test
    fun resetAsksFirstAndThenClearsTheClock() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 30 * min))
        show(server)

        onNodeWithText("Reset timer").performClick()
        settle()
        onNodeWithText("Reset the round clock?").assertExists()
        assertTrue(server.clockActions.isEmpty())
        onNodeWithText("Reset").performClick()
        settle()

        assertEquals(listOf<Pair<Int, RoundClockAction>>(1 to RoundClockAction.Reset(force = true)), server.clockActions.toList())
        onNodeWithText("Not started").assertExists()
        onNodeWithText("2:00:00").assertExists()
    }

    @Test
    fun aChangeThatTheServerRejectsShowsAnErrorAndKeepsTheMode() = runComposeUiTest {
        val server = server()
        show(server)
        server.clockError = AppError.Network

        onNodeWithTag("hands_closed").performClick()
        settle()

        onNodeWithText("Network request failed.").assertExists()
        onNodeWithTag("hands_auto").assertIsSelected()
        assertTrue(server.controlChanges.isEmpty())
    }

    @Test
    fun aTableThatTheServerFailsToCompleteStaysInPlay() = runComposeUiTest {
        val server = server()
        show(server)
        server.clockError = AppError.Network

        onNodeWithTag("table_1_completed").performScrollTo().performClick()
        settle()

        onNodeWithText("Network request failed.").assertExists()
        assertFalse(server.tables.getValue(TableKey(1, 1)).first.isCompleted)
        onNodeWithTag("table_1_in_play").assertIsSelected()
    }
}
