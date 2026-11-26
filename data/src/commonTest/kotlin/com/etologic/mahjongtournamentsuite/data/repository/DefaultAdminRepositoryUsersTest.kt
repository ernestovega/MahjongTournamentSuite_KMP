package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.ActivityFilter
import com.etologic.mahjongtournamentsuite.domain.model.ActivityOrigin
import com.etologic.mahjongtournamentsuite.domain.model.ActivityPage
import com.etologic.mahjongtournamentsuite.domain.model.ActivitySource
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The accounts screens of the admin: who the signed-in account is, the activity log, the list of accounts and every
 * change to one. Each call goes to its route, maps the answer, and marks the lists it changes as stale.
 */
class DefaultAdminRepositoryUsersTest {
    private class Recorder : RepositoryCache {
        val stale = mutableListOf<String>()
        val revisions = mutableListOf<Long>()

        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T {
            revisions += revision()
            return load()
        }

        override suspend fun <T : Any> shared(key: String, maxAge: Duration, fetch: suspend () -> T): T = fetch()

        override suspend fun markStale(vararg keyPrefixes: String) {
            stale += keyPrefixes
        }

        override suspend fun clear() = Unit
    }

    private val cache = Recorder()
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(answer: (HttpRequestData) -> String?) = DefaultAdminRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            if (request.url.encodedPath.endsWith("/sync/manifest")) {
                return@mockBackendApi jsonResponse("""{"resources":{"users":{"revision":7}},"tableVersions":{}}""")
            }
            answer(request)?.let { jsonResponse(it) } ?: respond("""{"error":"refused"}""", HttpStatusCode.Forbidden)
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultAdminRepositoryUsersTest"),
        cache = cache,
    )

    private val refused = repository { null }

    private fun HttpRequestData.line() = "${method.value} ${url.encodedPath.removePrefix("/functions")}"
    private val HttpRequestData.text get() = (body as TextContent).text
    private fun sent(line: String) = requests.single { it.line() == line }

    @Test
    fun whoAmIReadsTheRolesAndThePermissions() = runTest {
        val withPermissions = assertIs<AppResult.Success<AdminStatus>>(
            repository {
                """{"uid":"u1","role":"EDITOR","roles":["EDITOR","CLUB_ADMIN"],"adminOrganizations":["c1"],"permissions":["tournament.view"]}"""
            }.whoAmI(RefreshMode.FORCE),
        ).value
        assertEquals(setOf(GlobalUserRole.EDITOR, GlobalUserRole.CLUB_ADMIN), withPermissions.roles)
        assertEquals(setOf("c1"), withPermissions.adminOrganizations)
        assertEquals(setOf(Permission.TOURNAMENT_VIEW), withPermissions.permissions)
        assertEquals(listOf(7L), cache.revisions, "the cache is keyed on the users revision of the manifest")

        val older = assertIs<AppResult.Success<AdminStatus>>(repository { """{"uid":"u1","role":"EDITOR"}""" }.whoAmI(RefreshMode.FORCE)).value
        assertEquals(setOf(GlobalUserRole.EDITOR), older.roles, "an older server sends only the role")
        assertEquals(Permission.defaultsOf(GlobalUserRole.EDITOR), older.permissions, "without permissions, the defaults of the roles")

        assertIs<AppResult.Failure>(refused.whoAmI(RefreshMode.FORCE))
    }

    @Test
    fun theActivityLogSendsTheFilter() = runTest {
        val page = assertIs<AppResult.Success<ActivityPage>>(
            repository {
                """{"entries":[{"id":"e1","kind":"table","at":5,"action":"save","uid":"u1","details":[{"key":"k","value":"v"}],""" +
                    """"changes":[{"field":"score","from":"1","to":"2","handId":3}],"players":["Ana"]}],"nextCursor":"c2"}"""
            }.loadActivity(
                ActivityFilter(tournamentId = "t1", source = ActivitySource.TABLES, origin = ActivityOrigin.PLAYER_APP, text = "  Ana  "),
                cursor = "c1",
            ),
        ).value

        val entry = page.entries.single()
        assertTrue(entry.isTableSave)
        assertEquals(listOf("k" to "v"), entry.details)
        assertEquals(3, entry.changes.single().handId)
        assertEquals("c2", page.nextCursor)
        val url = requests.single().url
        assertEquals("tables", url.parameters["source"])
        assertEquals("player", url.parameters["origin"])
        assertEquals("Ana", url.parameters["q"], "the text goes trimmed")
        assertEquals("c1", url.parameters["cursor"])

        requests.clear()
        repository { """{"entries":[]}""" }.loadActivity(ActivityFilter(text = "   "), cursor = null)
        val bare = requests.single().url
        assertNull(bare.parameters["q"], "a blank text is no filter")
        assertNull(bare.parameters["origin"])

        assertIs<AppResult.Failure>(refused.loadActivity(ActivityFilter(), null))
    }

    @Test
    fun aLookupGoesToTheTournamentWhenThereIsOne() = runTest {
        val repository = repository { """{"uid":"u2","email":"a@b.c","alias":"Ana","emaId":"10000001","roles":["EDITOR"]}""" }

        val found = assertIs<AppResult.Success<UserProfile>>(repository.lookupUser("a@b.c", tournamentId = null)).value
        assertEquals(setOf(GlobalUserRole.EDITOR), found.roles)
        assertEquals("10000001", found.emaId)
        repository.lookupUser("a@b.c", tournamentId = "t1")

        assertEquals(listOf("GET /admin/users/lookup", "GET /tournaments/t1/users/lookup"), requests.map { it.line() })
        assertEquals("a@b.c", requests[0].url.parameters["identifier"])
        assertEquals("a@b.c", requests[1].url.parameters["email"])
        assertIs<AppResult.Failure>(refused.lookupUser("a@b.c", null))
    }

    @Test
    fun theAccountsListMapsEachAccount() = runTest {
        val users = assertIs<AppResult.Success<List<ManagedUser>>>(
            repository {
                """{"users":[{"uid":"u1","email":"a@b.c","role":"EDITOR","disabled":false,"photoUrl":" ",""" +
                    """"tournamentAssignments":[{"tournamentId":"t1","tournamentName":"Madrid","organizerIds":["c1"]}]},""" +
                    """{"uid":"u2","email":"d@e.f","role":"CLUB_ADMIN","roles":["CLUB_ADMIN","EDITOR"],"disabled":true,"photoUrl":"https://p"}]}"""
            }.listUsers(RefreshMode.FORCE),
        ).value

        assertNull(users[0].photoUrl, "a blank photo is no photo")
        assertEquals(listOf(TournamentAssignment("t1", "Madrid", listOf("c1"))), users[0].tournamentAssignments)
        assertEquals(setOf(GlobalUserRole.EDITOR), users[0].roles)
        assertEquals(setOf(GlobalUserRole.CLUB_ADMIN, GlobalUserRole.EDITOR), users[1].roles)
        assertEquals("https://p", users[1].photoUrl)
        assertIs<AppResult.Failure>(refused.listUsers(RefreshMode.FORCE))
    }

    @Test
    fun creatingAndChangingAnAccountSendsItsFields() = runTest {
        val user = """{"uid":"u2","email":"a@b.c","role":"EDITOR","disabled":false}"""
        val repository = repository { user }

        val created = assertIs<AppResult.Success<ManagedUser>>(
            repository.createUser(
                email = "a@b.c",
                alias = "Ana",
                role = GlobalUserRole.EDITOR,
                tournamentAssignments = listOf(TournamentAssignment("t1", "Madrid", emptyList())),
                emaId = null,
                adminOrganizations = setOf("z", "a"),
            ),
        ).value
        assertEquals("u2", created.uid)
        val createBody = sent("POST /admin/users").text
        assertTrue(""""emaId"""" !in createBody, "no EMA id goes as the empty default: $createBody")
        assertTrue(""""adminOrganizations":["a","z"]""" in createBody, "sorted: $createBody")
        assertTrue(""""tournamentId":"t1"""" in createBody, createBody)

        repository.updateUser(created.copy(alias = "Ana B", emaId = "10000001"))
        assertTrue(""""emaId":"10000001"""" in sent("PUT /admin/users/u2").text)
        assertTrue(cache.stale.isNotEmpty(), "the accounts list is read again")
    }

    @Test
    fun disablingDeletingAndPhotosChangeTheAccount() = runTest {
        val user = """{"uid":"u2","email":"a@b.c","role":"EDITOR","disabled":true}"""
        val repository = repository { request ->
            when (request.line()) {
                "DELETE /admin/users/u2" -> """{"ok":true}"""
                "POST /admin/users/delete" ->
                    """{"results":[{"uid":"u2","outcome":"DELETED"},{"uid":"u3","outcome":"NEW","reason":"SELF"},""" +
                        """{"uid":"u4","outcome":"FAILED","reason":"FROM_THE_FUTURE"}]}"""
                else -> user
            }
        }

        assertTrue(assertIs<AppResult.Success<ManagedUser>>(repository.setUserDisabled("u2", true)).value.disabled)
        assertEquals("""{"disabled":true}""", sent("PUT /admin/users/u2/disabled").text)
        assertIs<AppResult.Success<Unit>>(repository.deleteUser("u2"))
        assertIs<AppResult.Success<ManagedUser>>(repository.updateUserPhoto("u2", "image/png", byteArrayOf(1, 2, 3)))
        assertTrue(""""dataBase64":"AQID"""" in sent("PUT /admin/users/u2/photo").text)
        assertIs<AppResult.Success<ManagedUser>>(repository.removeUserPhoto("u2"))
        assertEquals(1, requests.count { it.line() == "DELETE /admin/users/u2/photo" })

        val deleteRoute = requests.size
        val results = assertIs<AppResult.Success<List<UserDeleteResult>>>(repository.deleteUsers(listOf("u2", "u3", "u4"))).value
        assertEquals(
            listOf(
                UserDeleteResult("u2", UserDeleteOutcome.DELETED, null),
                UserDeleteResult("u3", UserDeleteOutcome.FAILED, UserDeleteFailure.SELF),
                UserDeleteResult("u4", UserDeleteOutcome.FAILED, UserDeleteFailure.UNKNOWN),
            ),
            results,
            "an outcome or a reason this app does not know reads as a failure it cannot explain",
        )
        assertTrue(requests.drop(deleteRoute).single().text.contains(""""uids":["u2","u3","u4"]"""))
        assertTrue(cache.stale.isNotEmpty())
    }

    @Test
    fun accountRequestsAreListedAndResolved() = runTest {
        val user = """{"uid":"u2","email":"a@b.c","role":"EDITOR","disabled":false}"""
        val repository = repository { request ->
            when (request.line()) {
                "GET /admin/account-requests" ->
                    """{"requests":[{"id":"r1","kind":"DELETE","targetUid":"u2","createdAtMs":9},""" +
                        """{"id":"r2","kind":"SOMETHING_NEW","targetUid":"u3"}]}"""
                "POST /admin/account-requests/r1" -> """{"outcome":"APPROVED"}"""
                "DELETE /admin/organizations/c%201" -> """{"ok":true}"""
                else -> """{"user":$user}"""
            }
        }

        val listed = assertIs<AppResult.Success<List<AccountRequest>>>(repository.listAccountRequests()).value
        assertEquals(listOf(AccountRequestKind.DELETE, AccountRequestKind.DISABLE), listed.map { it.kind }, "an unknown kind reads as a disable")
        assertEquals(9L, listed[0].createdAtMillis)
        assertIs<AppResult.Success<Unit>>(repository.resolveAccountRequest("r1", approve = true))

        assertIs<AppResult.Success<ManagedUser>>(repository.removeFromMyOrganizations("u2", AccountRequestKind.DELETE, "left"))
        val removal = requests.last().text
        assertTrue(""""kind":"DELETE"""" in removal && """"reason":"left"""" in removal, removal)
        repository.removeFromMyOrganizations("u2", null, "left")
        assertTrue(""""kind"""" !in requests.last().text || """"kind":null""" in requests.last().text)

        assertIs<AppResult.Success<Unit>>(repository.deleteOrganization("c 1"))
    }

    @Test
    fun aRefusedAccountChangeGivesAFailure() = runTest {
        val user = ManagedUser(uid = "u2", email = "a@b.c", alias = "", role = GlobalUserRole.EDITOR, disabled = false, tournamentAssignments = emptyList())

        assertIs<AppResult.Failure>(refused.createUser("a@b.c", "", GlobalUserRole.EDITOR, emptyList(), "1", emptySet()))
        assertIs<AppResult.Failure>(refused.updateUser(user))
        assertIs<AppResult.Failure>(refused.setUserDisabled("u2", false))
        assertIs<AppResult.Failure>(refused.deleteUser("u2"))
        assertIs<AppResult.Failure>(refused.deleteUsers(listOf("u2")))
        assertIs<AppResult.Failure>(refused.updateUserPhoto("u2", "image/png", byteArrayOf()))
        assertIs<AppResult.Failure>(refused.removeUserPhoto("u2"))
        assertIs<AppResult.Failure>(refused.listAccountRequests())
        assertIs<AppResult.Failure>(refused.resolveAccountRequest("r1", false))
        assertIs<AppResult.Failure>(refused.deleteOrganization("c1"))
    }
}
