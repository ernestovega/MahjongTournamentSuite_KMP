package com.etologic.mahjongtournamentsuite.data.cache

import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A cache that only loads and shares: the calls it does not have just fetch or load, and closing it does nothing. */
class RepositoryCacheDefaultsTest {
    private val calls = mutableListOf<String>()

    private val cache = object : RepositoryCache {
        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T = load().also { calls += "load $key" }

        override suspend fun <T : Any> shared(key: String, maxAge: Duration, fetch: suspend () -> T): T = fetch().also { calls += "shared $key" }

        override suspend fun markStale(vararg keyPrefixes: String) = Unit

        override suspend fun clear() = Unit
    }

    @Test
    fun seedingSharesOnlyTheAnswerAndPersistingOnlyLoads() = runTest {
        val seeded = cache.sharedAndSeed("manifest", 5.seconds, alsoShares = { mapOf("global" to it) }) { "answer" }
        val persisted = cache.getOrLoadPersisted(
            ownerUid = "user-1",
            key = "players",
            refreshMode = RefreshMode.IF_CHANGED,
            revision = { 1 },
            persistence = Persistence("players", String.serializer()),
            load = { "saved" },
            map = { it.length },
        )
        cache.close()

        assertEquals("answer", seeded)
        assertEquals(5, persisted)
        assertEquals(listOf("shared manifest", "load players"), calls)
    }
}
