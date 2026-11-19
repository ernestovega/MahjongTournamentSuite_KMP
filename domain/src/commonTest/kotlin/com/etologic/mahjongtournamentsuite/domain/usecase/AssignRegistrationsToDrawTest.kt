package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssignRegistrationsToDrawTest {
    private fun slots(count: Int, isTeams: Boolean) = (1..count).map { id ->
        TournamentPlayer(id = id, name = "", team = if (isTeams) (id - 1) / 4 + 1 else 0)
    }

    private val people = (1..8).map { "1099" + it.toString().padStart(4, '0') }

    @Test
    fun everyPersonTakesOneSlotWithoutTeams() {
        val result = assignRegistrationsToDraw(slots(8, false), people, isTeams = false, teamOf = { null }, random = Random(1))
        assertEquals(8, result?.size)
        assertEquals((1..8).toSet(), result!!.map { it.playerId }.toSet())
        assertEquals(people.toSet(), result.map { it.emaId }.toSet())
    }

    @Test
    fun theOrderIsRandom() {
        val first = assignRegistrationsToDraw(slots(8, false), people, false, { null }, Random(1))
        val second = assignRegistrationsToDraw(slots(8, false), people, false, { null }, Random(2))
        assertFalse(first == second)
    }

    @Test
    fun aRegistrationTeamTakesTheSlotsOfOneDrawTeam() {
        val teamOf = { emaId: String -> if (people.indexOf(emaId) < 4) "red" else "blue" }
        val slots = slots(8, true)
        val result = assignRegistrationsToDraw(slots, people, isTeams = true, teamOf = teamOf, random = Random(3))!!
        val teamOfSlot = slots.associate { it.id to it.team }
        val drawTeamsOfRed = result.filter { teamOf(it.emaId) == "red" }.map { teamOfSlot.getValue(it.playerId) }.toSet()
        val drawTeamsOfBlue = result.filter { teamOf(it.emaId) == "blue" }.map { teamOfSlot.getValue(it.playerId) }.toSet()
        assertEquals(1, drawTeamsOfRed.size)
        assertEquals(1, drawTeamsOfBlue.size)
        assertTrue(drawTeamsOfRed != drawTeamsOfBlue)
    }

    @Test
    fun anIncompleteTeamOrAPersonWithoutTeamGivesNull() {
        assertNull(assignRegistrationsToDraw(slots(8, true), people, true, { emaId -> if (people.indexOf(emaId) < 5) "red" else "blue" }))
        assertNull(assignRegistrationsToDraw(slots(8, true), people, true, { emaId -> if (people.indexOf(emaId) < 4) "red" else null }))
        assertNull(assignRegistrationsToDraw(slots(4, false), people, false, { null }))
    }

    @Test
    fun theSettingsSayWhenThePlacesAreFull() {
        assertTrue(RegistrationSettings(maxPlayers = 4, confirmed = 4).isFull)
        assertFalse(RegistrationSettings(maxPlayers = null, confirmed = 400).isFull)
    }
}
