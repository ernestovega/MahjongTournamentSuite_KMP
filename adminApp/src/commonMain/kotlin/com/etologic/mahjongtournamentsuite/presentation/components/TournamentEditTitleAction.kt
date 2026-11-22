package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.edit_tournament
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Pencil button for the top bar title. [onEdit] opens the edit tournament dialog, see [TournamentEditDialog].
 * It shows only for admin and editor users, and only when the tournament is loaded.
 * On a phone it can be an option of the three-dots menu. The menu closes on the click, so the screen keeps the dialog.
 */
@Composable
fun TournamentEditTitleAction(
    tournamentId: String,
    onEdit: () -> Unit,
) {
    val store = koinInject<AppMemoryStore>()
    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()
    if (adminStatus == null || tournaments.none { it.id == tournamentId }) return

    AppTopBarButton(
        text = stringResource(Res.string.edit_tournament),
        icon = Icons.Default.Edit,
        onClick = onEdit,
        showLabel = false,
    )
}

/** The edit tournament dialog of [TournamentEditTitleAction]. */
@Composable
fun TournamentEditDialog(
    tournamentId: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    onPlayerCount: ((Tournament) -> Unit)? = null,
) {
    val store = koinInject<AppMemoryStore>()
    val tournaments by store.tournaments.collectAsState()
    val tournament = tournaments.firstOrNull { it.id == tournamentId } ?: return
    EditTournamentDialog(
        tournament = tournament,
        onDismiss = onDismiss,
        onDeleted = onDeleted,
        onPlayerCount = onPlayerCount,
    )
}
