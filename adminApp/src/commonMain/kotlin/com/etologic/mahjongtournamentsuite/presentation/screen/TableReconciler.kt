package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.field_hand_score
import mahjongtournamentsuite.adminapp.generated.resources.field_is_chicken_hand
import mahjongtournamentsuite.adminapp.generated.resources.field_is_completed
import mahjongtournamentsuite.adminapp.generated.resources.field_is_done
import mahjongtournamentsuite.adminapp.generated.resources.field_manual_player_east_score
import mahjongtournamentsuite.adminapp.generated.resources.field_manual_player_north_score
import mahjongtournamentsuite.adminapp.generated.resources.field_manual_player_south_score
import mahjongtournamentsuite.adminapp.generated.resources.field_manual_player_west_score
import mahjongtournamentsuite.adminapp.generated.resources.field_player_east_id
import mahjongtournamentsuite.adminapp.generated.resources.field_player_east_penalty
import mahjongtournamentsuite.adminapp.generated.resources.field_player_looser_id
import mahjongtournamentsuite.adminapp.generated.resources.field_player_north_id
import mahjongtournamentsuite.adminapp.generated.resources.field_player_north_penalty
import mahjongtournamentsuite.adminapp.generated.resources.field_player_south_id
import mahjongtournamentsuite.adminapp.generated.resources.field_player_south_penalty
import mahjongtournamentsuite.adminapp.generated.resources.field_player_west_id
import mahjongtournamentsuite.adminapp.generated.resources.field_player_west_penalty
import mahjongtournamentsuite.adminapp.generated.resources.field_player_winner_id
import mahjongtournamentsuite.adminapp.generated.resources.field_use_totals_only
import mahjongtournamentsuite.adminapp.generated.resources.hand_field_label
import mahjongtournamentsuite.adminapp.generated.resources.no
import mahjongtournamentsuite.adminapp.generated.resources.value_empty
import mahjongtournamentsuite.adminapp.generated.resources.yes
import org.jetbrains.compose.resources.StringResource

/** One value that the user typed and that the server data replaced. */
internal data class LostChange(
    val label: UiText,
    val value: UiText,
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
            lostChangesOf(current.buildTablePatch(), current.buildHandPatches().toMap())
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
                add(LostChange(UiText.of(Res.string.hand_field_label, handId, field.toFieldLabel()), value.toDisplayValue()))
            }
        }
    }
}

private val FieldLabels: Map<String, StringResource> = mapOf(
    "isCompleted" to Res.string.field_is_completed,
    "useTotalsOnly" to Res.string.field_use_totals_only,
    "playerEastId" to Res.string.field_player_east_id,
    "playerSouthId" to Res.string.field_player_south_id,
    "playerWestId" to Res.string.field_player_west_id,
    "playerNorthId" to Res.string.field_player_north_id,
    "manualPlayerEastScore" to Res.string.field_manual_player_east_score,
    "manualPlayerSouthScore" to Res.string.field_manual_player_south_score,
    "manualPlayerWestScore" to Res.string.field_manual_player_west_score,
    "manualPlayerNorthScore" to Res.string.field_manual_player_north_score,
    "playerWinnerId" to Res.string.field_player_winner_id,
    "playerLooserId" to Res.string.field_player_looser_id,
    "handScore" to Res.string.field_hand_score,
    "isChickenHand" to Res.string.field_is_chicken_hand,
    "isDone" to Res.string.field_is_done,
    "playerEastPenalty" to Res.string.field_player_east_penalty,
    "playerSouthPenalty" to Res.string.field_player_south_penalty,
    "playerWestPenalty" to Res.string.field_player_west_penalty,
    "playerNorthPenalty" to Res.string.field_player_north_penalty,
)

/** The name of a field of the table or of a hand, in the language of the app. */
internal fun String.toFieldLabel(): UiText =
    FieldLabels[this]?.let { UiText.of(it) }
        ?: UiText.Plain(
            replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2].lowercase()}" }
                .replaceFirstChar { it.uppercase() },
        )

internal fun Any?.toDisplayValue(): UiText = when (this) {
    null -> UiText.of(Res.string.value_empty)
    is String -> if (isBlank()) UiText.of(Res.string.value_empty) else UiText.Plain(this)
    is Boolean -> UiText.of(if (this) Res.string.yes else Res.string.no)
    else -> UiText.Plain(toString())
}
