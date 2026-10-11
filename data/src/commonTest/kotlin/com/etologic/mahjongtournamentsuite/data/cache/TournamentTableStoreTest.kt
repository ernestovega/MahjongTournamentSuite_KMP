package com.etologic.mahjongtournamentsuite.data.cache

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun table(round: Int, id: Int, version: Long = 1, east: String = "") = TableState(
    version = version, roundId = round, tableId = id, playerIds = listOf(1, 2, 3, 4),
    playerEastId = east, playerSouthId = "", playerWestId = "", playerNorthId = "",
    playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
    playerEastPoints = "", playerSouthPoints = "", playerWestPoints = "", playerNorthPoints = "",
    isCompleted = false, useTotalsOnly = true, usePointsCalculation = true,
)

private fun hands(score: String = "") = listOf(TableHand(1, "", "", score, false, false, "", "", "", ""))

/** A tiny server. It counts the full loads. */
private class Server(vararg initial: TableState) {
    val tables = initial.associateBy { TableKey(it.roundId, it.tableId) }.toMutableMap()
    var fullLoads = 0
    var fail = false

    suspend fun TournamentTableStore.all(owner: String = "u1", reload: Boolean = false) = all(
        ownerUid = owner,
        tournamentId = "t1",
        reload = reload,
        fetchAll = {
            fullLoads++
            if (fail) error("offline")
            tables.values.map { it to hands("v${it.version}") }
        },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentTableStoreTest {
    private val store = TournamentTableStore()

    @Test
    fun firstLoadFetchesEverythingOnceAndSortsIt() = runTest {
        val server = Server(table(2, 1), table(1, 2), table(1, 1))

        val result = with(server) { store.all() }

        assertEquals(listOf(TableKey(1, 1), TableKey(1, 2), TableKey(2, 1)), result.map { TableKey(it.first.roundId, it.first.tableId) })
        assertEquals(1, server.fullLoads)
    }

    @Test
    fun laterLoadsNeverAskTheServer() = runTest {
        val server = Server(table(1, 1))
        with(server) { store.all() }
        server.tables[TableKey(1, 1)] = table(1, 1, version = 9)

        repeat(5) { with(server) { store.all() } }

        assertEquals(1, server.fullLoads)
        assertEquals(1L, with(server) { store.all() }.single().first.version, "the store keeps what it holds")
    }

    @Test
    fun aReloadFetchesEverythingAgain() = runTest {
        val server = Server(table(1, 1))
        with(server) { store.all() }
        server.tables[TableKey(1, 1)] = table(1, 1, version = 2)

        val result = with(server) { store.all(reload = true) }

        assertEquals(2, server.fullLoads)
        assertEquals(2L, result.single().first.version)
    }

    @Test
    fun tablesTheEditorPutAreVisibleWithoutAnyRequest() = runTest {
        val server = Server(table(1, 1), table(1, 2))
        with(server) { store.all() }

        store.put("t1", table(1, 1, version = 2, east = "9"), hands("mine"))
        val result = with(server) { store.all() }

        assertEquals("9", result.first().first.playerEastId)
        assertEquals(1, server.fullLoads)
    }

    @Test
    fun anOlderCopyNeverReplacesANewerOne() = runTest {
        val server = Server(table(1, 1, version = 5))
        with(server) { store.all() }

        store.put("t1", table(1, 1, version = 4, east = "old"), hands())
        val result = with(server) { store.all() }

        assertEquals(5L, result.single().first.version)
        assertEquals("", result.single().first.playerEastId)
    }

    @Test
    fun aFullLoadThatReturnsOlderDataThanTheStoreKeepsTheNewerTable() = runTest {
        val server = Server(table(1, 1, version = 1))
        store.put("t1", table(1, 1, version = 3, east = "mine"), hands())

        val result = with(server) { store.all() }

        assertEquals(3L, result.single().first.version)
        assertEquals("mine", result.single().first.playerEastId)
    }

    @Test
    fun tablesPutBeforeTheFirstFullLoadDoNotCountAsComplete() = runTest {
        val server = Server(table(1, 1), table(1, 2))
        store.put("t1", table(1, 1), hands())

        val result = with(server) { store.all() }

        assertEquals(1, server.fullLoads)
        assertEquals(2, result.size)
    }

    @Test
    fun anotherAccountNeverSeesTheTablesOfTheFirst() = runTest {
        val server = Server(table(1, 1, east = "secret"))
        with(server) { store.all(owner = "u1") }

        with(server) { store.all(owner = "u2") }

        assertEquals(2, server.fullLoads, "the second account loads for itself")
    }

    @Test
    fun aFailedFullLoadCanBeTriedAgain() = runTest {
        val server = Server(table(1, 1))
        server.fail = true
        assertFailsWith<IllegalStateException> { with(server) { store.all() } }

        server.fail = false
        val result = with(server) { store.all() }

        assertEquals(1, result.size)
        assertEquals(2, server.fullLoads)
    }

    @Test
    fun aSaveIsNeverHeldUpByASlowLoad() = runTest {
        val gate = CompletableDeferred<Unit>()
        val load = async {
            store.all(ownerUid = "u1", tournamentId = "t1", fetchAll = { gate.await(); listOf(table(1, 1) to hands()) })
        }
        runCurrent()

        store.put("t1", table(1, 1, version = 2, east = "saved"), hands())

        gate.complete(Unit)
        assertEquals("saved", load.await().single().first.playerEastId)
    }

    @Test
    fun clearEmptiesTheStore() = runTest {
        val server = Server(table(1, 1))
        with(server) { store.all() }

        store.clear()
        with(server) { store.all() }

        assertEquals(2, server.fullLoads)
    }
}
