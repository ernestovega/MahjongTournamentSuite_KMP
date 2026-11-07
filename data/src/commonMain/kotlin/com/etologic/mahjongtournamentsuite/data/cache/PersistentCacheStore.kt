package com.etologic.mahjongtournamentsuite.data.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Text values that stay on the device after the app closes: the local storage of the browser, or files.
 * A failure never stops the app. A read that fails gives null, and a write that fails is dropped.
 */
interface PersistentCacheStore {
    suspend fun read(name: String): String?

    suspend fun write(name: String, value: String)

    /** Removes every value of this store. */
    suspend fun clear()
}

/** The store of this platform. */
expect fun createPlatformPersistentCacheStore(): PersistentCacheStore

/** What a cached server answer is saved as, and under which name. */
class Persistence<D : Any>(
    val name: String,
    val serializer: KSerializer<D>,
)

@Serializable
internal data class PersistedCacheRecord(
    val revision: Long,
    val data: JsonElement,
)

/**
 * Server answers saved on the device, so a new start of the app does not download them again.
 * Each value keeps the revision it was loaded at. The app still asks the server for the revision before it uses one.
 * The values of one account are never read for another account.
 */
class PersistedValues(
    private val store: PersistentCacheStore,
    private val json: Json,
) {
    suspend fun <D : Any> read(ownerUid: String, name: String, serializer: KSerializer<D>): Pair<Long, D>? {
        val text = store.read(storageName(ownerUid, name)) ?: return null
        return runCatching {
            val record = json.decodeFromString(PersistedCacheRecord.serializer(), text)
            record.revision to json.decodeFromJsonElement(serializer, record.data)
        }.getOrNull()
    }

    suspend fun <D : Any> write(ownerUid: String, name: String, revision: Long, serializer: KSerializer<D>, value: D) {
        val text = runCatching {
            json.encodeToString(
                PersistedCacheRecord.serializer(),
                PersistedCacheRecord(revision, json.encodeToJsonElement(serializer, value)),
            )
        }.getOrNull() ?: return
        store.write(storageName(ownerUid, name), text)
    }

    suspend fun clear() = store.clear()

    private companion object {
        /** A change of the saved format must change this, so old values are ignored. */
        const val FORMAT = "v1"

        /** Only letters, digits, dots and hyphens. Any other character is written as `_` and its code. */
        fun storageName(ownerUid: String, name: String): String = buildString {
            append(FORMAT).append('.')
            "$ownerUid/$name".forEach { char ->
                if ((char.isLetterOrDigit() && char.code < 128) || char == '.' || char == '-') {
                    append(char)
                } else {
                    append('_').append(char.code.toString(16)).append('_')
                }
            }
        }
    }
}
