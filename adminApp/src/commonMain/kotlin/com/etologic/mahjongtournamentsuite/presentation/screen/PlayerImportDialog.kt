package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssue
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowStatus
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedSpreadsheet
import com.etologic.mahjongtournamentsuite.presentation.platform.SpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberSpreadsheetSaver
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberSpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.import_apply_count
import mahjongtournamentsuite.adminapp.generated.resources.import_applying
import mahjongtournamentsuite.adminapp.generated.resources.import_choose_other_file
import mahjongtournamentsuite.adminapp.generated.resources.import_count_errors
import mahjongtournamentsuite.adminapp.generated.resources.import_count_new
import mahjongtournamentsuite.adminapp.generated.resources.import_count_replace
import mahjongtournamentsuite.adminapp.generated.resources.import_creates_account
import mahjongtournamentsuite.adminapp.generated.resources.import_done_title
import mahjongtournamentsuite.adminapp.generated.resources.import_email_value
import mahjongtournamentsuite.adminapp.generated.resources.import_failure_line
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_duplicate_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_duplicate_slot
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_email_in_use
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_email_in_use_no_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_email_kept
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_ema_elsewhere
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_invalid_email
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_invalid_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_invalid_id
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_missing_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_replaces_player
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_conflict
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_ignored
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_in_use
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_too_long
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_unknown
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_unknown_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_unknown_slot
import mahjongtournamentsuite.adminapp.generated.resources.import_players_title
import mahjongtournamentsuite.adminapp.generated.resources.import_reading_file
import mahjongtournamentsuite.adminapp.generated.resources.import_replace_checkbox
import mahjongtournamentsuite.adminapp.generated.resources.import_replace_hint
import mahjongtournamentsuite.adminapp.generated.resources.import_result_assigned
import mahjongtournamentsuite.adminapp.generated.resources.import_result_emails
import mahjongtournamentsuite.adminapp.generated.resources.import_result_emails_refused
import mahjongtournamentsuite.adminapp.generated.resources.import_result_failures
import mahjongtournamentsuite.adminapp.generated.resources.import_result_skipped
import mahjongtournamentsuite.adminapp.generated.resources.import_result_teams
import mahjongtournamentsuite.adminapp.generated.resources.import_row_title
import mahjongtournamentsuite.adminapp.generated.resources.import_started_warning
import mahjongtournamentsuite.adminapp.generated.resources.import_status_error
import mahjongtournamentsuite.adminapp.generated.resources.import_status_new
import mahjongtournamentsuite.adminapp.generated.resources.import_status_replace
import mahjongtournamentsuite.adminapp.generated.resources.import_status_unchanged
import mahjongtournamentsuite.adminapp.generated.resources.import_team_value
import mahjongtournamentsuite.adminapp.generated.resources.import_untouched_slots
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/** What the dialog shows now. */
private sealed interface ImportStep {
    data object Choose : ImportStep
    data object Reading : ImportStep
    data class Preview(val file: SelectedSpreadsheet, val preview: PlayerImportPreview) : ImportStep
    data class Applying(val done: Int, val total: Int) : ImportStep
    data class Done(val result: PlayerImportResult) : ImportStep
}

/**
 * Fills the player slots from a CSV or Excel file. The file chooser opens at once. The editor sees what the import does
 * before it changes anything. [playerNameOf] gives the name of a player in the EMA registry. [onChanged] runs when the import changed data.
 */
@Composable
fun PlayerImportDialog(
    tournamentId: String,
    playerNameOf: (String) -> String?,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
    /** Gives the file chooser. Tests give a chooser that picks a file at once. */
    pickerFactory: @Composable (
        onSelected: (SelectedSpreadsheet) -> Unit,
        onError: (UiText) -> Unit,
        onCancelled: () -> Unit,
    ) -> SpreadsheetPicker = { onSelected, onError, onCancelled -> rememberSpreadsheetPicker(onSelected, onError, onCancelled) },
) {
    val presenter = koinInject<PlayerImportPresenter>()
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<ImportStep>(ImportStep.Choose) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var replaceExisting by remember { mutableStateOf(false) }

    fun preview(file: SelectedSpreadsheet) = scope.launch {
        step = ImportStep.Reading
        when (val result = presenter.preview(tournamentId, file.fileName, file.bytes)) {
            is AppResult.Success -> {
                replaceExisting = false
                step = ImportStep.Preview(file, result.value)
            }
            is AppResult.Failure -> {
                error = result.error.toUiText()
                step = ImportStep.Choose
            }
        }
    }

    val picker = pickerFactory(
        { file -> preview(file) },
        { error = it },
        { if (step == ImportStep.Choose) onDismiss() },
    )

    // The first step has no dialog. The file chooser of the system opens at once.
    LaunchedEffect(Unit) { picker.launch() }

    error?.let {
        AppErrorDialog(
            message = it,
            onDismiss = {
                error = null
                if (step == ImportStep.Choose) onDismiss()
            },
        )
    }

    when (val current = step) {
        ImportStep.Choose -> Unit

        ImportStep.Reading, is ImportStep.Applying -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.import_players_title)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(
                        if (current is ImportStep.Applying) {
                            stringResource(Res.string.import_applying, current.done, current.total)
                        } else {
                            stringResource(Res.string.import_reading_file)
                        },
                    )
                }
            },
            confirmButton = {},
        )

        is ImportStep.Preview -> {
            val preview = current.preview
            val appliedCount = preview.newCount + if (replaceExisting) preview.replaceCount else 0
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.appFocusGroup(),
                title = { Text(stringResource(Res.string.import_players_title)) },
                text = {
                    ImportPreviewContent(
                        preview = preview,
                        replaceExisting = replaceExisting,
                        onReplaceExistingChange = { replaceExisting = it },
                        playerNameOf = playerNameOf,
                    )
                },
                confirmButton = {
                    Button(
                        enabled = appliedCount > 0,
                        onClick = {
                            scope.launch {
                                step = ImportStep.Applying(0, appliedCount)
                                val result = presenter.apply(
                                    tournamentId, current.file.fileName, current.file.bytes, preview, replaceExisting,
                                    onProgress = { done, total -> step = ImportStep.Applying(done, total) },
                                )
                                when (result) {
                                    is AppResult.Success -> {
                                        onChanged()
                                        step = ImportStep.Done(result.value)
                                    }
                                    is AppResult.Failure -> {
                                        error = result.error.toUiText()
                                        step = current
                                    }
                                }
                            }
                        },
                    ) { Text(stringResource(Res.string.import_apply_count, appliedCount)) }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                        TextButton(onClick = picker::launch) { Text(stringResource(Res.string.import_choose_other_file)) }
                    }
                },
            )
        }

        is ImportStep.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.appFocusGroup(),
            title = { Text(stringResource(Res.string.import_done_title)) },
            text = { ImportResultContent(current.result) },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
        )
    }
}

@Composable
internal fun ImportPreviewContent(
    preview: PlayerImportPreview,
    replaceExisting: Boolean,
    onReplaceExistingChange: (Boolean) -> Unit,
    playerNameOf: (String) -> String?,
) {
    // Errors come first, so the editor sees the rows to fix before the list of good rows.
    val rows = remember(preview) {
        preview.rows.filter { it.status != ImportRowStatus.UNCHANGED }.sortedWith(compareBy({ it.status.sortOrder() }, { it.row }))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.import_count_new, preview.newCount))
        if (preview.replaceCount > 0) Text(stringResource(Res.string.import_count_replace, preview.replaceCount))
        if (preview.errorCount > 0) {
            Text(stringResource(Res.string.import_count_errors, preview.errorCount), color = MaterialTheme.colorScheme.error)
        }
        if (preview.untouchedSlotIds.isNotEmpty()) {
            Text(
                stringResource(Res.string.import_untouched_slots, preview.untouchedSlotIds.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (preview.replaceCount > 0) {
            if (preview.tournamentStarted) {
                Text(stringResource(Res.string.import_started_warning), color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.toggleable(
                    value = replaceExisting,
                    role = Role.Checkbox,
                    onValueChange = onReplaceExistingChange,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = replaceExisting, onCheckedChange = null)
                Text(stringResource(Res.string.import_replace_checkbox, preview.replaceCount))
            }
            if (!replaceExisting) {
                Text(
                    stringResource(Res.string.import_replace_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.row }) { row -> ImportRowItem(row, playerNameOf) }
        }
    }
}

private fun ImportRowStatus.sortOrder() = when (this) {
    ImportRowStatus.ERROR -> 0
    ImportRowStatus.REPLACE -> 1
    ImportRowStatus.NEW -> 2
    ImportRowStatus.UNCHANGED -> 3
}

@Composable
private fun ImportRowItem(row: ImportRowPlan, playerNameOf: (String) -> String?) {
    val statusColor = when (row.status) {
        ImportRowStatus.ERROR -> MaterialTheme.colorScheme.error
        ImportRowStatus.REPLACE -> MaterialTheme.colorScheme.tertiary
        ImportRowStatus.NEW -> MaterialTheme.colorScheme.primary
        ImportRowStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = stringResource(
        when (row.status) {
            ImportRowStatus.ERROR -> Res.string.import_status_error
            ImportRowStatus.REPLACE -> Res.string.import_status_replace
            ImportRowStatus.NEW -> Res.string.import_status_new
            ImportRowStatus.UNCHANGED -> Res.string.import_status_unchanged
        },
    )
    // The row shows only what changes: the old value, if there is one, and the new value.
    val playerLabel = { emaId: String -> playerNameOf(emaId)?.let { "$it · $emaId" } ?: "EMA $emaId" }
    val oldPlayer = row.current?.let { current -> current.emaId?.let(playerLabel) }
    val newPlayer = row.emaId?.takeIf { it != row.current?.emaId }?.let(playerLabel)
    // An account that has an email keeps it. The issue list warns about a different email.
    val email = row.email?.takeIf { row.current?.email.isNullOrBlank() }
    val team = row.team?.takeIf { it != row.current?.teamName.orEmpty() }
    fun change(old: String?, new: String) = if (old.isNullOrBlank()) new else "$old → $new"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(Res.string.import_row_title, row.row, row.slotId?.toString() ?: "?"),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(statusText, style = MaterialTheme.typography.labelLarge, color = statusColor)
        }
        if (row.status != ImportRowStatus.ERROR) {
            newPlayer?.let { Text(change(oldPlayer, it)) }
            team?.let { Text(stringResource(Res.string.import_team_value, change(row.current?.teamName, it)), style = MaterialTheme.typography.bodySmall) }
            email?.let { Text(stringResource(Res.string.import_email_value, change(row.current?.email, it)), style = MaterialTheme.typography.bodySmall) }
        }
        if (row.status != ImportRowStatus.ERROR && row.createsAccount) {
            Text(stringResource(Res.string.import_creates_account), style = MaterialTheme.typography.bodySmall)
        }
        // The old and the new value show the replaced player already.
        row.issues.filter { it.code != ImportIssueCode.REPLACES_PLAYER }.forEach { issue ->
            Text(
                issue.toUiText().asString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (issue.severity == ImportIssueSeverity.ERROR) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ImportResultContent(result: PlayerImportResult) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.import_result_assigned, result.assigned))
        Text(stringResource(Res.string.import_result_emails, result.emailsSaved))
        if (result.emailsRefused > 0) Text(stringResource(Res.string.import_result_emails_refused, result.emailsRefused))
        Text(stringResource(Res.string.import_result_teams, result.teamsRenamed))
        Text(stringResource(Res.string.import_result_skipped, result.skipped))
        if (result.failures.isNotEmpty()) {
            Text(stringResource(Res.string.import_result_failures), color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(result.failures) { failure ->
                    Text(
                        stringResource(Res.string.import_failure_line, failure.row?.toString() ?: "?", failure.message),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun ImportIssue.toUiText(): UiText {
    val first = args.getOrElse(0) { "" }
    val second = args.getOrElse(1) { "" }
    return when (code) {
        ImportIssueCode.INVALID_ID -> UiText.of(Res.string.import_issue_invalid_id)
        ImportIssueCode.UNKNOWN_SLOT -> UiText.of(Res.string.import_issue_unknown_slot, first)
        ImportIssueCode.DUPLICATE_SLOT -> UiText.of(Res.string.import_issue_duplicate_slot, first)
        ImportIssueCode.MISSING_EMA_ID -> UiText.of(Res.string.import_issue_missing_ema)
        ImportIssueCode.INVALID_EMA_ID -> UiText.of(Res.string.import_issue_invalid_ema, first)
        ImportIssueCode.UNKNOWN_EMA_ID -> UiText.of(Res.string.import_issue_unknown_ema, first)
        ImportIssueCode.DUPLICATE_EMA_ID -> UiText.of(Res.string.import_issue_duplicate_ema, first)
        ImportIssueCode.EMA_ASSIGNED_ELSEWHERE -> UiText.of(Res.string.import_issue_ema_elsewhere, first)
        ImportIssueCode.INVALID_EMAIL -> UiText.of(Res.string.import_issue_invalid_email, first)
        ImportIssueCode.TEAM_NAME_TOO_LONG -> UiText.of(Res.string.import_issue_team_too_long)
        ImportIssueCode.TEAM_NAME_CONFLICT -> UiText.of(Res.string.import_issue_team_conflict)
        ImportIssueCode.TEAM_NAME_IN_USE -> UiText.of(Res.string.import_issue_team_in_use, first)
        ImportIssueCode.REPLACES_PLAYER -> UiText.of(Res.string.import_issue_replaces_player)
        ImportIssueCode.EMAIL_KEPT_ACCOUNT -> UiText.of(Res.string.import_issue_email_kept, first, second)
        ImportIssueCode.EMAIL_IN_USE ->
            if (first.isBlank()) UiText.of(Res.string.import_issue_email_in_use_no_ema) else UiText.of(Res.string.import_issue_email_in_use, first)
        ImportIssueCode.TEAM_IGNORED -> UiText.of(Res.string.import_issue_team_ignored)
        ImportIssueCode.UNKNOWN -> UiText.of(Res.string.import_issue_unknown)
    }
}

/** Gives the action that downloads the players file of the tournament, with no dialog. */
@Composable
fun rememberPlayersExport(tournamentId: String, onError: (UiText) -> Unit): () -> Unit {
    val presenter = koinInject<PlayerImportPresenter>()
    val scope = rememberCoroutineScope()
    val saver = rememberSpreadsheetSaver(onError)
    val store = koinInject<AppMemoryStore>()
    val tournaments by store.tournaments.collectAsState()
    return {
        scope.launch {
            when (val result = presenter.export(tournamentId)) {
                is AppResult.Success -> {
                    // The name has the short name of the tournament and the time, like the other generated files.
                    val tournament = tournaments.firstOrNull { it.id == tournamentId }
                    val baseName = tournament?.shortName?.ifBlank { tournament.name.take(10) } ?: "tournament"
                    val year = tournament?.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
                    saver.save(generatedFileName(baseName, "players", "xlsx", Clock.System.now(), year = year), result.value.bytes)
                }
                is AppResult.Failure -> onError(result.error.toUiText())
            }
        }
    }
}
