package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerContactState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.contactState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup

/** The groups an editor can filter by. Each group lists the players of the given states. */
internal enum class AccountFilter(val label: String, val icon: ImageVector, val states: Set<PlayerContactState>?) {
    ALL("All", Icons.Default.Groups, null),
    NOT_INVITED("Not invited yet", Icons.Default.PersonAdd, setOf(PlayerContactState.NOT_INVITED, PlayerContactState.LINKED)),
    INVITED("Invited, waiting", Icons.Default.AccessTime, setOf(PlayerContactState.INVITED)),
    CONTACTED("Contacted", Icons.Default.Email, setOf(PlayerContactState.CONTACTED, PlayerContactState.INVITED)),
    NO_EMA_ID("No EMA ID", Icons.Default.Badge, setOf(PlayerContactState.NO_EMA_ID)),
}

internal fun TournamentPlayer.matches(filter: AccountFilter): Boolean =
    filter.states == null || contactState in filter.states

/** True when the query is blank or appears in the player number, name, saved email, EMA number or country. */
internal fun TournamentPlayer.matchesQuery(
    query: String,
    displayName: String,
    emaId: String? = null,
    countryCode: String = "",
    countryName: String = "",
): Boolean {
    val text = query.trim()
    return text.isEmpty() ||
        id.toString() == text ||
        displayName.matchesSearch(text) ||
        invitedEmail?.matchesSearch(text) == true ||
        emaId?.matchesSearch(text) == true ||
        countryCode.matchesSearch(text) ||
        countryName.matchesSearch(text)
}

/**
 * Sends, resends and revokes for many players at once.
 * The editor filters the list, selects players and starts the action by hand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerAccountsBulkDialog(
    players: List<TournamentPlayer>,
    nameOf: (TournamentPlayer) -> String,
    countryOf: (TournamentPlayer) -> String = { it.nonMember?.country.orEmpty() },
    emaIdOf: (TournamentPlayer) -> String? = { it.assignedEmaId },
    countryNameOf: (String) -> String = { "" },
    results: List<PlayerAccountActionResult>,
    busy: Boolean,
    errorMessage: String?,
    onAction: (PlayerAccountAction, List<PlayerAccountActionItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf(AccountFilter.NOT_INVITED) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    val shown = players.filter { it.matches(filter) && it.matchesQuery(query, nameOf(it), emaIdOf(it), countryOf(it), countryNameOf(countryOf(it))) }
    val chosen = shown.filter { it.id in selected }

    var pendingAction by remember { mutableStateOf<PlayerAccountAction?>(null) }

    fun items() = chosen.map { PlayerAccountActionItem(it.id) }

    /** Players that need an email before an invitation can go out. */
    fun missingEmail() = chosen.filter { it.contactState == PlayerContactState.NOT_INVITED && it.invitedEmail.isNullOrBlank() }

    fun run(action: PlayerAccountAction) {
        if (action != PlayerAccountAction.REVOKE && missingEmail().isNotEmpty()) pendingAction = action
        else onAction(action, items())
    }

    pendingAction?.let { action ->
        MissingEmailsDialog(
            players = missingEmail(),
            nameOf = nameOf,
            busy = busy,
            confirmLabel = if (action == PlayerAccountAction.SEND) "Invite or remind" else "Resend",
            onConfirm = { emails ->
                val skipped = missingEmail().map { it.id }.toSet() - emails.keys
                val withEmails = chosen.filter { it.id !in skipped }.map { PlayerAccountActionItem(it.id, emails[it.id]) }
                pendingAction = null
                if (withEmails.isNotEmpty()) onAction(action, withEmails)
            },
            onDismiss = { pendingAction = null },
        )
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup().padding(horizontal = 16.dp, vertical = 16.dp).widthIn(max = 960.dp).fillMaxSize(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("Invitations and reminders") },
        text = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountFilter.entries.forEach { option ->
                        val count = players.count { it.matches(option) }
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option; selected = emptySet() },
                            leadingIcon = { Icon(option.icon, contentDescription = null, Modifier.size(18.dp)) },
                            label = { Text("${option.label} ($count)") },
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search by name, number, email, EMA number or country") },
                )
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
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(shown, key = { it.id }) { slot ->
                        val result = results.firstOrNull { it.playerId == slot.id }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
                                Checkbox(
                                    checked = slot.id in selected,
                                    enabled = !busy,
                                    onCheckedChange = { checked -> selected = if (checked) selected + slot.id else selected - slot.id },
                                )
                            }
                            Text(slot.id.toString(), Modifier.width(36.dp))
                            val country = countryOf(slot)
                            CountryFlag(code = country, contentDescription = country.ifBlank { "No country" })
                            Text(
                                emaIdOf(slot) ?: "—",
                                Modifier.width(68.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Column(Modifier.weight(1f)) {
                                val email = slot.invitedEmail?.takeIf { it.isNotBlank() } ?: "No email saved"
                                val sent = contactTimeText(slot.contactedAt)?.let { "sent $it" }
                                if (wide) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(nameOf(slot), maxLines = 1)
                                        Text(
                                            " · " + listOfNotNull(email, sent).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                } else {
                                    Text(nameOf(slot), maxLines = 1)
                                    Text(
                                        listOfNotNull(email, sent).joinToString(" · "),
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
                var errorSeen by remember(errorMessage) { mutableStateOf(false) }
                errorMessage?.takeUnless { errorSeen }?.let { AppErrorDialog(it, onDismiss = { errorSeen = true }) }
            }
            }
        },
        confirmButton = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoTooltipIcon(
                    "Invite or remind: invites players that have a saved email, and reminds linked players that they are registered. " +
                        "Resend: new invitation link, or the same reminder again. " +
                        "Revoke: cancels the invitation, or removes the account link. " +
                        "An invitation link works once and expires after 14 days.",
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { run(PlayerAccountAction.SEND) },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Invite or remind (${chosen.size})")
                }
                OutlinedButton(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { run(PlayerAccountAction.RESEND) },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Resend (${chosen.size})")
                }
                OutlinedButton(
                    enabled = chosen.isNotEmpty() && !busy,
                    onClick = { run(PlayerAccountAction.REVOKE) },
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Revoke (${chosen.size})")
                }
            }
        },
    )
}

/**
 * Asks for the emails of players that have none. A player left blank is skipped.
 * The result maps the player number to the typed email.
 */
@Composable
private fun MissingEmailsDialog(
    players: List<TournamentPlayer>,
    nameOf: (TournamentPlayer) -> String,
    busy: Boolean,
    confirmLabel: String,
    onConfirm: (Map<Int, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var emails by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val firstFocus = remember { FocusRequester() }
    val filled = emails.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
    val allValid = filled.values.all { it.matches(EMAIL_PATTERN) }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    AlertDialog(
        modifier = Modifier.appFocusGroup().padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("Emails are missing") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("These players have no saved email. Type an email for each player. Players left blank are skipped.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(players, key = { it.id }) { slot ->
                        val value = emails[slot.id].orEmpty()
                        OutlinedTextField(
                            value = value,
                            onValueChange = { emails = emails + (slot.id to it) },
                            modifier = Modifier.fillMaxWidth().then(if (slot == players.first()) Modifier.focusRequester(firstFocus) else Modifier),
                            singleLine = true,
                            label = { Text("${slot.id} · ${nameOf(slot)}") },
                            isError = value.isNotBlank() && !value.trim().matches(EMAIL_PATTERN),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = filled.isNotEmpty() && allValid && !busy, onClick = { onConfirm(filled) }) {
                Text("$confirmLabel (${filled.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
