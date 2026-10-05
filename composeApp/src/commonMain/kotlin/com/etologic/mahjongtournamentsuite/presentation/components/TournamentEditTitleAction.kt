package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

/**
 * Pencil button for the top bar title. It opens the edit tournament dialog.
 * It shows only for admin and editor users, and only when the tournament is loaded.
 */
@Composable
fun TournamentEditTitleAction(
    tournamentId: String,
    onDeleted: () -> Unit,
) {
    val store = koinInject<AppMemoryStore>()
    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val tournament = tournaments.firstOrNull { it.id == tournamentId }
    if (adminStatus == null || tournament == null) return

    IconButton(onClick = { showDialog = true }) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit tournament",
        )
    }

    if (showDialog) {
        EditTournamentDialog(
            tournament = tournament,
            onDismiss = { showDialog = false },
            onDeleted = {
                showDialog = false
                onDeleted()
            },
        )
    }
}
