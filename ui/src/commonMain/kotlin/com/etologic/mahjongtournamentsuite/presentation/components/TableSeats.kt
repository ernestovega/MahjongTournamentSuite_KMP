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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.domain.model.PointsDiff
import com.etologic.mahjongtournamentsuite.domain.model.SeatDiffs
import com.etologic.mahjongtournamentsuite.domain.model.calculateTableDiffs
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import kotlinx.coroutines.launch
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.chicken_hand
import mahjongtournamentsuite.ui.generated.resources.diffs_about
import mahjongtournamentsuite.ui.generated.resources.diffs_explanation
import mahjongtournamentsuite.ui.generated.resources.diffs_him
import mahjongtournamentsuite.ui.generated.resources.diffs_label
import mahjongtournamentsuite.ui.generated.resources.diffs_other
import mahjongtournamentsuite.ui.generated.resources.diffs_self
import mahjongtournamentsuite.ui.generated.resources.icon_chicken
import mahjongtournamentsuite.ui.generated.resources.icon_sad_face
import mahjongtournamentsuite.ui.generated.resources.icon_trophy
import mahjongtournamentsuite.ui.generated.resources.icon_trophy_bronze
import mahjongtournamentsuite.ui.generated.resources.icon_trophy_silver
import mahjongtournamentsuite.ui.generated.resources.icon_wind_east
import mahjongtournamentsuite.ui.generated.resources.icon_wind_north
import mahjongtournamentsuite.ui.generated.resources.icon_wind_south
import mahjongtournamentsuite.ui.generated.resources.icon_wind_west
import mahjongtournamentsuite.ui.generated.resources.penalty_n
import mahjongtournamentsuite.ui.generated.resources.place_1st
import mahjongtournamentsuite.ui.generated.resources.place_1st_long
import mahjongtournamentsuite.ui.generated.resources.place_2nd
import mahjongtournamentsuite.ui.generated.resources.place_3rd
import mahjongtournamentsuite.ui.generated.resources.points_short
import mahjongtournamentsuite.ui.generated.resources.position_n
import mahjongtournamentsuite.ui.generated.resources.wind_east
import mahjongtournamentsuite.ui.generated.resources.wind_north
import mahjongtournamentsuite.ui.generated.resources.wind_south
import mahjongtournamentsuite.ui.generated.resources.wind_west
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class SeatWind {
    EAST,
    SOUTH,
    WEST,
    NORTH,
}

@Composable
fun SeatWind.label(): String = stringResource(
    when (this) {
        SeatWind.EAST -> Res.string.wind_east
        SeatWind.SOUTH -> Res.string.wind_south
        SeatWind.WEST -> Res.string.wind_west
        SeatWind.NORTH -> Res.string.wind_north
    },
)

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
    /** The country code of the player. The flag shows before the name. */
    val country: String = "",
    /** The level of the player. Null while the player has too few hands for a level. */
    val level: PlayerLevel? = null,
    val skillScore: Double? = null,
    val levelProvisional: Boolean = true,
    /** How much this table changed the skill score of the player. Null when it is not known. */
    val levelChange: Double? = null,
)

/** The small medal of the level of a seat, or nothing when the seat has no level. */
@Composable
fun SeatLevelMedal(seat: SeatDisplay, size: Dp = 26.dp) {
    if (!LocalFeatureFlags.current.levelsEnabled) return
    val level = seat.level ?: return
    LevelMedalTag(level, seat.skillScore, seat.levelProvisional, size = size)
}

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
    Icon(painter = painter, contentDescription = wind.label(), modifier = Modifier.size(size), tint = tint)
}

/** The chicken hand icon. */
@Composable
fun ChickenIcon(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(Res.drawable.icon_chicken),
        contentDescription = stringResource(Res.string.chicken_hand),
        modifier = modifier.size(size),
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
        contentDescription = stringResource(Res.string.position_n, position),
        modifier = Modifier.size(size),
        tint = Color.Unspecified,
    )
}

/**
 * A trophy for any rank. The first three ranks have a gold, silver or bronze trophy. The other ranks have a neutral
 * trophy that has the color of the text, so it is white on a dark background.
 */
@Composable
fun RankTrophy(rank: Int, size: Dp = 28.dp) {
    if (rank in 1..3) {
        PositionTrophy(rank, size)
    } else {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = stringResource(Res.string.position_n, rank),
            modifier = Modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The color of the result of a position at a table. It matches the trophy. The fourth place is red. */
@Composable
fun positionColor(position: Int): Color = when (position) {
    1 -> Color(0xFFFFAC33)
    2 -> Color(0xFF8A99A8)
    3 -> Color(0xFFA9632F)
    else -> MaterialTheme.colorScheme.error
}

/** The sad face of the player who finished fourth at a table. */
@Composable
fun LastPlaceIcon(size: Dp = 28.dp) {
    Icon(
        painter = painterResource(Res.drawable.icon_sad_face),
        contentDescription = stringResource(Res.string.position_n, 4),
        modifier = Modifier.size(size),
        tint = Color.Unspecified,
    )
}

private class PlacedSeat(val seat: SeatDisplay, val chair: Int, val wind: SeatWind?)

/**
 * The four players of a table.
 *
 * [seats] has the four players in the order of hand 1 (East, South, West, North).
 * [handNumber] is the hand in play (1 to 16). The chairs and the winds follow it.
 * The viewer stays at the bottom. The other players turn around the table.
 * The points of the table show only when [showPoints] is true, that is when the table is complete.
 * The winds show only when [windsKnown] is true. Before the players set the seats, the order is not a seating.
 */
@Composable
fun TableSeatsView(
    seats: List<SeatDisplay>,
    handNumber: Int,
    centerLabel: String,
    showPoints: Boolean,
    modifier: Modifier = Modifier,
    windsKnown: Boolean = true,
    /** When true, the id of the tournament player shows before the name, as in the detailed list of hands. */
    showIds: Boolean = false,
    /** The Diffs switch needs a table in play. Without it, the switch is hidden and the differences are off. */
    diffsAvailable: Boolean = true,
    onPlayerClick: (Int) -> Unit = {},
) {
    require(seats.size == 4) { "A table has four seats." }
    var showDiffs by rememberSaveable { mutableStateOf(false) }
    val placed = seats.mapIndexed { index, seat ->
        val chair = chairOf(index, handNumber)
        PlacedSeat(seat, chair, if (windsKnown) windOfChair(chair, handNumber) else null)
    }
    val diffs = calculateTableDiffs(seats.map { it.score.trim().toIntOrNull() ?: 0 })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeatsCross(placed, diffs, centerLabel, showPoints, showDiffs && diffsAvailable, showIds, onPlayerClick)
        if (diffsAvailable) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clickable { showDiffs = !showDiffs },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.diffs_label), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = showDiffs,
                    onCheckedChange = { showDiffs = it },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Box(Modifier.weight(1f))
            DiffsInfo()
        }
    }
}

/** An info icon. A tap, a long press or a hover shows what the differences mean. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiffsInfo() {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val diffsInfoDescription = stringResource(Res.string.diffs_about)
    TooltipBox(
        positionProvider = rememberAppTooltipPositionProvider(alignEnd = true),
        tooltip = { AppPlainTooltip { Text(stringResource(Res.string.diffs_explanation)) } },
        state = state,
    ) {
        IconButton(
            onClick = { scope.launch { if (state.isVisible) state.dismiss() else state.show() } },
            modifier = Modifier.semantics { contentDescription = diffsInfoDescription },
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SeatsCross(
    placed: List<PlacedSeat>,
    diffs: List<SeatDiffs>,
    centerLabel: String,
    showPoints: Boolean,
    showDiffs: Boolean,
    showIds: Boolean,
    onPlayerClick: (Int) -> Unit,
) {
    val viewerChair = placed.firstOrNull { it.seat.isMine }?.chair ?: 0
    // Bottom, right, top and left follow the order of the chairs.
    fun at(position: Int): PlacedSeat = placed.first { it.chair == (viewerChair + position) % 4 }

    // The places only exist when the table is complete.
    val ranked = rankedSeatIndices(placed, showPoints)

    @Composable
    fun card(position: Int, modifier: Modifier) {
        val item = at(position)
        val index = placed.indexOf(item)
        val place = if (showPoints) ranked.indexOf(index) + 1 else null
        SeatCard(item, diffs[index], place, showPoints, showDiffs, showIds, modifier, onPlayerClick)
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
    place: Int?,
    showPoints: Boolean,
    showDiffs: Boolean,
    showIds: Boolean,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item.wind?.let { WindIcon(it, size = 22.dp) }
            // The place icon of a complete table is at the side of the score.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (place != null) {
                    if (place <= 3) PositionTrophy(place, size = 24.dp) else LastPlaceIcon(size = 24.dp)
                }
                Text(
                    seat.score.ifBlank { "0" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (showPoints && seat.points.isNotBlank()) {
                // The table points are the result of a finished table, so they are big and have the color of the place.
                Text(
                    stringResource(Res.string.points_short, formatPoints(seat.points)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = place?.let { positionColor(it) } ?: Color.Unspecified,
                )
            }
            if (seat.penalty != 0) {
                Text(stringResource(Res.string.penalty_n, seat.penalty), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SeatLevelMedal(seat)
                if (seat.country.isNotBlank()) PlayerFlag(seat.country, width = 22.dp)
                Text(
                    if (showIds && seat.slotId != null) "${seat.slotId} ${seat.name}" else seat.name,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (seat.isMine) FontWeight.Bold else null,
                    textAlign = TextAlign.Center,
                    // A name always takes one row.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            seat.levelChange?.takeIf { LocalFeatureFlags.current.levelsEnabled }?.let { LevelChangeTag(it) }
            if (showDiffs) DiffsTable(diffs)
        }
    }
}

@Composable
private fun DiffsTable(diffs: SeatDiffs) {
    val rows = listOf(
        stringResource(Res.string.place_1st) to diffs.pointsToBeFirst,
        stringResource(Res.string.place_2nd) to diffs.pointsToBeSecond,
        stringResource(Res.string.place_3rd) to diffs.pointsToBeThird,
    ).mapNotNull { (label, value) -> value?.let { label to it } }
    if (rows.isEmpty()) {
        Text(stringResource(Res.string.place_1st_long), style = MaterialTheme.typography.labelMedium, color = GainColor)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DiffsRow("", stringResource(Res.string.diffs_self), stringResource(Res.string.diffs_him), stringResource(Res.string.diffs_other), header = true)
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

/**
 * The indices of the seats from the first place to the last. The table points come from the scores, so the score
 * decides. A substitute with forced 0 points has a low score sometimes, so the points come first.
 */
@Suppress("UNUSED_PARAMETER")
private fun rankedSeatIndices(placed: List<PlacedSeat>, showPoints: Boolean): List<Int> =
    placed.indices.sortedWith(
        compareByDescending<Int> { placed[it].seat.points.replace(',', '.').trim().toDoubleOrNull() ?: 0.0 }
            .thenByDescending { placed[it].seat.score.trim().toIntOrNull() ?: 0 },
    )
