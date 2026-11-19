package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CustomRole
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.LevelPermissions
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.PermissionChange
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DefaultAdminRepositoryRolesTest {
    private class StaleRecorder : RepositoryCache {
        val stale = mutableListOf<String>()

        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T = load()

        override suspend fun <T : Any> shared(key: String, maxAge: kotlin.time.Duration, fetch: suspend () -> T): T = fetch()

        override suspend fun markStale(vararg keyPrefixes: String) {
            stale += keyPrefixes
        }

        override suspend fun clear() = Unit
    }

    private val cache = StaleRecorder()
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(answer: (HttpRequestData) -> String?) = DefaultAdminRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            answer(request)?.let { jsonResponse(it) } ?: respond("{}", HttpStatusCode.Forbidden)
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultAdminRepositoryRolesTest"),
        cache = cache,
    )

    private val HttpRequestData.text get() = (body as TextContent).text

    @Test
    fun readsTheCatalogAndLeavesOutUnknownRolesAndPermissions() = runTest {
        val catalog = assertIs<AppResult.Success<RolesCatalog>>(
            repository {
                """{
                    "standard":[{"id":"EDITOR","base":"EDITOR","permissions":["tournament.view","tournament.edit","from.the.future"]},
                                {"id":"PRESIDENT","base":"PRESIDENT","permissions":[]}],
                    "custom":[{"id":"r1","name":"Head referee","base":"EDITOR","permissions":["tournament.view"],"accounts":3},
                              {"id":"r2","name":"Unknown","base":"PRESIDENT"}],
                    "grantable":{"EDITOR":["tournament.view","tournament.edit"],"CLUB_ADMIN":["ema.create"]},
                    "required":["tournament.view"]
                }"""
            }.listRoles(),
        ).value

        assertEquals("/functions/admin/roles", requests.single().url.encodedPath)
        assertEquals(mapOf(GlobalUserRole.EDITOR to setOf(Permission.TOURNAMENT_VIEW, Permission.TOURNAMENT_EDIT)), catalog.standard)
        assertEquals(listOf(CustomRole("r1", "Head referee", GlobalUserRole.EDITOR, setOf(Permission.TOURNAMENT_VIEW), accounts = 3)), catalog.custom)
        assertEquals(setOf(Permission.EMA_CREATE), catalog.grantable[GlobalUserRole.CLUB_ADMIN])
        assertEquals(setOf(Permission.TOURNAMENT_VIEW), catalog.required)
    }

    @Test
    fun createsChangesAndDeletesRoles() = runTest {
        val role = """{"id":"r1","name":"Head referee","base":"EDITOR","permissions":["tournament.view","tournament.edit"]}"""
        val repository = repository { request -> if (request.method == HttpMethod.Delete) """{"ok":true,"accounts":2}""" else role }

        val created = repository.createRole("  Head referee ", GlobalUserRole.EDITOR, setOf(Permission.TOURNAMENT_EDIT, Permission.TOURNAMENT_VIEW))
        assertEquals("r1", assertIs<AppResult.Success<CustomRole>>(created).value.id)
        assertEquals(HttpMethod.Post, requests[0].method)
        // The name is trimmed and the permissions go in the order of the app.
        assertTrue(requests[0].text.contains("\"name\":\"Head referee\""))
        assertTrue(requests[0].text.contains("\"permissions\":[\"tournament.view\",\"tournament.edit\"]"))
        assertTrue(requests[0].text.contains("\"base\":\"EDITOR\""))
        assertTrue(cache.stale.isEmpty(), "a new role has no accounts yet")

        assertIs<AppResult.Success<CustomRole>>(repository.updateRole("r 1", "Head referee", setOf(Permission.TOURNAMENT_VIEW)))
        assertEquals(HttpMethod.Put, requests[1].method)
        assertEquals("/functions/admin/roles/r%201", requests[1].url.encodedPath)
        assertEquals(listOf("global:users", "global:whoAmI"), cache.stale)

        cache.stale.clear()
        assertEquals(2, assertIs<AppResult.Success<Int>>(repository.deleteRole("r1")).value)
        assertEquals(HttpMethod.Delete, requests[2].method)
        assertEquals(listOf("global:users", "global:whoAmI"), cache.stale)
    }

    @Test
    fun savesThePermissionsOfOneAccountAndReadsItsSettings() = runTest {
        val repository = repository {
            """{"uid":"u1","email":"a@b.c","role":"EDITOR","roles":["EDITOR"],"disabled":false,
                "permissionSettings":{"customRoles":{"EDITOR":"r1","PRESIDENT":"x"},
                                      "permissionChanges":{"EDITOR":{"added":["ema.create"],"removed":["tournament.edit"]}}},
                "rolePermissions":{"EDITOR":["tournament.view","ema.create"],"CLUB_ADMIN":["tournament.view"]}}"""
        }

        val user = assertIs<AppResult.Success<ManagedUser>>(
            repository.setUserPermissions(
                "u1",
                mapOf(
                    GlobalUserRole.EDITOR to LevelPermissions("r1", setOf(Permission.EMA_CREATE, Permission.TOURNAMENT_VIEW)),
                    GlobalUserRole.CLUB_ADMIN to LevelPermissions(null),
                ),
            ),
        ).value

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/functions/admin/users/u1/permissions", request.url.encodedPath)
        assertTrue(request.text.contains("\"EDITOR\":{\"roleId\":\"r1\",\"permissions\":[\"tournament.view\",\"ema.create\"]}"), request.text)
        assertEquals(mapOf(GlobalUserRole.EDITOR to "r1"), user.customRoleIds)
        assertEquals(mapOf(GlobalUserRole.EDITOR to PermissionChange(setOf(Permission.EMA_CREATE), setOf(Permission.TOURNAMENT_EDIT))), user.permissionChanges)
        assertEquals(setOf(Permission.TOURNAMENT_VIEW, Permission.EMA_CREATE), user.rolePermissions[GlobalUserRole.EDITOR])
        assertEquals(listOf("global:users"), cache.stale)
    }

    @Test
    fun aRefusalOfTheServerBecomesAFailure() = runTest {
        val repository = repository { null }
        assertIs<AppResult.Failure>(repository.listRoles())
        assertIs<AppResult.Failure>(repository.deleteRole("r1"))
        assertIs<AppResult.Failure>(repository.setUserPermissions("u1", emptyMap()))
        assertTrue(cache.stale.isEmpty())
    }
}
