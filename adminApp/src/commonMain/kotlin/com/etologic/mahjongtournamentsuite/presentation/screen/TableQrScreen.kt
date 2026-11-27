package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.data.network.PublicSite
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.domain.model.myTableQrLink
import com.etologic.mahjongtournamentsuite.domain.model.physicalTableCount
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.ExportFile
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.OrganizationLogo
import com.etologic.mahjongtournamentsuite.presentation.components.dialogVerticalScroll
import com.etologic.mahjongtournamentsuite.presentation.components.rememberBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlin.time.Clock
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.create_pdf
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_card_size
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_hint
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_no_tables
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_open_app
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_per_sheet
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_print_info
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_save
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_top_bar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.MtsSegmentedButton as SegmentedButton

/** The cards per A4 sheet that the PDF offers. */
private val CARDS_PER_SHEET = listOf(1, 2, 4, 6, 8)

/** How the PDF puts the cards on an A4 sheet (sheetGrid of tableQrService.ts). Every card is landscape. */
private class SheetLayout(val columns: Int, val rows: Int, val landscape: Boolean, val cardSize: String)

private fun sheetLayout(perSheet: Int): SheetLayout = when (perSheet) {
    1 -> SheetLayout(1, 1, landscape = true, cardSize = "297 × 210 mm")
    2 -> SheetLayout(1, 2, landscape = false, cardSize = "210 × 148.5 mm")
    6 -> SheetLayout(2, 3, landscape = true, cardSize = "148.5 × 70 mm")
    8 -> SheetLayout(2, 4, landscape = false, cardSize = "105 × 74 mm")
    else -> SheetLayout(2, 2, landscape = true, cardSize = "148.5 × 105 mm")
}

/**
 * The dialog "Tables IDs" (docs/table-qr.md), in the "More" menu of the tournament, like the dialog of the ID cards. It
 * downloads a PDF with a card for each table: its number in big and the QR code of the tournament, which opens the
 * tables of the player in the player app. The "QR" button of the top bar opens the code in a window of its own.
 */
@Composable
fun TablesIdsDialog(
    tournament: Tournament,
    onDismiss: () -> Unit,
    /** The web of the players. Tests and screenshots pass a fixed one. */
    site: String = PublicSite.url,
) {
    val presenter = koinInject<PlayersPresenter>()
    val download = rememberTableQrDownload(presenter)
    TablesIdsDialogContent(
        tournament = tournament,
        site = site,
        onDownload = { perSheet ->
            onDismiss()
            download(tournament, perSheet)
        },
        onDismiss = onDismiss,
    )
}

/** Starts the PDF of the cards as a background export: a card at the bottom saves it when it is ready. */
@Composable
private fun rememberTableQrDownload(presenter: PlayersPresenter): (Tournament, Int) -> Unit {
    val backgroundExports = rememberBackgroundExports()
    val saveTitle = stringResource(Res.string.table_qr_save)
    return { tournament, perSheet ->
        backgroundExports.start("table-qr:${tournament.id}:$perSheet", UiText.of(Res.string.table_qr_top_bar)) {
            exportTableQr(presenter, tournament, perSheet, saveTitle)
        }
    }
}

private suspend fun exportTableQr(
    presenter: PlayersPresenter,
    tournament: Tournament,
    perSheet: Int,
    saveTitle: String,
): AppResult<ExportFile> = presenter.generateTableQr(tournament.id, perSheet).map { content ->
    val baseName = tournament.shortName.ifBlank { tournament.name.take(10) }
    val year = tournament.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
    ExportFile(generatedFileName(baseName, "table-qr", "pdf", Clock.System.now(), year = year), content, "application/pdf", saveTitle)
}

@Composable
internal fun TablesIdsDialogContent(
    tournament: Tournament,
    site: String,
    onDownload: (perSheet: Int) -> Unit,
    onDismiss: () -> Unit,
    /** The cards per sheet chosen at first. The screenshots use it. */
    initialPerSheet: Int = 4,
) {
    val tableCount = tournament.takeUnless { it.drawPending }?.physicalTableCount ?: 0
    var perSheet by rememberSaveable(tournament.id) { mutableIntStateOf(initialPerSheet) }
    val compact = LocalCompactLayout.current
    val link = tournament.myTableQrLink(site)
    TrackedDialog("CreateTablesIds") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.table_qr_top_bar)) },
            text = {
                Column(
                    modifier = Modifier.dialogVerticalScroll(),
                    verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
                ) {
                    if (tableCount == 0) {
                        Text(stringResource(Res.string.table_qr_no_tables), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(Res.string.table_qr_print_info, tableCount), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(Res.string.table_qr_per_sheet),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("table_qr_per_sheet")) {
                            CARDS_PER_SHEET.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = perSheet == option,
                                    onClick = { perSheet = option },
                                    shape = SegmentedButtonDefaults.itemShape(index, CARDS_PER_SHEET.size),
                                    icon = {},
                                    label = { Text(option.toString()) },
                                )
                            }
                        }
                        val layout = sheetLayout(perSheet)
                        Text(
                            stringResource(Res.string.table_qr_card_size, layout.cardSize),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("table_qr_card_size"),
                        )
                        SheetPreview(
                            tournament = tournament,
                            link = link,
                            layout = layout,
                            tableCount = tableCount,
                            modifier = Modifier.align(Alignment.CenterHorizontally).width(
                                when {
                                    !layout.landscape && compact -> 180.dp
                                    !layout.landscape -> 220.dp
                                    compact -> 260.dp
                                    else -> 320.dp
                                },
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        enabled = tableCount > 0,
                        onClick = { onDownload(perSheet) },
                        modifier = Modifier.testTag("table_qr_download"),
                    ) {
                        ButtonText(stringResource(Res.string.create_pdf))
                    }
                    FocusedTextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** The first sheet of the PDF, drawn like tableQrService.ts draws it: an A4 with 1, 2 or 4 cards. */
@Composable
private fun SheetPreview(tournament: Tournament, link: String, layout: SheetLayout, tableCount: Int, modifier: Modifier) {
    val columns = layout.columns
    val rows = layout.rows
    Column(
        modifier
            // One and four cards go on a landscape sheet, two on a portrait one: every card is landscape.
            .aspectRatio(if (layout.landscape) 1f / A4_RATIO else A4_RATIO)
            .shadow(4.dp, RectangleShape)
            .background(Color.White)
            .testTag("table_qr_preview"),
    ) {
        repeat(rows) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(columns) { column ->
                    val tableId = row * columns + column + 1
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .border(Dp.Hairline, Color.LightGray),
                    ) {
                        if (tableId <= tableCount) PreviewCard(tournament, link, tableId)
                    }
                }
            }
        }
    }
}

/** One card of the preview: the logo and the name, "Table" and its number (always in English), and the code. */
@Composable
private fun PreviewCard(tournament: Tournament, link: String, tableId: Int) {
    val tournamentName = tournament.name
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val unit = minOf(maxWidth, maxHeight) / 20
        val wide = maxWidth > maxHeight * 1.2f
        val number = @Composable { numberModifier: Modifier ->
            Column(numberModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    TABLE_WORD,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = (unit.value * 1.4f).sp,
                    lineHeight = (unit.value * 1.5f).sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = unit * 0.4f),
                )
                Text(
                    tableId.toString(),
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = (unit.value * (if (wide) 10f else 5.5f)).sp,
                    lineHeight = (unit.value * (if (wide) 10.2f else 5.7f)).sp,
                    maxLines = 1,
                )
            }
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(unit * 2.8f).padding(start = unit, end = unit, top = unit * 0.6f), contentAlignment = Alignment.Center) {
                // Without a logo, the circle of the color of the organizer with its first letter, like the PDF.
                OrganizationLogo(
                    name = tournament.mainOrganizerName(),
                    logoUrl = tournament.associationLogoUrl,
                    primaryColor = tournament.primaryColor,
                    size = unit * 1.8f,
                    modifier = Modifier.align(Alignment.CenterStart).testTag("table_qr_preview_logo"),
                )
                Text(
                    tournamentName,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = (unit.value * 0.95f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = unit * 2.2f),
                )
            }
            val painter = rememberQrCodePainter(link)
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(unit), verticalAlignment = Alignment.CenterVertically) {
                    number(Modifier.weight(1f))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(HINT_TEXT, color = Color.Black, fontSize = (unit.value * 0.7f).sp, lineHeight = (unit.value * 0.8f).sp, textAlign = TextAlign.Center)
                        Text(
                            OPEN_APP_TEXT,
                            color = Color.DarkGray,
                            fontSize = (unit.value * 0.45f).sp,
                            lineHeight = (unit.value * 0.55f).sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = unit * 0.3f),
                        )
                        Text(
                            link.mainSite(),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = (unit.value * 0.55f).sp,
                            lineHeight = (unit.value * 0.65f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Image(painter, contentDescription = null, Modifier.padding(top = unit * 0.3f).aspectRatio(1f))
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().padding(unit), horizontalAlignment = Alignment.CenterHorizontally) {
                    number(Modifier.weight(0.42f))
                    Image(painter, contentDescription = null, Modifier.weight(0.58f).aspectRatio(1f))
                }
            }
        }
    }
}

private const val A4_RATIO = 210f / 297f
private const val DEFAULT_ORGANIZER_COLOR = "#02B16B"

/** The name of the main organizer, for the initial of its logo when it has none. */
internal fun Tournament.mainOrganizerName(): String = organizers.firstOrNull()?.name ?: name

/** The cards of the tables are always in English, like the PDF (tableQrService.ts). */
private const val TABLE_WORD = "TABLE"
private const val HINT_TEXT = "Scan the code to go to your table."
private const val OPEN_APP_TEXT = "Or just open the app:"

/**
 * The code of the tournament as big as the window allows, to project it (docs/table-qr.md). Always black on white,
 * also in the dark theme: phones read it best, and a projector shows white brighter.
 */
@Composable
fun TableQrProjectionScreen(
    tournamentName: String,
    link: String,
    logoUrl: String? = null,
    /** Without a logo, the circle shows the first letter of the organizer on its color, like the rest of the app. */
    organizerName: String = "",
    organizerColor: String = "",
) {
    // Edge to edge: the background fills the whole window, the content stays clear of the system bars and the cutout.
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(MtsTheme.spacing.xl)) {
        val roomWidth = maxWidth
        val roomHeight = maxHeight
        val wide = roomWidth > roomHeight * 1.3f
        val titleStyle = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xl)) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
                ) {
                    ProjectionLogo(organizerName.ifBlank { tournamentName }, logoUrl, organizerColor, 160.dp)
                    ProjectionTexts(tournamentName, link, titleStyle, TextAlign.Center)
                }
                QrCode(link, Modifier.size(minOf(roomHeight, roomWidth * 0.55f)).testTag("table_qr_projection_code"))
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg, Alignment.CenterVertically)) {
                ProjectionLogo(organizerName.ifBlank { tournamentName }, logoUrl, organizerColor, 96.dp)
                Text(tournamentName, style = titleStyle, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                    QrCode(link, Modifier.size(minOf(roomWidth, roomHeight * 0.7f)).testTag("table_qr_projection_code"))
                }
                ProjectionHint(link, TextAlign.Center)
            }
        }
    }
}

/** The logo of the main organizer over the name, or its initial on its color when it has no logo. */
@Composable
private fun ProjectionLogo(organizerName: String, logoUrl: String?, organizerColor: String, size: Dp) {
    OrganizationLogo(
        name = organizerName,
        logoUrl = logoUrl,
        primaryColor = organizerColor.ifBlank { DEFAULT_ORGANIZER_COLOR },
        size = size,
        modifier = Modifier.testTag("table_qr_projection_logo"),
    )
}

@Composable
private fun ProjectionTexts(tournamentName: String, link: String, titleStyle: TextStyle, align: TextAlign) {
    Text(tournamentName, style = titleStyle, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = align, maxLines = 3, overflow = TextOverflow.Ellipsis)
    ProjectionHint(link, align)
}

@Composable
private fun ProjectionHint(link: String, align: TextAlign) {
    Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        Text(stringResource(Res.string.table_qr_hint), style = MaterialTheme.typography.headlineSmall, color = Color.Black, textAlign = align, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MtsTheme.spacing.sm))
        Text(stringResource(Res.string.table_qr_open_app), style = MaterialTheme.typography.titleMedium, color = Color.DarkGray, textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(link.mainSite(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

/** Only the host of the link ("mts.mahjongmadrid.com"), easier to type than the full path to the tournament. */
internal fun String.mainSite(): String = substringAfter("://").substringBefore("/")

/** The code on white with a margin around it, also in the dark theme: phones read black on white best. */
@Composable
private fun QrCode(link: String, modifier: Modifier) {
    val painter = rememberQrCodePainter(link)
    Box(
        modifier.background(Color.White, MaterialTheme.shapes.small).padding(MtsTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter, contentDescription = link, Modifier.fillMaxSize())
    }
}
