package com.etologic.mahjongtournamentsuite.data.cache

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
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
     */
    suspend fun all(
        ownerUid: String,
        tournamentId: String,
        reload: Boolean = false,
        isOutdated: (suspend (known: Map<TableKey, Long>) -> Boolean)? = null,
        fetchAll: suspend () -> List<TableWithHands>,
    ): List<TableWithHands> {
        selectOwner(ownerUid)
        val known = mutex.withLock {
            entries[tournamentId]?.takeIf { it.isComplete }?.tables?.mapValues { it.value.first.version }
        }
        val needsLoad = reload || known == null || (isOutdated?.invoke(known) ?: false)
        val loaded = if (needsLoad) fetchAll() else null
        return mutex.withLock {
            val entry = entries.getOrPut(tournamentId) { Entry() }
            if (loaded != null) {
                loaded.forEach { value -> entry.accept(value) }
                entry.isComplete = true
            }
            entry.tables.entries
                .sortedWith(compareBy({ it.key.roundId }, { it.key.tableId }))
                .map { it.value }
        }
    }
}
