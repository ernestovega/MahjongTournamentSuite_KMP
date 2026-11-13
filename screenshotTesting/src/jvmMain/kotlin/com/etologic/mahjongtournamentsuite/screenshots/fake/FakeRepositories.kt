package com.etologic.mahjongtournamentsuite.screenshots.fake

import com.etologic.mahjongtournamentsuite.domain.model.ActivityFilter
import com.etologic.mahjongtournamentsuite.domain.model.ActivityPage
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.DigestResult
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.FeatureFlags
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.model.SupportMessage
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAgendaItem
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.FeatureFlagsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.StatisticsRepository
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing

/** What a write of a screenshot test gets: screenshots never write, so a write that runs is a mistake of the test. */
private fun <T> noWrite(): AppResult<T> = AppResult.Failure(AppError.Unexpected("Screenshots do not write"))

private fun <T> ok(value: T): AppResult<T> = AppResult.Success(value)

/** The tournaments of [ScreenshotData]. Open, so a test can change one answer. */
open class ScreenshotTournamentRepository(
    open val tournaments: List<Tournament> = ScreenshotData.tournaments,
) : TournamentRepository {
    private fun teams(tournamentId: String) = ScreenshotData.tournament(tournamentId).isTeams

    override suspend fun listTournaments(refreshMode: RefreshMode) = ok(tournaments)
    override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> = noWrite()
    override suspend fun renameTournament(tournamentId: String, name: String): AppResult<Unit> = noWrite()
    override suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        primaryColor: String,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        venue: TournamentVenue,
        associationLogoContentType: String?,
        associationLogoBytes: ByteArray?,
        associationLogoSourceTournamentId: String?,
        removeAssociationLogo: Boolean,
        roundSchedules: List<TournamentRoundSchedule>,
        agendaItems: List<TournamentAgendaItem>,
        countBestHands: Boolean?,
        countChickenHands: Boolean?,
        matchDurationMinutes: Int?,
        matchEndMode: MatchEndMode?,
        matchExtraMinutes: Int?,
        timeZone: String?,
        isPublic: Boolean?,
        showTableDiffs: Boolean?,
    ): AppResult<Tournament> = noWrite()

    override suspend fun generateTournamentIdCards(tournamentId: String, sheet: IdCardSheetLayout?): AppResult<ByteArray> = noWrite()
    override suspend fun generateIdCardProof(request: IdCardProofRequest): AppResult<ByteArray> = noWrite()
    override suspend fun generateTournamentIdList(tournamentId: String): AppResult<ByteArray> = noWrite()
    override suspend fun generateEmaReport(tournamentId: String, rankings: List<PlayerRanking>): AppResult<ByteArray> = noWrite()
    override suspend fun deleteTournament(tournamentId: String): AppResult<Unit> = noWrite()

    override suspend fun listTournamentMembers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentMember>> = ok(listOf(TournamentMember("editor-1", "bruno@example.test", GlobalUserRole.EDITOR)))

    override suspend fun upsertTournamentMember(tournamentId: String, uid: String): AppResult<Unit> = noWrite()
    override suspend fun removeTournamentMember(tournamentId: String, uid: String): AppResult<Unit> = noWrite()

    override suspend fun listTournamentPlayers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentPlayer>> = ok(ScreenshotData.tournamentPlayers(teams(tournamentId)))

    override suspend fun listTournamentTeams(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentTeam>> = ok(if (teams(tournamentId)) ScreenshotData.teams() else emptyList())

    override suspend fun updateTournamentTeam(tournamentId: String, teamId: Int, name: String, emaIds: List<String?>): AppResult<Unit> = noWrite()
    override suspend fun listCountries(refreshMode: RefreshMode): AppResult<List<Country>> = ok(ScreenshotData.countries)
    override suspend fun resolveVenueLink(url: String): AppResult<ResolvedVenueLink> = noWrite()
    override suspend fun assignTournamentPlayer(tournamentId: String, tournamentPlayerId: Int, emaId: String?): AppResult<Unit> = noWrite()
    override suspend fun setPlayerSubstitution(tournamentId: String, tournamentPlayerId: Int, substitution: PlayerSubstitution?): AppResult<Unit> = noWrite()

    override suspend fun listTournamentRounds(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentRound>> = ok((1..4).map(::TournamentRound))

    override suspend fun listTournamentTables(tournamentId: String, roundId: Int?, refreshMode: RefreshMode): AppResult<List<TournamentTable>> = ok(ScreenshotData.tables(tournamentId, roundId))

    override suspend fun getTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        refreshMode: RefreshMode,
    ): AppResult<Pair<TableState, List<TableHand>>> = ok(ScreenshotData.table(tournamentId, roundId, tableId))

    override suspend fun getRoundTablesWithHands(tournamentId: String, roundId: Int, refreshMode: RefreshMode) = ok((1..4).map { ScreenshotData.table(tournamentId, roundId, it) })

    override suspend fun getAllTablesWithHands(tournamentId: String, refreshMode: RefreshMode) = ok((1..4).flatMap { round -> (1..4).map { ScreenshotData.table(tournamentId, round, it) } })

    override suspend fun saveTableState(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        expectedVersion: Long,
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ): AppResult<Pair<TableState, List<TableHand>>> = noWrite()

    override suspend fun getTournamentManifest(tournamentId: String): AppResult<TournamentManifest> = ok(
        TournamentManifest(
            tablesRevision = 1,
            tableVersions = ScreenshotData.tables(tournamentId).associate { TableKey(it.roundId, it.tableId) to it.version },
        ),
    )

    override suspend fun listMyTournamentSlots(): AppResult<List<MyTournamentSlot>> = ok(emptyList())
}

/** The player base of [ScreenshotData]. */
open class ScreenshotPlayerRepository(private val players: List<Player> = ScreenshotData.players) : PlayerRepository {
    override suspend fun listPlayers(refreshMode: RefreshMode) = ok(players)
    override suspend fun createPlayer(player: Player): AppResult<Player> = noWrite()
    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = noWrite()
    override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = noWrite()
}

/** A signed-in account: [profile], or nobody when it is null. */
open class ScreenshotAuthRepository(private val profile: UserProfile? = ScreenshotData.admin) : AuthRepository {
    override suspend fun currentSession(): AuthSession? = profile?.let { AuthSession(it.uid, "token", "refresh") }
    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> = noWrite()
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> = noWrite()
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() {}
    override suspend fun refreshSession(): AppResult<AuthSession> = currentSession()?.let(::ok) ?: AppResult.Failure(AppError.Unexpected("Signed out"))
    override suspend fun signOut() {}
    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = profile?.let(::ok) ?: AppResult.Failure(AppError.Unexpected("Signed out"))
    override suspend fun updateAlias(alias: String): AppResult<UserProfile> = noWrite()
    override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = noWrite()
    override suspend fun removePhoto(): AppResult<UserProfile> = noWrite()
}

/** The administration of an account with [role]. */
open class ScreenshotAdminRepository(
    private val role: GlobalUserRole = GlobalUserRole.ADMIN,
    private val users: List<ManagedUser> = ScreenshotData.users,
) : AdminRepository {
    override suspend fun whoAmI(refreshMode: RefreshMode) = ok(AdminStatus("admin-1", role))
    override suspend fun lookupUser(identifier: String, tournamentId: String?): AppResult<UserProfile> = noWrite()
    override suspend fun listUsers(refreshMode: RefreshMode) = ok(users)
    override suspend fun createUser(
        email: String,
        alias: String,
        role: GlobalUserRole,
        tournamentAssignments: List<TournamentAssignment>,
        emaId: String?,
    ): AppResult<ManagedUser> = noWrite()

    override suspend fun updateUser(user: ManagedUser): AppResult<ManagedUser> = noWrite()
    override suspend fun setUserDisabled(uid: String, disabled: Boolean): AppResult<ManagedUser> = noWrite()
    override suspend fun updateUserPhoto(uid: String, contentType: String, bytes: ByteArray): AppResult<ManagedUser> = noWrite()
    override suspend fun removeUserPhoto(uid: String): AppResult<ManagedUser> = noWrite()
    override suspend fun sendPasswordResets(uids: List<String>): AppResult<List<PasswordResetResult>> = noWrite()
    override suspend fun loadErrorGroups(sinceMillis: Long?, cursor: ErrorLogCursor?): AppResult<ErrorGroupsPage> = ok(ErrorGroupsPage(groups = emptyList(), nextCursor = null))

    override suspend fun loadErrorGroup(id: String, reportCursor: ErrorLogCursor?): AppResult<ErrorGroupDetail> = noWrite()
    override suspend fun loadActivity(filter: ActivityFilter, cursor: String?): AppResult<ActivityPage> = ok(ActivityPage(entries = emptyList(), nextCursor = null))
}

/**
 * Statistics of the server: an empty overview, and the history of a person computed from the tables of every
 * tournament that has started. A null EMA number is the signed-in player.
 */
open class ScreenshotStatisticsRepository : StatisticsRepository {
    override suspend fun loadOverview(): AppResult<StatisticsOverview> = ok(
        StatisticsOverview(
            tournaments = 0, tablesCompleted = 0, tablesWithHands = 0, handsPlayed = 0, drawnHands = 0, winningHands = 0,
            winningScoreSum = 0, chickenHands = 0, penaltyCount = 0, highestHand = 0, players = emptyList(),
        ),
    )

    override suspend fun loadPerson(emaId: String?): AppResult<List<PersonTournamentDigest>> {
        val person = emaId ?: ScreenshotData.playerAccount.emaId.orEmpty()
        return ok(
            ScreenshotData.tournaments.filter { it.id != ScreenshotData.UPCOMING }.mapNotNull { tournament ->
                val players = ScreenshotData.tournamentPlayers(tournament.isTeams)
                val slotId = players.firstOrNull { it.assignedEmaId == person }?.id ?: return@mapNotNull null
                val tables = (1..4).flatMap { round ->
                    (1..4).map { table -> ScreenshotData.table(tournament.id, round, table).let { (state, hands) -> RankingTable(state, hands) } }
                }
                val statistics = CalculateStatisticsUseCase()(players, tables, tournament.id)
                val results = statistics.players.first { it.playerId == slotId }.results
                PersonTournamentDigest(tournament.id, results.map { DigestResult(person, slotId, it) })
            },
        )
    }
    override suspend fun loadStandings(emaId: String?, tournamentIds: List<String>): AppResult<Map<String, Standing>> = ok(emptyMap())
}

class ScreenshotFeatureFlagsRepository(private val flags: FeatureFlags = FeatureFlags()) : FeatureFlagsRepository {
    override suspend fun load(): FeatureFlags = flags
}

class ScreenshotSupportRepository : SupportRepository {
    override suspend fun send(message: SupportMessage): AppResult<Unit> = noWrite()
}
