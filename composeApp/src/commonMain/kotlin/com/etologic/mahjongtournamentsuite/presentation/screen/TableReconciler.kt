package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable

/** One value that the user typed and that the server data replaced. */
internal data class LostChange(
    val label: String,
    val value: String,
)

/** Tells the user that the server data replaced a table, and which typed values were lost. */
internal data class TableUpdateNotice(
    val key: TableKey,
    val lostChanges: List<LostChange>,
    /** True when the user tried to save and the server refused, because someone saved the table first. */
    val saveRejected: Boolean = false,
)

internal data class TableUpdate(
    val editor: TableManagerEditorState,
    val notice: TableUpdateNotice,
)

/**
 * The server always wins. When a newer table version arrives, the editor takes the server data.
 * The user sees every replaced change, so the user can type it again.
 */
internal object TableReconciler {
    /** The version of each table that this client holds. An open editor is newer than a summary row. */
    fun knownVersions(
        summaries: List<TournamentTable>,
        editors: Collection<TableManagerEditorState>,
    ): Map<TableKey, Long> {
        val versions = summaries.associate { TableKey(it.roundId, it.tableId) to it.version }.toMutableMap()
        editors.forEach { editor ->
            val key = TableKey(editor.roundId, editor.tableId)
            versions[key] = maxOf(versions[key] ?: Long.MIN_VALUE, editor.version)
        }
        return versions
    }

    /**
     * Returns the update for a table that the server sent, or null when the data is not newer than what the
     * editor holds. That happens when a poll answer arrives after the user's own save.
     */
    fun reconcile(
        current: TableManagerEditorState?,
        serverTable: TableState,
        serverHands: List<TableHand>,
        saveRejected: Boolean = false,
    ): TableUpdate? {
        // A rejected save proves that the server version differs, whatever number it has.
        if (!saveRejected && current != null && serverTable.version <= current.version) return null
        val key = TableKey(serverTable.roundId, serverTable.tableId)
        val lost = if (current?.hasUnsavedChanges == true) {
            lostChangesOf(current.buildApplicationTablePatch(), current.buildHandPatches().toMap())
        } else {
            emptyList()
        }
        return TableUpdate(
            editor = TableManagerEditorState.from(serverTable, serverHands),
            notice = TableUpdateNotice(key, lost, saveRejected),
        )
    }

    /** The computed totals follow from the hands. The user did not type them, so they stay out of the list. */
    private val derivedFields = setOf(
        "playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
        "playerEastPoints", "playerSouthPoints", "playerWestPoints", "playerNorthPoints",
    )

    fun lostChangesOf(
        tablePatch: Map<String, Any?>,
        handPatches: Map<Int, Map<String, Any?>>,
    ): List<LostChange> = buildList {
        tablePatch.filterKeys { it !in derivedFields }.forEach { (field, value) ->
            add(LostChange(field.toFieldLabel(), value.toDisplayValue()))
        }
        handPatches.entries.sortedBy { it.key }.forEach { (handId, patch) ->
            patch.forEach { (field, value) ->
                add(LostChange("Hand $handId · ${field.toFieldLabel()}", value.toDisplayValue()))
            }
        }
    }
}

/** `playerEastId` becomes `Player east id`. */
internal fun String.toFieldLabel(): String =
    replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2].lowercase()}" }
        .replaceFirstChar { it.uppercase() }

internal fun Any?.toDisplayValue(): String = when (this) {
    null -> "empty"
    is String -> if (isBlank()) "empty" else this
    is Boolean -> if (this) "Yes" else "No"
    else -> toString()
}
