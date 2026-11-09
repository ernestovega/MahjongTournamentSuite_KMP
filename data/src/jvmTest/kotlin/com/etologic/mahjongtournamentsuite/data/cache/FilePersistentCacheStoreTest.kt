package com.etologic.mahjongtournamentsuite.data.cache

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The cache of the desktop app: one file for each value under the home folder of the user. */
class FilePersistentCacheStoreTest {
    private lateinit var home: File
    private var originalHome: String? = null

    private val cacheFolder get() = File(File(home, ".mahjongtournamentsuite"), "cache")

    @BeforeTest
    fun useATemporaryHome() {
        home = Files.createTempDirectory("mts-cache-test").toFile()
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreTheHome() {
        originalHome?.let { System.setProperty("user.home", it) } ?: System.clearProperty("user.home")
        home.deleteRecursively()
    }

    @Test
    fun aValueIsReadBackAfterANewStart() = runTest {
        createPlatformPersistentCacheStore().write("v1.uid_2f_tournaments", "{\"revision\":3}")

        assertEquals("{\"revision\":3}", createPlatformPersistentCacheStore().read("v1.uid_2f_tournaments"))
        assertTrue(File(cacheFolder, "v1.uid_2f_tournaments").isFile)
    }

    @Test
    fun aMissingValueIsNull() = runTest {
        assertNull(createPlatformPersistentCacheStore().read("v1.nothing"))
    }

    @Test
    fun aNewValueReplacesTheOldOneAndLeavesNoPartialFile() = runTest {
        val store = createPlatformPersistentCacheStore()
        store.write("v1.players", "old")
        store.write("v1.players", "new")

        assertEquals("new", store.read("v1.players"))
        assertEquals(listOf("v1.players"), cacheFolder.list()?.toList())
    }

    @Test
    fun clearRemovesEveryValue() = runTest {
        val store = createPlatformPersistentCacheStore()
        store.write("v1.a", "1")
        store.write("v1.b", "2")

        store.clear()

        assertNull(store.read("v1.a"))
        assertNull(store.read("v1.b"))
        assertFalse(cacheFolder.exists())
        // The store still works after a clear, as after a sign-out followed by a new sign-in.
        store.write("v1.a", "3")
        assertEquals("3", store.read("v1.a"))
    }

    @Test
    fun aFolderThatCannotBeWrittenNeverStopsTheApp() = runTest {
        // A file where the cache folder should be makes every write fail.
        File(home, ".mahjongtournamentsuite").writeText("not a folder")
        val store = createPlatformPersistentCacheStore()

        store.write("v1.a", "1")

        assertNull(store.read("v1.a"))
        store.clear()
    }
}
