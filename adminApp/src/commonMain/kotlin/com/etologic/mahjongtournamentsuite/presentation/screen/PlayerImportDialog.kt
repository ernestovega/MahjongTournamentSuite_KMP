package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssue
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedSpreadsheet
import com.etologic.mahjongtournamentsuite.presentation.platform.SpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberSpreadsheetPicker
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberSpreadsheetSaver
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.import_apply_count
import mahjongtournamentsuite.adminapp.generated.resources.import_applying
import mahjongtournamentsuite.adminapp.generated.resources.import_choose_other_file
import mahjongtournamentsuite.adminapp.generated.resources.import_confirm_button
import mahjongtournamentsuite.adminapp.generated.resources.import_confirm_text
import mahjongtournamentsuite.adminapp.generated.resources.import_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.import_count_errors
import mahjongtournamentsuite.adminapp.generated.resources.import_done_title
import mahjongtournamentsuite.adminapp.generated.resources.import_existing_players
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_duplicate_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_duplicate_email
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_duplicate_slot
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_email_owned
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_invalid_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_invalid_email
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_invalid_id
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_missing_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_conflict
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_ignored
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_in_use
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_team_too_long
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_unknown
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_unknown_ema
import mahjongtournamentsuite.adminapp.generated.resources.import_issue_unknown_slot
import mahjongtournamentsuite.adminapp.generated.resources.import_players_title
import mahjongtournamentsuite.adminapp.generated.resources.import_reading_file
import mahjongtournamentsuite.adminapp.generated.resources.import_result_changed
import mahjongtournamentsuite.adminapp.generated.resources.import_result_emptied
import mahjongtournamentsuite.adminapp.generated.resources.import_result_no_accounts
import mahjongtournamentsuite.adminapp.generated.resources.import_result_players
import mahjongtournamentsuite.adminapp.generated.resources.import_row_title
import mahjongtournamentsuite.adminapp.generated.resources.import_started_blocked
import mahjongtournamentsuite.adminapp.generated.resources.import_summary_players
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/** What the dialog shows now. */
private sealed interface ImportStep {
    data object Choose : ImportStep
    data object Reading : ImportStep
    data class Preview(val file: SelectedSpreadsheet, val preview: PlayerImportPreview) : ImportStep
    data object Applying : ImportStep
    data class Done(val result: PlayerImportResult) : ImportStep
}

/**
 * Overwrites all players and teams of the tournament with a CSV or Excel file. The file chooser opens at once.
 * The editor sees the check of the file before anything changes. A file with errors cannot be applied, and a
 * tournament with table data refuses the import. When the tournament has players, the editor confirms the overwrite.
 * [playerNameOf] gives the name of a player in the EMA registry. [onChanged] runs when the import changed data.
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
    var confirming by remember { mutableStateOf(false) }

    fun preview(file: SelectedSpreadsheet) = scope.launch {
        step = ImportStep.Reading
        when (val result = presenter.preview(tournamentId, file.fileName, file.bytes)) {
            is AppResult.Success -> step = ImportStep.Preview(file, result.value)
            is AppResult.Failure -> {
                error = result.error.toUiText()
                step = ImportStep.Choose
            }
        }
    }

    fun apply(current: ImportStep.Preview) = scope.launch {
        confirming = false
        step = ImportStep.Applying
        when (val result = presenter.apply(tournamentId, current.file.fileName, current.file.bytes)) {
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

        ImportStep.Reading, ImportStep.Applying -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.import_players_title)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(if (current == ImportStep.Applying) Res.string.import_applying else Res.string.import_reading_file))
                }
            },
            confirmButton = {},
        )

        is ImportStep.Preview -> {
            val preview = current.preview
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.appFocusGroup(),
                title = { Text(stringResource(Res.string.import_players_title)) },
                text = { ImportPreviewContent(preview = preview, playerNameOf = playerNameOf) },
                confirmButton = {
                    Button(
                        enabled = preview.canApply,
                        onClick = { if (preview.existingPlayers > 0) confirming = true else apply(current) },
                    ) { Text(stringResource(Res.string.import_apply_count, preview.importedPlayers)) }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                        TextButton(onClick = picker::launch) { Text(stringResource(Res.string.import_choose_other_file)) }
                    }
                },
            )
            if (confirming) {
                AlertDialog(
                    onDismissRequest = { confirming = false },
                    modifier = Modifier.appFocusGroup(),
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text(stringResource(Res.string.import_confirm_title)) },
                    text = { Text(stringResource(Res.string.import_confirm_text, preview.existingPlayers, preview.importedPlayers)) },
                    confirmButton = { Button(onClick = { apply(current) }) { Text(stringResource(Res.string.import_confirm_button)) } },
                    dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.cancel)) } },
                )
            }
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

/** The check of the file: what the import saves, what it overwrites, and every row with a problem. */
@Composable
internal fun ImportPreviewContent(
    preview: PlayerImportPreview,
    playerNameOf: (String) -> String?,
) {
    // Errors come first, so the editor sees the rows to fix before the warnings.
    val rows = remember(preview) {
        preview.rows.filter { it.issues.isNotEmpty() }.sortedWith(compareBy({ !it.hasError }, { it.row }))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (preview.tournamentStarted) {
            ImportNote(Icons.Filled.ErrorOutline, stringResource(Res.string.import_started_blocked), MaterialTheme.colorScheme.error)
        }
        ImportNote(Icons.Filled.Info, stringResource(Res.string.import_summary_players, preview.importedPlayers), MaterialTheme.colorScheme.onSurfaceVariant)
        if (preview.errorCount > 0) {
            ImportNote(Icons.Filled.ErrorOutline, stringResource(Res.string.import_count_errors, preview.errorCount), MaterialTheme.colorScheme.error)
        } else if (preview.existingPlayers > 0 && !preview.tournamentStarted) {
            ImportNote(Icons.Filled.Warning, stringResource(Res.string.import_existing_players, preview.existingPlayers), MaterialTheme.colorScheme.tertiary)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.row }) { row -> ImportRowItem(row, playerNameOf) }
        }
    }
}

@Composable
private fun ImportNote(icon: ImageVector, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** A row of the file with a problem. It shows the values of the file, so the editor finds the line to fix. */
@Composable
private fun ImportRowItem(row: ImportRowPlan, playerNameOf: (String) -> String?) {
    val color = if (row.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.import_row_title, row.row, row.slotId?.toString() ?: "?"),
                style = MaterialTheme.typography.labelLarge,
            )
            val values = listOfNotNull(
                row.emaId?.let { emaId -> playerNameOf(emaId)?.let { "$it · $emaId" } ?: "EMA $emaId" },
                row.team,
                row.email,
            )
            if (values.isNotEmpty()) {
                Text(values.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            row.issues.forEach { issue ->
                val isError = issue.severity == ImportIssueSeverity.ERROR
                ImportNote(
                    icon = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.Warning,
                    text = issue.toUiText().asString(),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImportResultContent(result: PlayerImportResult) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ImportResultLine(Icons.Filled.PersonAdd, stringResource(Res.string.import_result_players, result.players), MaterialTheme.colorScheme.primary)
        ImportResultLine(Icons.Filled.Groups, stringResource(Res.string.import_result_changed, result.changedSlots), MaterialTheme.colorScheme.primary)
        if (result.emptiedSlots > 0) {
            ImportResultLine(Icons.Filled.SkipNext, stringResource(Res.string.import_result_emptied, result.emptiedSlots), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ImportNote(Icons.Filled.Info, stringResource(Res.string.import_result_no_accounts), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImportResultLine(icon: ImageVector, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text)
    }
}

private fun ImportIssue.toUiText(): UiText {
    val first = args.getOrElse(0) { "" }
    return when (code) {
        ImportIssueCode.INVALID_ID -> UiText.of(Res.string.import_issue_invalid_id)
        ImportIssueCode.UNKNOWN_SLOT -> UiText.of(Res.string.import_issue_unknown_slot, first)
        ImportIssueCode.DUPLICATE_SLOT -> UiText.of(Res.string.import_issue_duplicate_slot, first)
        ImportIssueCode.MISSING_EMA_ID -> UiText.of(Res.string.import_issue_missing_ema)
        ImportIssueCode.INVALID_EMA_ID -> UiText.of(Res.string.import_issue_invalid_ema, first)
        ImportIssueCode.UNKNOWN_EMA_ID -> UiText.of(Res.string.import_issue_unknown_ema, first)
        ImportIssueCode.DUPLICATE_EMA_ID -> UiText.of(Res.string.import_issue_duplicate_ema, first)
        ImportIssueCode.INVALID_EMAIL -> UiText.of(Res.string.import_issue_invalid_email, first)
        ImportIssueCode.DUPLICATE_EMAIL -> UiText.of(Res.string.import_issue_duplicate_email, first)
        ImportIssueCode.EMAIL_OWNED_BY_OTHER_ACCOUNT -> UiText.of(Res.string.import_issue_email_owned, first)
        ImportIssueCode.TEAM_NAME_TOO_LONG -> UiText.of(Res.string.import_issue_team_too_long)
        ImportIssueCode.TEAM_NAME_CONFLICT -> UiText.of(Res.string.import_issue_team_conflict)
        ImportIssueCode.TEAM_NAME_IN_USE -> UiText.of(Res.string.import_issue_team_in_use, first)
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
