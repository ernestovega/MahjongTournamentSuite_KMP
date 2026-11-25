package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The public tournament list goes through the CDN of Firebase Hosting, which keeps it up to a minute, while the
 * manifest of revisions is newer. The game day of 25-nov saw it: a tournament created a moment before never showed in
 * the player list, because the cache saved the old list with the new revision and then never asked again. The list
 * now asks for its revision in the address, so the CDN cannot answer an older list for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PublicTournamentListCdnTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private var revision = 1L
    private val names = mutableListOf("Old open")

    /** What the CDN still has for the address without a revision: the list of before. */
    private var cdnCopy = listJson()
    private val listAddresses = mutableListOf<String>()
    private val clock = TestTimeSource()

    private fun listJson() = names.joinToString(",", prefix = """{"tournaments":[""", postfix = "]}") { name ->
        """{"id":"${name.lowercase().replace(' ', '-')}","name":"$name","isTeams":false,"numPlayers":4,"numRounds":1,"isPublic":true}"""
    }

    private fun TestScope.publicRepository(): DefaultTournamentRepository {
        val cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = clock,
            freshFor = 10.seconds,
        )
        val backend = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            when (path) {
                "/public/sync/manifest" -> jsonResponse("""{"resources":{"tournaments":{"revision":$revision}}}""")
                "/public/tournaments" -> {
                    val asked = request.url.parameters["revision"]
                    listAddresses += asked ?: "none"
                    // A new address is not in the CDN: the function answers the current list.
                    jsonResponse(if (asked == revision.toString()) listJson() else cdnCopy)
                }
                else -> respond(ByteReadChannel("unexpected $path"), HttpStatusCode.NotFound)
            }
        }
        return DefaultTournamentRepository(
            backendApi = backend,
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("tournaments"),
            cache = cache,
            json = json,
            tableStore = TournamentTableStore(),
            publicReads = true,
        )
    }

    private fun <T> AppResult<T>.value(): T = assertIs<AppResult.Success<T>>(this).value

    @Test
    fun aTournamentCreatedAMomentAgoShowsAlthoughTheCdnHasTheOldList() = runTest {
        // Someone creates a tournament: the revision goes up, and the CDN still keeps the list of before.
        names += "New open"
        revision = 2

        val list = publicRepository().listTournaments(RefreshMode.FORCE).value()

        assertEquals(setOf("Old open", "New open"), list.map { it.name }.toSet())
        assertEquals(listOf("2"), listAddresses)
    }

    @Test
    fun theNextCheckSeesALaterTournament() = runTest {
        val repository = publicRepository()
        repository.listTournaments(RefreshMode.FORCE).value()
        names += "Later open"
        revision = 2
        clock += 11.seconds

        val list = repository.listTournaments(RefreshMode.IF_CHANGED).value()

        assertEquals(setOf("Old open", "Later open"), list.map { it.name }.toSet())
        assertEquals(listOf("1", "2"), listAddresses)
    }
}
