package com.etologic.mahjongtournamentsuite.presentation.sync

import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableChangeDetectorTest {
    private fun manifest(vararg versions: Pair<TableKey, Long>) =
        TournamentManifest(tablesRevision = 1, tableVersions = versions.toMap())

    private val a = TableKey(1, 1)
    private val b = TableKey(1, 2)
    private val c = TableKey(2, 1)

    @Test
    fun higherServerVersionIsAChange() {
        assertEquals(setOf(a), TableChangeDetector.changedTables(mapOf(a to 3L), manifest(a to 4L)))
    }

    @Test
    fun sameVersionIsNotAChange() {
        assertTrue(TableChangeDetector.changedTables(mapOf(a to 3L), manifest(a to 3L)).isEmpty())
    }

    @Test
    fun lowerServerVersionIsNotAChange() {
        // The manifest was read before this client's own save committed. It must not look like a change.
        assertTrue(TableChangeDetector.changedTables(mapOf(a to 5L), manifest(a to 4L)).isEmpty())
    }

    @Test
    fun tableWithoutServerVersionIsNotAChange() {
        assertTrue(TableChangeDetector.changedTables(mapOf(a to 3L), manifest()).isEmpty())
    }

    @Test
    fun tableTheClientDoesNotKnowIsIgnored() {
        assertTrue(TableChangeDetector.changedTables(mapOf(a to 3L), manifest(b to 9L)).isEmpty())
    }

    @Test
    fun tableNeverSavedBeforeCanBecomeChanged() {
        assertEquals(setOf(a), TableChangeDetector.changedTables(mapOf(a to 0L), manifest(a to 1L)))
    }

    @Test
    fun onlyTheChangedTablesAreReturned() {
        val changed = TableChangeDetector.changedTables(
            known = mapOf(a to 1L, b to 2L, c to 3L),
            manifest = manifest(a to 1L, b to 5L, c to 4L),
        )

        assertEquals(setOf(b, c), changed)
    }

    @Test
    fun nothingKnownMeansNothingChanged() {
        assertTrue(TableChangeDetector.changedTables(emptyMap(), manifest(a to 1L)).isEmpty())
    }
}
