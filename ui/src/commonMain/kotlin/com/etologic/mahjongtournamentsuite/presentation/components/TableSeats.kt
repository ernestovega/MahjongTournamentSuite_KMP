package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PointsDiff
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import com.etologic.mahjongtournamentsuite.domain.model.SeatDiffs
import com.etologic.mahjongtournamentsuite.domain.model.calculateTableDiffs
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.icon_chicken
import mahjongtournamentsuite.ui.generated.resources.icon_trophy
import mahjongtournamentsuite.ui.generated.resources.icon_trophy_bronze
import mahjongtournamentsuite.ui.generated.resources.icon_trophy_silver
import mahjongtournamentsuite.ui.generated.resources.icon_wind_east
import mahjongtournamentsuite.ui.generated.resources.icon_wind_north
import mahjongtournamentsuite.ui.generated.resources.icon_wind_south
import mahjongtournamentsuite.ui.generated.resources.icon_wind_west
import org.jetbrains.compose.resources.painterResource

enum class SeatWind(val label: String) {
    EAST("East"),
    SOUTH("South"),
    WEST("West"),
    NORTH("North"),
}

/** What one player of a table shows. Scores and points are the text of the table. */
data class SeatDisplay(
    val name: String,
    val score: String,
    val points: String,
    /** Total of the penalties of the player. Zero means no penalty. */
    val penalty: Int = 0,
    val isMine: Boolean = false,
    /** The slot of the tournament player. It is used to open the tables of that player. */
    val slotId: Int? = null,
)

/**
 * The chair (0 East, 1 South, 2 West, 3 North) where the player sits during a hand.
 * [initialIndex] is the seat of the player in hand 1. Players change chairs after every four hands.
 */
fun chairOf(initialIndex: Int, handNumber: Int): Int = initialIndex xor ((handNumber - 1).coerceIn(0, 15) / 4)

/** The wind of a chair during a hand. The winds turn after every hand. */
fun windOfChair(chair: Int, handNumber: Int): SeatWind =
    SeatWind.entries[(chair - (handNumber - 1).coerceIn(0, 15) % 4 + 4) % 4]

/** The wind of the round of a hand: hands 1 to 4 are East, 5 to 8 South, and so on. */
fun roundWindOf(handNumber: Int): SeatWind = SeatWind.entries[(handNumber - 1).coerceIn(0, 15) / 4]

private val GainColor = Color(0xFF00934C)

@Composable
fun WindIcon(wind: SeatWind, size: Dp = 24.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val painter = painterResource(
        when (wind) {
            SeatWind.EAST -> Res.drawable.icon_wind_east
            SeatWind.SOUTH -> Res.drawable.icon_wind_south
            SeatWind.WEST -> Res.drawable.icon_wind_west
            SeatWind.NORTH -> Res.drawable.icon_wind_north
        },
    )
    Icon(painter = painter, contentDescription = wind.label, modifier = Modifier.size(size), tint = tint)
}

/** The chicken hand icon. */
@Composable
fun ChickenIcon(size: Dp = 22.dp) {
    Icon(
        painter = painterResource(Res.drawable.icon_chicken),
        contentDescription = "Chicken hand",
        modifier = Modifier.size(size),
        tint = Color.Unspecified,
    )
}

/** The number of a hand as the wind of its round and the place in the round, such as E1, E2, S1 or N4. */
@Composable
fun HandLabel(handId: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WindIcon(roundWindOf(handId), size = 18.dp)
        Text("${(handId - 1) % 4 + 1}", fontWeight = FontWeight.Bold)
    }
}

/** A gold, silver or bronze trophy for the first three positions. Other positions show nothing. */
@Composable
fun PositionTrophy(position: Int, size: Dp = 28.dp) {
    val resource = when (position) {
        1 -> Res.drawable.icon_trophy
        2 -> Res.drawable.icon_trophy_silver
        3 -> Res.drawable.icon_trophy_bronze
        else -> return
    }
    Icon(
        painter = painterResource(resource),
        contentDescription = "Position $position",
        modifier = Modifier.size(size),
        tint = Color.Unspecified,
    )
}

private class PlacedSeat(val seat: SeatDisplay, val chair: Int, val wind: SeatWind)

/**
 * The four players of a table.
 *
 * [seats] has the four players in the order of hand 1 (East, South, West, North).
 * [handNumber] is the hand in play (1 to 16). The chairs and the winds follow it.
 * The viewer stays at the bottom. The other players turn around the table.
 * The points of the table show only when [showPoints] is true, that is when the table is complete.
 */
@Composable
fun TableSeatsView(
    seats: List<SeatDisplay>,
    handNumber: Int,
    centerLabel: String,
    showPoints: Boolean,
    initialRanking: Boolean,
    modifier: Modifier = Modifier,
    onPlayerClick: (Int) -> Unit = {},
) {
    require(seats.size == 4) { "A table has four seats." }
    var byRanking by rememberSaveable(initialRanking) { mutableStateOf(initialRanking) }
    var showDiffs by rememberSaveable { mutableStateOf(false) }
    val placed = seats.mapIndexed { index, seat ->
        val chair = chairOf(index, handNumber)
        PlacedSeat(seat, chair, windOfChair(chair, handNumber))
    }
    val diffs = calculateTableDiffs(seats.map { it.score.trim().toIntOrNull() ?: 0 })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ViewButton(selected = !byRanking, description = "Seats view", onClick = { byRanking = false }) {
                Icon(Icons.Default.GridView, contentDescription = null)
            }
            ViewButton(selected = byRanking, description = "Ranking view", onClick = { byRanking = true }) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = null)
            }
            Box(Modifier.weight(1f))
            Row(
                Modifier.clickable { showDiffs = !showDiffs },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Differences", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = showDiffs,
                    onCheckedChange = { showDiffs = it },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            DiffsInfo()
        }
        if (byRanking) {
            SeatsRanking(placed, diffs, showPoints, showDiffs, onPlayerClick)
        } else {
            SeatsCross(placed, diffs, centerLabel, showPoints, showDiffs, onPlayerClick)
        }
    }
}

private const val DIFFS_EXPLANATION = "The table shows the hand score that a player needs to reach a higher place. " +
    "There are three ways to win:\n" +
    "• Self: the player wins by self-pick.\n" +
    "• Him: the player wins on a discard of the player to overtake.\n" +
    "• Other: the player wins on a discard of another player."

/**
 * Puts the tooltip above the icon with its right edge on the right edge of the icon, and keeps it inside the window.
 * The icon is at the right edge of the screen, so a centered tooltip would leave the screen.
 */
private object EndAlignedTooltipPosition : PopupPositionProvider {
    private const val MARGIN = 16

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - MARGIN).coerceAtLeast(MARGIN)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(MARGIN, maxX)
        val above = anchorBounds.top - popupContentSize.height - MARGIN
        val y = if (above >= 0) above else anchorBounds.bottom + MARGIN
        return IntOffset(x, y)
    }
}

/** An info icon. A tap, a long press or a hover shows what the differences mean. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiffsInfo() {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = remember { EndAlignedTooltipPosition },
        tooltip = { PlainTooltip(Modifier.widthIn(max = 280.dp)) { Text(DIFFS_EXPLANATION) } },
        state = state,
    ) {
        IconButton(
            onClick = { scope.launch { if (state.isVisible) state.dismiss() else state.show() } },
            modifier = Modifier.semantics { contentDescription = "About differences" },
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ViewButton(selected: Boolean, description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { icon() }
}

@Composable
private fun SeatsCross(
    placed: List<PlacedSeat>,
    diffs: List<SeatDiffs>,
    centerLabel: String,
    showPoints: Boolean,
    showDiffs: Boolean,
    onPlayerClick: (Int) -> Unit,
) {
    val viewerChair = placed.firstOrNull { it.seat.isMine }?.chair ?: 0
    // Bottom, right, top and left follow the order of the chairs.
    fun at(position: Int): PlacedSeat = placed.first { it.chair == (viewerChair + position) % 4 }

    @Composable
    fun card(position: Int, modifier: Modifier) {
        val item = at(position)
        SeatCard(item, diffs[placed.indexOf(item)], showPoints, showDiffs, modifier, onPlayerClick)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            card(2, Modifier.fillMaxWidth(0.5f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            card(3, Modifier.weight(1f))
            Surface(
                modifier = Modifier.padding(horizontal = 6.dp).size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        centerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            card(1, Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            card(0, Modifier.fillMaxWidth(0.5f))
        }
    }
}

@Composable
private fun SeatCard(
    item: PlacedSeat,
    diffs: SeatDiffs,
    showPoints: Boolean,
    showDiffs: Boolean,
    modifier: Modifier,
    onPlayerClick: (Int) -> Unit,
) {
    val seat = item.seat
    Card(
        modifier = modifier.then(
            if (seat.slotId != null) Modifier.clickable { onPlayerClick(seat.slotId) } else Modifier,
        ),
        border = BorderStroke(
            if (seat.isMine) 2.dp else 1.dp,
            if (seat.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WindIcon(item.wind, size = 22.dp)
                Text(item.wind.label, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                seat.score.ifBlank { "0" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (seat.score.trim().startsWith("-")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (showPoints && seat.points.isNotBlank()) {
                Text(
                    "${formatPoints(seat.points)} pts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (seat.penalty != 0) {
                Text("Penalty ${seat.penalty}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                seat.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (seat.isMine) FontWeight.Bold else null,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showDiffs) DiffsTable(diffs)
        }
    }
}

@Composable
private fun DiffsTable(diffs: SeatDiffs) {
    val rows = listOf(
        "1st" to diffs.pointsToBeFirst,
        "2nd" to diffs.pointsToBeSecond,
        "3rd" to diffs.pointsToBeThird,
    ).mapNotNull { (label, value) -> value?.let { label to it } }
    if (rows.isEmpty()) {
        Text("1st place", style = MaterialTheme.typography.labelMedium, color = GainColor)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DiffsRow("", "Self", "Him", "Other", header = true)
        rows.forEach { (label, value) -> DiffsRow(label, "${value.bySelfPick}", "${value.byDirectHu}", "${value.byIndirectHu}") }
    }
}

@Composable
private fun DiffsRow(label: String, self: String, him: String, other: String, header: Boolean = false) {
    val style = MaterialTheme.typography.labelSmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.width(26.dp), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(self, him, other).forEach { cell ->
            Text(cell, Modifier.width(34.dp), style = style, color = color, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SeatsRanking(
    placed: List<PlacedSeat>,
    diffs: List<SeatDiffs>,
    showPoints: Boolean,
    showDiffs: Boolean,
    onPlayerClick: (Int) -> Unit,
) {
    val ranked = placed.indices.sortedWith(
        compareByDescending<Int> { if (showPoints) placed[it].seat.points.trim().toDoubleOrNull() ?: 0.0 else 0.0 }
            .thenByDescending { placed[it].seat.score.trim().toIntOrNull() ?: 0 },
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ranked.forEachIndexed { rank, index ->
            val item = placed[index]
            val seat = item.seat
            Card(
                modifier = Modifier.fillMaxWidth().then(
                    if (seat.slotId != null) Modifier.clickable { onPlayerClick(seat.slotId) } else Modifier,
                ),
                border = BorderStroke(
                    if (seat.isMine) 2.dp else 1.dp,
                    if (seat.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                            if (rank < 3) PositionTrophy(rank + 1) else Text("${rank + 1}")
                        }
                        WindIcon(item.wind, size = 24.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                seat.name,
                                fontWeight = if (seat.isMine) FontWeight.Bold else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (seat.penalty != 0) {
                                Text(
                                    "Penalty ${seat.penalty}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            seat.score.ifBlank { "0" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (seat.score.trim().startsWith("-")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        if (showPoints && seat.points.isNotBlank()) {
                            Text(
                                formatPoints(seat.points),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (showDiffs) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { DiffsTable(diffs[index]) }
                }
            }
        }
    }
}
