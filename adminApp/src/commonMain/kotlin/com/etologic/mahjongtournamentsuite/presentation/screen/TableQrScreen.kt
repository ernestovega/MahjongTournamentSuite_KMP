package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.data.network.PublicSite
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.domain.model.myTableQrLink
import com.etologic.mahjongtournamentsuite.domain.model.physicalTableCount
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ExportFile
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.rememberBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.platform.openTableQrWindow
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_download
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_hint
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_info
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_no_tables
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_per_sheet
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_print_info
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_print_title
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_project
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_save
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_top_bar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/** The cards per A4 sheet that the PDF offers. */
private val CARDS_PER_SHEET = listOf(1, 2, 4)

/**
 * The QR code of a tournament (docs/table-qr.md). One code for all the tables: it opens the tables of the player in
 * the player app, with their table in play, or the next one, open. The screen shows it, opens it in a window of its
 * own to project it, and downloads a PDF with a card for each table (its number in big and the code), 1, 2 or 4 per
 * sheet.
 */
@Composable
fun TableQrScreen(
    tournamentId: String,
    onBack: () -> Unit,
    /** The web of the players. Tests and screenshots pass a fixed one. */
    site: String = PublicSite.url,
) {
    val store = koinInject<AppMemoryStore>()
    val presenter = koinInject<PlayersPresenter>()
    LoadMissingSession()
    val tournaments by store.tournaments.collectAsState()
    val tournament = tournaments.firstOrNull { it.id == tournamentId }
    TableQrContent(
        tournament = tournament,
        site = site,
        onBack = onBack,
        onDownload = rememberTableQrDownload(presenter),
        onProject = { name, link -> openTableQrWindow(name, link) },
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
internal fun TableQrContent(
    tournament: Tournament?,
    site: String,
    onBack: () -> Unit,
    onDownload: (Tournament, perSheet: Int) -> Unit,
    onProject: (tournamentName: String, link: String) -> Unit,
) {
    val tableCount = tournament?.takeUnless { it.drawPending }?.physicalTableCount ?: 0
    var perSheet by rememberSaveable(tournament?.id) { mutableIntStateOf(4) }
    val compact = LocalCompactLayout.current
    AppScaffold(
        title = stringResource(Res.string.table_qr_top_bar),
        subtitle = tournament?.name,
        isLoading = tournament == null,
        onBack = onBack,
    ) {
        if (tournament == null) return@AppScaffold
        val link = tournament.myTableQrLink(site)
        ScreenColumn(scrollable = true, maxWidth = 640.dp, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg)) {
            Text(stringResource(Res.string.table_qr_info), style = MaterialTheme.typography.bodyMedium)
            SectionCard(title = tournament.name, centerTitle = true) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
                    QrCode(link, Modifier.size(if (compact) 220.dp else 280.dp).testTag("table_qr_code"))
                    Text(
                        link.substringAfter("://"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    FocusedButton(onClick = { onProject(tournament.name, link) }, modifier = Modifier.testTag("table_qr_project")) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(MtsTheme.spacing.sm))
                        Text(stringResource(Res.string.table_qr_project))
                    }
                }
            }
            SectionCard(title = stringResource(Res.string.table_qr_print_title)) {
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
                                label = { Text(option.toString()) },
                            )
                        }
                    }
                    FocusedOutlinedButton(
                        onClick = { onDownload(tournament, perSheet) },
                        modifier = Modifier.fillMaxWidth().testTag("table_qr_download"),
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(MtsTheme.spacing.sm))
                        Text(stringResource(Res.string.table_qr_download))
                    }
                }
            }
        }
    }
}

/**
 * The code of the tournament as big as the window allows, to project it (docs/table-qr.md). Always black on white,
 * also in the dark theme: phones read it best, and a projector shows white brighter.
 */
@Composable
fun TableQrProjectionScreen(tournamentName: String, link: String) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White).padding(MtsTheme.spacing.xl)) {
        val roomWidth = maxWidth
        val roomHeight = maxHeight
        val wide = roomWidth > roomHeight * 1.3f
        val titleStyle = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xl)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg)) {
                    ProjectionTexts(tournamentName, link, titleStyle, TextAlign.Start)
                }
                QrCode(link, Modifier.size(minOf(roomHeight, roomWidth * 0.55f)).testTag("table_qr_projection_code"))
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg, Alignment.CenterVertically)) {
                Text(tournamentName, style = titleStyle, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                    QrCode(link, Modifier.size(minOf(roomWidth, roomHeight * 0.7f)).testTag("table_qr_projection_code"))
                }
                ProjectionHint(link, TextAlign.Center)
            }
        }
    }
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
        Text(link.substringAfter("://"), style = MaterialTheme.typography.titleMedium, color = Color.DarkGray, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

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
