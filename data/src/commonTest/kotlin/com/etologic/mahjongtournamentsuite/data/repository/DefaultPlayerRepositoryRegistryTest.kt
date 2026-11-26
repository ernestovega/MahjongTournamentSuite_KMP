package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerTournament
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerTournaments
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The registry of EMA players: the list (with the account in the admin app, without one in the player app) and every
 * change to a player. A change reads the list again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPlayerRepositoryRegistryTest {
    private val requests = mutableListOf<String>()
    private var refuse = false

    private fun TestScope.repository(publicReads: Boolean = false) = DefaultPlayerRepository(
        backendApi = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            val auth = request.headers[HttpHeaders.Authorization]?.let { " [$it]" }.orEmpty()
            requests += "${request.method.value} $path$auth $body".trim()
            when {
                path.endsWith("/sync/manifest") -> jsonResponse("""{"resources":{"emaPlayers":{"revision":4}},"tableVersions":{}}""")
                refuse -> respond(ByteReadChannel("""{"error":"refused"}"""), HttpStatusCode.Forbidden)
                path.endsWith("/ema-player-registry") || path == "/public/players" -> jsonResponse(
                    """{"players":[{"emaId":"10000001","firstName":"Ana","lastName":"Gil","country":"ES"}]}""",
                )
                path.endsWith("/delete/preview") -> jsonResponse(
                    """{"players":[{"emaId":"10000001","tournaments":[{"id":"t1","name":"Madrid Open"}]},{"emaId":"10000002"}]}""",
                )
                path.endsWith("/delete") -> jsonResponse(
                    """{"results":[{"emaId":"10000001","outcome":"DISABLED"},{"emaId":"10000002","outcome":"NEW"},""" +
                        """{"emaId":"10000003","outcome":"FAILED","reason":"NOT_FOUND"},{"emaId":"10000004","outcome":"FAILED","reason":"NEW"}]}""",
                )
                path.endsWith("/photo") -> jsonResponse("""{"emaId":"10000001","firstName":"Ana","lastName":"Gil","photoUrl":"https://p"}""")
                else -> jsonResponse("""{"ok":true}""")
            }
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultPlayerRepositoryRegistryTest"),
        cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = TestTimeSource(),
            freshFor = 10.seconds,
        ),
        publicReads = publicReads,
    )

    private fun count(prefix: String) = requests.count { it.startsWith(prefix) }

    @Test
    fun theListComesOnceAndAgainAfterAChange() = runTest {
        val repository = repository()

        val players = assertIs<AppResult.Success<List<Player>>>(repository.listPlayers(RefreshMode.IF_CHANGED)).value
        assertEquals("Ana", players.single().firstName)
        repository.listPlayers(RefreshMode.IF_CHANGED)
        assertEquals(1, count("GET /ema-player-registry"))

        assertIs<AppResult.Success<Unit>>(repository.updatePlayer("10000001", Player("10000009", "Ana", "Gil B", "ES", hiddenFromPublic = true)))
        val update = requests.single { it.startsWith("PUT /ema-player-registry/10000001") }
        assertTrue(""""emaId":"10000009"""" in update && """"hiddenFromPublic":true""" in update, update)
        repository.listPlayers(RefreshMode.IF_CHANGED)
        assertEquals(2, count("GET /ema-player-registry"))
    }

    @Test
    fun thePlayerAppReadsTheListWithoutAnAccount() = runTest {
        val repository = repository(publicReads = true)

        assertIs<AppResult.Success<List<Player>>>(repository.listPlayers(RefreshMode.FORCE))

        assertTrue(requests.none { "Bearer id-token" in it }, requests.toString())
        assertEquals(1, count("GET /public/players"))
    }

    @Test
    fun aPhotoGoesInBase64() = runTest {
        val player = assertIs<AppResult.Success<Player>>(repository().updatePlayerPhoto("10000001", "image/jpeg", byteArrayOf(1, 2, 3))).value

        assertEquals("https://p", player.photoUrl)
        assertTrue(requests.single { it.startsWith("PUT /ema-player-registry/10000001/photo") }.contains(""""dataBase64":"AQID""""))
    }

    @Test
    fun deletingPlayersGivesTheOutcomeOfEachOne() = runTest {
        val results = assertIs<AppResult.Success<List<EmaPlayerDeleteResult>>>(
            repository().deleteEmaPlayers(listOf("10000001", "10000002", "10000003", "10000004")),
        ).value

        assertEquals(
            listOf(
                EmaPlayerDeleteResult("10000001", EmaPlayerDeleteOutcome.DISABLED, null),
                EmaPlayerDeleteResult("10000002", EmaPlayerDeleteOutcome.FAILED, null),
                EmaPlayerDeleteResult("10000003", EmaPlayerDeleteOutcome.FAILED, EmaPlayerDeleteFailure.NOT_FOUND),
                EmaPlayerDeleteResult("10000004", EmaPlayerDeleteOutcome.FAILED, EmaPlayerDeleteFailure.UNKNOWN),
            ),
            results,
            "what this app does not know reads as a failure it cannot explain",
        )
    }

    @Test
    fun beforeDeletingTheTournamentsOfEachPlayerAreShown() = runTest {
        val shown = assertIs<AppResult.Success<List<EmaPlayerTournaments>>>(repository().tournamentsOfEmaPlayers(listOf("10000001", "10000002"))).value

        assertEquals(
            listOf(
                EmaPlayerTournaments("10000001", listOf(EmaPlayerTournament("t1", "Madrid Open"))),
                EmaPlayerTournaments("10000002", emptyList()),
            ),
            shown,
        )
    }

    @Test
    fun aRefusedCallGivesAFailure() = runTest {
        val repository = repository()
        refuse = true

        assertIs<AppResult.Failure>(repository.listPlayers(RefreshMode.FORCE))
        assertIs<AppResult.Failure>(repository.updatePlayer("10000001", Player("10000001", "Ana", "Gil")))
        assertIs<AppResult.Failure>(repository.updatePlayerPhoto("10000001", "image/jpeg", byteArrayOf()))
        assertIs<AppResult.Failure>(repository.deleteEmaPlayers(listOf("10000001")))
        assertIs<AppResult.Failure>(repository.tournamentsOfEmaPlayers(listOf("10000001")))
    }
}
