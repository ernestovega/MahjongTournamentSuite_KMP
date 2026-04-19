package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRole

interface TournamentRepository {
    suspend fun listTournaments(): AppResult<List<Tournament>>

    suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament>

    suspend fun deleteTournament(tournamentId: String): AppResult<Unit>

    suspend fun listTournamentMembers(tournamentId: String): AppResult<List<TournamentMember>>

    suspend fun upsertTournamentMember(
        tournamentId: String,
        uid: String,
        role: TournamentRole,
    ): AppResult<Unit>

    suspend fun removeTournamentMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit>

    suspend fun listTournamentPlayers(tournamentId: String): AppResult<List<TournamentPlayer>>

    suspend fun listTournamentRounds(tournamentId: String): AppResult<List<TournamentRound>>

    suspend fun listTournamentTables(
        tournamentId: String,
        roundId: Int? = null,
    ): AppResult<List<TournamentTable>>

    suspend fun getTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Pair<TableState, List<TableHand>>>

    suspend fun patchTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        patch: Map<String, Any?>,
    ): AppResult<Unit>

    suspend fun patchHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        handId: Int,
        patch: Map<String, Any?>,
    ): AppResult<Unit>
}
