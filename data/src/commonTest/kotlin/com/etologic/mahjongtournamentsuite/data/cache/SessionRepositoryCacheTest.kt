package com.etologic.mahjongtournamentsuite.data.cache

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryCacheTest {
    @Test
    fun cachedValueReturnsBeforeBackgroundRefreshCompletes() = runTest {
        val cache = SessionRepositoryCache(Logger.withTag("cache-test"), UnconfinedTestDispatcher(testScheduler))
        var revision = 1L
        var loads = 0
        val first = cache.getOrLoad("one", "players", RefreshMode.IF_CHANGED, { revision }) {
            loads++
            "first"
        }
        revision = 2L

        val cached = cache.getOrLoad("one", "players", RefreshMode.IF_CHANGED, { revision }) {
            loads++
            "second"
        }

        assertEquals("first", first)
        assertEquals("first", cached)
        advanceUntilIdle()
        assertEquals(2, loads)
        assertEquals(
            "second",
            cache.getOrLoad<String>("one", "players", RefreshMode.IF_CHANGED, { revision }) { error("not needed") },
        )
    }

    @Test
    fun forceRefreshWaitsForRemoteValue() = runTest {
        val cache = SessionRepositoryCache(Logger.withTag("cache-test"), UnconfinedTestDispatcher(testScheduler))
        cache.getOrLoad("one", "users", RefreshMode.IF_CHANGED, { 1 }) { "old" }

        val result = cache.getOrLoad("one", "users", RefreshMode.FORCE, { 1 }) { "new" }

        assertEquals("new", result)
    }

    @Test
    fun accountChangeClearsCachedValues() = runTest {
        val cache = SessionRepositoryCache(Logger.withTag("cache-test"), UnconfinedTestDispatcher(testScheduler))
        cache.getOrLoad("one", "profile", RefreshMode.IF_CHANGED, { 1 }) { "first account" }

        val result = cache.getOrLoad("two", "profile", RefreshMode.IF_CHANGED, { 1 }) { "second account" }

        assertEquals("second account", result)
    }
}
