package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.no_player_photo
import mahjongtournamentsuite.adminapp.generated.resources.photo_of
import mahjongtournamentsuite.adminapp.generated.resources.round
import mahjongtournamentsuite.adminapp.generated.resources.table
import mahjongtournamentsuite.adminapp.generated.resources.tables
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton

internal val TournamentPlayerPhotoSize = 40.dp
internal val TournamentPlayerPhotoColumnWidth = 48.dp
internal val TournamentPlayerCountryColumnWidth = 64.dp
internal val TournamentPlayerIdColumnWidth = 48.dp
internal val TournamentPlayerEmaColumnWidth = 96.dp

// The widths of the name and the team when there is room. The space left over stays before the menu.
private val TournamentPlayerNameMaxWidth = 300.dp
private val TournamentPlayerTeamColumnWidth = 180.dp

// The button that opens the tables of a player shows its text from this width of the list, and only the arrow below it.
internal val TablesToggleLabelMinWidth = 1000.dp
internal val TablesToggleLabeledWidth = 112.dp

// Wide enough for the title of the column above the arrow.
internal val TablesToggleIconWidth = 64.dp

/**
 * Inside the row of the name and the team: the same width in the header and in every row, so the columns line up.
 * Not filled: the fixed width, or less when the row is narrower. Never the width of the text.
 * On a narrow screen the name keeps two thirds of the room and the team one third.
 */
@Composable
internal fun RowScope.TournamentPlayerNameModifier(): Modifier = Modifier.weight(2f, fill = false).width(TournamentPlayerNameMaxWidth)

@Composable
internal fun RowScope.TournamentPlayerTeamModifier(): Modifier = Modifier.weight(1f, fill = false).width(TournamentPlayerTeamColumnWidth)

// The photo column of a phone. The tables title below it takes the same width.
internal val TournamentPlayerCompactLeadingWidth = 44.dp
private val RoundTableCellWidth = 36.dp

/** One round and the table where a player sits in it. */
internal data class PlayerRoundTable(val roundId: Int, val tableId: Int)

/** Every player's table per round, in round order. A player has at most one table per round. */
internal fun roundTablesByPlayer(tables: List<TournamentTable>): Map<Int, List<PlayerRoundTable>> = tables
    .sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
    .flatMap { table -> table.playerIds.map { playerId -> playerId to PlayerRoundTable(table.roundId, table.tableId) } }
    .groupBy(keySelector = { it.first }, valueTransform = { it.second })

/**
 * The "Tables" title and a small two-row table: the round on top and the table below.
 * The table is as wide as the row: the cells share the free width. When the rounds do not fit, the title and the
 * table scroll sideways from one edge of the row to the other, with a scrollbar on desktop and web.
 */
@Composable
internal fun PlayerRoundTablesToggle(
    expanded: Boolean,
    showLabel: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(Res.string.tables)
    val icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore
    if (showLabel) {
        TextButton(onClick = onToggle, modifier = Modifier.testTag(PlayerRoundTablesToggleTag)) {
            Text(label, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(icon, contentDescription = null)
        }
    } else {
        IconButton(onClick = onToggle, modifier = Modifier.testTag(PlayerRoundTablesToggleTag)) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
internal fun PlayerRoundTablesStrip(
    roundTables: List<PlayerRoundTable>,
    titleWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val shape = MaterialTheme.shapes.small
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().testTag(PlayerRoundTablesTag)) {
        // Whole pixels, so the cells add up to the row exactly and nothing scrolls by a pixel.
        val cellWidths = with(density) {
            val divider = 1.dp.roundToPx()
            val free = maxWidth.roundToPx() -
                (RoundTableStripPadding * 2 + titleWidth + RoundTableStripGap + RoundTableLabelWidth).roundToPx() -
                // The dividers between cells and the 1 dp inside the border on each side.
                divider * (roundTables.size + 2)
            val minCell = RoundTableCellWidth.roundToPx()
            val even = free / roundTables.size
            if (even < minCell) {
                List(roundTables.size) { minCell.toDp() }
            } else {
                // The pixels left over go to the first cells, one each.
                val extra = free - even * roundTables.size
                List(roundTables.size) { index -> (even + if (index < extra) 1 else 0).toDp() }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .horizontalScrollWithIndicators(scrollState)
                    .padding(horizontal = RoundTableStripPadding),
                horizontalArrangement = Arrangement.spacedBy(RoundTableStripGap),
                verticalAlignment = Alignment.Top,
            ) {
                // The title sits under the ID (desktop) or the photo (phone).
                Box(
                    modifier = Modifier.width(titleWidth).height(RoundTableCellHeight * 2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.tables),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .border(1.dp, borderColor, shape)
                        .padding(1.dp),
                ) {
                    // The labels stay as the first column so each row says what it holds.
                    RoundTableCell(
                        top = stringResource(Res.string.round),
                        bottom = stringResource(Res.string.table),
                        width = RoundTableLabelWidth,
                        isLabel = true,
                    )
                    roundTables.forEachIndexed { index, roundTable ->
                        Box(Modifier.width(1.dp).height(RoundTableCellHeight * 2).background(borderColor))
                        RoundTableCell(
                            top = roundTable.roundId.toString(),
                            bottom = roundTable.tableId.toString(),
                            width = cellWidths[index],
                        )
                    }
                }
            }
        }
    }
}

/** Test tag of each player's round and table strip. */
internal const val PlayerRoundTablesTag = "playerRoundTables"
internal const val PlayerRoundTablesToggleTag = "playerRoundTablesToggle"
private val RoundTableStripPadding = 12.dp
private val RoundTableStripGap = 12.dp
private val RoundTableLabelWidth = 60.dp
private val RoundTableCellHeight = 28.dp

@Composable
private fun RoundTableCell(
    top: String,
    bottom: String,
    width: Dp,
    isLabel: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RoundTableCellHeight)
                .background(colors.surfaceVariant)
                .padding(horizontal = MtsTheme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = top,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RoundTableCellHeight)
                .padding(horizontal = MtsTheme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = bottom,
                style = if (isLabel) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (isLabel) colors.onSurfaceVariant else colors.onSurface,
                fontWeight = if (isLabel) null else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
internal val TournamentPlayerActionColumnWidth = 112.dp
internal val AssignmentPhotoColumnWidth = 48.dp
internal val AssignmentCountryColumnWidth = 64.dp
internal val AssignmentEmaColumnWidth = 96.dp

@Composable
internal fun TournamentPlayerHeader(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}

@Composable
internal fun TournamentPlayerPhoto(
    photoUrl: String?,
    playerName: String,
    columnWidth: Dp,
) {
    Box(
        modifier = Modifier.width(columnWidth).height(TournamentPlayerPhotoSize),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(TournamentPlayerPhotoSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(Res.string.no_player_photo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TournamentPlayerPhotoSize * 0.62f),
                )
            }
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(Res.string.photo_of, playerName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(TournamentPlayerPhotoSize),
            )
        }
    }
}
