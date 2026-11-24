package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerCountRulesTest {
    private val drawn = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 20, numRounds = 7)

    @Test
    fun aDrawnTournamentWithoutDataCanChange() {
        assertNull(PlayerCountRules.blockerOf(drawn))
    }

    @Test
    fun tableDataAFinishedTournamentOrAMissingDrawBlockTheChange() {
        assertEquals(PlayerCountBlocker.STARTED, PlayerCountRules.blockerOf(drawn.copy(hasTableData = true)))
        assertEquals(PlayerCountBlocker.COMPLETED, PlayerCountRules.blockerOf(drawn.copy(isCompleted = true)))
        assertEquals(PlayerCountBlocker.DRAW_PENDING, PlayerCountRules.blockerOf(drawn.copy(drawPending = true)))
        assertEquals(
            PlayerCountBlocker.DRAW_PENDING,
            PlayerCountRules.blockerOf(drawn.copy(drawPending = true, registration = RegistrationSettings(), hasTableData = true)),
        )
    }

    @Test
    fun theNumberMovesByWholeTablesBetweenSixteenAndTheMaximum() {
        assertFalse(PlayerCountRules.canDecrease(16))
        assertTrue(PlayerCountRules.canDecrease(20))
        assertTrue(PlayerCountRules.canIncrease(16))
        assertFalse(PlayerCountRules.canIncrease(PlayerCountRules.MAX_PLAYERS))
        assertTrue(PlayerCountRules.isValidTarget(20, 16))
        assertTrue(PlayerCountRules.isValidTarget(20, 28))
        assertFalse(PlayerCountRules.isValidTarget(20, 20))
        assertFalse(PlayerCountRules.isValidTarget(20, 12))
        assertFalse(PlayerCountRules.isValidTarget(20, 22))
        assertFalse(PlayerCountRules.isValidTarget(20, PlayerCountRules.MAX_PLAYERS + 4))
    }

    @Test
    fun countsTheSlotsThatLeaveOrCome() {
        assertEquals(4, PlayerCountRules.slotsToRemove(20, 16))
        assertEquals(0, PlayerCountRules.slotsToRemove(16, 20))
        assertEquals(8, PlayerCountRules.newSlots(16, 24))
        assertEquals(0, PlayerCountRules.newSlots(24, 16))
    }

    @Test
    fun theRemovedSlotsAreThePeopleMarkedAlsoWithTeams() {
        assertEquals(listOf(3, 7, 9), PlayerCountRules.removedSlots(setOf(9, 3, 7)))
        assertEquals(emptyList(), PlayerCountRules.removedSlots(emptySet()))
    }

    @Test
    fun theTeamsThatLoseSomeoneButNotAllAreIncomplete() {
        // Teams of 4 by number: 3 of team 1 and 1 of team 2 leave, and team 3 leaves whole.
        val slots = (1..16).map { TournamentPlayer(it, "Player $it", (it - 1) / 4 + 1) }
        val broken = PlayerCountRules.brokenTeams(slots, listOf(1, 2, 3, 5, 9, 10, 11, 12))
        assertEquals(mapOf(1 to listOf(4), 2 to listOf(6, 7, 8)), broken)
        assertEquals(emptyMap(), PlayerCountRules.brokenTeams(slots, (5..8).toList()))
        assertEquals(emptyMap(), PlayerCountRules.brokenTeams(slots.map { it.copy(team = 0) }, listOf(1, 2, 3, 5)))
    }

    private val teamsOf24 = (1..24).map { TournamentPlayer(it, "Player $it", (it - 1) / 4 + 1) }

    @Test
    fun theTeamsAfterTheMovesOfTheOrganizer() {
        val removed = listOf(1, 2, 3, 5)
        assertEquals(mapOf(1 to listOf(4), 2 to listOf(6, 7, 8)), PlayerCountRules.incompleteTeams(teamsOf24, removed))
        val after = PlayerCountRules.teamsAfter(teamsOf24, removed, mapOf(4 to 2))
        assertEquals(listOf(4, 6, 7, 8), after[2])
        assertFalse(1 in after, "A team without people is not a team any more")
        assertEquals(emptyMap(), PlayerCountRules.incompleteTeams(teamsOf24, removed, mapOf(4 to 2)))
    }

    @Test
    fun theProposalClosesEveryTeamWithFewMoves() {
        // 3 of team 1 and 1 of team 2 leave: the last of team 1 goes to team 2 (the example of Ernesto).
        assertEquals(mapOf(4 to 2), PlayerCountRules.proposeTeams(teamsOf24, listOf(1, 2, 3, 5)))
        // Two and two: team 1 takes both of team 2.
        assertEquals(mapOf(7 to 1, 8 to 1), PlayerCountRules.proposeTeams(teamsOf24, listOf(1, 2, 5, 6)))
        // One of each of four teams: three moves, every team of 4 again.
        val removed = listOf(1, 5, 9, 13)
        val moves = PlayerCountRules.proposeTeams(teamsOf24, removed)
        assertEquals(3, moves.size)
        assertTrue(PlayerCountRules.teamsAfter(teamsOf24, removed, moves).values.all { it.size == 4 })
        // Whole teams leaving need no move.
        assertEquals(emptyMap(), PlayerCountRules.proposeTeams(teamsOf24, (5..8).toList()))
    }

    @Test
    fun theProposalStartsFromTheMovesAlreadyMadeAndForgetsAMoveBack() {
        val removed = listOf(1, 2, 5, 6)
        // The organizer already sent 3 to team 2: team 2 has 3, 7, 8 and takes the 4.
        assertEquals(mapOf(3 to 2, 4 to 2), PlayerCountRules.proposeTeams(teamsOf24, removed, mapOf(3 to 2)))
        // A move back to the own team is not a move.
        assertEquals(mapOf(7 to 1, 8 to 1), PlayerCountRules.proposeTeams(teamsOf24, removed, mapOf(3 to 1)))
    }

    @Test
    fun theSlotsOfTheNewDrawTakeTheMovesOfTheTeams() {
        val slots = PlayerCountRules.targetSlots(teamsOf24, listOf(1, 2, 3, 5), 20, isTeams = true, teamMoves = mapOf(4 to 2))
        assertEquals(2, slots.first { it.id == 4 }.team)
        assertEquals((6..24).map { (it - 1) / 4 + 1 }, slots.filter { it.id >= 6 }.map { it.team })
    }

    @Test
    fun aChoiceIsCompleteWithExactlyTheSlotsToRemove() {
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, setOf(1, 2, 3), emptyList()))
        assertTrue(PlayerCountRules.isChoiceComplete(20, 16, setOf(1, 2, 3, 4), emptyList()))
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, setOf(1, 2, 3, 4, 5), emptyList()))
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, setOf(1, 2, 3, 4), listOf("1")))
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, emptySet(), emptyList()))
    }

    @Test
    fun moreTablesTakeUpToTheNewSlotsFromTheWaitingList() {
        assertTrue(PlayerCountRules.isChoiceComplete(16, 20, emptySet(), emptyList()))
        assertTrue(PlayerCountRules.isChoiceComplete(16, 20, emptySet(), listOf("1", "2", "3", "4")))
        assertFalse(PlayerCountRules.isChoiceComplete(16, 20, emptySet(), listOf("1", "2", "3", "4", "5")))
        assertFalse(PlayerCountRules.isChoiceComplete(16, 20, setOf(1), emptyList()))
        assertFalse(PlayerCountRules.isChoiceComplete(16, 16, emptySet(), emptyList()))
    }

    @Test
    fun theNumberAfterTheChangeCountsWhoLeavesAndTheTablesAdded() {
        assertEquals(16, PlayerCountRules.targetOf(20, 4, 0))
        assertEquals(28, PlayerCountRules.targetOf(20, 0, 2))
        assertEquals(20, PlayerCountRules.targetOf(20, 0, 0))
    }

    @Test
    fun atLeastTheMinimumStaysAndTheMaximumIsNeverPassed() {
        assertEquals(0, PlayerCountRules.maxRemovable(16))
        assertEquals(8, PlayerCountRules.maxRemovable(24))
        assertTrue(PlayerCountRules.canAddTable(396, 0))
        assertFalse(PlayerCountRules.canAddTable(396, 1))
    }

    @Test
    fun theMarkedPeopleFitInWholeTablesOrSayWhatIsMissing() {
        assertEquals(PlayerCountRules.MarkedFit(0, 0), PlayerCountRules.markedFit(40, 0))
        assertTrue(PlayerCountRules.markedFit(40, 8).fits)
        // 7 marked: one more or three less.
        assertEquals(PlayerCountRules.MarkedFit(1, 3), PlayerCountRules.markedFit(40, 7))
        assertEquals(PlayerCountRules.MarkedFit(3, 1), PlayerCountRules.markedFit(40, 1))
        // With 24 players at most 8 leave: 9 marked can only drop 1.
        assertEquals(PlayerCountRules.MarkedFit(0, 1), PlayerCountRules.markedFit(24, 9))
        assertFalse(PlayerCountRules.markedFit(24, 9).fits)
    }
}
