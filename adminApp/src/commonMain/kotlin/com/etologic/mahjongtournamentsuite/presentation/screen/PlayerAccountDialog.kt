package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup

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
@Composable
fun PlayerAccountDialog(
    slot: TournamentPlayer,
    invitation: PlayerInvitation?,
    result: PlayerAccountActionResult?,
    busy: Boolean,
    errorMessage: String?,
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
            Column(Modifier.fillMaxWidth()) {
                Text(state.label(), style = MaterialTheme.typography.titleSmall)
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
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(emailFocus),
                        )
                    }
                    PlayerContactState.LINKED ->
                        Text(
                            "An account with this EMA ID already exists and sees this tournament. " +
                                "No email was sent. Send a reminder when you are ready.",
                        )
                    PlayerContactState.INVITED -> {
                        Text("Invitation sent to ${invitation?.email ?: slot.invitedEmail ?: "this player"}.")
                        contactTimeText(slot.contactedAt)?.let { Text("Sent on $it.") }
                        Text(
                            "The link works once and expires after 14 days. Resend creates a new link and the old link stops working.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PlayerContactState.CONTACTED -> {
                        Text("This player has an account and received an email.")
                        contactTimeText(slot.contactedAt)?.let { Text("Sent on $it.") }
                    }
                }
                result?.let {
                    Text(
                        "${it.outcome.name.lowercase().replaceFirstChar(Char::uppercase)}" +
                            (it.message?.let { message -> ": $message" } ?: ""),
                        color = if (it.outcome.name == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
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
        },
        dismissButton = {
            Column {
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
        },
    )
}
