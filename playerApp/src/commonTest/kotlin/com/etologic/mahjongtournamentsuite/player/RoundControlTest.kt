package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundControl
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The manual control of an admin (the plan B of the Timer) wins over the clock in the player app, as on the server. */
class RoundControlTest {
    private fun at(time: String) = LocalDateTime.parse("2026-10-10T$time")

    private fun ms(time: String) = at(time).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    /** Planned at 10:00, a match of 115 minutes. [started] says whether the Timer started it at 10:00. */
    private fun tournament(started: Boolean, vararg controls: RoundControl) = Tournament(
        id = "t",
        name = "T",
        isTeams = false,
        numPlayers = 4,
        numRounds = 1,
        roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-10", "10:00")),
        roundClocks = if (started) listOf(RoundClock(roundId = 1, startedAtMs = ms("10:00"))) else emptyList(),
        roundControls = controls.toList(),
        matchDurationMinutes = 115,
        matchEndMode = MatchEndMode.STOP,
    )

    private fun tableWithHands(done: Int) = TableState(
        roundId = 1,
        tableId = 1,
        playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1",
        playerSouthId = "2",
        playerWestId = "3",
        playerNorthId = "4",
        playerEastScore = "",
        playerSouthScore = "",
        playerWestScore = "",
        playerNorthScore = "",
        isCompleted = false,
        useTotalsOnly = false,
    ) to List(16) { index ->
        TableHand(
            handId = index + 1,
            playerWinnerId = "",
            playerLooserId = "",
            handScore = "",
            isChickenHand = false,
            isDone = index < done,
            playerEastPenalty = "",
            playerSouthPenalty = "",
            playerWestPenalty = "",
            playerNorthPenalty = "",
        )
    }

    @Test
    fun anOpenRoundPlaysBeforeThePlayAndATableControlWinsOverItsRound() {
        val open = tournament(started = false, RoundControl(1, hands = ControlMode.OPEN, tables = mapOf(2 to ControlMode.CLOSED)))

        assertEquals(MatchPhase.NOT_STARTED, open.clockPhase(1, at("09:00")))
        assertEquals(MatchPhase.PLAYING, open.matchPhase(1, at("09:00")))
        assertEquals(MatchPhase.PLAYING, open.matchPhase(1, at("09:00"), tableId = 1))
        assertEquals(MatchPhase.CLOSED, open.matchPhase(1, at("09:00"), tableId = 2))
    }

    @Test
    fun aClosedRoundRefusesHandsWhileTheClockRuns() {
        val closed = tournament(started = true, RoundControl(1, hands = ControlMode.CLOSED))
        assertEquals(MatchPhase.PLAYING, closed.clockPhase(1, at("10:30")))
        assertEquals(MatchPhase.CLOSED, closed.matchPhase(1, at("10:30"), tableId = 1))
    }

    @Test
    fun theAppDoesNotCloseTheTablesOfAnOpenRoundAfterTheTime() {
        val automatic = tournament(started = true)
        val open = tournament(started = true, RoundControl(1, hands = ControlMode.OPEN))

        assertTrue(automatic.closeExpiredTables(listOf(tableWithHands(3)), at("13:00")).single().first.isCompleted)
        assertFalse(open.closeExpiredTables(listOf(tableWithHands(3)), at("13:00")).single().first.isCompleted)
    }

    @Test
    fun manualSeatsWinOverThePlannedStartAndTheClock() {
        assertFalse(tournament(started = false).seatingIsOpen(1, at("09:00")))
        assertTrue(tournament(started = false, RoundControl(1, seats = ControlMode.OPEN)).seatingIsOpen(1, at("09:00")))
        assertFalse(tournament(started = true, RoundControl(1, seats = ControlMode.CLOSED)).seatingIsOpen(1, at("10:30")))
    }
}
