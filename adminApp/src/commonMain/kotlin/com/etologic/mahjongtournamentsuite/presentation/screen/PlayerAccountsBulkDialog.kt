package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account
import mahjongtournamentsuite.adminapp.generated.resources.account_has
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.ema_id
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.id
import mahjongtournamentsuite.adminapp.generated.resources.name
import mahjongtournamentsuite.adminapp.generated.resources.account_none
import mahjongtournamentsuite.adminapp.generated.resources.bulk_info
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.filter_all
import mahjongtournamentsuite.adminapp.generated.resources.invitations_and_reminders
import mahjongtournamentsuite.adminapp.generated.resources.no_country
import mahjongtournamentsuite.adminapp.generated.resources.no_email_warning
import mahjongtournamentsuite.adminapp.generated.resources.no_players_in_this_group
import mahjongtournamentsuite.adminapp.generated.resources.outcome_label_invited
import mahjongtournamentsuite.adminapp.generated.resources.outcome_label_reminded
import mahjongtournamentsuite.adminapp.generated.resources.search_by_name_number_email_ema
import mahjongtournamentsuite.adminapp.generated.resources.select_all_shown
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import mahjongtournamentsuite.adminapp.generated.resources.send_count
import mahjongtournamentsuite.adminapp.generated.resources.send_count_label
import mahjongtournamentsuite.adminapp.generated.resources.send_summary
import mahjongtournamentsuite.adminapp.generated.resources.usage_filter_chip
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The groups an editor can filter by. [hasAccount] null shows every player. */
internal enum class AccountFilter(val label: StringResource, val icon: ImageVector, val hasAccount: Boolean?) {
    ALL(Res.string.filter_all, Icons.Default.Groups, null),
    NO_ACCOUNT(Res.string.account_none, Icons.Default.PersonAdd, false),
    HAS_ACCOUNT(Res.string.account_has, Icons.Default.CheckCircle, true),
}

internal fun TournamentPlayer.matches(filter: AccountFilter): Boolean =
    filter.hasAccount == null || hasAccount == filter.hasAccount

/** True when the query is blank or appears in the player number, name, email, EMA number or country. */
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
        email?.matchesSearch(text) == true ||
        emaId?.matchesSearch(text) == true ||
        countryCode.matchesSearch(text) ||
        countryName.matchesSearch(text)
}

/**
 * Invites the players with no account and resets the password of the players with one, for many players at once.
 * The editor filters the list, selects players and starts the action by hand. The server picks the action for each
 * player by its state, so a mixed selection works.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerAccountsBulkDialog(
    players: List<TournamentPlayer>,
    nameOf: (TournamentPlayer) -> String,
    countryOf: (TournamentPlayer) -> String = { "" },
    emaIdOf: (TournamentPlayer) -> String? = { it.assignedEmaId },
    countryNameOf: (String) -> String = { "" },
    results: List<PlayerAccountActionResult>,
    busy: Boolean,
    errorMessage: UiText?,
    /** [emails] holds the emails typed for players that had none. The screen saves them before it sends. */
    onSend: (playerIds: List<Int>, emails: Map<Int, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf(AccountFilter.NO_ACCOUNT) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    val shown = players.filter { it.matches(filter) && it.matchesQuery(query, nameOf(it), emaIdOf(it), countryOf(it), countryNameOf(countryOf(it))) }
    val chosen = shown.filter { it.id in selected }
    var askingEmails by remember { mutableStateOf(false) }
    // While the emails go out the dialog shows a loading dialog. When the send ends it shows what happened.
    var summary by remember { mutableStateOf<List<PlayerAccountActionResult>?>(null) }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (wasBusy && !busy && results.isNotEmpty()) summary = results
        wasBusy = busy
    }
    /** Players with no account and no email. They need an email before the server can invite them. */
    val missingEmail = chosen.filter { !it.hasAccount && !it.hasEmail }

    if (askingEmails) {
        MissingEmailsDialog(
            players = missingEmail,
            nameOf = nameOf,
            busy = busy,
            confirmLabel = stringResource(Res.string.send_count_label),
            onConfirm = { emails ->
                askingEmails = false
                // A player left blank is skipped: it gets no email, so it cannot get an account.
                val skipped = missingEmail.map { it.id }.toSet() - emails.keys
                onSend(chosen.map { it.id }.filter { it !in skipped }, emails)
            },
            onDismiss = { askingEmails = false },
        )
    }

    TrackedDialog("InvitationsAndReminders") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().padding(horizontal = 16.dp, vertical = 16.dp).widthIn(max = 960.dp).fillMaxSize(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.invitations_and_reminders)) },
            text = {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccountFilter.entries.forEach { option ->
                                val count = players.count { it.matches(option) }
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option; selected = emptySet() },
                                    leadingIcon = { Icon(option.icon, contentDescription = null, Modifier.size(18.dp)) },
                                    label = { Text(stringResource(Res.string.usage_filter_chip, stringResource(option.label), count)) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(Res.string.search_by_name_number_email_ema)) },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = shown.isNotEmpty() && !busy,
                                onClick = { selected = shown.map { it.id }.toSet() },
                            ) { Text(stringResource(Res.string.select_all_shown, shown.size)) }
                            TextButton(enabled = selected.isNotEmpty() && !busy, onClick = { selected = emptySet() }) {
                                Text(stringResource(Res.string.clear))
                            }
                            Text(stringResource(Res.string.selected_count, chosen.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("account_list")) {
                            // The titles stay at the top while the rows scroll under them.
                            stickyHeader { ColumnTitles() }
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
                                    Text(slot.id.toString(), Modifier.width(IdColumnWidth))
                                    val country = countryOf(slot)
                                    Box(Modifier.width(FlagColumnWidth), contentAlignment = Alignment.Center) {
                                        CountryFlag(code = country, contentDescription = country.ifBlank { stringResource(Res.string.no_country) })
                                    }
                                    Text(
                                        emaIdOf(slot) ?: "—",
                                        Modifier.width(EmaColumnWidth),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    Text(nameOf(slot), Modifier.weight(1f), maxLines = 1)
                                    EmailCell(slot, Modifier.weight(1f))
                                    ResultCell(slot, result)
                                }
                            }
                        }
                        if (shown.isEmpty()) Text(stringResource(Res.string.no_players_in_this_group), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    InfoTooltipIcon(stringResource(Res.string.bulk_info))
                    Text(
                        stringResource(Res.string.send_summary, chosen.count { !it.hasAccount }, chosen.count { it.hasAccount }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                    Button(
                        enabled = chosen.isNotEmpty() && !busy,
                        onClick = { if (missingEmail.isNotEmpty()) askingEmails = true else onSend(chosen.map { it.id }, emptyMap()) },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.send_count, chosen.size))
                    }
                }
            },
        )
    }
    // These dialogs come after the main dialog, so they are drawn above it.
    if (busy) SendingDialog()
    summary?.let { SendSummaryDialog(it, nameOf = { id -> players.firstOrNull { p -> p.id == id }?.let { p -> "$id · ${nameOf(p)}" } ?: id.toString() }, onDismiss = { summary = null }) }
}

private val CheckboxColumnWidth = 24.dp
private val IdColumnWidth = 36.dp
private val FlagColumnWidth = 32.dp
private val EmaColumnWidth = 68.dp
private val ResultColumnWidth = 120.dp

/** The titles of the columns of the list. They use the same widths as the rows, so each title sits over its column. */
@Composable
private fun ColumnTitles() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        @Composable
        fun Title(textRes: StringResource, modifier: Modifier, align: TextAlign = TextAlign.Start) = Text(
            stringResource(textRes),
            modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
        )
        Spacer(Modifier.width(CheckboxColumnWidth))
        Title(Res.string.id, Modifier.width(IdColumnWidth))
        Title(Res.string.country, Modifier.width(FlagColumnWidth), TextAlign.Center)
        Title(Res.string.ema_id, Modifier.width(EmaColumnWidth))
        Title(Res.string.name, Modifier.weight(1f))
        Title(Res.string.email, Modifier.weight(1f))
        Title(Res.string.account, Modifier.width(ResultColumnWidth), TextAlign.Center)
    }
}

/** The email of the slot when the user may read it. A slot with no account and no email shows a warning. */
@Composable
private fun EmailCell(slot: TournamentPlayer, modifier: Modifier) {
    val email = slot.email?.takeIf { it.isNotBlank() }
    when {
        email != null -> Text(
            email,
            modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        !slot.hasAccount && !slot.hasEmail -> Text(
            stringResource(Res.string.no_email_warning),
            modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
        )
        else -> Spacer(modifier)
    }
}

/**
 * The result of the last send for the slot, or the account state when nothing was sent.
 * A send that went out shows the date and time of the email under the text.
 */
@Composable
private fun ResultCell(slot: TournamentPlayer, result: PlayerAccountActionResult?) {
    if (result == null) {
        Box(Modifier.width(ResultColumnWidth), contentAlignment = Alignment.Center) { AccountStateIcon(slot.hasAccount) }
        return
    }
    val text = when {
        result.outcome == PlayerAccountOutcome.INVITED -> stringResource(Res.string.outcome_label_invited)
        result.outcome == PlayerAccountOutcome.REMINDED -> stringResource(Res.string.outcome_label_reminded)
        else -> stringResource((result.reason ?: PlayerAccountFailure.UNEXPECTED).textRes())
    }
    val color = if (result.outcome == PlayerAccountOutcome.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(Modifier.width(ResultColumnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        // Only an email that went out has a time. A failure shows its reason alone.
        localizedDateTimeOrNull(result.sentAt).takeIf { result.outcome != PlayerAccountOutcome.FAILED }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}
