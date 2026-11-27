package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.IdCardStaffCandidate
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
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
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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
    var tables: List<Pair<TableState, List<TableHand>>> = emptyList()

    /** The own tables of the hidden rounds, with their results. Null: the request fails. */
    var ownHidden: List<Pair<TableState, List<TableHand>>>? = null
    var ownHiddenCalls = 0

    // The revisions request answers at once, unless a test holds it to see what waits for it.
    var holdRevisions = false
    val revisionsGate = CompletableDeferred<Unit>()

    // The manifest of each tournament, for the places of the tournaments list. A missing one fails.
    val manifests = mutableMapOf<String, TournamentManifest>()

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
        if (server.tablesFail) AppResult.Failure(AppError.Network) else AppResult.Success(server.tables)
    }

    override suspend fun getOwnHiddenTables(tournamentId: String): AppResult<List<Pair<TableState, List<TableHand>>>> {
        server.ownHiddenCalls++
        return server.ownHidden?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Network)
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

    override suspend fun getRoundClocks(tournamentId: String): AppResult<RoundClocks> = server.answer("clocks", null) { AppResult.Failure(AppError.Network) }

    override suspend fun getTournamentManifest(tournamentId: String): AppResult<TournamentManifest> = server.manifests[tournamentId]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Network)

    override suspend fun createTournament(request: CreateTournamentRequest) = unusedFakeResult<Tournament>()

    override suspend fun renameTournament(tournamentId: String, name: String) = unusedFakeResult<Unit>()

    override suspend fun updateTournamentSettings(
        tournamentId: String,
        name: String,
        shortName: String,
        organizerIds: List<String>?,
        eventStartDate: String,
        eventEndDate: String,
        hostCountry: String,
        hostCity: String,
        venue: TournamentVenue,
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
        hideResultsFromRound: Int?,
    ) = unusedFakeResult<Tournament>()

    override suspend fun generateTournamentIdCards(tournamentId: String, sheet: IdCardSheetLayout?) = unusedFakeResult<ByteArray>()
    override suspend fun generateIdCardProof(request: IdCardProofRequest) = unusedFakeResult<ByteArray>()
    override suspend fun listIdCardStaffCandidates(tournamentId: String) = unusedFakeResult<List<IdCardStaffCandidate>>()
    override suspend fun loadIdCardStaffNames(tournamentId: String) = unusedFakeResult<List<String>>()
    override suspend fun saveIdCardStaffNames(tournamentId: String, names: List<String>) = unusedFakeResult<List<String>>()
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
    override suspend fun deleteEmaPlayers(emaIds: List<String>): AppResult<List<com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult>> = error("unused")
    override suspend fun tournamentsOfEmaPlayers(emaIds: List<String>): AppResult<List<com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerTournaments>> = error("unused")
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

    override suspend fun setPublicPhoto(visible: Boolean): AppResult<UserProfile> = error("unused")
    override suspend fun deleteAccount(): AppResult<Unit> = error("unused")
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

        assertEquals(setOf("revisions", "clocks", "tournaments", "players", "registry", "tables", "me"), server.started.toSet())
        server.release()
        assertIs<AppResult.Success<PlayerTournamentView>>(load.await())
    }

    @Test
    fun theRevisionsAreAskedOnceBeforeAnyRead() = runTest {
        server.holdRevisions = true
        val load = async { presenter.load("t1") }
        runCurrent()

        // The clocks need no revision, so they do not wait for them.
        assertEquals(listOf("revisions", "clocks"), server.started)

        server.revisionsGate.complete(Unit)
        runCurrent()

        assertEquals("revisions", server.started.first())
        assertEquals(setOf("clocks", "tournaments", "players", "registry", "tables", "me"), server.started.drop(1).toSet())
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

    private fun manifest(tables: Long, players: Long = 1, teams: Long = 1, rounds: Long = 1) = TournamentManifest(tablesRevision = tables, tableVersions = emptyMap(), playersRevision = players, teamsRevision = teams, roundsRevision = rounds)

    @Test
    fun thePlacesKeyChangesWhenHandsPlayersOrTeamsChange() = runTest {
        server.manifests["a"] = manifest(tables = 1)
        server.manifests["b"] = manifest(tables = 1)
        val first = presenter.standingsRevision(listOf("a", "b"))

        server.manifests["a"] = manifest(tables = 1, rounds = 2)
        assertEquals(first, presenter.standingsRevision(listOf("b", "a")))

        server.manifests["b"] = manifest(tables = 2)
        assertNotEquals(first, presenter.standingsRevision(listOf("a", "b")))
        server.manifests["b"] = manifest(tables = 1, players = 2)
        assertNotEquals(first, presenter.standingsRevision(listOf("a", "b")))
        server.manifests["b"] = manifest(tables = 1, teams = 2)
        assertNotEquals(first, presenter.standingsRevision(listOf("a", "b")))
    }

    @Test
    fun aFailedManifestGivesNoPlacesKey() = runTest {
        server.manifests["a"] = manifest(tables = 1)

        assertNull(presenter.standingsRevision(listOf("a", "missing")))
    }

    private fun table(tableId: Int, version: Long, eastScore: String, hidden: Boolean) = TableState(
        version = version, roundId = 1, tableId = tableId, playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
        playerEastScore = eastScore, playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
        isCompleted = false, useTotalsOnly = false, resultsHidden = hidden,
    )

    @Test
    fun theOwnTableOfAHiddenRoundComesWithItsResultsAndACachedLoadKeepsIt() = runTest {
        server.release()
        server.tournamentsResult = {
            AppResult.Success(listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1, resultsHiddenFromRound = 1)))
        }
        server.tables = listOf(table(1, version = 3, eastScore = "", hidden = true) to emptyList(), table(2, version = 3, eastScore = "", hidden = true) to emptyList())
        // The own table 1 has its results. An own copy older than the public table is left out.
        server.ownHidden = listOf(table(1, version = 3, eastScore = "40", hidden = false) to emptyList(), table(2, version = 2, eastScore = "9", hidden = false) to emptyList())

        val loaded = assertIs<AppResult.Success<PlayerTournamentView>>(presenter.load("t1")).value
        assertEquals(listOf("40", ""), loaded.tables.map { it.table.playerEastScore })

        // A cached load asks nothing and uses the own tables of the last load; so does a load whose request fails.
        val cached = assertIs<AppResult.Success<PlayerTournamentView>>(presenter.load("t1", RefreshMode.CACHED)).value
        assertEquals("40", cached.tables.first().table.playerEastScore)
        assertEquals(1, server.ownHiddenCalls)
        server.ownHidden = null
        val failed = assertIs<AppResult.Success<PlayerTournamentView>>(presenter.load("t1", RefreshMode.FORCE)).value
        assertEquals("40", failed.tables.first().table.playerEastScore)
    }

    @Test
    fun aCachedLoadOfAHiddenTournamentWithoutAnEarlierLoadHasNoOwnTables() = runTest {
        server.release()
        server.tournamentsResult = {
            AppResult.Success(listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1, resultsHiddenFromRound = 1)))
        }
        server.tables = listOf(table(1, version = 3, eastScore = "", hidden = true) to emptyList())

        val cached = assertIs<AppResult.Success<PlayerTournamentView>>(presenter.load("t1", RefreshMode.CACHED)).value
        assertEquals("", cached.tables.single().table.playerEastScore)
        assertEquals(0, server.ownHiddenCalls)
    }
}
