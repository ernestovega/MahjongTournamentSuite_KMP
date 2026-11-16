package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChildren
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
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.CompletableDeferred
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

    /** Round 1 is over and its tables 1, 3 and 4 stayed open; round 2 is in play. */
    private fun serverWithThreeOpenTables() = server(
        RoundClock(roundId = 1, startedAtMs = now - 200 * min),
        RoundClock(roundId = 2, startedAtMs = now - 10 * min),
    ).apply {
        seed(syncTable(tableId = 3))
        seed(syncTable(tableId = 4))
    }

    private fun ComposeUiTest.show(server: FakeTournamentRepository, round: Int = 1): RoundTimerController {
        val controller = RoundTimerController(server, "t1", round, deviceNowMs = { now })
        setContent {
            MtsTheme(useDarkTheme = false) {
                RoundControlDialogContent(
                    controller = controller,
                    loadTables = { mode -> server.listTournamentTables("t1", null, mode) },
                    loadRoundHands = { roundId -> server.getRoundTablesWithHands("t1", roundId, RefreshMode.FORCE) },
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

    @Test
    fun inPlayExplainsWhyATableWithEveryHandDoneStaysCompleted() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 30 * min)).apply { tablesWithAllHands = setOf(2) }
        show(server)

        onNodeWithTag("table_2_in_play").performScrollTo().performClick()
        settle()

        onNodeWithText("This table has every hand done, so it stays completed.", substring = true).assertExists()
        assertTrue(server.tables.getValue(TableKey(1, 2)).first.isCompleted)
        onNodeWithTag("table_2_completed").assertIsSelected()
    }

    @Test
    fun theOpenTablesOfAFinishedRoundShowABadgeAndCloseFromTheReview() = runComposeUiTest {
        // Round 1 is over: its time ran out. Table 1 stayed open, table 2 is completed.
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 200 * min))
        show(server)

        onNodeWithTag("round_chip_1_open_tables", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Open tables: 1").assertExists()
        onNodeWithText("There is 1 open table in ended rounds").assertExists()
        onNodeWithText("Review").performClick()
        settle()
        onNodeWithText("Open tables in ended rounds").assertExists()
        onNodeWithTag("open_table_1_1").assertExists()
        onNodeWithTag("open_table_1_2").assertDoesNotExist()
        onNodeWithTag("open_tables_select_all").performClick()
        onNodeWithTag("open_tables_close_selected").performClick()
        settle()

        assertEquals(listOf(listOf(TableKey(1, 1))), server.completedTableRequests.toList())
        onNodeWithTag("finished_rounds_open_tables").assertDoesNotExist()
        onNodeWithTag("round_chip_1_open_tables", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun closingTheReviewWithoutCloseSelectedClosesNothing() = runComposeUiTest {
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 200 * min))
        show(server)

        onNodeWithText("Review").performClick()
        settle()
        onNodeWithTag("open_table_1_1_check").performClick()
        onNodeWithTag("open_tables_dismiss").performClick()
        settle()

        assertTrue(server.completedTableRequests.isEmpty())
        onNodeWithTag("finished_rounds_open_tables").assertExists()
    }

    @Test
    fun goToRoundClosesTheReviewAndSelectsTheRoundOfTheTable() = runComposeUiTest {
        // Round 2 is in play, so round 1 is over. The dialog opens on round 2.
        val server = server(RoundClock(roundId = 1, startedAtMs = now - 200 * min), RoundClock(roundId = 2, startedAtMs = now - 10 * min))
        show(server, round = 2)

        onNodeWithText("Review").performClick()
        settle()
        onNodeWithTag("open_table_1_1_go").performClick()
        settle()

        onNodeWithText("Open tables in ended rounds").assertDoesNotExist()
        onNodeWithTag("round_chip_1").assertIsSelected()
        onNodeWithTag("table_row_1").assertIsDisplayed()
        assertTrue(server.completedTableRequests.isEmpty())
    }

    @Test
    fun closeSelectedClosesTheCheckedTablesAndKeepsTheReviewOpen() = runComposeUiTest {
        val server = serverWithThreeOpenTables()
        show(server, round = 2)

        onNodeWithText("Review").performClick()
        settle()
        onNodeWithTag("open_tables_close_selected").assertIsNotEnabled()
        onNodeWithTag("open_table_1_1_check").performClick()
        onNodeWithTag("open_table_1_4_check").performClick()
        onNodeWithTag("open_tables_close_selected").performClick()
        settle()

        assertEquals(listOf(listOf(TableKey(1, 1), TableKey(1, 4))), server.completedTableRequests.toList())
        onNodeWithText("Open tables in ended rounds").assertExists()
        onNodeWithTag("open_table_1_1").assertDoesNotExist()
        onNodeWithTag("open_table_1_4").assertDoesNotExist()
        onNodeWithTag("open_table_1_3").assertExists()
        onNodeWithTag("open_tables_close_selected").assertIsNotEnabled()
    }

    @Test
    fun selectAllThenCloseSelectedEndsTheReviewWhenNothingIsLeft() = runComposeUiTest {
        val server = serverWithThreeOpenTables()
        show(server, round = 2)

        onNodeWithText("Review").performClick()
        settle()
        onNodeWithTag("open_tables_select_all").performClick()
        onNodeWithTag("open_tables_close_selected").performClick()
        settle()

        assertEquals(listOf(listOf(TableKey(1, 1), TableKey(1, 3), TableKey(1, 4))), server.completedTableRequests.toList())
        onNodeWithText("Open tables in ended rounds").assertDoesNotExist()
        onNodeWithTag("finished_rounds_open_tables").assertDoesNotExist()
    }

    @Test
    fun theReviewWaitsWithASpinnerWhileTheTablesClose() = runComposeUiTest {
        val server = serverWithThreeOpenTables()
        val gate = CompletableDeferred<Unit>()
        server.beforeCompleteTables = { gate.await() }
        show(server, round = 2)

        onNodeWithText("Review").performClick()
        settle()
        onNodeWithTag("open_table_1_3_check").performClick()
        onNodeWithTag("open_tables_close_selected").performClick()
        settle()

        onNodeWithTag("open_tables_closing", useUnmergedTree = true).assertExists()
        onNodeWithTag("open_tables_close_selected").assertIsNotEnabled()
        onNodeWithTag("open_table_1_1_check").assertIsNotEnabled()
        onNodeWithTag("open_table_1_1_go").assertIsNotEnabled()

        gate.complete(Unit)
        settle()

        onNodeWithTag("open_tables_closing", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("open_table_1_3").assertDoesNotExist()
        onNodeWithTag("open_table_1_1_check").assertIsEnabled()
        onNodeWithText("Open tables in ended rounds").assertExists()
    }

    @Test
    fun theReviewCountsTheHandsEnteredOfEachOpenTable() = runComposeUiTest {
        val server = serverWithThreeOpenTables().apply {
            val key = TableKey(1, 3)
            val (table, hands) = tables.getValue(key)
            tables[key] = table to hands.mapIndexed { index, hand -> hand.copy(isDone = index < 5) }
        }
        show(server, round = 2)

        onNodeWithText("Review").performClick()
        settle()

        onNodeWithTag("open_table_1_3").onChildren().filterToOne(hasText("5")).assertExists()
        onNodeWithTag("open_table_1_1").onChildren().filterToOne(hasText("0")).assertExists()
    }

    @Test
    fun theHeaderOfTheReviewStaysInPlaceWhileTheRowsScroll() = runComposeUiTest {
        val server = serverWithThreeOpenTables().apply { (5..20).forEach { seed(syncTable(tableId = it)) } }
        show(server, round = 2)

        onNodeWithText("Review").performClick()
        settle()
        onNodeWithTag("open_table_1_20").performScrollTo().assertIsDisplayed()

        onNodeWithTag("open_tables_select_all").assertIsDisplayed()
        onNodeWithText("Hands").assertIsDisplayed()
    }

    @Test
    fun aRoundInPlayKeepsItsOpenTablesWithoutWarning() = runComposeUiTest {
        show(server(RoundClock(roundId = 1, startedAtMs = now - 30 * min)))

        onNodeWithTag("round_chip_1_open_tables", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("finished_rounds_open_tables").assertDoesNotExist()
    }
}
