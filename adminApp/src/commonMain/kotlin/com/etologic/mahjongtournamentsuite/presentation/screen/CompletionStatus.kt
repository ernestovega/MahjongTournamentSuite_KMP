package com.etologic.mahjongtournamentsuite.presentation.screen

import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.presentation.components.HintTooltip
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.completed
import mahjongtournamentsuite.adminapp.generated.resources.icon_status_completed
import mahjongtournamentsuite.adminapp.generated.resources.icon_status_empty
import mahjongtournamentsuite.adminapp.generated.resources.icon_status_in_progress
import mahjongtournamentsuite.adminapp.generated.resources.icon_status_manual
import mahjongtournamentsuite.adminapp.generated.resources.status_empty
import mahjongtournamentsuite.adminapp.generated.resources.status_incomplete
import mahjongtournamentsuite.adminapp.generated.resources.status_partial
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal enum class CompletionStatus(
    val label: StringResource,
    val description: StringResource,
    val image: DrawableResource,
) {
    Empty(Res.string.status_empty, Res.string.status_empty, Res.drawable.icon_status_empty),
    InProgress(Res.string.status_incomplete, Res.string.status_incomplete, Res.drawable.icon_status_in_progress),
    Manual(Res.string.status_partial, Res.string.status_partial, Res.drawable.icon_status_manual),
    Completed(Res.string.completed, Res.string.completed, Res.drawable.icon_status_completed),
}

/** Status icon with a hover and click tooltip. An empty status shows no icon. */
@Composable
internal fun StatusIconWithTooltip(status: CompletionStatus) {
    if (status == CompletionStatus.Empty) return
    HintTooltip(stringResource(status.description)) {
        Image(
            painter = painterResource(status.image),
            contentDescription = stringResource(status.label),
            modifier = Modifier.size(StatusIconSize),
        )
    }
}

/** Size of the status icon next to round and table numbers. */
private val StatusIconSize = 16.dp

internal fun tableCompletionStatus(table: TournamentTable): CompletionStatus = when (table.completionStatus) {
    "empty" -> CompletionStatus.Empty
    "incomplete" -> CompletionStatus.InProgress
    "partial" -> CompletionStatus.Manual
    "completed" -> CompletionStatus.Completed
    // Data from an older server has no status. Use the old flags until the next refresh.
    else -> when {
        table.hasValidManualTotals -> CompletionStatus.Manual
        table.isCompleted -> CompletionStatus.Completed
        table.hasProgress -> CompletionStatus.InProgress
        else -> CompletionStatus.Empty
    }
}

internal fun firstTournamentTableToOpen(tables: List<TournamentTable>): TournamentTable? {
    val orderedTables = tables.sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
    return orderedTables.firstOrNull { table ->
        tableCompletionStatus(table) in setOf(CompletionStatus.Empty, CompletionStatus.InProgress)
    } ?: orderedTables.firstOrNull()
}

/** A round is complete only when all its tables are. Any other data makes it incomplete. */
internal fun roundCompletionStatus(tables: List<TournamentTable>): CompletionStatus {
    if (tables.isEmpty()) return CompletionStatus.Empty
    val statuses = tables.map(::tableCompletionStatus)
    val finished = setOf(CompletionStatus.Manual, CompletionStatus.Completed)
    return when {
        statuses.all { it in finished } ->
            if (CompletionStatus.Manual in statuses) CompletionStatus.Manual else CompletionStatus.Completed
        statuses.any { it != CompletionStatus.Empty } -> CompletionStatus.InProgress
        else -> CompletionStatus.Empty
    }
}
