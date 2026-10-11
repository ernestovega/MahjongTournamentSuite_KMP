package com.etologic.mahjongtournamentsuite.data.cache

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryCacheTest {
    private fun TestScope.newCache(timeSource: TestTimeSource) = SessionRepositoryCache(
        logger = Logger.withTag("cache-test"),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        timeSource = timeSource,
        freshFor = 10.seconds,
    )

    @Test
    fun freshValueIsReturnedWithoutRevisionCheck() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var revisionChecks = 0
        suspend fun read() = cache.getOrLoad("one", "players", RefreshMode.IF_CHANGED, { revisionChecks++; 1L }) { "value" }

        read()
        clock += 9.seconds
        read()

        assertEquals(1, revisionChecks)
    }

    @Test
    fun oldValueWaitsForRevisionCheckAndReloadsWhenChanged() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var revision = 1L
        var loads = 0
        suspend fun read() = cache.getOrLoad("one", "players", RefreshMode.IF_CHANGED, { revision }) { "load ${++loads}" }

        assertEquals("load 1", read())
        revision = 2L
        clock += 11.seconds

        assertEquals("load 2", read())
    }

    @Test
    fun oldValueWithSameRevisionIsReusedAndBecomesFreshAgain() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var revisionChecks = 0
        var loads = 0
        suspend fun read() = cache.getOrLoad("one", "players", RefreshMode.IF_CHANGED, { revisionChecks++; 1L }) { "load ${++loads}" }

        read()
        clock += 11.seconds
        assertEquals("load 1", read())
        clock += 5.seconds
        read()

        assertEquals(1, loads)
        assertEquals(2, revisionChecks)
    }

    @Test
    fun staleValueIsNeverReturnedByIfChanged() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var loads = 0
        suspend fun read() = cache.getOrLoad("one", "table:1", RefreshMode.IF_CHANGED, { 1L }) { "load ${++loads}" }

        read()
        cache.markStale("table:")

        // Same revision, but the entry is marked stale, so the cache must load again at once.
        assertEquals("load 2", read())
    }

    @Test
    fun writeDuringLoadMakesTheLoadedValueStale() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var loads = 0
        cache.getOrLoad("one", "table:1", RefreshMode.IF_CHANGED, { 1L }) {
            loads++
            cache.markStale("table:")
            "old data"
        }

        val next = cache.getOrLoad("one", "table:1", RefreshMode.IF_CHANGED, { 1L }) { "load ${++loads}" }

        assertEquals("load 2", next)
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

    @Test
    fun sharedResultIsReusedInsideMaxAgeAndFetchedAgainAfterIt() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var calls = 0
        suspend fun read(maxAge: kotlin.time.Duration = 2.seconds) = cache.shared("manifest", maxAge) { ++calls }

        assertEquals(1, read())
        clock += 1.seconds
        assertEquals(1, read())
        clock += 2.seconds
        assertEquals(2, read())
    }

    @Test
    fun sharedCallWithZeroMaxAgeAlwaysFetches() = runTest {
        val cache = newCache(TestTimeSource())
        var calls = 0

        cache.shared("manifest", kotlin.time.Duration.ZERO) { ++calls }
        cache.shared("manifest", kotlin.time.Duration.ZERO) { ++calls }

        assertEquals(2, calls)
    }

    @Test
    fun sharedCallsAtTheSameTimeMakeOneRequest() = runTest {
        val cache = newCache(TestTimeSource())
        val gate = CompletableDeferred<Unit>()
        var calls = 0

        val results = (1..5).map {
            async { cache.shared("manifest", 2.seconds) { calls++; gate.await(); "answer" } }
        }
        gate.complete(Unit)

        assertEquals(List(5) { "answer" }, results.awaitAll())
        assertEquals(1, calls)
    }

    @Test
    fun sharedFailureReachesEveryWaitingCallerAndDoesNotStayCached() = runTest {
        val cache = newCache(TestTimeSource())
        var calls = 0

        val first = runCatching { cache.shared<String>("manifest", 2.seconds) { calls++; error("offline") } }
        val second = cache.shared("manifest", 2.seconds) { calls++; "online" }

        assertEquals("offline", first.exceptionOrNull()?.message)
        assertEquals("online", second)
        assertEquals(2, calls)
    }

    @Test
    fun markStaleDropsSharedResults() = runTest {
        val cache = newCache(TestTimeSource())
        var calls = 0
        cache.shared("manifest", 2.seconds) { ++calls }

        cache.markStale("anything")

        assertEquals(2, cache.shared("manifest", 2.seconds) { ++calls })
    }

    @Test
    fun sharedResultThatArrivesAfterAWriteIsNotKept() = runTest {
        val cache = newCache(TestTimeSource())
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val slow = async { cache.shared("manifest", 2.seconds) { ++calls; gate.await(); "before the write" } }
        runCurrent()

        cache.markStale("table:")
        gate.complete(Unit)

        assertEquals("before the write", slow.await())
        assertEquals("after the write", cache.shared("manifest", 2.seconds) { "after the write" })
    }

    @Test
    fun callerThatStartsAfterAWriteDoesNotJoinTheOlderRequest() = runTest {
        val cache = newCache(TestTimeSource())
        val gate = CompletableDeferred<Unit>()
        val old = async { cache.shared("manifest", 2.seconds) { gate.await(); "old" } }
        runCurrent()

        cache.markStale("table:")
        val fresh = cache.shared("manifest", 2.seconds) { "fresh" }
        gate.complete(Unit)

        assertEquals("fresh", fresh)
        assertEquals("old", old.await())
    }

    @Test
    fun accountChangeDropsSharedResults() = runTest {
        val cache = newCache(TestTimeSource())
        cache.getOrLoad("one", "profile", RefreshMode.IF_CHANGED, { 1 }) { "first account" }
        cache.shared("manifest", 2.seconds) { "first account" }

        cache.getOrLoad("two", "profile", RefreshMode.IF_CHANGED, { 1 }) { "second account" }

        assertEquals("second account data", cache.shared("manifest", 2.seconds) { "second account data" })
    }

    @Test
    fun sharedResultBeforeTheFirstCachedReadSurvivesIt() = runTest {
        val cache = newCache(TestTimeSource())
        cache.shared("manifest", 2.seconds) { "answer" }

        cache.getOrLoad("one", "profile", RefreshMode.IF_CHANGED, { 1 }) { "profile" }

        assertEquals("answer", cache.shared("manifest", 2.seconds) { "new answer" })
    }

    @Test
    fun forcedLoadIgnoresFreshness() = runTest {
        val cache = newCache(TestTimeSource())
        var loads = 0
        suspend fun read(mode: RefreshMode) = cache.getOrLoad("one", "players", mode, { 1L }) { "load ${++loads}" }

        read(RefreshMode.IF_CHANGED)

        assertEquals("load 2", read(RefreshMode.FORCE))
    }

    @Test
    fun concurrentReadsOfOneKeyShareOneLoad() = runTest {
        val cache = newCache(TestTimeSource())
        val gate = CompletableDeferred<Unit>()
        var loads = 0

        val reads = (1..4).map {
            async { cache.getOrLoad("one", "players", RefreshMode.IF_CHANGED, { 1L }) { loads++; gate.await(); "data" } }
        }
        runCurrent()
        gate.complete(Unit)

        assertEquals(List(4) { "data" }, reads.awaitAll())
        assertEquals(1, loads)
    }

    @Test
    fun failedRevisionCheckKeepsTheOldValueForTheNextTry() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var offline = false
        var loads = 0
        suspend fun read() = cache.getOrLoad("one", "players", RefreshMode.IF_CHANGED, { if (offline) error("offline") else 1L }) { "load ${++loads}" }

        read()
        clock += 20.seconds
        offline = true
        val failed = runCatching { read() }
        offline = false

        assertEquals("offline", failed.exceptionOrNull()?.message)
        assertEquals("load 1", read())
        assertEquals(1, loads)
    }

    @Test
    fun cachedModeNeverChecksTheServerWhileAValueIsHeld() = runTest {
        val clock = TestTimeSource()
        val cache = newCache(clock)
        var revisionChecks = 0
        var loads = 0
        suspend fun read(mode: RefreshMode) = cache.getOrLoad("one", "players", mode, { revisionChecks++; 1L }) { "load ${++loads}" }

        read(RefreshMode.CACHED)
        clock += 1000.seconds
        val later = read(RefreshMode.CACHED)

        assertEquals("load 1", later)
        assertEquals(1, revisionChecks, "only the first load checked the revision")
        assertEquals(1, loads)
    }

    @Test
    fun cachedModeLoadsWhenAWriteMadeTheValueOld() = runTest {
        val cache = newCache(TestTimeSource())
        var loads = 0
        suspend fun read() = cache.getOrLoad("one", "table:1", RefreshMode.CACHED, { 1L }) { "load ${++loads}" }
        read()

        cache.markStale("table:")

        assertEquals("load 2", read())
    }

    @Test
    fun cachedModeLoadsWhenNothingIsHeld() = runTest {
        val cache = newCache(TestTimeSource())

        assertEquals("first", cache.getOrLoad("one", "players", RefreshMode.CACHED, { 1L }) { "first" })
    }
}
