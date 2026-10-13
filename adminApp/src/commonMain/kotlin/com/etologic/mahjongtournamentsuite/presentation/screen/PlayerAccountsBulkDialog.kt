package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerContactState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.contactState
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup

/** The groups an editor can filter by. Each group lists the players of the given states. */
internal enum class AccountFilter(val label: String, val states: Set<PlayerContactState>?) {
    ALL("All", null),
    NOT_INVITED("Not invited yet", setOf(PlayerContactState.NOT_INVITED, PlayerContactState.LINKED)),
    INVITED("Invited, waiting", setOf(PlayerContactState.INVITED)),
    CONTACTED("Contacted", setOf(PlayerContactState.CONTACTED, PlayerContactState.INVITED)),
    NO_EMA_ID("No EMA ID", setOf(PlayerContactState.NO_EMA_ID)),
}

internal fun TournamentPlayer.matches(filter: AccountFilter): Boolean =
    filter.states == null || contactState in filter.states

/**
 * Sends, resends and revokes for many players at once.
 * The editor filters the list, selects players and starts the action by hand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerAccountsBulkDialog(
    players: List<TournamentPlayer>,
    nameOf: (TournamentPlayer) -> String,
    results: List<PlayerAccountActionResult>,
    busy: Boolean,
    errorMessage: String?,
    onAction: (PlayerAccountAction, List<PlayerAccountActionItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf(AccountFilter.NOT_INVITED) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val shown = players.filter { it.matches(filter) }
    val chosen = shown.filter { it.id in selected }

    fun items() = chosen.map { PlayerAccountActionItem(it.id) }

    AlertDialog(
        modifier = Modifier.appFocusGroup().widthIn(max = 900.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("Invitations and registration emails") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nothing is sent when you assign a player. Choose players and press an action.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountFilter.entries.forEach { option ->
                        val count = players.count { it.matches(option) }
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option; selected = emptySet() },
                            label = { Text("${option.label} ($count)") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = shown.isNotEmpty() && !busy,
                        onClick = { selected = shown.map { it.id }.toSet() },
                    ) { Text("Select all shown (${shown.size})") }
                    TextButton(enabled = selected.isNotEmpty() && !busy, onClick = { selected = emptySet() }) {
                        Text("Clear")
                    }
                    Text("${chosen.size} selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(shown, key = { it.id }) { slot ->
                        val result = results.firstOrNull { it.playerId == slot.id }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = slot.id in selected,
                                enabled = !busy,
                                onCheckedChange = { checked -> selected = if (checked) selected + slot.id else selected - slot.id },
                            )
                            Text(slot.id.toString(), Modifier.width(36.dp))
                            Column(Modifier.weight(1f)) {
                                Text(nameOf(slot), maxLines = 1)
                                val detail = listOfNotNull(
                                    slot.invitedEmail,
                                    contactTimeText(slot.contactedAt)?.let { "sent $it" },
                                ).joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                result?.let { "${it.outcome.name.lowercase()}${it.message?.let { m -> ": $m" } ?: ""}" }
                                    ?: slot.contactState.label(),
                                color = when (result?.outcome) {
                                    PlayerAccountOutcome.FAILED -> MaterialTheme.colorScheme.error
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (shown.isEmpty()) Text("No players in this group.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Send: invites players that have a saved email, and tells linked players that they are registered. " +
                        "Resend: new invitation link, or the same registration email again. " +
                        "Revoke: cancels the invitation, or removes the account link.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { onAction(PlayerAccountAction.SEND, items()) },
                ) { Text("Send (${chosen.size})") }
                OutlinedButton(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { onAction(PlayerAccountAction.RESEND, items()) },
                ) { Text("Resend (${chosen.size})") }
                OutlinedButton(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { onAction(PlayerAccountAction.REVOKE, items()) },
                ) { Text("Revoke (${chosen.size})") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
