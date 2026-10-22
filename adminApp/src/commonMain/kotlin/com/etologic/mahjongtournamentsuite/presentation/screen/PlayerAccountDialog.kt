package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
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
import com.etologic.mahjongtournamentsuite.domain.model.PlayerInvitation
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.contactState
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup

private const val INVITATION_INFO =
    "An invitation link works once and expires after 14 days. After that, the player cannot accept it and the slot stays unlinked. " +
        "Resend creates a new link and the old link stops working. Revoke cancels the invitation."

internal val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** The time of a contact as local date and time in the device language. */
internal fun contactTimeText(isoTime: String?): String? = localizedDateTimeOrNull(isoTime)

internal fun PlayerContactState.label(): String = when (this) {
    PlayerContactState.NO_EMA_ID -> "No EMA ID"
    PlayerContactState.NOT_INVITED -> "Not invited"
    PlayerContactState.LINKED -> "Linked, not reminded"
    PlayerContactState.INVITED -> "Invited"
    PlayerContactState.CONTACTED -> "Linked, reminded"
}

/**
 * The account state of one tournament slot. An editor invites, reminds or revokes by hand.
 * Assigning a player never sends an email.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerAccountDialog(
    slot: TournamentPlayer,
    invitation: PlayerInvitation?,
    busy: Boolean,
    onAction: (PlayerAccountAction, String?) -> Unit,
    onSaveEmail: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = slot.contactState
    var email by remember(slot.id) { mutableStateOf(slot.invitedEmail.orEmpty()) }
    val emailFocus = remember { FocusRequester() }
    val validEmail = email.trim().matches(EMAIL_PATTERN)
    LaunchedEffect(slot.id, state) {
        if (state == PlayerContactState.NOT_INVITED) emailFocus.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        title = { Text("Account of player ${slot.id}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (state == PlayerContactState.NOT_INVITED || state == PlayerContactState.INVITED) {
                        InfoTooltipIcon(INVITATION_INFO)
                    }
                }
                when (state) {
                    PlayerContactState.NO_EMA_ID ->
                        Text("Assign an EMA player to this slot first.")
                    PlayerContactState.NOT_INVITED -> {
                        Text("Send an invitation link to the email of this player. Nothing is sent before you press Send.")
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                        )
                    }
                    PlayerContactState.LINKED ->
                        Text(
                            "An account with this EMA ID already exists and sees this tournament. " +
                                "No email was sent. Send a reminder when you are ready.",
                        )
                    PlayerContactState.INVITED -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            DetailRow("Email", invitation?.email ?: slot.invitedEmail ?: "Not known")
                            contactTimeText(slot.contactedAt)?.let { DetailRow("Sent", it) }
                        }
                        Text(
                            "The link works once and expires after 14 days. Resend creates a new link and the old link stops working.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PlayerContactState.CONTACTED -> {
                        Text("This player has an account and received an email.")
                        contactTimeText(slot.contactedAt)?.let { DetailRow("Sent", it) }
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
                    enabled = (validEmail || slot.invitedEmail != null && email.isBlank()) && !busy,
                    onClick = { onAction(PlayerAccountAction.SEND, email.trim().ifBlank { null }) },
                ) { Text("Send invitation") }
                PlayerContactState.LINKED -> Button(
                    enabled = !busy,
                    onClick = { onAction(PlayerAccountAction.SEND, null) },
                ) { Text("Send reminder") }
                PlayerContactState.INVITED -> Button(
                    enabled = !busy,
                    onClick = { onAction(PlayerAccountAction.RESEND, null) },
                ) { Text("Resend with new link") }
                PlayerContactState.CONTACTED -> OutlinedButton(
                    enabled = !busy,
                    onClick = { onAction(PlayerAccountAction.RESEND, null) },
                ) { Text("Send reminder again") }
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
        TextButton(enabled = validEmail && !busy, onClick = { onSaveEmail(email.trim()) }) { Text("Save email only") }
    }
    if (state == PlayerContactState.INVITED) {
        TextButton(enabled = !busy, onClick = { onAction(PlayerAccountAction.REVOKE, null) }) { Text("Revoke invitation") }
    }
    if (state == PlayerContactState.LINKED || state == PlayerContactState.CONTACTED) {
        TextButton(enabled = !busy, onClick = { onAction(PlayerAccountAction.REVOKE, null) }) { Text("Remove account link") }
    }
    TextButton(onClick = onDismiss) { Text("Close") }
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
data class AccountOutcome(val message: String, val isError: Boolean)

/** The plain sentence for the result of one account action on one slot. */
internal fun PlayerAccountActionResult.toOutcome(): AccountOutcome {
    val detail = message?.let { " $it" }.orEmpty()
    return when (outcome.name) {
        "FAILED" -> AccountOutcome("The action failed.$detail", isError = true)
        "SKIPPED" -> AccountOutcome("Nothing was done.$detail", isError = false)
        "INVITED" -> AccountOutcome("The invitation was sent.$detail", isError = false)
        "NOTIFIED" -> AccountOutcome("The reminder was sent.$detail", isError = false)
        "REVOKED" -> AccountOutcome("The invitation or account link was removed.$detail", isError = false)
        else -> AccountOutcome(outcome.name.lowercase().replaceFirstChar(Char::uppercase) + "." + detail, isError = false)
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
        title = { Text(if (outcome.isError) "Something went wrong" else "Done", color = color) },
        text = { Text(outcome.message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
    )
}
