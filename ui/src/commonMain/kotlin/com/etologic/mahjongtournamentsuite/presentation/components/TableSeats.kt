package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.domain.model.SeatDiffs
import com.etologic.mahjongtournamentsuite.domain.model.calculateTableDiffs
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
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
import mahjongtournamentsuite.ui.generated.resources.icon_self_draw
import mahjongtournamentsuite.ui.generated.resources.icon_trophy
import mahjongtournamentsuite.ui.generated.resources.icon_trophy_bronze
import mahjongtournamentsuite.ui.generated.resources.icon_trophy_silver
import mahjongtournamentsuite.ui.generated.resources.icon_wind_east
import mahjongtournamentsuite.ui.generated.resources.icon_wind_north
import mahjongtournamentsuite.ui.generated.resources.icon_wind_south
import mahjongtournamentsuite.ui.generated.resources.icon_wind_west
import mahjongtournamentsuite.ui.generated.resources.penalty_n
import mahjongtournamentsuite.ui.generated.resources.place_1st
import mahjongtournamentsuite.ui.generated.resources.place_2nd
import mahjongtournamentsuite.ui.generated.resources.place_3rd
import mahjongtournamentsuite.ui.generated.resources.points_short
import mahjongtournamentsuite.ui.generated.resources.position_n
import mahjongtournamentsuite.ui.generated.resources.wind_east
import mahjongtournamentsuite.ui.generated.resources.wind_north
import mahjongtournamentsuite.ui.generated.resources.wind_south
import mahjongtournamentsuite.ui.generated.resources.wind_west
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton

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
fun windOfChair(chair: Int, handNumber: Int): SeatWind = SeatWind.entries[(chair - (handNumber - 1).coerceIn(0, 15) % 4 + 4) % 4]

/** The wind of the round of a hand: hands 1 to 4 are East, 5 to 8 South, and so on. */
fun roundWindOf(handNumber: Int): SeatWind = SeatWind.entries[(handNumber - 1).coerceIn(0, 15) / 4]

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

/** [ChickenIcon] that says what it is on a touch, a long touch or a hover. */
@Composable
fun ChickenHandMark(size: Dp = 22.dp) {
    AppTooltipBox(stringResource(Res.string.chicken_hand), alignEnd = true) { ChickenIcon(size) }
}

/** The self-draw: a mushroom cloud in a few warm colors, its own colors unless [tint] (Ernesto, 2026-11-25). A label beside it says the words, so it has no description of its own. */
@Composable
fun SelfDrawIcon(size: Dp = 22.dp, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(
        painter = painterResource(Res.drawable.icon_self_draw),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
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
fun positionColor(position: Int): Color = MtsTheme.colors.position(position, MaterialTheme.colorScheme.errorContainer)

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

private class PlacedSeat(val seat: SeatDisplay, val chair: Int, val wind: SeatWind?, val score: AnimatedScore)

/**
 * The score of a seat on screen. When a new hand changes it, the number counts from the old score to
 * the new one and the difference rises over it, green for a gain and red for a loss.
 */
@Stable
private class AnimatedScore(initial: Int) {
    val value = Animatable(initial.toFloat())
    var delta by mutableIntStateOf(0)

    /**
     * From 0 to 1 while the difference rises, then 1. The score keeps the color of the change until the difference
     * fades, and both fade together.
     */
    val rise = Animatable(1f)

    /** How much of the color of the change the score and the difference have now: 0 is none, 1 is all. */
    val accentWeight: Float get() = if (rise.value >= 1f) 0f else minOf((1f - rise.value) * 2.5f, 1f)
}

@Composable
private fun rememberAnimatedScore(target: Int, animate: Boolean): AnimatedScore {
    val score = remember { AnimatedScore(target) }
    // The flag belongs to the same load as the new score, so the first load jumps to the scores.
    LaunchedEffect(target) {
        val from = score.value.targetValue.roundToInt()
        if (!animate || from == target) {
            score.value.snapTo(target.toFloat())
            return@LaunchedEffect
        }
        score.delta = target - from
        score.rise.snapTo(0f)
        launch { score.rise.animateTo(1f, tween(DELTA_RISE_MILLIS, easing = LinearEasing)) }
        score.value.animateTo(target.toFloat(), tween(SCORE_COUNT_MILLIS, easing = FastOutSlowInEasing))
    }
    return score
}

private const val SCORE_COUNT_MILLIS = 1_800
private const val DELTA_RISE_MILLIS = 3_600
private const val CENTER_LABEL_MILLIS = 1_200

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
    /** The Diffs switch needs a table in play. Without it, the switch is hidden and the differences are off. */
    diffsAvailable: Boolean = true,
    /** At the start of the row of the Diffs switch, above the seats, such as the time left of the round. */
    leading: @Composable (() -> Unit)? = null,
    /**
     * When true, a new score counts from the old one and its difference rises over it. The screen sets it for the
     * hands that arrive while it is open, and leaves it off for the first load.
     */
    animateScoreChanges: Boolean = false,
    /** Before the round starts the players are a plain list, not seats around the table (Ernesto, 2026-11-26). */
    asList: Boolean = false,
    onPlayerClick: (Int) -> Unit = {},
) {
    require(seats.size == 4) { "A table has four seats." }
    var showDiffs by rememberSaveable { mutableStateOf(false) }
    // One score per player in the order of hand 1, so it stays with the player when the chairs change.
    val scores = seats.map { rememberAnimatedScore(it.score.trim().toIntOrNull() ?: 0, animateScoreChanges) }
    val placed = seats.mapIndexed { index, seat ->
        val chair = chairOf(index, handNumber)
        PlacedSeat(seat, chair, if (windsKnown) windOfChair(chair, handNumber) else null, scores[index])
    }
    val diffs = calculateTableDiffs(seats.map { it.score.trim().toIntOrNull() ?: 0 })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
    ) {
        // The row above the seats: the leading content on the left and the Diffs switch with its info on the right.
        // Without the Diffs switch, the leading content is in the middle.
        if (diffsAvailable) {
            // The row is low, with little space above and below the switch (Ernesto, 2026-11-25). The label is part of
            // the switch, so the touch target stays wide.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { leading?.invoke() }
                    // The info icon goes after the switch, as far from it as the label is (Ernesto, 2026-11-25).
                    MtsSwitchRow(checked = showDiffs, onCheckedChange = { showDiffs = it }, gap = DiffsGap) {
                        Text(stringResource(Res.string.diffs_label), style = MaterialTheme.typography.labelLarge)
                    }
                    DiffsInfo()
                }
            }
        } else if (leading != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { leading() }
        }
        // A finished table is a list from the first place to the last, with the points of each player (Ernesto, 2026-11-25).
        if (showPoints) {
            SeatsPodium(placed, onPlayerClick)
        } else if (asList) {
            SeatsList(placed, onPlayerClick)
        } else {
            SeatsCross(placed, diffs, centerLabel, showDiffs && diffsAvailable, animateScoreChanges, onPlayerClick)
        }
    }
}

/** The space between the label of the Diffs switch and the switch, and between the switch and its info icon. */
private val DiffsGap = 8.dp

/** The info button of the Diffs switch: the 24 dp icon with (DiffsGap - 2 dp) on each side. */
private val DiffsInfoSize = 24.dp + (DiffsGap - 2.dp) * 2

/** An info icon. A touch, a hover or a click shows what the differences mean. */
@Composable
private fun DiffsInfo() {
    val diffsInfoDescription = stringResource(Res.string.diffs_about)
    AppTooltipBox(stringResource(Res.string.diffs_explanation), alignEnd = true) {
        // A button only as high as the row of the switch. Its icon starts DiffsGap after the switch: the circle of the
        // icon leaves 2 dp of its 24 dp empty, so the button adds the rest.
        IconButton(
            onClick = { toggleTooltip() },
            modifier = Modifier.size(DiffsInfoSize).semantics { contentDescription = diffsInfoDescription },
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
    showDiffs: Boolean,
    animateChanges: Boolean,
    onPlayerClick: (Int) -> Unit,
) {
    val viewerChair = placed.firstOrNull { it.seat.isMine }?.chair ?: 0

    // Bottom, right, top and left follow the order of the chairs.
    fun at(position: Int): PlacedSeat = placed.first { it.chair == (viewerChair + position) % 4 }

    @Composable
    fun card(position: Int, modifier: Modifier) {
        val item = at(position)
        val index = placed.indexOf(item)
        SeatCard(item, diffs[index], showDiffs, modifier, onPlayerClick)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            card(2, Modifier.fillMaxWidth(0.5f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            card(3, Modifier.weight(1f))
            // A blank label hides the circle and keeps its place, so the seats do not move.
            if (centerLabel.isBlank()) {
                Box(Modifier.padding(horizontal = MtsTheme.spacing.sm).size(64.dp))
            } else {
                Surface(
                    modifier = Modifier.padding(horizontal = MtsTheme.spacing.sm).size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    // A new hand slides the new number in from below, slowly, like the totals.
                    AnimatedContent(
                        targetState = centerLabel,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            if (animateChanges) {
                                (slideInVertically(tween(CENTER_LABEL_MILLIS)) { it } + fadeIn(tween(CENTER_LABEL_MILLIS)))
                                    .togetherWith(slideOutVertically(tween(CENTER_LABEL_MILLIS)) { -it } + fadeOut(tween(CENTER_LABEL_MILLIS)))
                            } else {
                                EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                    ) { label ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                // The number of the hand in the text font, like every number of the table (Ernesto, 2026-11-25).
                                style = MaterialTheme.typography.labelMedium.inTextFont(),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
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
    showDiffs: Boolean,
    modifier: Modifier,
    onPlayerClick: (Int) -> Unit,
) {
    val seat = item.seat
    SeatCardFrame(seat, modifier, onPlayerClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = MtsTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item.wind?.let { WindIcon(it, size = 22.dp) }
            // Under the wind, the flag and the number of the player in the tournament (as in the hand dialogs), then the
            // name with the whole width of the card, then the score (Ernesto, 2026-11-24).
            PlayerFlagAndNumber(seat.country, seat.slotId)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SeatLevelMedal(seat)
                Text(
                    seat.name,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    // A name always takes one row.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SeatScore(seat.score, item.score)
            if (seat.penalty != 0) {
                Text(stringResource(Res.string.penalty_n, seat.penalty), style = MaterialTheme.typography.labelSmall.inTextFont(), color = MaterialTheme.colorScheme.error)
            }
            seat.levelChange?.takeIf { LocalFeatureFlags.current.levelsEnabled }?.let { LevelChangeTag(it) }
            if (showDiffs) DiffsTable(diffs)
        }
    }
}

/**
 * The players of a table whose round has not started: one card per player, one under the other, with the flag, the
 * number and the name. No wind: the seats are not the ones of the game yet (Ernesto, 2026-11-26).
 */
@Composable
private fun SeatsList(placed: List<PlacedSeat>, onPlayerClick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(SEATS_LIST_TAG), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        placed.forEach { item ->
            val seat = item.seat
            SeatCardFrame(seat, Modifier.fillMaxWidth(), onPlayerClick) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MtsTheme.spacing.lg, vertical = MtsTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                ) {
                    PlayerFlagAndNumber(seat.country, seat.slotId)
                    SeatLevelMedal(seat)
                    Text(
                        seat.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The test tag of the list of the players of a table whose round has not started. */
const val SEATS_LIST_TAG = "seatsList"

/** The card of a player: white, with a green border for the viewer. A tap opens the player. */
@Composable
private fun SeatCardFrame(seat: SeatDisplay, modifier: Modifier, onPlayerClick: (Int) -> Unit, content: @Composable () -> Unit) {
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
    ) { content() }
}

/**
 * The final result of a finished table: a podium with the second, the first and the third place, each on a step of
 * the color of its medal and of a height that follows the place, and the fourth below it. The winner is the biggest.
 * The steps carry the table points in white; the players stand on them with their score (Ernesto,
 * 2026-11-25).
 */
@Composable
private fun SeatsPodium(placed: List<PlacedSeat>, onPlayerClick: (Int) -> Unit) {
    val ranked = rankedSeatIndices(placed)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(MtsTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                // Second on the left, first in the middle and third on the right, as on a podium.
                listOf(2, 1, 3).forEach { place ->
                    val index = ranked[place - 1]
                    PodiumPlace(placed[index], place, onPlayerClick, Modifier.weight(if (place == 1) 1.15f else 1f))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val last = ranked[3]
            FourthPlace(placed[last], onPlayerClick)
        }
    }
}

/** A player on the podium and the step under them. */
@Composable
private fun PodiumPlace(
    item: PlacedSeat,
    place: Int,
    onPlayerClick: (Int) -> Unit,
    modifier: Modifier,
) {
    val seat = item.seat
    val first = place == 1
    val color = positionColor(place)
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (seat.slotId != null) Modifier.clickable { onPlayerClick(seat.slotId) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    // The viewer has the green border of the seats.
                    if (seat.isMine) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier,
                )
                .padding(horizontal = MtsTheme.spacing.xs, vertical = MtsTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs),
        ) {
            PositionTrophy(place, size = if (first) 44.dp else 32.dp)
            PlayerFlagAndNumber(seat.country, seat.slotId)
            Text(
                seat.name,
                style = (if (first) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium).inTextFont(),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                // A long name takes two rows on the narrow places of a phone.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SeatScore(seat.score, item.score, big = first)
            SeatExtras(seat)
        }
        // The step: as wide as the place, of the color of the medal, higher for a better place.
        Column(
            Modifier
                .padding(top = MtsTheme.spacing.xs)
                .fillMaxWidth()
                // A minimum height, so big text (the font size of the system) makes the step higher instead of cutting it.
                .heightIn(
                    min = when (place) {
                        1 -> 96.dp
                        2 -> 72.dp
                        else -> 56.dp
                    },
                )
                .background(color, MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (seat.points.isNotBlank()) {
                Text(
                    tablePointsText(seat.points),
                    // The points are as big as the place is high (Ernesto, 2026-11-25).
                    style = when (place) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }.inTextFont(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The fourth place, under the podium: one row with the sad face, the player, the score and the points. */
@Composable
private fun FourthPlace(item: PlacedSeat, onPlayerClick: (Int) -> Unit) {
    val seat = item.seat
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (seat.isMine) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .then(if (seat.slotId != null) Modifier.clickable { onPlayerClick(seat.slotId) } else Modifier)
            .padding(horizontal = MtsTheme.spacing.sm, vertical = MtsTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
            LastPlaceIcon(size = 28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs)) {
                PlayerFlagAndNumber(seat.country, seat.slotId)
                Text(
                    seat.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SeatExtras(seat)
            }
            Column(horizontalAlignment = Alignment.End) {
                SeatScore(seat.score, item.score)
                if (seat.points.isNotBlank()) {
                    Text(
                        tablePointsText(seat.points),
                        style = MaterialTheme.typography.labelLarge.inTextFont(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** The table points of a player, short: "1 pt", "2 pts", "0.5 pts" (Ernesto, 2026-11-25: one point is singular). */
@Composable
private fun tablePointsText(points: String): String {
    val value = points.replace(',', '.').trim().toDoubleOrNull()
    // A whole number picks its plural form. A decimal one takes the general form, like 0 or 2.
    val quantity = if (value != null && value == kotlin.math.floor(value)) kotlin.math.abs(value).toInt() else 0
    return pluralStringResource(Res.plurals.points_short, quantity, formatPoints(points))
}

/** The penalty and the change of level of a player, when they have one. */
@Composable
private fun SeatExtras(seat: SeatDisplay) {
    if (seat.penalty != 0) {
        Text(stringResource(Res.string.penalty_n, seat.penalty), style = MaterialTheme.typography.labelSmall.inTextFont(), color = MaterialTheme.colorScheme.error)
    }
    seat.levelChange?.takeIf { LocalFeatureFlags.current.levelsEnabled }?.let { LevelChangeTag(it) }
}

/**
 * The score of a seat. While it counts to a new score it has the color of the change, and the difference rises over
 * its end and fades. The difference takes no space, so the score does not move.
 */
@Composable
private fun SeatScore(text: String, score: AnimatedScore, big: Boolean = false) {
    val accent = if (score.delta >= 0) MtsTheme.colors.gain else MaterialTheme.colorScheme.error
    val weight = score.accentWeight
    Box {
        Text(
            if (score.value.isRunning) "${score.value.value.roundToInt()}" else text.ifBlank { "0" },
            style = (if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium).inTextFont(),
            fontWeight = FontWeight.Bold,
            color = lerp(MaterialTheme.colorScheme.onSurface, accent, weight),
        )
        if (score.rise.value < 1f) RisingDelta(score, accent, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun RisingDelta(score: AnimatedScore, color: Color, modifier: Modifier) {
    val delta = score.delta
    Text(
        if (delta > 0) "+$delta" else "$delta",
        modifier
            // Placed after the end of the score, rising from its middle, with no size of its own.
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                val rise = (score.rise.value * 20.dp.toPx()).roundToInt()
                layout(0, 0) { placeable.place(4.dp.roundToPx(), -placeable.height / 2 - rise) }
            }
            // It appears fast, stays and fades out at the end, together with the color of the score.
            .graphicsLayer { alpha = minOf(score.rise.value * 6f, 1f) * score.accentWeight },
        style = MaterialTheme.typography.headlineSmall.inTextFont(),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun DiffsTable(diffs: SeatDiffs) {
    val rows = listOf(
        stringResource(Res.string.place_1st) to diffs.pointsToBeFirst,
        stringResource(Res.string.place_2nd) to diffs.pointsToBeSecond,
        stringResource(Res.string.place_3rd) to diffs.pointsToBeThird,
    ).mapNotNull { (label, value) -> value?.let { label to it } }
    // The first player has nothing to catch up, so the card shows nothing.
    if (rows.isEmpty()) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DiffsRow("", stringResource(Res.string.diffs_self), stringResource(Res.string.diffs_him), stringResource(Res.string.diffs_other), header = true)
        rows.forEach { (label, value) -> DiffsRow(label, "${value.bySelfPick}", "${value.byDirectHu}", "${value.byIndirectHu}") }
    }
}

@Composable
private fun DiffsRow(label: String, self: String, him: String, other: String, header: Boolean = false) {
    val style = MaterialTheme.typography.labelSmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    // The four columns share the width of the card with no gap between them, and every text keeps to one line,
    // so the table stays short on the narrow cards of a phone.
    Row(Modifier.widthIn(max = 180.dp).fillMaxWidth()) {
        Text(label, Modifier.weight(0.8f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        listOf(self, him, other).forEach { cell ->
            Text(cell, Modifier.weight(1f), style = if (header) style else style.inTextFont(), color = color, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The indices of the seats from the first place to the last. The table points come from the scores, so the score
 * decides. A substitute with forced 0 points has a low score sometimes, so the points come first.
 */
private fun rankedSeatIndices(placed: List<PlacedSeat>): List<Int> = placed.indices.sortedWith(
    compareByDescending<Int> { placed[it].seat.points.replace(',', '.').trim().toDoubleOrNull() ?: 0.0 }
        .thenByDescending { placed[it].seat.score.trim().toIntOrNull() ?: 0 },
)
