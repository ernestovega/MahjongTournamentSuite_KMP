package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.data.network.PublicSite
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.domain.model.physicalTableCount
import com.etologic.mahjongtournamentsuite.domain.model.tableQrLink
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.ExportFile
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformBackHandler
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.rememberBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_all
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_download
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_info
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_next
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_no_tables
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_previous
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_save
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_table
import mahjongtournamentsuite.adminapp.generated.resources.table_qr_top_bar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * The QR codes of the physical tables of a tournament (docs/table-qr.md). Each code opens its table in the player
 * app, in the round in play. The grid shows all of them; a touch shows one in big, to show it on a screen, with
 * buttons to the previous and the next table. The top bar downloads a PDF with a card for each table, to print.
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
    )
}

/** Starts the PDF of the codes as a background export: a card at the bottom saves it when it is ready. */
@Composable
private fun rememberTableQrDownload(presenter: PlayersPresenter): (Tournament) -> Unit {
    val backgroundExports = rememberBackgroundExports()
    val saveTitle = stringResource(Res.string.table_qr_save)
    return { tournament ->
        backgroundExports.start("table-qr:${tournament.id}", UiText.of(Res.string.table_qr_top_bar)) {
            exportTableQr(presenter, tournament, saveTitle)
        }
    }
}

private suspend fun exportTableQr(presenter: PlayersPresenter, tournament: Tournament, saveTitle: String): AppResult<ExportFile> = presenter.generateTableQr(tournament.id).map { content ->
    val baseName = tournament.shortName.ifBlank { tournament.name.take(10) }
    val year = tournament.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
    ExportFile(generatedFileName(baseName, "table-qr", "pdf", Clock.System.now(), year = year), content, "application/pdf", saveTitle)
}

@Composable
internal fun TableQrContent(
    tournament: Tournament?,
    site: String,
    onBack: () -> Unit,
    onDownload: (Tournament) -> Unit,
    /** The table shown in big at first. The screenshots use it. */
    initialTable: Int? = null,
) {
    val tableCount = tournament?.takeUnless { it.drawPending }?.physicalTableCount ?: 0
    // The table shown in big, or null for the grid of all the tables.
    var shown by rememberSaveable(tournament?.id) { mutableStateOf(initialTable) }
    val big = shown?.takeIf { it in 1..tableCount }
    // Back leaves the big code first.
    PlatformBackHandler(enabled = big != null) { shown = null }
    AppScaffold(
        title = stringResource(Res.string.table_qr_top_bar),
        subtitle = tournament?.name,
        isLoading = tournament == null,
        onBack = { if (big != null) shown = null else onBack() },
        actions = if (tournament != null && tableCount > 0) {
            {
                AppTopBarButton(
                    text = stringResource(Res.string.table_qr_download),
                    icon = Icons.Default.Print,
                    onClick = { onDownload(tournament) },
                )
            }
        } else {
            null
        },
    ) {
        when {
            tournament == null -> Unit
            tableCount == 0 -> ScreenColumn {
                Text(stringResource(Res.string.table_qr_no_tables), style = MaterialTheme.typography.bodyLarge)
            }
            big != null -> BigTableQr(
                tournament = tournament,
                tableId = big,
                link = tournament.tableQrLink(site, big),
                tableCount = tableCount,
                onShow = { shown = it },
            )
            else -> TableQrGrid(tournament, site, tableCount, onShow = { shown = it })
        }
    }
}

@Composable
private fun TableQrGrid(tournament: Tournament, site: String, tableCount: Int, onShow: (Int) -> Unit) {
    val state = rememberLazyGridState()
    Column(Modifier.fillMaxSize().padding(horizontal = MtsTheme.layout.screenGutter)) {
        Text(
            stringResource(Res.string.table_qr_info),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = MtsTheme.spacing.md),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            state = state,
            contentPadding = PaddingValues(bottom = MtsTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
            modifier = Modifier.fillMaxSize().scrollIndicators(state),
        ) {
            items((1..tableCount).toList(), key = { it }) { tableId ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("table_qr_$tableId").clickable { onShow(tableId) },
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(MtsTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                    ) {
                        Text(
                            stringResource(Res.string.table_qr_table, tableId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        QrCode(tournament.tableQrLink(site, tableId), Modifier.size(136.dp))
                    }
                }
            }
        }
    }
}

/** One code as big as the screen allows, to show it on a screen or a projector. */
@Composable
private fun BigTableQr(tournament: Tournament, tableId: Int, link: String, tableCount: Int, onShow: (Int?) -> Unit) {
    val compact = LocalCompactLayout.current
    Column(
        Modifier.fillMaxSize().padding(MtsTheme.layout.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            tournament.name,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(Res.string.table_qr_table, tableId),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("table_qr_big_title"),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = MtsTheme.spacing.md), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            QrCode(link, Modifier.size(side))
        }
        Text(
            link.substringAfter("://"),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(MtsTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            NavigationButton(
                text = stringResource(Res.string.table_qr_previous),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconOnly = compact,
                enabled = tableId > 1,
                onClick = { onShow(tableId - 1) },
            )
            FocusedButton(onClick = { onShow(null) }) {
                Icon(Icons.Default.GridView, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.table_qr_all))
            }
            NavigationButton(
                text = stringResource(Res.string.table_qr_next),
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                iconOnly = compact,
                enabled = tableId < tableCount,
                onClick = { onShow(tableId + 1) },
                iconAtEnd = true,
            )
        }
    }
}

/** A button with text, or with only its icon on a phone, where the three buttons would not fit. */
@Composable
private fun NavigationButton(
    text: String,
    icon: ImageVector,
    iconOnly: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    iconAtEnd: Boolean = false,
) {
    FocusedOutlinedButton(onClick = onClick, enabled = enabled) {
        if (iconOnly) {
            Icon(icon, contentDescription = text, Modifier.size(18.dp))
        } else if (iconAtEnd) {
            Text(text)
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text)
        }
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
