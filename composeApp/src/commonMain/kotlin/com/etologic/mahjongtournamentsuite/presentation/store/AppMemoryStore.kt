package com.etologic.mahjongtournamentsuite.presentation.store

import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class AppMemoryStore {
    val profile = MutableStateFlow<UserProfile?>(null)
    val adminStatus = MutableStateFlow<AdminStatus?>(null)
    val tournaments = MutableStateFlow<List<Tournament>>(emptyList())

    val tournamentPlayers = MutableStateFlow<Map<String, List<TournamentPlayer>>>(emptyMap())
    val tournamentRounds = MutableStateFlow<Map<String, List<TournamentRound>>>(emptyMap())
    val tournamentTables = MutableStateFlow<Map<String, Map<String, List<TournamentTable>>>>(emptyMap())

    fun upsertTournaments(items: List<Tournament>) {
        tournaments.value = items
    }

    fun addTournament(item: Tournament) {
        tournaments.update { current ->
            val existingIndex = current.indexOfFirst { it.id == item.id }
            if (existingIndex >= 0) {
                current.toMutableList().also { it[existingIndex] = item }
            } else {
                current + item
            }
        }
    }

    fun removeTournament(tournamentId: String) {
        tournaments.update { current -> current.filterNot { it.id == tournamentId } }
        tournamentPlayers.update { it - tournamentId }
        tournamentRounds.update { it - tournamentId }
        tournamentTables.update { it - tournamentId }
    }

    fun upsertPlayers(
        tournamentId: String,
        players: List<TournamentPlayer>,
    ) {
        tournamentPlayers.update { it + (tournamentId to players) }
    }

    fun upsertRounds(
        tournamentId: String,
        rounds: List<TournamentRound>,
    ) {
        tournamentRounds.update { it + (tournamentId to rounds) }
    }

    fun upsertTables(
        tournamentId: String,
        roundId: Int?,
        tables: List<TournamentTable>,
    ) {
        val key = roundId?.toString() ?: "all"
        tournamentTables.update { current ->
            val byRound = current[tournamentId].orEmpty()
            current + (tournamentId to (byRound + (key to tables)))
        }
    }
}

