package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.domain.model.physicalTableCount
import com.etologic.mahjongtournamentsuite.presentation.components.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.EmaLogo
import com.etologic.mahjongtournamentsuite.presentation.components.ExportFile
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.GongIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsRadioRow
import com.etologic.mahjongtournamentsuite.presentation.components.OrganizationLogo
import com.etologic.mahjongtournamentsuite.presentation.components.dialogVerticalScroll
import com.etologic.mahjongtournamentsuite.presentation.components.rememberBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.create_pdf
import mahjongtournamentsuite.adminapp.generated.resources.icon_chicken
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_east
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_north
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_south
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_west
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

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

internal suspend fun exportScoreSheets(
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
                    ScoreSheetPreview(
                        tournament = tournament,
                        perTable = perTable,
                        emaLogo = emaLogo,
                        modifier = Modifier
                            .padding(top = MtsTheme.spacing.sm)
                            .align(Alignment.CenterHorizontally)
                            .width(if (LocalCompactLayout.current) 230.dp else 300.dp),
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

/**
 * The first sheet of the PDF, drawn as scoreSheetsService.ts draws it, in the same proportions: the short name, the round
 * and the table, the seats, the 16 hands with the chicken hand column when the tournament counts them, the Sum 0 boxes,
 * the table points and signatures, and the logos.
 */
@Composable
private fun ScoreSheetPreview(tournament: Tournament, perTable: Boolean, emaLogo: Boolean, modifier: Modifier) {
    val sheet = remember(tournament.countChickenHands) { ScoreSheetLayout(tournament.countChickenHands) }
    BoxWithConstraints(
        modifier
            .aspectRatio(A4_WIDTH / A4_HEIGHT)
            .shadow(4.dp, RectangleShape)
            .background(Color.White)
            .testTag("score_sheets_preview"),
    ) {
        // One point of the PDF in dp of the preview.
        val pt = maxWidth / A4_WIDTH
        val density = LocalDensity.current
        fun textSize(points: Float) = with(density) { (pt * points).toSp() }
        Canvas(Modifier.fillMaxSize()) { drawSheetLines(sheet, size.width / A4_WIDTH) }

        // The short name, the round (gong) and the table, with the numbers of the first sheet when they are written.
        val name = tournament.shortName.ifBlank { tournament.name }
        Box(
            Modifier.offset(pt * ScoreSheetLayout.MARGIN, pt * ScoreSheetLayout.MARGIN).size(pt * sheet.titleWidth, pt * (sheet.headerBottom - ScoreSheetLayout.MARGIN - 4f)),
            contentAlignment = Alignment.Center,
        ) {
            // In the font of the titles of the app, as the PDF.
            Text(
                name,
                color = Color.Black,
                fontFamily = MaterialTheme.typography.headlineSmall.fontFamily,
                fontSize = textSize(if (name.length > 8) 14f else 22f),
                lineHeight = textSize(if (name.length > 8) 16f else 25f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        listOf(GongIcon, Icons.Default.TableRestaurant).forEachIndexed { index, icon ->
            val boxTop = sheet.boxTops[index]
            Icon(
                icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.offset(pt * sheet.boxesLeft, pt * (boxTop + 1.5f)).size(pt * 17f),
            )
            if (perTable) {
                Box(
                    Modifier.offset(pt * (sheet.boxesLeft + 21f), pt * boxTop).size(pt * 34f, pt * 20f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("1", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = textSize(14f), lineHeight = textSize(15f))
                }
            }
        }

        // The winds over the seats, and the chicken over its column.
        WIND_ICONS.forEachIndexed { seat, wind ->
            Image(
                painterResource(wind),
                contentDescription = null,
                modifier = Modifier.offset(pt * (sheet.seatsLeft + sheet.seatWidth * seat + sheet.seatWidth / 2 - 7.5f), pt * (ScoreSheetLayout.MARGIN + 4f)).size(pt * 15f),
            )
        }
        if (sheet.chicken > 0f) {
            Image(
                painterResource(Res.drawable.icon_chicken),
                contentDescription = null,
                modifier = Modifier.offset(pt * (sheet.chickenLeft + (sheet.chicken - 22f) / 2), pt * (sheet.titlesTop + 4f)).size(pt * 22f),
            )
        }

        // The logo of the organizer, always, and the one of the EMA when chosen.
        val logoSize = pt * (ScoreSheetLayout.FOOTER_HEIGHT - 4f)
        Row(
            Modifier.offset(pt * (ScoreSheetLayout.MARGIN + 2f), pt * (sheet.footerTop + 2f)),
            horizontalArrangement = Arrangement.spacedBy(pt * 8f),
        ) {
            OrganizationLogo(
                name = tournament.mainOrganizerName(),
                logoUrl = tournament.associationLogoUrl,
                primaryColor = tournament.primaryColor,
                size = logoSize,
                modifier = Modifier.testTag("score_sheets_preview_logo"),
            )
            if (emaLogo) EmaLogo(logoSize, Modifier.testTag("score_sheets_preview_ema"))
        }
    }
}

private val WIND_ICONS = listOf(Res.drawable.icon_wind_east, Res.drawable.icon_wind_south, Res.drawable.icon_wind_west, Res.drawable.icon_wind_north)

private const val A4_WIDTH = 595.28f
private const val A4_HEIGHT = 841.89f
private const val HANDS = 16

/** The positions of scoreSheetsService.ts, in points of the A4 sheet. */
private class ScoreSheetLayout(chickenHands: Boolean) {
    private val id = if (chickenHands) 8f else 8f + 5f / 3f
    private val unitsTotal = 3.25f + id * 3 + (if (chickenHands) 5f else 0f) + (8.88f + 4.63f) * 4 + 5f
    private val unit = (A4_WIDTH - MARGIN * 2) / unitsTotal
    val number = 3.25f * unit
    val idWidth = id * unit
    val chicken = if (chickenHands) 5f * unit else 0f
    val score = 8.88f * unit
    val seatWidth = (8.88f + 4.63f) * unit
    val check = 5f * unit
    val left = MARGIN
    val chickenLeft = left + number + idWidth * 3
    val seatsLeft = chickenLeft + chicken
    val checkLeft = seatsLeft + seatWidth * 4
    val right = checkLeft + check

    // The header is measured on the columns with the chicken hands, so it stays the same on every sheet.
    private val headerUnit = (A4_WIDTH - MARGIN * 2) / (3.25f + 8f * 3 + 5f + (8.88f + 4.63f) * 4 + 5f)
    val titleWidth = (3.25f + 8f + 8f * 0.55f) * headerUnit
    val boxesLeft = left + titleWidth + 4f
    val boxTops = listOf(MARGIN + 6f, MARGIN + 6f + 20f + 8f)
    val headerBottom = MARGIN + WIND_ROW + NAME_ROW * 2

    val titlesTop = headerBottom + 5f
    val handsTop = titlesTop + 30f
    private val handsHeight = A4_HEIGHT - MARGIN - FOOTER_HEIGHT - 6f - handsTop
    val lineHeight = handsHeight / (HANDS * 2 - 1)
    val handTops = List(HANDS) { index -> handsTop + if (index == 0) 0f else lineHeight * (index * 2 - 1) }
    val handsBottom = handsTop + handsHeight
    val footerTop = handsBottom + 6f

    companion object {
        const val MARGIN = 14.17f
        const val WIND_ROW = 24f
        const val NAME_ROW = 19f
        const val FOOTER_HEIGHT = 52f
    }
}

/** The lines of the sheet, [k] pixels for each point. */
private fun DrawScope.drawSheetLines(sheet: ScoreSheetLayout, k: Float) {
    val thin = (0.6f * k).coerceAtLeast(0.6f)
    val thick = (1.6f * k).coerceAtLeast(1f)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = thin) = drawLine(Color.Black, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), strokeWidth = width)
    fun rect(x: Float, y: Float, w: Float, h: Float, width: Float) = drawRect(Color.Black, Offset(x * k, y * k), Size(w * k, h * k), style = Stroke(width))

    // The boxes of the round and the table.
    sheet.boxTops.forEach { top -> rect(sheet.boxesLeft + 21f, top, 34f, 20f, (1.2f * k).coerceAtLeast(1f)) }
    // The name and id of each seat.
    val namesTop = ScoreSheetLayout.MARGIN + ScoreSheetLayout.WIND_ROW
    rect(sheet.seatsLeft, namesTop, sheet.seatWidth * 4, ScoreSheetLayout.NAME_ROW * 2, thick)
    line(sheet.seatsLeft, namesTop + ScoreSheetLayout.NAME_ROW, sheet.seatsLeft + sheet.seatWidth * 4, namesTop + ScoreSheetLayout.NAME_ROW)
    for (seat in 1..3) line(sheet.seatsLeft + sheet.seatWidth * seat, namesTop, sheet.seatsLeft + sheet.seatWidth * seat, namesTop + ScoreSheetLayout.NAME_ROW * 2, thick)

    // The columns of the hands.
    var x = sheet.left + sheet.number
    repeat(3) {
        line(x, sheet.titlesTop, x, sheet.handsBottom)
        x += sheet.idWidth
    }
    if (sheet.chicken > 0f) line(sheet.chickenLeft, sheet.titlesTop, sheet.chickenLeft, sheet.handsBottom)
    for (seat in 0..3) {
        val scoreRight = sheet.seatsLeft + sheet.seatWidth * seat + sheet.score
        line(scoreRight, sheet.titlesTop, scoreRight, sheet.handsTop)
    }
    sheet.handTops.forEachIndexed { index, top ->
        for (seat in 0..3) {
            val scoreRight = sheet.seatsLeft + sheet.seatWidth * seat + sheet.score
            line(scoreRight, top, scoreRight, top + sheet.lineHeight)
        }
        if (index > 0) {
            line(sheet.seatsLeft, top + sheet.lineHeight, sheet.checkLeft, top + sheet.lineHeight)
            line(sheet.left, top, sheet.right, top, if (index % 4 == 0) thick else thin)
        }
        // The boxes to tick: chicken hand and Sum 0.
        val height = if (index == 0) sheet.lineHeight else sheet.lineHeight * 2
        val box = minOf(10f, sheet.lineHeight * 0.55f)
        val centers = listOfNotNull(sheet.chickenLeft + sheet.chicken / 2 takeIf (sheet.chicken > 0f), sheet.checkLeft + sheet.check / 2)
        centers.forEach { center -> rect(center - box / 2, top + height / 2 - box / 2, box, box, thin) }
    }
    rect(sheet.left, sheet.titlesTop, sheet.right - sheet.left, sheet.handsBottom - sheet.titlesTop, thick)
    line(sheet.left, sheet.handsTop, sheet.right, sheet.handsTop, thick)
    for (seat in 0..4) line(sheet.seatsLeft + sheet.seatWidth * seat, sheet.titlesTop, sheet.seatsLeft + sheet.seatWidth * seat, sheet.handsBottom, thick)

    // The table points and the signatures.
    rect(sheet.seatsLeft, sheet.footerTop, sheet.seatWidth * 4, ScoreSheetLayout.FOOTER_HEIGHT, thick)
    line(sheet.seatsLeft, sheet.footerTop + 22f, sheet.seatsLeft + sheet.seatWidth * 4, sheet.footerTop + 22f)
    for (seat in 1..3) line(sheet.seatsLeft + sheet.seatWidth * seat, sheet.footerTop, sheet.seatsLeft + sheet.seatWidth * seat, sheet.footerTop + ScoreSheetLayout.FOOTER_HEIGHT, thick)
}

private infix fun Float.takeIf(condition: Boolean): Float? = if (condition) this else null
