package com.etologic.mahjongtournamentsuite.data.cache

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

interface RepositoryCache {
    suspend fun <T : Any> getOrLoad(
        ownerUid: String,
        key: String,
        refreshMode: RefreshMode,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ): T

    /**
     * Shares one remote call, for example a manifest, between many callers. A result younger than [maxAge]
     * is reused, and calls that run at the same time share one request. [markStale] drops all results.
     */
    suspend fun <T : Any> shared(
        key: String,
        maxAge: Duration,
        fetch: suspend () -> T,
    ): T

    suspend fun markStale(vararg keyPrefixes: String)

    suspend fun clear()
}

/**
 * In-memory cache for one signed-in account.
 *
 * [RefreshMode.IF_CHANGED] never returns old data. A cached value is returned at once only if the server
 * confirmed its revision less than [freshFor] ago. Otherwise the cache waits for a revision check and
 * loads again if the revision changed. [markStale] always forces a new load.
 */
class SessionRepositoryCache(
    @Suppress("unused") private val logger: Logger,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val freshFor: Duration = DEFAULT_FRESH_FOR,
) : RepositoryCache {
    private data class Entry(
        val revision: Long,
        val value: Any,
        val confirmedAt: TimeMark,
    )

    private data class SharedResult(
        val value: Any,
        val receivedAt: TimeMark,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val sharedResults = mutableMapOf<String, SharedResult>()
    private val sharedInFlight = mutableMapOf<String, Deferred<SharedResult>>()
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()
    private val inFlight = mutableMapOf<String, Deferred<Entry>>()
    private var ownerUid: String? = null

    /** Counts calls of [markStale]. A load that started before a call must not look fresh afterwards. */
    private var staleCount = 0

    override suspend fun <T : Any> getOrLoad(
        ownerUid: String,
        key: String,
        refreshMode: RefreshMode,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ): T {
        selectOwner(ownerUid)
        val cached = mutex.withLock { entries[key] }
        if (cached != null && cached.revision != STALE_REVISION) {
            when (refreshMode) {
                RefreshMode.CACHED -> return cached.value.cast()
                RefreshMode.IF_CHANGED -> if (cached.isFresh()) return cached.value.cast()
                RefreshMode.FORCE -> Unit
            }
        }

        return refresh(key, cached, refreshMode == RefreshMode.FORCE, revision, load).value.cast()
    }

    override suspend fun <T : Any> shared(
        key: String,
        maxAge: Duration,
        fetch: suspend () -> T,
    ): T {
        val request = mutex.withLock {
            sharedResults[key]?.takeIf { it.receivedAt.elapsedNow() < maxAge }?.let { return it.value.cast() }
            sharedInFlight[key] ?: scope.async {
                SharedResult(value = fetch(), receivedAt = timeSource.markNow())
            }.also { sharedInFlight[key] = it }
        }
        return try {
            val result = request.await()
            // A write finished while the call ran when markStale removed the request. Do not keep that result.
            mutex.withLock { if (sharedInFlight[key] === request) sharedResults[key] = result }
            result.value.cast()
        } finally {
            mutex.withLock { if (sharedInFlight[key] === request) sharedInFlight.remove(key) }
        }
    }

    override suspend fun markStale(vararg keyPrefixes: String) {
        mutex.withLock {
            staleCount++
            sharedResults.clear()
            sharedInFlight.clear()
            entries.keys.filter { key -> keyPrefixes.any(key::startsWith) }.forEach { key ->
                entries[key] = entries.getValue(key).copy(revision = STALE_REVISION)
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
            entries.clear()
            sharedResults.clear()
            sharedInFlight.values.forEach { it.cancel() }
            sharedInFlight.clear()
            ownerUid = null
        }
    }

    private suspend fun selectOwner(uid: String) {
        mutex.withLock {
            if (ownerUid == uid) return
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
            entries.clear()
            // Shared answers can exist before the first cached read. They belong to the account that is signed in.
            if (ownerUid != null) {
                sharedResults.clear()
                sharedInFlight.values.forEach { it.cancel() }
                sharedInFlight.clear()
            }
            ownerUid = uid
        }
    }

    private suspend fun <T : Any> refresh(
        key: String,
        cached: Entry?,
        force: Boolean,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ): Entry {
        // A forced request must not reuse a background revision check.
        val flightKey = if (force) "$key#force" else key
        val request = mutex.withLock {
            inFlight[flightKey] ?: scope.async {
                val staleCountAtStart = mutex.withLock { staleCount }
                // The revision is read before the data. A write between the two calls then only
                // causes one extra load later. The cache never keeps a new revision with old data.
                val remoteRevision = revision()
                val entry = if (!force && cached != null && cached.revision == remoteRevision) {
                    cached.copy(confirmedAt = timeSource.markNow())
                } else {
                    Entry(revision = remoteRevision, value = load(), confirmedAt = timeSource.markNow())
                }
                // A write finished while this load ran, so the data may be old. Use it once, but load again next time.
                if (mutex.withLock { staleCount } != staleCountAtStart) entry.copy(revision = STALE_REVISION) else entry
            }.also { inFlight[flightKey] = it }
        }

        return try {
            request.await().also { result -> mutex.withLock { entries[key] = result } }
        } finally {
            mutex.withLock {
                if (inFlight[flightKey] === request) inFlight.remove(flightKey)
            }
        }
    }

    private fun Entry.isFresh(): Boolean = revision != STALE_REVISION && confirmedAt.elapsedNow() < freshFor

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> Any.cast(): T = this as T

    private companion object {
        const val STALE_REVISION = -1L
        val DEFAULT_FRESH_FOR = 10.seconds
    }
}
