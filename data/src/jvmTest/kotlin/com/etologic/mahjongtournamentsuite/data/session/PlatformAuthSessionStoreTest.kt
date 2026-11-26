package com.etologic.mahjongtournamentsuite.data.session

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop app keeps the session in a file of the home folder that only the owner can read. */
class PlatformAuthSessionStoreTest {
    private val home: File = Files.createTempDirectory("home").toFile()
    private var realHome: String? = null
    private val file get() = File(home, ".mahjongtournamentsuite/auth_session.json")

    @BeforeTest
    fun useATemporaryHome() {
        realHome = System.getProperty("user.home")
        System.setProperty("user.home", home.path)
    }

    @AfterTest
    fun restoreTheHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    @Test
    fun savesReadsAndDeletesTheSession() = runTest {
        val store = PlatformAuthSessionStore(Json)
        assertNull(store.load())
        assertNull(storedSessionUid())

        store.save(StoredAuthSession("u1", "id-1", "refresh-1"))

        assertEquals(StoredAuthSession("u1", "id-1", "refresh-1"), store.load())
        assertEquals("u1", storedSessionUid())
        assertTrue(file.canRead())

        store.save(null)
        assertFalse(file.exists())
        assertNull(store.load())
    }

    @Test
    fun aBlankOrBrokenFileIsNoSession() = runTest {
        val store = PlatformAuthSessionStore(Json)

        file.writeText("  ")
        assertNull(store.load())
        assertNull(storedSessionUid())

        file.writeText("{broken")
        assertNull(store.load())
        assertNull(storedSessionUid())
    }
}
