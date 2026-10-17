package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Keeps small texts for the life of one browser tab. A page refresh keeps them. A new tab starts empty.
 * Other platforms do not use it.
 */
interface SessionStateStorage {
    fun read(name: String): String?
    fun write(name: String, value: String)
}

/** The scroll position of one list or page. [index] is the first visible item, or the pixel value of a plain scroll. */
data class ScrollPosition(val index: Int, val offset: Int = 0)

/**
 * Remembers where the user scrolled, so that a page refresh can put the screens back at the same place.
 * Each position is restored once. A later visit to the same screen starts at the top as usual.
 */
class ScrollPositionStore(private val storage: SessionStateStorage) {
    private val current = mutableMapOf<String, ScrollPosition>()
    private val toRestore = mutableMapOf<String, ScrollPosition>().apply { putAll(parse(storage.read(STORAGE_NAME))) }

    private val currentTexts = mutableMapOf<String, String>()
    private val textsToRestore = mutableMapOf<String, String>().apply { putAll(parseTexts(storage.read(TEXTS_NAME))) }

    /** Returns the saved text of [key] one time. */
    fun takeRestoredText(key: String): String? = textsToRestore.remove(key)

    /** Saves a short text. A text with a line break or a tab is not saved. */
    fun putText(key: String, value: String) {
        if ('\n' in value || '\t' in value || currentTexts[key] == value) return
        currentTexts[key] = value
        storage.write(TEXTS_NAME, (textsToRestore + currentTexts).entries.joinToString("\n") { (name, text) -> "$name\t$text" })
    }

    private fun parseTexts(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.lineSequence().mapNotNull { line ->
            val name = line.substringBefore('\t').takeIf { it.isNotBlank() && '\t' in line } ?: return@mapNotNull null
            name to line.substringAfter('\t')
        }.toMap()
    }

    /** Returns the saved position of [key] one time. */
    fun takeRestored(key: String): ScrollPosition? = toRestore.remove(key)

    fun put(key: String, position: ScrollPosition) {
        if (current[key] == position) return
        current[key] = position
        // Positions that were not restored yet stay in the saved text.
        val all = toRestore + current
        storage.write(STORAGE_NAME, all.entries.joinToString("\n") { (name, value) ->
            "${value.index},${value.offset},$name"
        })
    }

    private fun parse(text: String?): Map<String, ScrollPosition> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.lineSequence().mapNotNull { line ->
            val parts = line.split(',', limit = 3)
            val index = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val offset = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val name = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name to ScrollPosition(index, offset)
        }.toMap()
    }

    private companion object {
        const val STORAGE_NAME = "scrollPositions"
        const val TEXTS_NAME = "texts"
    }
}

/** Is null where the app does not restore scroll positions. */
val LocalScrollPositionStore = staticCompositionLocalOf<ScrollPositionStore?> { null }
