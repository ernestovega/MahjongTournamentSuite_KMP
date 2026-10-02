package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.*
import com.etologic.mahjongtournamentsuite.domain.repository.*
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class PresenterDelegationTest {
    @Test
    fun tablesPresenterUsesForceRefreshAndForwardsTableArguments() = runTest {
        val repository = RecordingTournamentRepository()
        val presenter = TablesPresenter(
            tournamentRepository = repository,
            playerRepository = RecordingPlayerRepository(),
            logger = Logger.withTag("TablesPresenterTest"),
        )

        presenter.loadTables(tournamentId = "tournament-1", roundId = 3, force = true)

        assertEquals("tournament-1", repository.lastTournamentId)
        assertEquals(3, repository.lastRoundId)
        assertEquals(RefreshMode.FORCE, repository.lastRefreshMode)
    }

    @Test
    fun authPresenterReturnsRepositoryResults() = runTest {
        val expected = AppResult.Success(AuthSession("uid", "id-token", "refresh-token"))
        val repository = RecordingAuthRepository(signInResult = expected)
        val presenter = AuthPresenter(repository, Logger.withTag("AuthPresenterTest"))

        val result = presenter.signIn("user@example.test", "password")

        assertEquals(expected, result)
        assertEquals("user@example.test", repository.lastEmail)
    }

    @Test
    fun rankingPresenterPropagatesTheFirstLoadingFailure() = runTest {
        val failure = AppResult.Failure(AppError.Unexpected("backend unavailable"))
        val repository = RecordingTournamentRepository(listTournamentsResult = failure)
        val presenter = RankingPresenter(
            tournamentRepository = repository,
            playerRepository = RecordingPlayerRepository(),
            calculateRankings = CalculateTournamentRankingsUseCase(),
            logger = Logger.withTag("RankingPresenterTest"),
        )

        val result = presenter.load("tournament-1")

        assertEquals(failure, result)
    }

    @Test
    fun createTournamentPresenterPassesTheGeneratedScheduleToTheRepository() = runTest {
        val tournament = tournament("created")
        val repository = RecordingTournamentRepository(
            createTournamentResult = AppResult.Success(tournament),
        )
        val progress = mutableListOf<CreateTournamentPresenter.Phase>()
        val presenter = CreateTournamentPresenter(
            tournamentRepository = repository,
            logger = Logger.withTag("CreateTournamentPresenterTest"),
            generateSchedule = GenerateTournamentScheduleBruteForceParallelUseCase(),
        )

        val result = presenter.createTournament(
            name = "Small Open",
            shortName = "SO",
            primaryColor = "#02B16B",
            associationLogoContentType = null,
            associationLogoBytes = null,
            associationLogoSourceTournamentId = null,
            eventStartDate = "2026-10-01",
            eventEndDate = "2026-10-01",
            hostCountry = "ES",
            hostCity = "Madrid",
            isTeams = false,
            numPlayers = 4,
            numRounds = 1,
            roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-01", "09:30")),
            agendaItems = listOf(TournamentAgendaItem("Registration", "2026-10-01", "08:30", "09:00")),
            computeMode = CreateTournamentPresenter.ComputeMode.LIGHT,
            onProgress = { progress += it.phase },
        )

        assertEquals(AppResult.Success(tournament), result)
        assertEquals("Small Open", repository.lastCreateRequest?.name)
        assertEquals(4, repository.lastCreateRequest?.players?.size)
        assertEquals(1, repository.lastCreateRequest?.tables?.size)
        assertEquals("09:30", repository.lastCreateRequest?.roundSchedules?.single()?.startTime)
        assertEquals("Registration", repository.lastCreateRequest?.agendaItems?.single()?.title)
        assertEquals(
            listOf(CreateTournamentPresenter.Phase.CALCULATING, CreateTournamentPresenter.Phase.CREATING),
            progress.distinct(),
        )
    }

    private fun tournament(id: String) = Tournament(
        id = id,
        name = id,
        isTeams = false,
        numPlayers = 4,
        numRounds = 1,
    )
}

private class RecordingTournamentRepository(
    private val listTournamentsResult: AppResult<List<Tournament>> = unusedResult(),
    private val createTournamentResult: AppResult<Tournament> = unusedResult(),
) : TournamentRepository {
    var lastTournamentId: String? = null
    var lastRoundId: Int? = null
    var lastRefreshMode: RefreshMode? = null
    var lastCreateRequest: CreateTournamentRequest? = null

    override suspend fun listTournaments(refreshMode: RefreshMode) = listTournamentsResult

    override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> {
        lastCreateRequest = request
        return createTournamentResult
    }

    override suspend fun renameTournament(tournamentId: String, name: String) = unusedResult<Unit>()

    override suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        primaryColor: String,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        associationLogoContentType: String?,
        associationLogoBytes: ByteArray?,
        associationLogoSourceTournamentId: String?,
        removeAssociationLogo: Boolean,
        roundSchedules: List<TournamentRoundSchedule>,
        agendaItems: List<TournamentAgendaItem>,
    ) = unusedResult<Tournament>()

    override suspend fun generateTournamentIdCards(tournamentId: String) = unusedResult<ByteArray>()
    override suspend fun generateIdCardProof(request: IdCardProofRequest) = unusedResult<ByteArray>()
    override suspend fun generateTournamentIdList(tournamentId: String) = unusedResult<ByteArray>()
    override suspend fun generateEmaReport(tournamentId: String, rankings: List<PlayerRanking>) = unusedResult<ByteArray>()
    override suspend fun deleteTournament(tournamentId: String) = unusedResult<Unit>()

    override suspend fun listTournamentMembers(tournamentId: String, refreshMode: RefreshMode) = unusedResult<List<TournamentMember>>()
    override suspend fun upsertTournamentMember(tournamentId: String, uid: String) = unusedResult<Unit>()
    override suspend fun removeTournamentMember(tournamentId: String, uid: String) = unusedResult<Unit>()

    override suspend fun listTournamentPlayers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentPlayer>> = unusedResult()
    override suspend fun listTournamentTeams(tournamentId: String, refreshMode: RefreshMode) = unusedResult<List<TournamentTeam>>()
    override suspend fun updateTournamentTeam(tournamentId: String, teamId: Int, name: String, emaIds: List<String?>) = unusedResult<Unit>()
    override suspend fun listCountries(refreshMode: RefreshMode) = unusedResult<List<Country>>()

    override suspend fun assignTournamentPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
        nonMember: NonMemberPlayer?,
    ) = unusedResult<Unit>()

    override suspend fun listTournamentRounds(tournamentId: String, refreshMode: RefreshMode) = unusedResult<List<TournamentRound>>()

    override suspend fun listTournamentTables(
        tournamentId: String,
        roundId: Int?,
        refreshMode: RefreshMode,
    ): AppResult<List<TournamentTable>> {
        lastTournamentId = tournamentId
        lastRoundId = roundId
        lastRefreshMode = refreshMode
        return unusedResult()
    }

    override suspend fun getTableWithHands(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        refreshMode: RefreshMode,
    ) = unusedResult<Pair<TableState, List<TableHand>>>()

    override suspend fun patchTable(tournamentId: String, roundId: Int, tableId: Int, patch: Map<String, Any?>) = unusedResult<Unit>()
    override suspend fun patchHand(tournamentId: String, roundId: Int, tableId: Int, handId: Int, patch: Map<String, Any?>) = unusedResult<Unit>()

    override suspend fun saveTableState(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        expectedVersion: Long,
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ) = unusedResult<Pair<TableState, List<TableHand>>>()

    override suspend fun resetTable(tournamentId: String, roundId: Int, tableId: Int) = unusedResult<Unit>()
}

private class RecordingPlayerRepository : PlayerRepository {
    override suspend fun listPlayers(refreshMode: RefreshMode) = unusedResult<List<Player>>()
    override suspend fun createPlayer(player: Player) = unusedResult<Player>()
    override suspend fun updatePlayer(previousEmaId: String, player: Player) = unusedResult<Unit>()
    override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray) = unusedResult<Player>()
}

private class RecordingAuthRepository(
    private val signInResult: AppResult<AuthSession>,
) : AuthRepository {
    var lastEmail: String? = null

    override suspend fun currentSession(): AuthSession? = null

    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> {
        lastEmail = email
        return signInResult
    }

    override suspend fun requestPasswordReset(email: String) = unusedResult<Unit>()
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() = Unit
    override suspend fun refreshSession() = unusedResult<AuthSession>()
    override suspend fun signOut() = Unit
    override suspend fun getMe(refreshMode: RefreshMode) = unusedResult<UserProfile>()
}

private fun <T> unusedResult(): AppResult<T> =
    AppResult.Failure(AppError.Unexpected("Unused test repository method"))
