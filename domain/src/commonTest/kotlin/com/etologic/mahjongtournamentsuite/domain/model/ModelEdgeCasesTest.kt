package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edges of small models: no hands won, no tables, no slots, a table with its own control, a clock already over. */
class ModelEdgeCasesTest {

    private fun totals(handsPlayed: Int, drawnHands: Int, selfDraws: Int, tablesWithHands: Int) = TournamentRoundTotals(
        roundId = 1, tablesTotal = 2, tablesCompleted = 2, handsPlayed = handsPlayed, drawnHands = drawnHands, selfDraws = selfDraws,
        penaltyCount = 0, winningScoreSum = 40, highestHand = null, highestTableScore = null, tablesWithHands = tablesWithHands,
    )

    @Test
    fun theRatesOfARoundAreUnknownWithoutHandsWonOrTables() {
        val onlyDraws = totals(handsPlayed = 4, drawnHands = 4, selfDraws = 0, tablesWithHands = 0)
        assertNull(onlyDraws.selfDrawRate)
        assertNull(onlyDraws.averageHandScore)
        assertNull(onlyDraws.averageHandsPerTable)

        val played = totals(handsPlayed = 8, drawnHands = 4, selfDraws = 2, tablesWithHands = 2)
        assertEquals(0.5, played.selfDrawRate)
        assertEquals(10.0, played.averageHandScore)
        assertEquals(4.0, played.averageHandsPerTable)
    }

    @Test
    fun aTableWithItsOwnControlWinsOverTheRound() {
        val control = RoundControl(roundId = 1, hands = ControlMode.CLOSED, tables = mapOf(2 to ControlMode.OPEN, 3 to ControlMode.AUTO))
        assertEquals(ControlMode.OPEN, control.handsAt(2))
        assertEquals(ControlMode.CLOSED, control.handsAt(3))
        assertEquals(ControlMode.CLOSED, control.handsAt(9))
        assertTrue(control.isManual)

        assertFalse(RoundControl(roundId = 1).isManual)
        assertTrue(RoundControl(roundId = 1, seats = ControlMode.OPEN).isManual)
        assertTrue(RoundControl(roundId = 1, tables = mapOf(1 to ControlMode.CLOSED)).isManual)
        assertFalse(RoundControl(roundId = 1, tables = mapOf(1 to ControlMode.AUTO)).isManual)
    }

    @Test
    fun aStopAfterTheEndOfTheTimeChangesNothing() {
        val clock = RoundClock(roundId = 1, startedAtMs = 0)
        assertEquals(clock, clock.stoppedAt(nowMs = 130 * 60_000L, durationMinutes = 120, extraMs = 5 * 60_000L))
    }

    @Test
    fun aDrawWithoutSlotsStartsTheNumbersAndTheTeamsAtOne() {
        val slots = PlayerCountRules.targetSlots(current = emptyList(), removedSlotIds = emptyList(), target = 4, isTeams = true)
        assertEquals(listOf(1, 2, 3, 4), slots.map { it.id })
        assertEquals(listOf(1, 1, 1, 1), slots.map { it.team })
    }

    @Test
    fun theTeamsLeaveOutTheSlotsWithoutTeamAndTheMovesBackHome() {
        val slots = listOf(
            TournamentPlayer(id = 1, name = "A", team = 1),
            TournamentPlayer(id = 2, name = "B", team = 0),
            TournamentPlayer(id = 3, name = "C", team = 2),
        )
        assertEquals(mapOf(1 to listOf(1), 2 to listOf(3)), PlayerCountRules.teamsAfter(slots, removedSlotIds = emptyList()))
        // Slot 3 back in its own team is not a move.
        assertEquals(emptyMap(), PlayerCountRules.proposeTeams(slots.take(1) + slots.last(), removedSlotIds = listOf(1), teamMoves = mapOf(3 to 2)))
    }
}
