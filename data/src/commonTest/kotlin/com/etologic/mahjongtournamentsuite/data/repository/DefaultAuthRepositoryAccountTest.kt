package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.CredentialStore
import com.etologic.mahjongtournamentsuite.data.session.SavedCredentials
import com.etologic.mahjongtournamentsuite.data.session.StoredAuthSession
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The own account: sign in, the password reset, the profile, the alias and the photo, and deleting the account. Without
 * a session each of them fails instead of calling the server.
 */
class DefaultAuthRepositoryAccountTest {
    private class MemoryStore(var stored: StoredAuthSession?) : AuthSessionStore {
        override suspend fun load(): StoredAuthSession? = stored
        override suspend fun save(session: StoredAuthSession?) {
            stored = session
        }
    }

    private class MemoryCredentials : CredentialStore {
        var saved: SavedCredentials? = null
        override suspend fun load(): SavedCredentials? = saved
        override suspend fun save(credentials: SavedCredentials) {
            saved = credentials
        }
        override suspend fun clear() {
            saved = null
        }
    }

    private val requests = mutableListOf<String>()
    private var refuse = false
    private var manifest = """{"resources":{"users":{"revision":3}},"tableVersions":{}}"""
    private val credentials = MemoryCredentials()

    private fun repository(stored: StoredAuthSession? = StoredAuthSession("u1", "id-1", "refresh-1")) = MemoryStore(stored).let { store ->
        store to DefaultAuthRepository(
            backendApi = mockBackendApi { request ->
                val path = request.url.encodedPath.removePrefix("/functions")
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                requests += "${request.method.value} $path $body".trim()
                when {
                    path.endsWith("/sync/manifest") -> jsonResponse(manifest)
                    refuse -> respond(ByteReadChannel("""{"error":"refused"}"""), HttpStatusCode.BadRequest)
                    path == "/auth/signIn" -> jsonResponse("""{"idToken":"id-2","refreshToken":"refresh-2","uid":"u2"}""")
                    path == "/auth/passwordReset" || (path == "/auth/me" && request.method.value == "DELETE") -> jsonResponse("""{"ok":true}""")
                    path.startsWith("/auth/me") -> jsonResponse(PROFILE)
                    else -> respond(ByteReadChannel("unexpected"), HttpStatusCode.NotFound)
                }
            },
            sessionStore = store,
            logger = Logger.withTag("DefaultAuthRepositoryAccountTest"),
            credentialStore = credentials,
            cache = SessionRepositoryCache(logger = Logger.withTag("cache")),
        )
    }

    @Test
    fun signingInKeepsTheSessionAndTheCredentials() = runTest {
        val (store, auth) = repository(stored = null)
        assertNull(auth.currentSession())

        val session = assertIs<AppResult.Success<AuthSession>>(auth.signIn("a@b.c", "secret")).value

        assertEquals(AuthSession(uid = "u2", idToken = "id-2", refreshToken = "refresh-2"), session)
        assertEquals(session, auth.currentSession())
        assertEquals("id-2", store.stored?.idToken)
        assertEquals(SavedCredentials("a@b.c", "secret"), auth.savedCredentials())
        auth.clearSavedCredentials()
        assertNull(auth.savedCredentials())
    }

    @Test
    fun aRefusedSignInKeepsNoSession() = runTest {
        val (store, auth) = repository(stored = null)
        refuse = true

        assertIs<AppResult.Failure>(auth.signIn("a@b.c", "wrong"))
        assertNull(auth.currentSession())
        assertNull(store.stored)
        assertNull(credentials.saved)
    }

    @Test
    fun thePasswordResetSendsTheEmail() = runTest {
        val (_, auth) = repository(stored = null)

        assertIs<AppResult.Success<Unit>>(auth.requestPasswordReset("a@b.c"))
        assertEquals(listOf("""POST /auth/passwordReset {"email":"a@b.c"}"""), requests)

        refuse = true
        assertIs<AppResult.Failure>(auth.requestPasswordReset("a@b.c"))
    }

    @Test
    fun theProfileLeavesBlankFieldsEmpty() = runTest {
        val (_, auth) = repository()

        val me = assertIs<AppResult.Success<UserProfile>>(auth.getMe(RefreshMode.FORCE)).value

        assertEquals("Ana", me.alias)
        assertNull(me.emaId, "a blank EMA id is none")
        assertNull(me.photoUrl, "a blank photo is none")
        assertEquals(setOf(GlobalUserRole.EDITOR), me.roles)
        assertTrue(requests.any { it.startsWith("GET /auth/me") })
    }

    @Test
    fun aServerWithoutTheRevisionOfTheAccountsStillGivesTheProfile() = runTest {
        manifest = """{"resources":{}}"""
        val (_, auth) = repository()

        assertEquals("Ana", assertIs<AppResult.Success<UserProfile>>(auth.getMe(RefreshMode.IF_CHANGED)).value.alias)
    }

    @Test
    fun theAliasAndThePhotoChangeTheProfile() = runTest {
        val (_, auth) = repository()

        assertIs<AppResult.Success<UserProfile>>(auth.updateAlias("Ana B"))
        assertIs<AppResult.Success<UserProfile>>(auth.updatePhoto("image/png", byteArrayOf(1, 2, 3)))
        assertIs<AppResult.Success<UserProfile>>(auth.removePhoto())

        assertEquals(
            listOf(
                """PUT /auth/me {"alias":"Ana B"}""",
                """PUT /auth/me/photo {"contentType":"image/png","dataBase64":"AQID"}""",
                "DELETE /auth/me/photo",
            ),
            requests,
        )
    }

    @Test
    fun deletingTheAccountSignsOutAndForgetsTheCredentials() = runTest {
        val (store, auth) = repository()
        credentials.saved = SavedCredentials("a@b.c", "secret")

        assertIs<AppResult.Success<Unit>>(auth.deleteAccount())

        assertEquals(listOf("DELETE /auth/me"), requests)
        assertNull(auth.currentSession())
        assertNull(store.stored)
        assertNull(credentials.saved)
    }

    @Test
    fun withoutASessionTheAccountCallsFailWithoutCallingTheServer() = runTest {
        val (_, auth) = repository(stored = null)

        assertIs<AppResult.Failure>(auth.getMe(RefreshMode.FORCE))
        assertIs<AppResult.Failure>(auth.updateAlias("Ana"))
        assertIs<AppResult.Failure>(auth.updatePhoto("image/png", byteArrayOf()))
        assertIs<AppResult.Failure>(auth.removePhoto())
        assertIs<AppResult.Failure>(auth.deleteAccount())
        assertEquals(emptyList(), requests)
    }

    @Test
    fun aRefusedAccountCallGivesAFailure() = runTest {
        val (store, auth) = repository()
        refuse = true

        assertIs<AppResult.Failure>(auth.getMe(RefreshMode.FORCE))
        assertIs<AppResult.Failure>(auth.updateAlias("Ana"))
        assertIs<AppResult.Failure>(auth.removePhoto())
        assertIs<AppResult.Failure>(auth.deleteAccount())
        assertEquals("id-1", store.stored?.idToken, "a refused delete keeps the session")
    }

    private companion object {
        const val PROFILE = """{"uid":"u1","email":"a@b.c","alias":"Ana","emaId":" ","photoUrl":"","roles":["EDITOR"]}"""
    }
}
