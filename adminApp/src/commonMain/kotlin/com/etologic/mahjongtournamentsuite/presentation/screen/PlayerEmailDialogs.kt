package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.email_dialog_hidden_hint
import mahjongtournamentsuite.adminapp.generated.resources.email_dialog_title
import mahjongtournamentsuite.adminapp.generated.resources.email_invalid
import mahjongtournamentsuite.adminapp.generated.resources.emails_are_missing
import mahjongtournamentsuite.adminapp.generated.resources.remove_email
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.these_players_have_no_email
import org.jetbrains.compose.resources.stringResource

internal val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * Sets the pending email of one slot. [currentEmail] is null when the user may not read it: editors see no emails,
 * so the dialog starts empty and says so. [onSave] gets the new email. [onRemove] clears it, and it shows only when the slot has one.
 * [confirmLabel] is "Save", or "Invite" when the dialog opens from the Invite action of a slot with no email.
 */
@Composable
internal fun SlotEmailDialog(
    playerName: String,
    currentEmail: String?,
    hasEmail: Boolean,
    confirmLabel: String,
    busy: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf(currentEmail.orEmpty()) }
    val focus = remember { FocusRequester() }
    val valid = email.trim().matches(EMAIL_PATTERN)
    LaunchedEffect(Unit) { focus.requestFocus() }

    TrackedDialog("EmailDialog") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.email_dialog_title, playerName)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentEmail == null && hasEmail) Text(stringResource(Res.string.email_dialog_hidden_hint))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        singleLine = true,
                        label = { Text(stringResource(Res.string.email)) },
                        isError = email.isNotBlank() && !valid,
                        supportingText = { if (email.isNotBlank() && !valid) Text(stringResource(Res.string.email_invalid)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
            },
            confirmButton = { Button(enabled = valid && !busy, onClick = { onSave(email.trim()) }) { Text(confirmLabel) } },
            dismissButton = {
                Column {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    if (hasEmail) TextButton(enabled = !busy, onClick = onRemove) { Text(stringResource(Res.string.remove_email)) }
                }
            },
        )
    }
}

/**
 * Asks for the emails of players that have no account and no email. A player left blank is skipped.
 * The result maps the player number to the typed email.
 */
@Composable
internal fun MissingEmailsDialog(
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

    TrackedDialog("EmailsAreMissing") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.emails_are_missing)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.these_players_have_no_email))
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}
