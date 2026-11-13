package com.etologic.mahjongtournamentsuite.data.cache

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias TableWithHands = Pair<TableState, List<TableHand>>

/**
 * Keeps every table of a tournament, with its hands, in memory. The rankings read all tables.
 *
 * - The first [all] call fetches all tables in one request. After that, [all] makes **no request**,
 *   unless the caller says that a table is outdated.
 * - The table editor keeps the store current. Its saves, loads and polling call [put].
 * - A table only moves forward. An older copy never replaces a newer one.
 * - When only a few rounds changed, the caller can reload just those rounds instead of every table.
 */
class TournamentTableStore {
    private class Entry(
        val tables: MutableMap<TableKey, TableWithHands> = mutableMapOf(),
        /** True after one load of all tables. Before that, the store only holds some tables. */
        var isComplete: Boolean = false,
    )

    // The lock guards the maps only. It is never held during a request, so a save is never held up by a ranking load.
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()
    private var ownerUid: String? = null

    private fun Entry.accept(value: TableWithHands) {
        val key = TableKey(value.first.roundId, value.first.tableId)
        val existing = tables[key]
        if (existing == null || value.first.version >= existing.first.version) tables[key] = value
    }

    /** Stores a table that the app received. It is ignored if the store holds a newer version. */
    suspend fun put(tournamentId: String, table: TableState, hands: List<TableHand>) = mutex.withLock {
        entries.getOrPut(tournamentId) { Entry() }.accept(table to hands)
    }

    suspend fun clear() = mutex.withLock {
        entries.clear()
        ownerUid = null
    }

    /** Tables of one account must never reach another account. A new account starts with an empty store. */
    private suspend fun selectOwner(uid: String) = mutex.withLock {
        if (ownerUid != uid) {
            // Tables put before the first load belong to the account that is signed in. Only a change of account clears.
            if (ownerUid != null) entries.clear()
            ownerUid = uid
        }
    }

    /**
     * Returns all tables of the tournament sorted by round and table.
     * It calls [fetchAll] only if the store has not loaded all tables yet, if [reload] is true, or if [isOutdated]
     * says so. [isOutdated] gets the version of each table that the store holds. It runs without the lock.
     *
     * Before the first load, [restore] can give the tables that were saved on the device. They are checked with
     * [verifyRestored] (or [isOutdated]) before use, also when the caller does not check, because they can be old.
     * [onFetched] gets every complete load, so the caller can save it.
     *
     * [changedRounds] replaces [isOutdated] when it is given: it gets the same versions and returns the rounds that
     * have a newer table on the server. Up to [MAX_PARTIAL_ROUNDS] of them are reloaded with [fetchRound], one request
     * each; more than that reload everything with [fetchAll]. During play only the round in progress changes, so a
     * check loads one round instead of the whole tournament.
     */
    suspend fun all(
        ownerUid: String,
        tournamentId: String,
        reload: Boolean = false,
        isOutdated: (suspend (known: Map<TableKey, Long>) -> Boolean)? = null,
        restore: (suspend () -> List<TableWithHands>?)? = null,
        verifyRestored: (suspend (known: Map<TableKey, Long>) -> Boolean)? = null,
        onFetched: (suspend (List<TableWithHands>) -> Unit)? = null,
        changedRounds: (suspend (known: Map<TableKey, Long>) -> Set<Int>)? = null,
        fetchRound: (suspend (roundId: Int) -> List<TableWithHands>)? = null,
        fetchAll: suspend () -> List<TableWithHands>,
    ): List<TableWithHands> {
        selectOwner(ownerUid)
        var known = completeVersions(tournamentId)
        var restored = false
        if (known == null && !reload && restore != null) {
            val saved = restore()
            if (saved != null) {
                mutex.withLock {
                    val entry = entries.getOrPut(tournamentId) { Entry() }
                    if (!entry.isComplete) {
                        saved.forEach { value -> entry.accept(value) }
                        entry.isComplete = true
                        restored = true
                    }
                }
                known = completeVersions(tournamentId)
            }
        }
        var partial: List<TableWithHands>? = null
        val needsLoad = when {
            reload || known == null -> true
            changedRounds != null && fetchRound != null -> {
                val rounds = changedRounds(known)
                when {
                    rounds.isEmpty() -> false
                    rounds.size > MAX_PARTIAL_ROUNDS -> true
                    else -> {
                        partial = coroutineScope { rounds.sorted().map { round -> async { fetchRound(round) } }.awaitAll() }.flatten()
                        false
                    }
                }
            }
            else -> (isOutdated ?: verifyRestored.takeIf { restored })?.invoke(known) ?: false
        }
        val loaded = if (needsLoad) fetchAll().also { onFetched?.invoke(it) } else null
        return mutex.withLock {
            val entry = entries.getOrPut(tournamentId) { Entry() }
            if (loaded != null) {
                loaded.forEach { value -> entry.accept(value) }
                entry.isComplete = true
            }
            partial?.forEach { value -> entry.accept(value) }
            entry.tables.entries
                .sortedWith(compareBy({ it.key.roundId }, { it.key.tableId }))
                .map { it.value }
        }
    }

    companion object {
        /** More changed rounds than this reload every table in one request, which is cheaper than many small ones. */
        const val MAX_PARTIAL_ROUNDS = 2
    }

    private suspend fun completeVersions(tournamentId: String): Map<TableKey, Long>? = mutex.withLock {
        entries[tournamentId]?.takeIf { it.isComplete }?.tables?.mapValues { it.value.first.version }
    }
}
