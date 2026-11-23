package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.presentation.components.playerNumberDigits
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountSlot
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountWaiting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The labels and the search of the screen of the number of players, with "#NN" as everywhere else (23-nov). */
class PlayerCountLabelsTest {
    private val ana = PlayerCountSlot(7, "Ana García", "10990001", team = 2)
    private val empty = PlayerCountSlot(8, "Player 8", null, team = 2)

    @Test
    fun aSlotShowsItsNumberWithTheDigitsOfTheTournament() {
        assertEquals("#07 · Ana García", ana.label())
        assertEquals("#007 · Ana García", ana.label(playerNumberDigits(120)))
    }

    @Test
    fun theMembersOfATeamShowTheirNamesAndTheEmptySlotsTheirNumber() {
        val team = TournamentTeam(2, "Dragons", listOf(7, 8, 99))
        assertEquals("Ana García, #08", team.memberNames(listOf(ana, empty), digits = 2))
        // In a tournament of more than 99 players the number has three digits here too.
        assertEquals("Ana García, #008", team.memberNames(listOf(ana, empty), digits = 3))
    }

    @Test
    fun theSearchFindsASlotByNumberNameOrEmaNumber() {
        assertTrue(ana.matches(""))
        assertTrue(ana.matches("7"))
        assertTrue(ana.matches("#7"))
        assertTrue(ana.matches("#07"))
        assertTrue(ana.matches("#007"), "the number of a tournament of more than 99 players")
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
