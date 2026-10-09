package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentEditTitleAction
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.tournamentBestHandScores
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.TeamsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.HintTooltip
import com.etologic.mahjongtournamentsuite.presentation.components.TableStatBadges
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.ResetTableDialog
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.platform.openRankings
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.icon_status_completed
import mahjongtournamentsuite.composeapp.generated.resources.icon_status_empty
import mahjongtournamentsuite.composeapp.generated.resources.icon_status_in_progress
import mahjongtournamentsuite.composeapp.generated.resources.icon_status_manual
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions

internal enum class CompletionStatus(
    val label: String,
    val description: String,
    val image: DrawableResource,
) {
    Empty("Empty", "Empty", Res.drawable.icon_status_empty),
    InProgress("Incomplete", "Incomplete", Res.drawable.icon_status_in_progress),
    Manual("Partially complete", "Partially complete", Res.drawable.icon_status_manual),
    Completed("Completed", "Completed", Res.drawable.icon_status_completed),
}

/** Status icon with a hover and click tooltip. An empty status shows no icon. */
@Composable
internal fun StatusIconWithTooltip(status: CompletionStatus) {
    if (status == CompletionStatus.Empty) return
    HintTooltip(status.description) {
        Image(
            painter = painterResource(status.image),
            contentDescription = status.label,
            modifier = Modifier.size(StatusIconSize),
        )
    }
}

/** Size of the status icon next to round and table numbers. */
internal val StatusIconSize = 16.dp

/** Space between the icons of a list entry. */
internal val SidebarIconSpacing = 12.dp

/** Extra space between a list entry title and its first icon. It adds to [SidebarIconSpacing]. */
internal val SidebarTitleIconSpacing = 2.dp

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
