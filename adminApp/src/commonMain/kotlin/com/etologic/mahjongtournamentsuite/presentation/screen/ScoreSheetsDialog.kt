package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.domain.model.physicalTableCount
import com.etologic.mahjongtournamentsuite.presentation.components.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.ExportFile
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsRadioRow
import com.etologic.mahjongtournamentsuite.presentation.components.dialogVerticalScroll
import com.etologic.mahjongtournamentsuite.presentation.components.rememberBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import kotlin.time.Clock
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.create_pdf
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_blank
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_blank_info
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_chicken_off
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_chicken_on
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_ema_logo
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_info
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_no_tables
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_save
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_tables
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_tables_info
import mahjongtournamentsuite.adminapp.generated.resources.score_sheets_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The dialog "Score sheets" (docs/score-sheets.md), in the "More" menu of the tournament: a PDF of A4 sheets to fill in
 * by hand at the tables, blank or with the round and the table written in (one for each table of each round), with
 * the logo of the organizer and, if chosen, the one of the EMA. The chicken hand column follows the tournament.
 */
@Composable
fun ScoreSheetsDialog(tournament: Tournament, onDismiss: () -> Unit) {
    val presenter = koinInject<PlayersPresenter>()
    val backgroundExports = rememberBackgroundExports()
    val saveTitle = stringResource(Res.string.score_sheets_save)
    ScoreSheetsDialogContent(
        tournament = tournament,
        onDownload = { perTable, emaLogo ->
            onDismiss()
            backgroundExports.start("score-sheets:${tournament.id}:$perTable:$emaLogo", UiText.of(Res.string.score_sheets_title)) {
                exportScoreSheets(presenter, tournament, perTable, emaLogo, saveTitle)
            }
        },
        onDismiss = onDismiss,
    )
}

private suspend fun exportScoreSheets(
    presenter: PlayersPresenter,
    tournament: Tournament,
    perTable: Boolean,
    emaLogo: Boolean,
    saveTitle: String,
): AppResult<ExportFile> = presenter.generateScoreSheets(tournament.id, perTable, emaLogo).map { content ->
    val baseName = tournament.shortName.ifBlank { tournament.name.take(10) }
    val year = tournament.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
    val kind = if (perTable) "score-sheets-tables" else "score-sheets"
    ExportFile(generatedFileName(baseName, kind, "pdf", Clock.System.now(), year = year), content, "application/pdf", saveTitle)
}

/** The number of sheets with the round and the table: one for each table of each round. */
internal fun Tournament.scoreSheetCount(): Int = physicalTableCount * numRounds

@Composable
internal fun ScoreSheetsDialogContent(
    tournament: Tournament,
    onDownload: (perTable: Boolean, emaLogo: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** The choice shown at first. The screenshots use it. */
    initialPerTable: Boolean = false,
) {
    val sheetCount = tournament.scoreSheetCount()
    var perTable by rememberSaveable(tournament.id) { mutableStateOf(initialPerTable && sheetCount > 0) }
    var emaLogo by rememberSaveable(tournament.id) { mutableStateOf(true) }
    TrackedDialog("CreateScoreSheets") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.score_sheets_title)) },
            text = {
                Column(
                    modifier = Modifier.dialogVerticalScroll(),
                    verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                ) {
                    Text(stringResource(Res.string.score_sheets_info), style = MaterialTheme.typography.bodyMedium)
                    ScoreSheetsOption(
                        title = stringResource(Res.string.score_sheets_blank),
                        info = stringResource(Res.string.score_sheets_blank_info),
                        selected = !perTable,
                        enabled = true,
                        onClick = { perTable = false },
                        tag = "score_sheets_blank",
                    )
                    ScoreSheetsOption(
                        title = stringResource(Res.string.score_sheets_tables),
                        info = if (sheetCount > 0) {
                            stringResource(Res.string.score_sheets_tables_info, sheetCount)
                        } else {
                            stringResource(Res.string.score_sheets_no_tables)
                        },
                        selected = perTable,
                        enabled = sheetCount > 0,
                        onClick = { perTable = true },
                        tag = "score_sheets_tables",
                    )
                    MtsCheckboxRow(
                        text = stringResource(Res.string.score_sheets_ema_logo),
                        checked = emaLogo,
                        onCheckedChange = { emaLogo = it },
                        modifier = Modifier.testTag("score_sheets_ema"),
                    )
                    Text(
                        stringResource(if (tournament.countChickenHands) Res.string.score_sheets_chicken_on else Res.string.score_sheets_chicken_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("score_sheets_chicken"),
                    )
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = { onDownload(perTable, emaLogo) }, modifier = Modifier.testTag("score_sheets_download")) {
                        ButtonText(stringResource(Res.string.create_pdf))
                    }
                    FocusedTextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** One choice of the sheets: a radio button with its title and, under it, what the PDF has. */
@Composable
private fun ScoreSheetsOption(title: String, info: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, tag: String) {
    MtsRadioRow(selected = selected, onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
