package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.request.HttpRequestData
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** What the admin repository does with answers it does not fully know: roles from a newer server, logos, no account. */
class DefaultAdminRepositoryUnknownAnswersTest {
    private val requests = mutableListOf<HttpRequestData>()

    private val cache = object : RepositoryCache {
        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T = load()

        override suspend fun <T : Any> shared(key: String, maxAge: Duration, fetch: suspend () -> T): T = fetch()

        override suspend fun markStale(vararg keyPrefixes: String) = Unit

        override suspend fun clear() = Unit
    }

    private fun repository(auth: AuthRepository = FakeAuthRepository(), answer: (HttpRequestData) -> String) = DefaultAdminRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            jsonResponse(answer(request))
        },
        authRepository = auth,
        logger = Logger.withTag("DefaultAdminRepositoryUnknownAnswersTest"),
        cache = cache,
    )

    @Test
    fun aRoleWithABaseFromANewerServerIsAFailure() = runTest {
        val repository = repository { """{"id":"r1","name":"Helper","base":"FROM_THE_FUTURE"}""" }

        assertIs<AppResult.Failure>(repository.createRole("Helper", GlobalUserRole.EDITOR, emptySet()))
        assertIs<AppResult.Failure>(repository.updateRole("r1", "Helper", emptySet()))
    }

    @Test
    fun theRolesAndSettingsOfAnUnknownLevelAreLeftOut() = runTest {
        val user = assertIs<AppResult.Success<List<ManagedUser>>>(
            repository {
                """{"users":[{"uid":"u1","email":"a@b.c","role":"EDITOR","disabled":false,
                    "permissionSettings":{"customRoles":{"EDITOR":"r1","NEW_LEVEL":"r2"},
                        "permissionChanges":{"NEW_LEVEL":{"added":["tournament.view"]},"EDITOR":{"removed":["tournament.edit"]}}},
                    "rolePermissions":{"EDITOR":["tournament.view"],"NEW_LEVEL":["tournament.view"]}}]}"""
            }.listUsers(RefreshMode.FORCE),
        ).value.single()

        assertEquals(mapOf(GlobalUserRole.EDITOR to "r1"), user.customRoleIds)
        assertEquals(setOf(GlobalUserRole.EDITOR), user.permissionChanges.keys)
        assertEquals(mapOf(GlobalUserRole.EDITOR to setOf(Permission.TOURNAMENT_VIEW)), user.rolePermissions)

        val catalog = assertIs<AppResult.Success<RolesCatalog>>(
            repository { """{"standard":[],"custom":[],"grantable":{"NEW_LEVEL":["tournament.view"]},"required":[]}""" }.listRoles(),
        ).value
        assertEquals(emptyMap(), catalog.grantable)
    }

    @Test
    fun aLogoGoesInBase64AndComesBackAsItsAddress() = runTest {
        val repository = repository { """{"id":"o1","name":"Club","logoUrl":"https://logo"}""" }
        val draft = OrganizationDraft("Club", OrganizationType.CLUB, "es", "#00aa00", logoContentType = "image/png", logoBytes = byteArrayOf(1, 2, 3))

        val saved = assertIs<AppResult.Success<Organization>>(repository.createOrganization(draft)).value

        assertEquals("https://logo", saved.logoUrl)
        val sent = (requests.single().body as TextContent).text
        assertTrue(""""logoDataBase64":"AQID"""" in sent && """"logoContentType":"image/png"""" in sent, sent)

        requests.clear()
        repository.updateOrganization("o1", draft.copy(logoBytes = null))
        val withoutLogo = (requests.single().body as TextContent).text
        assertTrue("logoDataBase64" !in withoutLogo && "logoContentType" !in withoutLogo, withoutLogo)
    }

    @Test
    fun theListsNeedAnAccount() = runTest {
        val signedOut = object : AuthRepository by FakeAuthRepository() {
            override suspend fun currentSession(): AuthSession? = null
        }

        assertIs<AppResult.Failure>(repository(signedOut) { "{}" }.listUsers(RefreshMode.FORCE))
        assertNull(requests.firstOrNull())
    }
}
