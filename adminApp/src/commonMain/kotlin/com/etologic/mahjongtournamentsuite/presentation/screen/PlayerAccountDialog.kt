package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerContactState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.contactState
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_exists_note
import mahjongtournamentsuite.adminapp.generated.resources.account_of_player
import mahjongtournamentsuite.adminapp.generated.resources.assign_an_ema_player_to_this
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.contact_invited
import mahjongtournamentsuite.adminapp.generated.resources.contact_no_ema_id
import mahjongtournamentsuite.adminapp.generated.resources.contact_state_linked
import mahjongtournamentsuite.adminapp.generated.resources.contact_state_not_invited
import mahjongtournamentsuite.adminapp.generated.resources.contact_state_reminded
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.invitation_info
import mahjongtournamentsuite.adminapp.generated.resources.link_works_once
import mahjongtournamentsuite.adminapp.generated.resources.not_known
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.outcome_failed
import mahjongtournamentsuite.adminapp.generated.resources.outcome_invited
import mahjongtournamentsuite.adminapp.generated.resources.outcome_notified
import mahjongtournamentsuite.adminapp.generated.resources.outcome_revoked
import mahjongtournamentsuite.adminapp.generated.resources.outcome_skipped
import mahjongtournamentsuite.adminapp.generated.resources.remove_account_link
import mahjongtournamentsuite.adminapp.generated.resources.resend_invitation
import mahjongtournamentsuite.adminapp.generated.resources.save_email_only
import mahjongtournamentsuite.adminapp.generated.resources.send_an_invitation_link_to_the
import mahjongtournamentsuite.adminapp.generated.resources.send_invitation
import mahjongtournamentsuite.adminapp.generated.resources.send_reminder
import mahjongtournamentsuite.adminapp.generated.resources.send_reminder_again
import mahjongtournamentsuite.adminapp.generated.resources.sent
import mahjongtournamentsuite.adminapp.generated.resources.something_went_wrong
import mahjongtournamentsuite.adminapp.generated.resources.this_player_has_an_account_and
import org.jetbrains.compose.resources.stringResource

internal val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** The time of a contact as local date and time in the device language. */
internal fun contactTimeText(isoTime: String?): String? = localizedDateTimeOrNull(isoTime)

@Composable
internal fun PlayerContactState.label(): String = stringResource(
    when (this) {
        PlayerContactState.NO_EMA_ID -> Res.string.contact_no_ema_id
        PlayerContactState.NOT_INVITED -> Res.string.contact_state_not_invited
        PlayerContactState.LINKED -> Res.string.contact_state_linked
        PlayerContactState.INVITED -> Res.string.contact_invited
        PlayerContactState.CONTACTED -> Res.string.contact_state_reminded
    },
)

/**
 * The account state of one tournament slot. An editor invites, reminds or revokes by hand.
 * Assigning a player never sends an email.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerAccountDialog(
    slot: TournamentPlayer,
    busy: Boolean,
    onAction: (PlayerAccountAction, String?) -> Unit,
    onSaveEmail: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = slot.contactState
    var email by remember(slot.id) { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val validEmail = email.trim().matches(EMAIL_PATTERN)
    LaunchedEffect(slot.id, state) {
        if (state == PlayerContactState.NOT_INVITED) emailFocus.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.account_of_player, slot.id)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (state == PlayerContactState.NOT_INVITED || state == PlayerContactState.INVITED) {
                        InfoTooltipIcon(stringResource(Res.string.invitation_info))
                    }
                }
                when (state) {
                    PlayerContactState.NO_EMA_ID ->
                        Text(stringResource(Res.string.assign_an_ema_player_to_this))
                    PlayerContactState.NOT_INVITED -> {
                        Text(stringResource(Res.string.send_an_invitation_link_to_the))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(Res.string.email)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                        )
                    }
                    PlayerContactState.LINKED -> {
                        DetailRow(stringResource(Res.string.email), slot.email ?: stringResource(Res.string.not_known))
                        Text(stringResource(Res.string.account_exists_note))
                    }
                    PlayerContactState.INVITED -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            DetailRow(stringResource(Res.string.email), slot.email ?: stringResource(Res.string.not_known))
                            contactTimeText(slot.contactedAt)?.let { DetailRow(stringResource(Res.string.sent), it) }
                        }
                        Text(
                            stringResource(Res.string.link_works_once),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PlayerContactState.CONTACTED -> {
                        DetailRow(stringResource(Res.string.email), slot.email ?: stringResource(Res.string.not_known))
                        Text(stringResource(Res.string.this_player_has_an_account_and))
                        contactTimeText(slot.contactedAt)?.let { DetailRow(stringResource(Res.string.sent), it) }
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryActions(state, email, validEmail, busy, onAction, onSaveEmail, onDismiss)
            when (state) {
                PlayerContactState.NO_EMA_ID -> Unit
                PlayerContactState.NOT_INVITED -> Button(
                    enabled = validEmail && !busy,
                    onClick = { onAction(PlayerAccountAction.SEND, email.trim()) },
                ) { Text(stringResource(Res.string.send_invitation)) }
                PlayerContactState.LINKED -> Button(
                    enabled = !busy,
                    onClick = { onAction(PlayerAccountAction.SEND, null) },
                ) { Text(stringResource(Res.string.send_reminder)) }
                PlayerContactState.INVITED -> Button(
                    enabled = !busy,
                    onClick = { onAction(PlayerAccountAction.RESEND, null) },
                ) { Text(stringResource(Res.string.resend_invitation)) }
                PlayerContactState.CONTACTED -> OutlinedButton(
                    enabled = !busy,
                    onClick = { onAction(PlayerAccountAction.RESEND, null) },
                ) { Text(stringResource(Res.string.send_reminder_again)) }
            }
            }
        },
    )
}

@Composable
private fun SecondaryActions(
    state: PlayerContactState,
    email: String,
    validEmail: Boolean,
    busy: Boolean,
    onAction: (PlayerAccountAction, String?) -> Unit,
    onSaveEmail: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == PlayerContactState.NOT_INVITED) {
        TextButton(enabled = validEmail && !busy, onClick = { onSaveEmail(email.trim()) }) { Text(stringResource(Res.string.save_email_only)) }
    }
    if (state == PlayerContactState.INVITED) {
        TextButton(enabled = !busy, onClick = { onAction(PlayerAccountAction.REVOKE, null) }) { Text(stringResource(Res.string.remove_account_link)) }
    }
    if (state == PlayerContactState.LINKED || state == PlayerContactState.CONTACTED) {
        TextButton(enabled = !busy, onClick = { onAction(PlayerAccountAction.REVOKE, null) }) { Text(stringResource(Res.string.remove_account_link)) }
    }
    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Text(value, modifier = Modifier.weight(1f))
    }
}

/** The result of one account action, shown in its own dialog after the account dialog closes. */
data class AccountOutcome(val message: UiText, val isError: Boolean)

/** The plain sentence for the result of one account action on one slot. */
internal fun PlayerAccountActionResult.toOutcome(): AccountOutcome {
    val detail = message?.let { " $it" }.orEmpty()
    return when (outcome.name) {
        "FAILED" -> AccountOutcome(UiText.of(Res.string.outcome_failed, detail), isError = true)
        "SKIPPED" -> AccountOutcome(UiText.of(Res.string.outcome_skipped, detail), isError = false)
        "INVITED" -> AccountOutcome(UiText.of(Res.string.outcome_invited, detail), isError = false)
        "NOTIFIED" -> AccountOutcome(UiText.of(Res.string.outcome_notified, detail), isError = false)
        "REVOKED" -> AccountOutcome(UiText.of(Res.string.outcome_revoked, detail), isError = false)
        else -> AccountOutcome(UiText.Plain(outcome.name.lowercase().replaceFirstChar(Char::uppercase) + "." + detail), isError = false)
    }
}

@Composable
fun AccountOutcomeDialog(outcome: AccountOutcome, onDismiss: () -> Unit) {
    val color = if (outcome.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (outcome.isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = color,
            )
        },
        title = { Text(stringResource(if (outcome.isError) Res.string.something_went_wrong else Res.string.dialog_done_title), color = color) },
        text = { Text(outcome.message.asString(), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.ok)) } },
    )
}
