package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import mahjongtournamentsuite.adminapp.generated.resources.import_creates_account
import mahjongtournamentsuite.adminapp.generated.resources.import_done_title
import mahjongtournamentsuite.adminapp.generated.resources.import_failure_line
import mahjongtournamentsuite.adminapp.generated.resources.import_field_email
import mahjongtournamentsuite.adminapp.generated.resources.import_field_player
import mahjongtournamentsuite.adminapp.generated.resources.import_field_team
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
import mahjongtournamentsuite.adminapp.generated.resources.import_untouched_slots
import org.jetbrains.compose.resources.StringResource
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
    // A tap on a summary tile shows only the rows of that status. A second tap shows all rows again.
    var filter by remember(preview) { mutableStateOf<ImportRowStatus?>(null) }
    // Errors come first, so the editor sees the rows to fix before the list of good rows.
    val rows = remember(preview, filter) {
        preview.rows
            .filter { it.status != ImportRowStatus.UNCHANGED && (filter == null || it.status == filter) }
            .sortedWith(compareBy({ it.status.sortOrder() }, { it.row }))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ImportRowStatus.NEW to preview.newCount,
                ImportRowStatus.REPLACE to preview.replaceCount,
                ImportRowStatus.ERROR to preview.errorCount,
            ).forEach { (status, count) ->
                ImportSummaryTile(
                    status = status,
                    count = count,
                    selected = filter == status,
                    onClick = { filter = if (filter == status) null else status },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        if (preview.errorCount > 0) {
            ImportNote(
                icon = Icons.Filled.ErrorOutline,
                text = stringResource(Res.string.import_count_errors, preview.errorCount),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (preview.untouchedSlotIds.isNotEmpty()) {
            ImportNote(
                icon = Icons.Filled.Info,
                text = stringResource(Res.string.import_untouched_slots, preview.untouchedSlotIds.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (preview.replaceCount > 0) {
            ImportReplaceChoice(preview, replaceExisting, onReplaceExistingChange)
        }
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

private class StatusStyle(val icon: ImageVector, val color: Color, val container: Color, val label: StringResource)

@Composable
private fun ImportRowStatus.style(): StatusStyle {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        ImportRowStatus.ERROR -> StatusStyle(Icons.Filled.ErrorOutline, colors.error, colors.errorContainer, Res.string.import_status_error)
        ImportRowStatus.REPLACE -> StatusStyle(Icons.Filled.SwapHoriz, colors.tertiary, colors.tertiaryContainer, Res.string.import_status_replace)
        ImportRowStatus.NEW -> StatusStyle(Icons.Filled.PersonAdd, colors.primary, colors.primaryContainer, Res.string.import_status_new)
        ImportRowStatus.UNCHANGED -> StatusStyle(Icons.Filled.Check, colors.onSurfaceVariant, colors.surfaceVariant, Res.string.import_status_unchanged)
    }
}

/** A big count with the name of a status. It filters the list of rows. */
@Composable
private fun ImportSummaryTile(
    status: ImportRowStatus,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = status.style()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (count > 0) style.container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(if (selected) 2.dp else 0.dp, style.color, shape)
            .selectable(selected = selected, enabled = count > 0, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (count > 0) style.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        Icon(style.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = tint)
        Text(stringResource(style.label), style = MaterialTheme.typography.labelMedium, color = tint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ImportNote(icon: ImageVector, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun ImportReplaceChoice(preview: PlayerImportPreview, replaceExisting: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (preview.tournamentStarted) {
            ImportNote(Icons.Filled.Warning, stringResource(Res.string.import_started_warning), MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.toggleable(value = replaceExisting, role = Role.Checkbox, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = replaceExisting, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
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
}

@Composable
private fun ImportRowItem(row: ImportRowPlan, playerNameOf: (String) -> String?) {
    val style = row.status.style()
    val isError = row.status == ImportRowStatus.ERROR
    val playerLabel = { emaId: String -> playerNameOf(emaId)?.let { "$it · $emaId" } ?: "EMA $emaId" }
    // The row shows only what changes: the old value, if there is one, and the new value.
    // An error row shows the values of the file, so the editor finds the line to fix.
    val oldPlayer = row.current?.emaId?.takeUnless { isError }?.let(playerLabel)
    val newPlayer = row.emaId?.takeIf { isError || it != row.current?.emaId }?.let(playerLabel)
    // An account that has an email keeps it. The issue list warns about a different email.
    val email = row.email?.takeIf { isError || row.current?.email.isNullOrBlank() }
    val team = row.team?.takeIf { isError || it != row.current?.teamName.orEmpty() }
    val oldTeam = row.current?.teamName?.takeUnless { isError }
    val oldEmail = row.current?.email?.takeUnless { isError }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(style.color))
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.import_row_title, row.row, row.slotId?.toString() ?: "?"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(style.container).padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(14.dp))
                    Text(stringResource(style.label), style = MaterialTheme.typography.labelMedium, color = style.color)
                }
            }
            newPlayer?.let { ImportFieldChange(Res.string.import_field_player, oldPlayer, it) }
            team?.let { ImportFieldChange(Res.string.import_field_team, oldTeam, it) }
            email?.let { ImportFieldChange(Res.string.import_field_email, oldEmail, it) }
            if (!isError && row.createsAccount) {
                ImportNote(Icons.Filled.PersonAdd, stringResource(Res.string.import_creates_account), MaterialTheme.colorScheme.primary)
            }
            // The old and the new value show the replaced player already.
            row.issues.filter { it.code != ImportIssueCode.REPLACES_PLAYER }.forEach { issue ->
                val isIssueError = issue.severity == ImportIssueSeverity.ERROR
                ImportNote(
                    icon = if (isIssueError) Icons.Filled.ErrorOutline else Icons.Filled.Warning,
                    text = issue.toUiText().asString(),
                    color = if (isIssueError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A field with its name, the old value in grey and struck out, an arrow and the new value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportFieldChange(label: StringResource, old: String?, new: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp).padding(top = 3.dp),
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (!old.isNullOrBlank()) {
                Text(
                    old,
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(new, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun ImportResultContent(result: PlayerImportResult) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ImportResultLine(Icons.Filled.PersonAdd, stringResource(Res.string.import_result_assigned, result.assigned), MaterialTheme.colorScheme.primary)
        ImportResultLine(Icons.Filled.Email, stringResource(Res.string.import_result_emails, result.emailsSaved), MaterialTheme.colorScheme.primary)
        if (result.emailsRefused > 0) {
            ImportResultLine(Icons.Filled.Warning, stringResource(Res.string.import_result_emails_refused, result.emailsRefused), MaterialTheme.colorScheme.tertiary)
        }
        ImportResultLine(Icons.Filled.Groups, stringResource(Res.string.import_result_teams, result.teamsRenamed), MaterialTheme.colorScheme.primary)
        ImportResultLine(Icons.Filled.SkipNext, stringResource(Res.string.import_result_skipped, result.skipped), MaterialTheme.colorScheme.onSurfaceVariant)
        if (result.failures.isNotEmpty()) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ImportNote(Icons.Filled.ErrorOutline, stringResource(Res.string.import_result_failures), MaterialTheme.colorScheme.error)
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
