package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking

interface TournamentRepository {
    suspend fun listTournaments(): AppResult<List<Tournament>>

    suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament>

    suspend fun renameTournament(
        tournamentId: String,
        name: String,
    ): AppResult<Unit>

    suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        primaryColor: String,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        associationLogoContentType: String? = null,
        associationLogoBytes: ByteArray? = null,
        removeAssociationLogo: Boolean = false,
    ): AppResult<Tournament>

    suspend fun generateTournamentIdCards(tournamentId: String): AppResult<ByteArray>

    suspend fun generateEmaReport(
        tournamentId: String,
        rankings: List<PlayerRanking>,
    ): AppResult<ByteArray>

    suspend fun deleteTournament(tournamentId: String): AppResult<Unit>

    suspend fun listTournamentMembers(tournamentId: String): AppResult<List<TournamentMember>>

    suspend fun upsertTournamentMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit>

    suspend fun removeTournamentMember(
        tournamentId: String,
        uid: String,
    ): AppResult<Unit>

    suspend fun listTournamentPlayers(tournamentId: String): AppResult<List<TournamentPlayer>>

    suspend fun listTournamentTeams(tournamentId: String): AppResult<List<TournamentTeam>>

    /**
     * Renames a team and assigns EMA players to its fixed schedule slots.
     * The list order matches [TournamentTeam.playerIds]. Null leaves a slot empty.
     */
    suspend fun updateTournamentTeam(
        tournamentId: String,
        teamId: Int,
        name: String,
        emaIds: List<String?>,
    ): AppResult<Unit>

    suspend fun listCountries(): AppResult<List<Country>>

    /** Assigns an EMA player or a tournament-only non-member. Pass both null to clear it. */
    suspend fun assignTournamentPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
        nonMember: NonMemberPlayer? = null,
    ): AppResult<Unit>

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

    suspend fun resetTable(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
    ): AppResult<Unit>
}
