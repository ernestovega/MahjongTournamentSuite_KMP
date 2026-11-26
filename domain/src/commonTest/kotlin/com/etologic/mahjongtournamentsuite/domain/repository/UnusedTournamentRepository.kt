package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.IdCardStaffCandidate
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.MyTournaments
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountChangeRequest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue

/** A tournament repository whose own calls fail. A test overrides what it needs and keeps the defaults of the interface. */
open class UnusedTournamentRepository : TournamentRepository {
    override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = error("Not used by this test")
    override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> = error("Not used by this test")
    override suspend fun renameTournament(tournamentId: String, name: String): AppResult<Unit> = error("Not used by this test")
    override suspend fun updateTournamentSettings(tournamentId: String, name: String, shortName: String, organizerIds: List<String>?, eventStartDate: String, eventEndDate: String, hostCountry: String, hostCity: String, venue: TournamentVenue, roundSchedules: List<TournamentRoundSchedule>, agendaItems: List<TournamentAgendaItem>, countBestHands: Boolean?, countChickenHands: Boolean?, matchDurationMinutes: Int?, matchEndMode: MatchEndMode?, matchExtraMinutes: Int?, timeZone: String?, isPublic: Boolean?, showTableDiffs: Boolean?): AppResult<Tournament> = error("Not used by this test")
    override suspend fun generateTournamentIdCards(tournamentId: String, sheet: IdCardSheetLayout?): AppResult<ByteArray> = error("Not used by this test")
    override suspend fun generateIdCardProof(request: IdCardProofRequest): AppResult<ByteArray> = error("Not used by this test")
    override suspend fun loadIdCardStaffNames(tournamentId: String): AppResult<List<String>> = error("Not used by this test")
    override suspend fun listIdCardStaffCandidates(tournamentId: String): AppResult<List<IdCardStaffCandidate>> = error("Not used by this test")
    override suspend fun saveIdCardStaffNames(tournamentId: String, names: List<String>): AppResult<List<String>> = error("Not used by this test")
    override suspend fun generateTournamentIdList(tournamentId: String): AppResult<ByteArray> = error("Not used by this test")
    override suspend fun generateEmaReport(tournamentId: String, rankings: List<PlayerRanking>): AppResult<ByteArray> = error("Not used by this test")
    override suspend fun deleteTournament(tournamentId: String): AppResult<Unit> = error("Not used by this test")
    override suspend fun listTournamentMembers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentMember>> = error("Not used by this test")
    override suspend fun upsertTournamentMember(tournamentId: String, uid: String): AppResult<Unit> = error("Not used by this test")
    override suspend fun removeTournamentMember(tournamentId: String, uid: String): AppResult<Unit> = error("Not used by this test")
    override suspend fun listTournamentPlayers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentPlayer>> = error("Not used by this test")
    override suspend fun listTournamentTeams(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentTeam>> = error("Not used by this test")
    override suspend fun updateTournamentTeam(tournamentId: String, teamId: Int, name: String, emaIds: List<String?>): AppResult<Unit> = error("Not used by this test")
    override suspend fun listCountries(refreshMode: RefreshMode): AppResult<List<Country>> = error("Not used by this test")
    override suspend fun resolveVenueLink(url: String): AppResult<ResolvedVenueLink> = error("Not used by this test")
    override suspend fun assignTournamentPlayer(tournamentId: String, tournamentPlayerId: Int, emaId: String?): AppResult<Unit> = error("Not used by this test")
    override suspend fun setPlayerSubstitution(tournamentId: String, tournamentPlayerId: Int, substitution: PlayerSubstitution?): AppResult<Unit> = error("Not used by this test")
    override suspend fun listTournamentRounds(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentRound>> = error("Not used by this test")
    override suspend fun listTournamentTables(tournamentId: String, roundId: Int?, refreshMode: RefreshMode): AppResult<List<TournamentTable>> = error("Not used by this test")
    override suspend fun getTableWithHands(tournamentId: String, roundId: Int, tableId: Int, refreshMode: RefreshMode): AppResult<Pair<TableState, List<TableHand>>> = error("Not used by this test")
    override suspend fun getRoundTablesWithHands(tournamentId: String, roundId: Int, refreshMode: RefreshMode): AppResult<List<Pair<TableState, List<TableHand>>>> = error("Not used by this test")
    override suspend fun getAllTablesWithHands(tournamentId: String, refreshMode: RefreshMode): AppResult<List<Pair<TableState, List<TableHand>>>> = error("Not used by this test")
    override suspend fun saveTableState(tournamentId: String, roundId: Int, tableId: Int, expectedVersion: Long, tablePatch: Map<String, Any?>, handPatches: Map<Int, Map<String, Any?>>): AppResult<Pair<TableState, List<TableHand>>> = error("Not used by this test")
    override suspend fun getTournamentManifest(tournamentId: String): AppResult<TournamentManifest> = error("Not used by this test")
}
