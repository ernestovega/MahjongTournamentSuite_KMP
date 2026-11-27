package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.ActivityFilter
import com.etologic.mahjongtournamentsuite.domain.model.ActivityPage
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.LevelPermissions
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.MyTournaments
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountChangeRequest
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** What a repository answers for the calls it does not implement, and the defaults of the arguments a caller leaves out. */
class RepositoryDefaultsTest {
    private val calls = mutableListOf<String>()

    @Test
    fun aTournamentRepositoryWithoutTheNewerCallsSaysTheyAreNotAvailable() = runTest {
        val repository: TournamentRepository = UnusedTournamentRepository()
        val request = PlayerCountChangeRequest("t1", numTries = 1, players = emptyList(), tables = emptyList())

        assertIs<AppResult.Failure>(repository.generateTableQrCodes("t1", perSheet = 6))
        assertIs<AppResult.Failure>(repository.generateScoreSheets("t1", perTable = false, emaLogo = true))
        assertIs<AppResult.Failure>(repository.changePlayerCount(request))
        assertIs<AppResult.Failure>(repository.resetTable("t1", 1, 2))
        repository.prefetchRevisions("t1")
        assertIs<AppResult.Failure>(repository.getRoundClocks("t1"))
        assertIs<AppResult.Failure>(repository.updateRoundClock("t1", 1, RoundClockAction.Stop))
        assertIs<AppResult.Failure>(repository.updateRoundControl("t1", 1, RoundControlChange()))
        assertIs<AppResult.Failure>(repository.completeTables("t1", listOf(TableKey(1, 2))))
        assertEquals(AppResult.Success(emptyList()), repository.listMyTournamentSlots())
        assertEquals(AppResult.Success(MyTournaments()), repository.listMyTournaments())
    }

    @Test
    fun myTournamentsAreMySlotsOrTheirFailure() = runTest {
        val slot = MyTournamentSlot("t1", playerId = 3, teamId = 0)
        var slots: AppResult<List<MyTournamentSlot>> = AppResult.Success(listOf(slot))
        val repository: TournamentRepository = object : UnusedTournamentRepository() {
            override suspend fun listMyTournamentSlots() = slots
        }

        assertEquals(AppResult.Success(MyTournaments(slots = listOf(slot))), repository.listMyTournaments())

        slots = AppResult.Failure(AppError.Unexpected("offline"))
        assertEquals(slots, repository.listMyTournaments())
    }

    @Test
    fun aTournamentReadWithoutARefreshModeReadsIfChanged() = runTest {
        val repository: TournamentRepository = object : UnusedTournamentRepository() {
            override suspend fun generateTournamentIdCards(tournamentId: String, sheet: IdCardSheetLayout?): AppResult<ByteArray> {
                calls += "idCards $sheet"
                return AppResult.Success(ByteArray(0))
            }

            override suspend fun listTournamentMembers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentMember>> =
                record("members", refreshMode)

            override suspend fun listTournamentPlayers(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentPlayer>> =
                record("players", refreshMode)

            override suspend fun listTournamentTeams(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentTeam>> =
                record("teams", refreshMode)

            override suspend fun listTournamentRounds(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentRound>> =
                record("rounds", refreshMode)

            override suspend fun getRoundTablesWithHands(
                tournamentId: String,
                roundId: Int,
                refreshMode: RefreshMode,
            ): AppResult<List<Pair<TableState, List<TableHand>>>> = record("round tables", refreshMode)

            override suspend fun getAllTablesWithHands(
                tournamentId: String,
                refreshMode: RefreshMode,
            ): AppResult<List<Pair<TableState, List<TableHand>>>> = record("all tables", refreshMode)
        }

        repository.generateTournamentIdCards("t1")
        repository.listTournamentMembers("t1")
        repository.listTournamentPlayers("t1")
        repository.listTournamentTeams("t1")
        repository.listTournamentRounds("t1")
        repository.getRoundTablesWithHands("t1", roundId = 1)
        repository.getAllTablesWithHands("t1")

        assertEquals(
            listOf(
                "idCards null",
                "members IF_CHANGED",
                "players IF_CHANGED",
                "teams IF_CHANGED",
                "rounds IF_CHANGED",
                "round tables IF_CHANGED",
                "all tables IF_CHANGED",
            ),
            calls,
        )
    }

    @Test
    fun anAdminRepositoryWithoutOrganizationsAndRolesSaysTheyAreNotSupported() = runTest {
        val repository: AdminRepository = UnusedAdminRepository()
        val draft = OrganizationDraft("Club", OrganizationType.CLUB, "ES", "#00AA00")

        assertIs<AppResult.Failure>(repository.removeFromMyOrganizations("u1", kind = null, reason = "left"))
        assertEquals(AppResult.Success(emptyList()), repository.listAccountRequests())
        assertIs<AppResult.Failure>(repository.resolveAccountRequest("r1", approve = true))
        assertEquals(AppResult.Success(emptyList()), repository.listOrganizations())
        assertIs<AppResult.Failure>(repository.createOrganization(draft))
        assertIs<AppResult.Failure>(repository.updateOrganization("o1", draft))
        assertIs<AppResult.Failure>(repository.deleteOrganization("o1"))
        assertIs<AppResult.Failure>(repository.listRoles())
        assertIs<AppResult.Failure>(repository.createRole("Helper", GlobalUserRole.EDITOR, emptySet()))
        assertIs<AppResult.Failure>(repository.updateRole("r1", "Helper", emptySet()))
        assertIs<AppResult.Failure>(repository.deleteRole("r1"))
        assertIs<AppResult.Failure>(repository.setUserPermissions("u1", mapOf(GlobalUserRole.EDITOR to LevelPermissions(null))))
    }

    @Test
    fun anAdminCallWithoutTheOptionalArgumentsLeavesThemEmpty() = runTest {
        val repository: AdminRepository = object : UnusedAdminRepository() {
            override suspend fun whoAmI(refreshMode: RefreshMode) = record("whoAmI", refreshMode)

            override suspend fun listUsers(refreshMode: RefreshMode) = record("users", refreshMode)

            override suspend fun lookupUser(identifier: String, tournamentId: String?) = record("lookup", tournamentId)

            override suspend fun createUser(
                email: String,
                alias: String,
                role: GlobalUserRole,
                tournamentAssignments: List<TournamentAssignment>,
                emaId: String?,
                adminOrganizations: Set<String>,
            ): AppResult<ManagedUser> = record("create", "$emaId $adminOrganizations")

            override suspend fun loadErrorGroups(sinceMillis: Long?, cursor: ErrorLogCursor?): AppResult<ErrorGroupsPage> =
                record("errors", "$sinceMillis $cursor")

            override suspend fun loadErrorGroup(id: String, reportCursor: ErrorLogCursor?): AppResult<ErrorGroupDetail> =
                record("error", reportCursor)

            override suspend fun loadActivity(filter: ActivityFilter, cursor: String?): AppResult<ActivityPage> = record("activity", cursor)
        }

        repository.whoAmI()
        repository.listUsers()
        repository.lookupUser("ana@example.com")
        repository.createUser("ana@example.com", "Ana", GlobalUserRole.EDITOR, emptyList())
        repository.loadErrorGroups()
        repository.loadErrorGroup("g1")
        repository.loadActivity(ActivityFilter())

        assertEquals(
            listOf("whoAmI IF_CHANGED", "users IF_CHANGED", "lookup null", "create null []", "errors null null", "error null", "activity null"),
            calls,
        )
    }

    private fun record(call: String, argument: Any?): AppResult.Failure {
        calls += "$call $argument"
        return AppResult.Failure(AppError.Unexpected("Not used by this test"))
    }
}
