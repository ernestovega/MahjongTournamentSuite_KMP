package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.MyTournamentSlot
import com.etologic.mahjongtournamentsuite.domain.model.MyTournaments
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The reads and changes of the tournament repository when the server refuses them, or when nobody is signed in. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentRepositoryFailuresTest {
    private val requests = mutableListOf<String>()
    private var refuse = false
    private var refuseManifest = false
    private var manifest = """{"resources":{"tournaments":{"revision":3}},"globalResources":{},"tableVersions":{}}"""

    private val signedOut = object : AuthRepository by FakeAuthRepository() {
        override suspend fun currentSession(): AuthSession? = null
    }

    private fun TestScope.repository(auth: AuthRepository = FakeAuthRepository(), publicReads: Boolean = false) = DefaultTournamentRepository(
        backendApi = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            requests += "${request.method.value} $path"
            when {
                path.endsWith("/sync/manifest") && refuseManifest -> respond(ByteReadChannel("{}"), HttpStatusCode.ServiceUnavailable)
                path.endsWith("/sync/manifest") -> jsonResponse(manifest)
                refuse -> respond(ByteReadChannel("""{"error":"refused"}"""), HttpStatusCode.InternalServerError)
                path == "/countries" -> jsonResponse("""{"countries":[{"code":"ES","name":"Spain"}]}""")
                path == "/me" -> jsonResponse("""{"emaId":"1","tournaments":[{"tournamentId":"t1","playerId":3,"teamId":0}]}""")
                path.endsWith("/tournaments") -> jsonResponse("""{"tournaments":[]}""")
                else -> jsonResponse("""{"ok":true}""")
            }
        },
        authRepository = auth,
        logger = Logger.withTag("DefaultTournamentRepositoryFailuresTest"),
        cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = TestTimeSource(),
            freshFor = 10.seconds,
        ),
        json = Json { ignoreUnknownKeys = true },
        publicReads = publicReads,
    )

    @Test
    fun theCountriesComeWithTheirNames() = runTest {
        assertEquals(AppResult.Success(listOf(Country("ES", "Spain"))), repository().listCountries(RefreshMode.FORCE))
    }

    @Test
    fun aRefusedReadGivesAFailure() = runTest {
        val repository = repository()
        refuse = true

        assertIs<AppResult.Failure>(repository.listCountries(RefreshMode.FORCE))
        assertIs<AppResult.Failure>(repository.listTournamentPlayers("t1", RefreshMode.FORCE))
        assertIs<AppResult.Failure>(repository.getAllTablesWithHands("t1", RefreshMode.FORCE))
        assertIs<AppResult.Failure>(repository.getRoundClocks("t1"))
        assertIs<AppResult.Failure>(repository.generateTableQrCodes("t1", perSheet = 6))
        assertIs<AppResult.Failure>(repository.generateScoreSheets("t1", perTable = true, emaLogo = true))
    }

    @Test
    fun aRefusedClockOrControlWithoutItsReasonIsAnUnexpectedError() = runTest {
        val repository = repository()
        refuse = true

        assertIs<AppError.Unexpected>(assertIs<AppResult.Failure>(repository.updateRoundClock("t1", 1, RoundClockAction.Stop)).error)
        assertIs<AppError.Unexpected>(assertIs<AppResult.Failure>(repository.updateRoundControl("t1", 1, RoundControlChange())).error)
    }

    @Test
    fun aFailedPrefetchIsOnlyLogged() = runTest {
        refuseManifest = true

        repository().prefetchRevisions("t1")

        assertEquals(listOf("GET /tournaments/t1/sync/manifest"), requests.filter { "manifest" in it }.take(1))
    }

    @Test
    fun myTournamentsNeedAnAccount() = runTest {
        assertEquals(AppResult.Success(MyTournaments()), repository(auth = signedOut).listMyTournaments())
        assertEquals(AppResult.Success(emptyList()), repository(auth = signedOut).listMyTournamentSlots())
        assertEquals(listOf(MyTournamentSlot("t1", playerId = 3, teamId = 0)), assertIs<AppResult.Success<List<MyTournamentSlot>>>(repository().listMyTournamentSlots()).value)

        refuse = true
        assertIs<AppResult.Failure>(repository().listMyTournamentSlots())
    }

    @Test
    fun aReadOfTheAdminAppNeedsAnAccount() = runTest {
        assertIs<AppResult.Failure>(repository(auth = signedOut).listTournamentPlayers("t1", RefreshMode.FORCE))
    }

    @Test
    fun thePlayerAppReadsTheTournamentsWithoutAnAccount() = runTest {
        assertIs<AppResult.Success<*>>(repository(auth = signedOut, publicReads = true).listTournaments(RefreshMode.FORCE))
        assertTrue(requests.any { it.startsWith("GET /public/") }, requests.toString())
    }

    @Test
    fun thePublicListLoadsWithoutARevisionWhenTheManifestHasNone() = runTest {
        manifest = """{"resources":{},"globalResources":{},"tableVersions":{}}"""

        assertIs<AppResult.Success<*>>(repository(auth = signedOut, publicReads = true).listTournaments(RefreshMode.FORCE))
    }

    @Test
    fun theGeneratedFilesAndTheListOfTheAdminAppNeedAnAccount() = runTest {
        assertIs<AppResult.Failure>(repository(auth = signedOut).generateTableQrCodes("t1", perSheet = 6))
        assertIs<AppResult.Failure>(repository(auth = signedOut).generateScoreSheets("t1", perTable = false, emaLogo = false))
        assertIs<AppResult.Failure>(repository(auth = signedOut).listTournaments(RefreshMode.FORCE))
    }
}
