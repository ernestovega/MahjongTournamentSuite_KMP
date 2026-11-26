package com.etologic.mahjongtournamentsuite.data.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The cache of the repositories on iOS, one file for each value in the caches folder. */
class IosPersistentCacheStoreTest {
    private val store = createPlatformPersistentCacheStore()

    @AfterTest
    fun cleanUp() = runTest { store.clear() }

    @Test
    fun aValueComesBack() = runTest {
        store.write("tournaments", """[{"id":"madrid-open-2026","name":"Madrid Mahjong Open"}]""")
        assertEquals("""[{"id":"madrid-open-2026","name":"Madrid Mahjong Open"}]""", store.read("tournaments"))
    }

    @Test
    fun aMissingValueIsNull() = runTest {
        assertNull(store.read("nothing-here"))
    }

    @Test
    fun writingAgainKeepsTheLastValue() = runTest {
        store.write("players", "first")
        store.write("players", "second")
        assertEquals("second", store.read("players"))
    }

    @Test
    fun clearEmptiesEverything() = runTest {
        store.write("a", "1")
        store.write("b", "2")
        store.clear()
        assertNull(store.read("a"))
        assertNull(store.read("b"))
        store.write("a", "3")
        assertEquals("3", store.read("a"))
    }
}
