package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerCountRulesTest {
    private val drawn = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 20, numRounds = 7)
    private val teams = (1..5).map { team -> TournamentTeam(team, "Team $team", (1..4).map { (team - 1) * 4 + it }) }

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
    fun theRemovedSlotsAreTheChosenSlotsOrTheSlotsOfTheChosenTeams() {
        assertEquals(listOf(3, 7, 9), PlayerCountRules.removedSlots(false, setOf(9, 3, 7), teams))
        assertEquals(listOf(5, 6, 7, 8, 17, 18, 19, 20), PlayerCountRules.removedSlots(true, setOf(5, 2), teams))
        assertEquals(emptyList(), PlayerCountRules.removedSlots(true, setOf(9), teams))
    }

    @Test
    fun aChoiceIsCompleteWithExactlyTheSlotsToRemove() {
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, false, setOf(1, 2, 3), teams, emptyList()))
        assertTrue(PlayerCountRules.isChoiceComplete(20, 16, false, setOf(1, 2, 3, 4), teams, emptyList()))
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, false, setOf(1, 2, 3, 4, 5), teams, emptyList()))
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, false, setOf(1, 2, 3, 4), teams, listOf("1")))
        assertTrue(PlayerCountRules.isChoiceComplete(20, 16, true, setOf(3), teams, emptyList()))
        assertFalse(PlayerCountRules.isChoiceComplete(20, 16, true, emptySet(), teams, emptyList()))
    }

    @Test
    fun moreTablesTakeUpToTheNewSlotsFromTheWaitingList() {
        assertTrue(PlayerCountRules.isChoiceComplete(16, 20, false, emptySet(), teams, emptyList()))
        assertTrue(PlayerCountRules.isChoiceComplete(16, 20, false, emptySet(), teams, listOf("1", "2", "3", "4")))
        assertFalse(PlayerCountRules.isChoiceComplete(16, 20, false, emptySet(), teams, listOf("1", "2", "3", "4", "5")))
        assertFalse(PlayerCountRules.isChoiceComplete(16, 20, false, setOf(1), teams, emptyList()))
        assertFalse(PlayerCountRules.isChoiceComplete(16, 16, false, emptySet(), teams, emptyList()))
    }
}
