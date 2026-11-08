package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.*
import com.etologic.mahjongtournamentsuite.domain.repository.*
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A server whose answers wait for [release]. The test sees which requests started before any answer came,
 * so it can tell requests in a row from requests at the same time.
 */
private class GatedServer {
    val started = mutableListOf<String>()
    val modes = mutableListOf<RefreshMode>()
    val gate = CompletableDeferred<Unit>()
    var isTeams = false
    var tournamentsResult: (() -> AppResult<List<Tournament>>)? = null
    var tablesFail = false

    // The revisions request answers at once, unless a test holds it to see what waits for it.
    var holdRevisions = false
    val revisionsGate = CompletableDeferred<Unit>()

    fun release() = gate.complete(Unit)

    suspend fun prefetchRevisions() {
        started += "revisions"
        if (holdRevisions) revisionsGate.await()
    }

    suspend fun <T> answer(name: String, mode: RefreshMode?, value: () -> AppResult<T>): AppResult<T> {
        started += name
        mode?.let { modes += it }
        gate.await()
        return value()
    }
}

private class GatedTournamentRepository(private val server: GatedServer) : TournamentRepository {
    override suspend fun prefetchRevisions(tournamentId: String) = server.prefetchRevisions()

    override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = server.answer("tournaments", refreshMode) {
        server.tournamentsResult?.invoke()
            ?: AppResult.Success(listOf(Tournament(id = "t1", name = "Open", isTeams = server.isTeams, numPlayers = 4, numRounds = 1)))
    }

    override suspend fun listTournamentPlayers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentPlayer>> = server.answer("players", refreshMode) {
        AppResult.Success((1..4).map { TournamentPlayer(id = it, name = "", team = 1, assignedEmaId = "1000000$it") })
    }

    override suspend fun getAllTablesWithHands(
        tournamentId: String,
        refreshMode: RefreshMode,
    ): AppResult<List<Pair<TableState, List<TableHand>>>> = server.answer("tables", refreshMode) {
        if (server.tablesFail) AppResult.Failure(AppError.Network) else AppResult.Success(emptyList())
    }

    override suspend fun listTournamentTeams(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentTeam>> = server.answer("teams", refreshMode) { AppResult.Success(listOf(TournamentTeam(id = 1, name = "Team", playerIds = listOf(1, 2, 3, 4)))) }

    override suspend fun listTournamentTables(tournamentId: String, roundId: Int?, refreshMode: RefreshMode) = unusedFakeResult<List<TournamentTable>>()

    override suspend fun getTableWithHands(tournamentId: String, roundId: Int, tableId: Int, refreshMode: RefreshMode) = unusedFakeResult<Pair<TableState, List<TableHand>>>()

    override suspend fun getRoundTablesWithHands(tournamentId: String, roundId: Int, refreshMode: RefreshMode) = unusedFakeResult<List<Pair<TableState, List<TableHand>>>>()

    override suspend fun saveTableState(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        expectedVersion: Long,
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ) = unusedFakeResult<Pair<TableState, List<TableHand>>>()

    override suspend fun getTournamentManifest(tournamentId: String) = unusedFakeResult<TournamentManifest>()

    override suspend fun createTournament(request: CreateTournamentRequest) = unusedFakeResult<Tournament>()

    override suspend fun renameTournament(tournamentId: String, name: String) = unusedFakeResult<Unit>()

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
    ) = unusedFakeResult<Tournament>()

    override suspend fun generateTournamentIdCards(tournamentId: String, sheet: IdCardSheetLayout?) = unusedFakeResult<ByteArray>()
    override suspend fun generateIdCardProof(request: IdCardProofRequest) = unusedFakeResult<ByteArray>()
    override suspend fun generateTournamentIdList(tournamentId: String) = unusedFakeResult<ByteArray>()
    override suspend fun generateEmaReport(tournamentId: String, rankings: List<PlayerRanking>) = unusedFakeResult<ByteArray>()
    override suspend fun deleteTournament(tournamentId: String) = unusedFakeResult<Unit>()

    override suspend fun listTournamentMembers(tournamentId: String, refreshMode: RefreshMode) = unusedFakeResult<List<TournamentMember>>()
    override suspend fun upsertTournamentMember(tournamentId: String, uid: String) = unusedFakeResult<Unit>()
    override suspend fun removeTournamentMember(tournamentId: String, uid: String) = unusedFakeResult<Unit>()

    override suspend fun updateTournamentTeam(tournamentId: String, teamId: Int, name: String, emaIds: List<String?>) = unusedFakeResult<Unit>()
    override suspend fun listCountries(refreshMode: RefreshMode) = unusedFakeResult<List<Country>>()
    override suspend fun resolveVenueLink(url: String) = unusedFakeResult<ResolvedVenueLink>()

    override suspend fun assignTournamentPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
    ) = unusedFakeResult<Unit>()

    override suspend fun setPlayerSubstitution(tournamentId: String, tournamentPlayerId: Int, substitution: PlayerSubstitution?) = unusedFakeResult<Unit>()

    override suspend fun listTournamentRounds(tournamentId: String, refreshMode: RefreshMode) = unusedFakeResult<List<TournamentRound>>()
}

private class GatedPlayerRepository(private val server: GatedServer) : PlayerRepository {
    override suspend fun listPlayers(refreshMode: RefreshMode): AppResult<List<Player>> = server.answer("registry", refreshMode) { AppResult.Success(emptyList()) }
    override suspend fun createPlayer(player: Player): AppResult<Player> = error("unused")
    override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = error("unused")
    override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = error("unused")
}

private class GatedAuthRepository(private val server: GatedServer) : AuthRepository {
    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = server.answer("me", refreshMode) { AppResult.Success(UserProfile(uid = "u1", email = "p@x.es", emaId = "10000001")) }

    override suspend fun currentSession(): AuthSession = AuthSession(uid = "u1", idToken = "id", refreshToken = "refresh")
    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> = error("unused")
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> = error("unused")
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() = Unit
    override suspend fun refreshSession(): AppResult<AuthSession> = error("unused")
    override suspend fun signOut() = Unit
    override suspend fun updateAlias(alias: String): AppResult<UserProfile> = error("unused")
    override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = error("unused")
    override suspend fun removePhoto(): AppResult<UserProfile> = error("unused")
}

private fun <T> unusedFakeResult(): AppResult<T> = AppResult.Failure(AppError.Unexpected("Unused test repository method"))

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerTournamentLoadTest {
    private val server = GatedServer()
    private val presenter = PlayerTournamentPresenter(
        GatedTournamentRepository(server),
        GatedPlayerRepository(server),
        GatedAuthRepository(server),
        CalculateTournamentRankingsUseCase(),
    )

    @Test
    fun allRequestsStartBeforeTheFirstAnswerComes() = runTest {
        val load = async { presenter.load("t1") }
        runCurrent()

        assertEquals(setOf("revisions", "tournaments", "players", "registry", "tables", "me"), server.started.toSet())
        server.release()
        assertIs<AppResult.Success<PlayerTournamentView>>(load.await())
    }

    @Test
    fun theRevisionsAreAskedOnceBeforeAnyRead() = runTest {
        server.holdRevisions = true
        val load = async { presenter.load("t1") }
        runCurrent()

        assertEquals(listOf("revisions"), server.started)

        server.revisionsGate.complete(Unit)
        runCurrent()

        assertEquals("revisions", server.started.first())
        assertEquals(setOf("tournaments", "players", "registry", "tables", "me"), server.started.drop(1).toSet())
        server.release()
        assertIs<AppResult.Success<PlayerTournamentView>>(load.await())
        assertEquals(1, server.started.count { it == "revisions" })
    }

    @Test
    fun aForcedLoadAlsoAsksForTheRevisionsFirst() = runTest {
        server.release()

        presenter.load("t1", RefreshMode.FORCE)

        assertEquals("revisions", server.started.first())
        assertEquals(1, server.started.count { it == "revisions" })
    }

    @Test
    fun aCachedLoadDoesNotAskForTheRevisions() = runTest {
        server.release()

        assertIs<AppResult.Success<PlayerTournamentView>>(presenter.load("t1", RefreshMode.CACHED))
        assertTrue("revisions" !in server.started)
    }

    @Test
    fun everyRequestUsesTheRefreshModeOfTheCaller() = runTest {
        server.release()

        presenter.load("t1", RefreshMode.FORCE)

        assertTrue(server.modes.isNotEmpty())
        assertTrue(server.modes.all { it == RefreshMode.FORCE })
    }

    @Test
    fun aTeamTournamentAlsoLoadsTheTeams() = runTest {
        server.isTeams = true
        server.release()

        assertIs<AppResult.Success<PlayerTournamentView>>(presenter.load("t1"))
        assertTrue("teams" in server.started)
    }

    @Test
    fun aTournamentWithoutTeamsNeverAsksForTeams() = runTest {
        server.release()

        presenter.load("t1")

        assertTrue("teams" !in server.started)
    }

    @Test
    fun aFailedRequestFailsTheLoad() = runTest {
        server.tablesFail = true
        server.release()

        assertEquals(AppError.Network, assertIs<AppResult.Failure>(presenter.load("t1")).error)
    }

    @Test
    fun anUnknownTournamentFailsTheLoad() = runTest {
        server.tournamentsResult = { AppResult.Success(emptyList()) }
        server.release()

        assertIs<AppResult.Failure>(presenter.load("t1"))
    }

    @Test
    fun aFailedTournamentListFailsTheLoad() = runTest {
        server.tournamentsResult = { AppResult.Failure(AppError.Network) }
        server.release()

        assertEquals(AppError.Network, assertIs<AppResult.Failure>(presenter.load("t1")).error)
    }
}
