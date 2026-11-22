package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.domain.model.seatScores
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.ResultsSummaryCard
import com.etologic.mahjongtournamentsuite.presentation.components.ResultsSummaryMaxWidth
import com.etologic.mahjongtournamentsuite.presentation.components.ResultsSummaryMinWidth
import com.etologic.mahjongtournamentsuite.presentation.components.ResultsSummaryRow
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalTelemetry
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.launch
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.summary_share
import org.jetbrains.compose.resources.stringResource

/** The test tag of the summary of a player, the part that becomes the shared image. */
internal const val RESULTS_SUMMARY_TAG = "results-summary"

/** The space around the card in the shared image, so the image has the background of the app around the card. */
private val ImageMargin = 12.dp

/**
 * One row for each table of the player in [slotId], by round. The totals and the place come from the ranking after
 * the round, as the Ranking tab counts them. A table that is not finished yet has only its round, table and seat.
 */
internal fun PlayerTournamentView.summaryRows(slotId: Int): List<ResultsSummaryRow> {
    val tables = tables.filter { slotId in it.playerSlots() }.sortedBy { it.roundId }
    if (tables.isEmpty()) return emptyList()
    val rankings = rankingsByRound()
    return tables.map { table ->
        val seat = table.seatIds().indexOf(slotId).takeIf { table.seatsAssigned && it >= 0 }
        val finished = table.isComplete && seat != null
        val after = if (finished) rankings[table.roundId]?.players?.firstOrNull { it.playerId == slotId } else null
        ResultsSummaryRow(
            round = table.roundId,
            table = table.tableId,
            wind = seat?.let { SeatWind.entries[it] },
            points = if (finished) table.table.seatPoints[seat!!].trim().replace(',', '.').toDoubleOrNull() else null,
            score = if (finished) table.table.seatScores[seat!!].trim().toIntOrNull() else null,
            totalPoints = after?.points,
            totalScore = after?.score,
            place = after?.position,
        )
    }
}

/**
 * The Summary tab of a player: all the rounds in one table, as [ResultsSummaryCard] draws it. [layer] keeps what
 * the card draws, so [ShareSummaryButton] turns it into an image.
 */
@Composable
internal fun ColumnScope.PlayerSummaryTab(
    view: PlayerTournamentView,
    slotId: Int,
    emaId: String?,
    layer: GraphicsLayer,
    top: (@Composable () -> Unit)?,
) {
    val rows = remember(view, slotId) { view.summaryRows(slotId) }
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScrollWithIndicators()
            .padding(vertical = MtsTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
    ) {
        top?.let { Box(Modifier.padding(horizontal = MtsTheme.spacing.lg)) { it() } }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // On a narrow phone the card keeps its smallest width and scrolls sideways; the image still has all of it.
            val cardWidth = (maxWidth - ImageMargin * 2).coerceIn(ResultsSummaryMinWidth, ResultsSummaryMaxWidth)
            Box(Modifier.fillMaxWidth().horizontalScrollWithIndicators(), contentAlignment = Alignment.TopCenter) {
                val background = MaterialTheme.colorScheme.background
                Box(
                    Modifier
                        .testTag(RESULTS_SUMMARY_TAG)
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .background(background)
                        .padding(ImageMargin),
                ) {
                    ResultsSummaryCard(
                        tournament = view.tournament.name,
                        player = view.nameOf(slotId),
                        country = view.players[slotId]?.country.orEmpty(),
                        emaId = emaId,
                        rows = rows,
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

/**
 * The button of the top bar that shares the summary as a PNG image: the share sheet on Android and in a browser that
 * has one, a download in other browsers, and a save dialog on a computer.
 */
@Composable
internal fun ShareSummaryButton(layer: GraphicsLayer, fileName: String, onError: () -> Unit) {
    val scope = rememberCoroutineScope()
    val telemetry = LocalTelemetry.current
    val title = stringResource(Res.string.summary_share)
    val share = rememberShareImage(onError)
    AppTopBarButton(
        text = title,
        icon = Icons.Default.Share,
        onClick = {
            scope.launch {
                val png = runCatching { layer.toImageBitmap().encodePng() }.getOrNull()
                if (png == null) {
                    onError()
                    return@launch
                }
                telemetry.event(TelemetryEvent.RESULTS_SHARED)
                share(fileName, png, title)
            }
        },
    )
}

/** A file name for the image, from the names of the tournament and of the player, with only safe characters. */
internal fun summaryFileName(tournament: String, player: String): String {
    val safe = "$tournament $player".map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        .replace(Regex("-+"), "-").trim('-').take(80)
    return "${safe.ifEmpty { "results" }}.png"
}

/** The image as the bytes of a PNG file. */
internal expect fun ImageBitmap.encodePng(): ByteArray

/**
 * Shares a PNG image with the tools of the platform. The returned function takes the file name, the bytes and the
 * title of the share sheet. [onError] runs when the platform cannot share or save it.
 */
@Composable
internal expect fun rememberShareImage(onError: () -> Unit): (fileName: String, png: ByteArray, title: String) -> Unit
