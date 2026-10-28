package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportField
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssue
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRemovedPlayer
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.field
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppPlainTooltip
import com.etologic.mahjongtournamentsuite.presentation.components.rememberAppTooltipPositionProvider
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
import mahjongtournamentsuite.adminapp.generated.resources.team
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.player
import mahjongtournamentsuite.adminapp.generated.resources.id
import mahjongtournamentsuite.adminapp.generated.resources.import_tip_removed
import mahjongtournamentsuite.adminapp.generated.resources.import_tip_changed
import mahjongtournamentsuite.adminapp.generated.resources.import_summary_line
import mahjongtournamentsuite.adminapp.generated.resources.import_state_warning
import mahjongtournamentsuite.adminapp.generated.resources.import_state_unchanged
import mahjongtournamentsuite.adminapp.generated.resources.import_state_removed
import mahjongtournamentsuite.adminapp.generated.resources.import_state_error
import mahjongtournamentsuite.adminapp.generated.resources.import_state_changed
import mahjongtournamentsuite.adminapp.generated.resources.import_legend_warning
import mahjongtournamentsuite.adminapp.generated.resources.import_column_player
import mahjongtournamentsuite.adminapp.generated.resources.import_legend_error
import mahjongtournamentsuite.adminapp.generated.resources.import_legend_changed
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.import_apply_count
import mahjongtournamentsuite.adminapp.generated.resources.import_applying
import mahjongtournamentsuite.adminapp.generated.resources.import_choose_other_file
import mahjongtournamentsuite.adminapp.generated.resources.import_confirm_button
import mahjongtournamentsuite.adminapp.generated.resources.import_confirm_text
import mahjongtournamentsuite.adminapp.generated.resources.import_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.import_done_title
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
import mahjongtournamentsuite.adminapp.generated.resources.import_started_blocked
import org.jetbrains.compose.resources.StringResource
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
            Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(
                    modifier = Modifier.appFocusGroup().widthIn(max = 1100.dp).fillMaxWidth(0.92f).fillMaxHeight(0.94f),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(Res.string.import_players_title), style = MaterialTheme.typography.headlineSmall)
                        Box(Modifier.weight(1f)) { ImportPreviewContent(preview = preview, playerNameOf = playerNameOf) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                            TextButton(onClick = picker::launch) { Text(stringResource(Res.string.import_choose_other_file)) }
                            Button(
                                enabled = preview.canApply,
                                onClick = { if (preview.existingPlayers > 0) confirming = true else apply(current) },
                            ) { Text(stringResource(Res.string.import_apply_count, preview.importedPlayers)) }
                        }
                    }
                }
            }
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

/** What a cell of the preview shows. The colour and the tooltip follow it. */
internal enum class ImportCellState { NORMAL, CHANGED, WARNING, ERROR, REMOVED }

private val ChangedColor = Color(0xFF2E7D32)
private val WarningColor = Color(0xFFB26A00)

@Composable
private fun ImportCellState.color(): Color = when (this) {
    ImportCellState.NORMAL -> MaterialTheme.colorScheme.onSurface
    ImportCellState.CHANGED -> ChangedColor
    ImportCellState.WARNING, ImportCellState.REMOVED -> WarningColor
    ImportCellState.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun ImportCellState.label(): String = stringResource(
    when (this) {
        ImportCellState.NORMAL -> Res.string.import_state_unchanged
        ImportCellState.CHANGED -> Res.string.import_state_changed
        ImportCellState.WARNING -> Res.string.import_state_warning
        ImportCellState.ERROR -> Res.string.import_state_error
        ImportCellState.REMOVED -> Res.string.import_state_removed
    },
)

private val IdColumnWidth = 44.dp

/**
 * The check of the file as a table of the new data. A cell is green when the import changes it, red when it is wrong,
 * and amber for a warning or a removed player. A tooltip on a coloured cell says why. Rows with errors come first.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ImportPreviewContent(
    preview: PlayerImportPreview,
    playerNameOf: (String) -> String?,
) {
    val rows = remember(preview) { preview.rows.sortedWith(compareBy({ !it.hasError }, { it.row })) }
    val changedRows = preview.rows.count { it.changes.isNotEmpty() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (preview.tournamentStarted) {
            ImportNote(Icons.Filled.ErrorOutline, stringResource(Res.string.import_started_blocked), MaterialTheme.colorScheme.error)
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.import_summary_line, preview.importedPlayers, changedRows, preview.errorCount, preview.removed.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendItem(ImportCellState.CHANGED, stringResource(Res.string.import_legend_changed))
                LegendItem(ImportCellState.ERROR, stringResource(Res.string.import_legend_error))
                LegendItem(ImportCellState.WARNING, stringResource(Res.string.import_legend_warning))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).testTag("import_table"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            stickyHeader { ImportTableHeader(showTeam = preview.isTeams && preview.hasTeamColumn) }
            items(rows, key = { it.row }) { row -> ImportRowLine(row, preview, playerNameOf) }
            items(preview.removed, key = { "removed-${it.slotId}" }) { removed ->
                ImportRemovedLine(removed, showTeam = preview.isTeams && preview.hasTeamColumn, playerNameOf = playerNameOf)
            }
        }
    }
}

@Composable
private fun LegendItem(state: ImportCellState, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(state.color().copy(alpha = 0.35f)).border(1.dp, state.color(), RoundedCornerShape(3.dp)))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ImportTableHeader(showTeam: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun Title(textRes: StringResource, modifier: Modifier) = Text(
            stringResource(textRes), modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
        Title(Res.string.id, Modifier.width(IdColumnWidth))
        Title(Res.string.import_column_player, Modifier.weight(1.3f))
        Title(Res.string.email, Modifier.weight(1.3f))
        if (showTeam) Title(Res.string.team, Modifier.weight(1f))
    }
}

/** The state of one cell, and the text of its tooltip. The issues of the cell come first, then the change. */
private data class CellInfo(val state: ImportCellState, val tip: List<UiText>)

private fun cellInfo(row: ImportRowPlan, field: ImportField): CellInfo {
    val issues = row.issues.filter { it.code.field == field }
    return when {
        issues.any { it.severity == ImportIssueSeverity.ERROR } -> CellInfo(ImportCellState.ERROR, issues.map { it.toUiText() })
        issues.isNotEmpty() -> CellInfo(ImportCellState.WARNING, issues.map { it.toUiText() })
        field in row.changes -> CellInfo(ImportCellState.CHANGED, listOf(UiText.of(Res.string.import_tip_changed)))
        else -> CellInfo(ImportCellState.NORMAL, emptyList())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportCell(info: CellInfo, modifier: Modifier, content: @Composable () -> Unit) {
    val stateLabel = info.state.label()
    val tipText = info.tip.map { it.asString() }.joinToString("\n")
    val cell = @Composable { cellModifier: Modifier ->
        Box(
            cellModifier
                .clip(RoundedCornerShape(6.dp))
                .background(info.state.color().copy(alpha = if (info.state == ImportCellState.NORMAL) 0f else 0.14f))
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .semantics(mergeDescendants = true) {
                    if (info.state != ImportCellState.NORMAL) stateDescription = "$stateLabel: $tipText"
                },
        ) { content() }
    }
    // The weight or width of the column goes on the outer box, which is the direct child of the row.
    Box(modifier) {
        if (info.state == ImportCellState.NORMAL) {
            cell(Modifier.fillMaxWidth())
        } else {
            TooltipBox(
                positionProvider = rememberAppTooltipPositionProvider(),
                tooltip = { AppPlainTooltip { Text(tipText) } },
                state = rememberTooltipState(),
                modifier = Modifier.fillMaxWidth(),
            ) { cell(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun CellText(text: String, state: ImportCellState, secondary: String? = null, strike: Boolean = false) {
    Column {
        Text(
            text.ifBlank { "—" },
            color = state.color(),
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        secondary?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}

@Composable
private fun ImportRowLine(row: ImportRowPlan, preview: PlayerImportPreview, playerNameOf: (String) -> String?) {
    val showTeam = preview.isTeams && preview.hasTeamColumn
    // A valid EMA number shows with the name of the player. A wrong one shows as the file holds it.
    val ema = row.emaId ?: row.file.emaId
    val name = row.emaId?.let(playerNameOf)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val idInfo = cellInfo(row, ImportField.ID)
        ImportCell(idInfo, Modifier.width(IdColumnWidth)) { CellText(row.slotId?.toString() ?: row.file.id, idInfo.state) }
        val playerInfo = cellInfo(row, ImportField.PLAYER)
        ImportCell(playerInfo, Modifier.weight(1.3f)) {
            CellText(name ?: ema, playerInfo.state, secondary = if (name != null) ema else null)
        }
        val emailInfo = cellInfo(row, ImportField.EMAIL)
        ImportCell(emailInfo, Modifier.weight(1.3f)) { CellText(row.file.email, emailInfo.state) }
        if (showTeam) {
            val teamInfo = cellInfo(row, ImportField.TEAM)
            ImportCell(teamInfo, Modifier.weight(1f)) { CellText(row.file.team, teamInfo.state) }
        }
    }
}

/** A player that the file leaves out. The import removes the player, and the cells are amber with a tooltip. */
@Composable
private fun ImportRemovedLine(removed: ImportRemovedPlayer, showTeam: Boolean, playerNameOf: (String) -> String?) {
    val info = CellInfo(ImportCellState.REMOVED, listOf(UiText.of(Res.string.import_tip_removed)))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ImportCell(info, Modifier.width(IdColumnWidth)) { CellText(removed.slotId.toString(), info.state, strike = true) }
        ImportCell(info, Modifier.weight(1.3f)) {
            CellText(playerNameOf(removed.emaId) ?: removed.emaId, info.state, secondary = removed.emaId.takeIf { playerNameOf(removed.emaId) != null }, strike = true)
        }
        ImportCell(info, Modifier.weight(1.3f)) { CellText("", info.state) }
        if (showTeam) ImportCell(info, Modifier.weight(1f)) { CellText("", info.state) }
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
