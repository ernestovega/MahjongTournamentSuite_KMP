package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.MAX_FOOD_RESTRICTIONS_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.MAX_REGISTRATION_TEAM_NAME_LENGTH
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.dialogVerticalScroll
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.reg_choose_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_email_optional
import mahjongtournamentsuite.adminapp.generated.resources.reg_file_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_food
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_apply
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_ready
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_replaces
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_title
import mahjongtournamentsuite.adminapp.generated.resources.reg_import_with_problems
import mahjongtournamentsuite.adminapp.generated.resources.reg_notify
import mahjongtournamentsuite.adminapp.generated.resources.reg_paid
import mahjongtournamentsuite.adminapp.generated.resources.reg_pick_person
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_duplicate_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_ema_not_found
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_invalid_ema
import mahjongtournamentsuite.adminapp.generated.resources.reg_problem_invalid_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_save
import mahjongtournamentsuite.adminapp.generated.resources.reg_shared_email
import mahjongtournamentsuite.adminapp.generated.resources.reg_team_optional
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton

/** Adds a person ([registration] null) or changes one. */
@Composable
internal fun RegistrationEditDialog(
    title: String,
    registration: AdminRegistration?,
    registry: List<Player>,
    isTeams: Boolean,
    teamName: String,
    onSave: (emaId: String, email: String, teamName: String, food: String, paid: Boolean, notify: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var person by remember { mutableStateOf(registration?.let { Player(it.emaId, it.name, "", it.country) }) }
    var picking by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(registration?.email.orEmpty()) }
    var team by remember { mutableStateOf(teamName) }
    var food by remember { mutableStateOf(registration?.foodRestrictions.orEmpty()) }
    var paid by remember { mutableStateOf(registration?.paid == true) }
    var notify by remember { mutableStateOf(true) }
    if (picking) {
        EmaPlayerPickerDialog(
            players = registry,
            owners = emptyMap(),
            selectedEmaId = person?.emaId,
            onSelect = {
                person = it
                picking = false
            },
            onDismiss = { picking = false },
        )
        return
    }
    TrackedDialog(if (registration == null) "AddRegistration" else "EditRegistration") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(title) },
            text = {
                Column(Modifier.dialogVerticalScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (registration == null) {
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            ButtonText(person?.let { "${it.displayName.ifBlank { it.emaId }} · ${it.emaId}" } ?: stringResource(Res.string.reg_pick_person))
                        }
                    } else {
                        val shown = person?.takeIf { it.emaId != registration.emaId }
                        Text(
                            shown?.let { "${it.displayName.ifBlank { it.emaId }} · ${it.emaId}" } ?: "${registration.name} · ${registration.shownEmaId}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (registration.hasEmaProblem) {
                            if (shown == null) {
                                ProblemLines(registration.problems.filter { it in AdminRegistration.EMA_PROBLEMS })
                                registration.fileEmaId?.let {
                                    Text(stringResource(Res.string.reg_file_ema, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().testTag("registration-choose-ema")) {
                                ButtonText(stringResource(Res.string.reg_choose_ema))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text(stringResource(if (registration == null) Res.string.reg_email_optional else Res.string.reg_email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isTeams) {
                        OutlinedTextField(
                            value = team,
                            onValueChange = { team = it.take(MAX_REGISTRATION_TEAM_NAME_LENGTH) },
                            label = { Text(stringResource(Res.string.reg_team_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = food,
                        onValueChange = { food = it.take(MAX_FOOD_RESTRICTIONS_LENGTH) },
                        label = { Text(stringResource(Res.string.reg_food)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    )
                    MtsCheckboxRow(text = stringResource(Res.string.reg_paid), checked = paid, onCheckedChange = { paid = it })
                    if (registration == null) {
                        MtsCheckboxRow(text = stringResource(Res.string.reg_notify), checked = notify, onCheckedChange = { notify = it })
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        enabled = person != null,
                        onClick = { person?.let { onSave(it.emaId, email, team, food, paid, notify) } },
                    ) { ButtonText(stringResource(Res.string.reg_save)) }
                    FocusedTextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** Shows what the chosen CSV or Excel file of the Google Form will do, and imports it. */
@Composable
internal fun RegistrationImportDialog(
    file: Pair<String, ByteArray>,
    preview: suspend (String, ByteArray) -> AppResult<RegistrationImport>,
    apply: suspend (String, ByteArray) -> AppResult<RegistrationImport>,
    onDone: (imported: Int?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<RegistrationImport?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiText?>(null) }
    LaunchedEffect(file) {
        busy = true
        when (val read = preview(file.first, file.second)) {
            is AppResult.Success -> result = read.value
            is AppResult.Failure -> error = read.error.toUiText()
        }
        busy = false
    }
    error?.let {
        // Nothing to review without a readable file: the error closes the import too.
        AppErrorDialog(
            message = it,
            onDismiss = {
                error = null
                if (result == null) onDone(null)
            },
        )
        if (result == null) return
    }
    TrackedDialog("ImportRegistrations") {
        AlertDialog(
            onDismissRequest = { if (!busy) onDone(null) },
            title = { MtsDialogTitle(stringResource(Res.string.reg_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    result?.let { read ->
                        if (read.replaced > 0) {
                            Text(
                                pluralStringResource(Res.plurals.reg_import_replaces, read.replaced, read.replaced),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("registrations-import-replaces"),
                            )
                        }
                        Text(pluralStringResource(Res.plurals.reg_import_ready, read.rows.size, read.rows.size), fontWeight = FontWeight.SemiBold)
                        if (read.rows.any { it.problems.isNotEmpty() }) MtsDialogText(stringResource(Res.string.reg_import_with_problems))
                        val state = rememberLazyListState()
                        LazyColumnWithScrollbar(state = state, modifier = Modifier.heightIn(max = 320.dp), inDialog = true) {
                            items(read.rows, key = { it.line }) { row ->
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "${row.line}. ${row.registryName ?: "${row.firstName} ${row.lastName}".trim()} · ${row.emaId}" +
                                            (row.team?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    row.problems.forEach { problem ->
                                        Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    if (row.sharedEmail) {
                                        Text(stringResource(Res.string.reg_shared_email), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    val chosen = file
                    val ready = result?.rows?.size ?: 0
                    if (ready > 0) {
                        FocusedButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    when (val done = apply(chosen.first, chosen.second)) {
                                        is AppResult.Success -> onDone(done.value.imported)
                                        is AppResult.Failure -> error = done.error.toUiText()
                                    }
                                    busy = false
                                }
                            },
                        ) { ButtonText(stringResource(Res.string.reg_import_apply, ready)) }
                    }
                    FocusedTextButton(enabled = !busy, onClick = { onDone(null) }) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

@Composable
internal fun problemText(code: String): String = when (code) {
    "INVALID_EMA" -> stringResource(Res.string.reg_problem_invalid_ema)
    "DUPLICATE_EMA" -> stringResource(Res.string.reg_problem_duplicate_ema)
    "INVALID_EMAIL" -> stringResource(Res.string.reg_problem_invalid_email)
    "EMA_NOT_FOUND" -> stringResource(Res.string.reg_problem_ema_not_found)
    else -> code
}
