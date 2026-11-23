package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountSlot
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountWaiting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The labels and the search of the screen of the number of players. */
class PlayerCountLabelsTest {
    private val ana = PlayerCountSlot(7, "Ana García", "10990001", team = 2)
    private val empty = PlayerCountSlot(8, "Player 8", null, team = 2)

    @Test
    fun aSlotShowsItsNumberAndName() {
        assertEquals("#7 · Ana García", ana.label())
    }

    @Test
    fun theMembersOfATeamShowTheirNamesAndTheEmptySlotsTheirNumber() {
        val team = TournamentTeam(2, "Dragons", listOf(7, 8, 99))
        assertEquals("Ana García, #8", team.memberNames(listOf(ana, empty)))
    }

    @Test
    fun theSearchFindsASlotByNumberNameOrEmaNumber() {
        assertTrue(ana.matches(""))
        assertTrue(ana.matches("7"))
        assertTrue(ana.matches("#7"))
        assertTrue(ana.matches("garcia"), "without the accent")
        assertTrue(ana.matches("10990001"))
        assertFalse(ana.matches("#17"))
        assertFalse(ana.matches("Bruno"))
    }

    @Test
    fun aTeamIsFoundByItsNameOrOneOfItsPlayers() {
        val team = TournamentTeam(2, "Dragons", listOf(7, 8))
        assertTrue(team.matches("drag", listOf(ana, empty)))
        assertTrue(team.matches("ana", listOf(ana, empty)))
        assertFalse(team.matches("tigers", listOf(ana, empty)))
    }

    @Test
    fun aPersonOfTheWaitingListIsFoundByNameEmaNumberOrPlace() {
        val waiting = PlayerCountWaiting("10990002", "Bruno Díaz", position = 3)
        assertTrue(waiting.matches("bruno"))
        assertTrue(waiting.matches("10990002"))
        assertTrue(waiting.matches(" 3 "))
        assertFalse(waiting.matches("4"))
    }
}
