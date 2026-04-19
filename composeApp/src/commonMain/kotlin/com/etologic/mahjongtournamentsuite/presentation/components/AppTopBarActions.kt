package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun AppTopBarActions(
    onPlayers: (() -> Unit)? = null,
    onPlayerTables: (() -> Unit)? = null,
    onMembers: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onNewTournament: (() -> Unit)? = null,
) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            onPlayers?.let { AppTopBarButton("Players", it) }
            onPlayerTables?.let { AppTopBarButton("Player tables", it) }
            onMembers?.let { AppTopBarButton("Members", it) }
            onRefresh?.let { AppTopBarButton("Refresh", it) }
            onNewTournament?.let { AppTopBarButton("New Tournament", it) }
        }
}
