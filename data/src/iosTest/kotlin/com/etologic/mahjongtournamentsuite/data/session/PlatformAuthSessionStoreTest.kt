package com.etologic.mahjongtournamentsuite.data.session

import com.etologic.mahjongtournamentsuite.data.platform.IosFiles
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The session of the iOS app, in a file of Application Support that the suite reads before it starts. */
class PlatformAuthSessionStoreTest {
    private val store = PlatformAuthSessionStore(Json { ignoreUnknownKeys = true })
    private val file = IosFiles.child(IosFiles.supportDirectory(), "auth_session.json")
    private val session = StoredAuthSession(uid = "uid-1", idToken = "id-token", refreshToken = "refresh-token")

    @BeforeTest
    @AfterTest
    fun noSession() = IosFiles.delete(file)

    @Test
    fun withoutAFileThereIsNoSession() = runTest {
        assertNull(store.load())
        assertNull(storedSessionUid())
    }

    @Test
    fun aSavedSessionLoadsAgainAndGivesItsAccount() = runTest {
        store.save(session)
        assertEquals(session, store.load())
        assertEquals("uid-1", storedSessionUid())
    }

    @Test
    fun aNewStoreReadsTheSessionOfTheLastOne() = runTest {
        store.save(session)
        assertEquals(session, PlatformAuthSessionStore(Json).load())
    }

    @Test
    fun savingNullSignsOut() = runTest {
        store.save(session)
        store.save(null)
        assertNull(store.load())
        assertNull(storedSessionUid())
    }

    @Test
    fun aBrokenOrBlankFileIsNoSession() = runTest {
        IosFiles.writeText(file, "{not json")
        assertNull(store.load())
        assertNull(storedSessionUid())
        IosFiles.writeText(file, "  ")
        assertNull(store.load())
        assertNull(storedSessionUid())
    }
}
