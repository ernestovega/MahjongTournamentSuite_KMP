package com.etologic.mahjongtournamentsuite.presentation.store

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import kotlin.test.Test
import kotlin.test.assertEquals

/** The tournaments that the admin app holds in memory: a new one is added, the same id replaces it, a rename changes only its name. */
class AppMemoryStoreTest {
    private fun tournament(id: String, name: String = "Open") = Tournament(id = id, name = name, isTeams = false, numPlayers = 4, numRounds = 1)

    @Test
    fun theSameTournamentReplacesTheHeldOneAndARenameChangesOnlyItsName() {
        val store = AppMemoryStore()
        store.addTournament(tournament("t1"))
        store.addTournament(tournament("t2"))
        store.addTournament(tournament("t1", "Open 2"))
        assertEquals(listOf("Open 2", "Open"), store.tournaments.value.map { it.name })
        store.renameTournament("t2", "Cup")
        assertEquals(listOf("Open 2", "Cup"), store.tournaments.value.map { it.name })
    }

    @Test
    fun theRoundsAreHeldByTournament() {
        val store = AppMemoryStore()
        store.upsertRounds("t1", listOf(TournamentRound(1)))
        assertEquals(listOf(TournamentRound(1)), store.tournamentRounds.value["t1"])
        store.removeTournament("t1")
        assertEquals(null, store.tournamentRounds.value["t1"])
    }
}
