package com.etologic.mahjongtournamentsuite.data.cache

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@Serializable
private data class Answer(val names: List<String>)

/** The device: it keeps its values while the app starts again. */
private class MemoryStore : PersistentCacheStore {
    val values = mutableMapOf<String, String>()

    override suspend fun read(name: String): String? = values[name]

    override suspend fun write(name: String, value: String) {
        values[name] = value
    }

    override suspend fun clear() = values.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PersistedCacheTest {
    private val device = MemoryStore()
    private val persisted = PersistedValues(device, Json)
    private val persistence = Persistence("global:emaPlayers", Answer.serializer())

    /** A new start of the app: an empty memory cache with the same device. */
    private fun TestScope.startApp() = SessionRepositoryCache(
        logger = Logger.withTag("persisted-cache-test"),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        timeSource = TestTimeSource(),
        freshFor = 10.seconds,
        persisted = persisted,
    )

    private class Server(var revision: Long = 1) {
        var loads = 0
        var revisionChecks = 0
        fun answer() = Answer(listOf("load ${++loads}"))
    }

    private suspend fun SessionRepositoryCache.read(
        server: Server,
        refreshMode: RefreshMode = RefreshMode.IF_CHANGED,
        owner: String = "u1",
    ): String = getOrLoadPersisted(
        ownerUid = owner,
        key = "global:emaPlayers",
        refreshMode = refreshMode,
        revision = { server.revisionChecks++; server.revision },
        persistence = persistence,
        load = { server.answer() },
        map = { it.names.single() },
    )

    @Test
    fun aNewStartReusesTheSavedAnswerWhenTheRevisionIsTheSame() = runTest {
        val server = Server()
        assertEquals("load 1", startApp().read(server))

        val value = startApp().read(server)

        assertEquals("load 1", value)
        assertEquals(1, server.loads)
        assertEquals(2, server.revisionChecks)
    }

    @Test
    fun aNewStartLoadsAgainAndSavesWhenTheRevisionChanged() = runTest {
        val server = Server()
        startApp().read(server)
        server.revision = 2

        assertEquals("load 2", startApp().read(server))
        assertEquals("load 2", startApp().read(server))
        assertEquals(2, server.loads)
    }

    @Test
    fun cachedModeChecksTheRevisionOfASavedAnswerOnce() = runTest {
        val server = Server()
        startApp().read(server)
        server.revision = 2
        val cache = startApp()

        assertEquals("load 2", cache.read(server, RefreshMode.CACHED))
        val checks = server.revisionChecks
        assertEquals("load 2", cache.read(server, RefreshMode.CACHED))
        assertEquals(checks, server.revisionChecks)
    }

    @Test
    fun anotherAccountNeverGetsTheSavedAnswer() = runTest {
        val server = Server()
        startApp().read(server, owner = "u1")

        assertEquals("load 2", startApp().read(server, owner = "u2"))
    }

    @Test
    fun clearRemovesTheSavedAnswers() = runTest {
        val server = Server()
        val cache = startApp()
        cache.read(server)

        cache.clear()

        assertTrue(device.values.isEmpty())
        assertNull(persisted.read("u1", persistence.name, persistence.serializer))
    }

    @Test
    fun aSavedAnswerInAnOldFormatIsIgnored() = runTest {
        val server = Server()
        startApp().read(server)
        device.values.keys.toList().forEach { device.values[it] = "{\"revision\":1,\"data\":{\"other\":3}}" }

        assertEquals("load 2", startApp().read(server))
    }

    @Test
    fun savedTablesAreCheckedEvenWhenTheCallerDoesNotAsk() = runTest {
        val saved = listOf(table(1, 1, version = 3) to hands())
        var checks = 0
        var fullLoads = 0

        val result = TournamentTableStore().all(
            ownerUid = "u1",
            tournamentId = "t1",
            isOutdated = null,
            restore = { saved },
            verifyRestored = { known -> checks++; known[TableKey(1, 1)] != 4L },
            fetchAll = { fullLoads++; listOf(table(1, 1, version = 4) to hands()) },
        )

        assertEquals(1, checks)
        assertEquals(1, fullLoads)
        assertEquals(4L, result.single().first.version)
    }

    @Test
    fun currentSavedTablesNeedNoLoad() = runTest {
        var fullLoads = 0
        var fetched = 0

        val result = TournamentTableStore().all(
            ownerUid = "u1",
            tournamentId = "t1",
            restore = { listOf(table(1, 1, version = 3) to hands()) },
            verifyRestored = { false },
            onFetched = { fetched++ },
            fetchAll = { fullLoads++; emptyList() },
        )

        assertEquals(0, fullLoads)
        assertEquals(0, fetched)
        assertEquals(3L, result.single().first.version)
    }

    private fun table(round: Int, id: Int, version: Long) = TableState(
        version = version, roundId = round, tableId = id, playerIds = listOf(1, 2, 3, 4),
        playerEastId = "", playerSouthId = "", playerWestId = "", playerNorthId = "",
        playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
        isCompleted = false, useTotalsOnly = true,
    )

    private fun hands() = listOf(TableHand(1, "", "", "", false, false, "", "", "", ""))
}
