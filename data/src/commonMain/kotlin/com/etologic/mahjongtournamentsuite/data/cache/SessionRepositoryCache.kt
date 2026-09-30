package com.etologic.mahjongtournamentsuite.data.cache

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RepositoryCache {
    suspend fun <T : Any> getOrLoad(
        ownerUid: String,
        key: String,
        refreshMode: RefreshMode,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ): T

    suspend fun markStale(vararg keyPrefixes: String)

    suspend fun clear()
}

/** In-memory cache for one signed-in account. */
class SessionRepositoryCache(
    private val logger: Logger,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RepositoryCache {
    private data class Entry(
        val revision: Long,
        val value: Any,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()
    private val inFlight = mutableMapOf<String, Deferred<Entry>>()
    private var ownerUid: String? = null

    override suspend fun <T : Any> getOrLoad(
        ownerUid: String,
        key: String,
        refreshMode: RefreshMode,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ): T {
        selectOwner(ownerUid)
        val cached = mutex.withLock { entries[key] }
        if (cached != null && refreshMode == RefreshMode.IF_CHANGED) {
            refreshInBackground(key, cached, revision, load)
            return cached.value.cast()
        }

        return refresh(key, cached, refreshMode == RefreshMode.FORCE, revision, load).value.cast()
    }

    override suspend fun markStale(vararg keyPrefixes: String) {
        mutex.withLock {
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
            ownerUid = null
        }
    }

    private suspend fun selectOwner(uid: String) {
        mutex.withLock {
            if (ownerUid == uid) return
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
            entries.clear()
            ownerUid = uid
        }
    }

    private fun <T : Any> refreshInBackground(
        key: String,
        cached: Entry,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ) {
        scope.launch {
            runCatching { refresh(key, cached, false, revision, load) }
                .onFailure { error -> logger.w(error) { "Background cache refresh failed for $key." } }
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
                val remoteRevision = revision()
                if (!force && cached != null && cached.revision == remoteRevision) {
                    cached
                } else {
                    Entry(revision = remoteRevision, value = load())
                }
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

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> Any.cast(): T = this as T

    private companion object {
        const val STALE_REVISION = -1L
    }
}
