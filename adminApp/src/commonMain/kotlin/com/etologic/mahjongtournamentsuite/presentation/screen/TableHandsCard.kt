package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.etologic.mahjongtournamentsuite.domain.model.topHandScores
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.TrophyIcon
import com.etologic.mahjongtournamentsuite.presentation.components.TrophyMedal
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.at_least_one_hand_is_invalid
import mahjongtournamentsuite.adminapp.generated.resources.calculated_from_hands_trophies_show_the
import mahjongtournamentsuite.adminapp.generated.resources.chicken
import mahjongtournamentsuite.adminapp.generated.resources.chicken_hands
import mahjongtournamentsuite.adminapp.generated.resources.completed
import mahjongtournamentsuite.adminapp.generated.resources.done
import mahjongtournamentsuite.adminapp.generated.resources.fold_hands
import mahjongtournamentsuite.adminapp.generated.resources.hands
import mahjongtournamentsuite.adminapp.generated.resources.hands_done_summary
import mahjongtournamentsuite.adminapp.generated.resources.loser
import mahjongtournamentsuite.adminapp.generated.resources.no_hands_yet
import mahjongtournamentsuite.adminapp.generated.resources.not_used_manual
import mahjongtournamentsuite.adminapp.generated.resources.penalty
import mahjongtournamentsuite.adminapp.generated.resources.score
import mahjongtournamentsuite.adminapp.generated.resources.seat_positions_first
import mahjongtournamentsuite.adminapp.generated.resources.show_how_the_chicken_and_best
import mahjongtournamentsuite.adminapp.generated.resources.the_hands_of_this_table_show
import mahjongtournamentsuite.adminapp.generated.resources.total_scores
import mahjongtournamentsuite.adminapp.generated.resources.tournament_best_hand_n
import mahjongtournamentsuite.adminapp.generated.resources.unfold_hands
import mahjongtournamentsuite.adminapp.generated.resources.when_on_mark_the_table_complete
import mahjongtournamentsuite.adminapp.generated.resources.winner
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TableHandsCard(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
) {
    val seatLabels = seatLabels()
    val isHandsActive = !editor.useTotalsOnly
    var handsExpanded by remember { mutableStateOf(isHandsActive) }
    LaunchedEffect(isHandsActive) { handsExpanded = isHandsActive }
    val handsEnabled = enabled && isHandsActive
    val showChicken = LocalHandStatOptions.current.countChickenHands
    val doneCount = editor.hands.count { it.isDone }
    val subtitle = when {
        !editor.hasCompleteSeatPositions -> stringResource(Res.string.seat_positions_first)
        !isHandsActive -> stringResource(Res.string.not_used_manual)
        else -> null
    }
    val handSubtotals = editor.cumulativeHandScoreSubtotals
    val handResults = editor.handResultScores
    val rowNavigators = remember(editor.hands.map { it.handId }) {
        editor.hands.map { HandRowKeyboardNavigator() }
    }
    val stickyHost = LocalStickyHeaderHost.current
    val headerLineColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val headerHeightPx = with(density) { GridHeaderHeight.toPx() }
    // Top of the table inside the scrolled content. It does not change while the page scrolls.
    var tableContentTop by remember { mutableStateOf(0f) }
    var tableHeight by remember { mutableStateOf(0f) }

    SectionCard(
        title = stringResource(Res.string.hands),
        subtitle = subtitle,
        verticalSpacing = 20.dp,
        centerTitle = true,
        startContent = if (subtitle == null) {
            {
                Text(
                    text = stringResource(Res.string.hands_done_summary, doneCount, editor.hands.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        titleAction = {
            IconButton(
                onClick = { handsExpanded = !handsExpanded },
                buttonModifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (handsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (handsExpanded) Res.string.fold_hands else Res.string.unfold_hands),
                )
            }
        },
        actions = {
            LabeledSwitch(
                label = stringResource(Res.string.completed),
                description = stringResource(Res.string.when_on_mark_the_table_complete),
                checked = editor.isCompleted,
                enabled = handsEnabled,
                onCheckedChange = { editor.updateCompletedState(it) },
            )
        },
    ) {
        if (!handsExpanded) return@SectionCard
        if (editor.hands.isEmpty()) {
            AppEmptyState(
                icon = Icons.Default.Edit,
                title = stringResource(Res.string.no_hands_yet),
                message = stringResource(Res.string.the_hands_of_this_table_show),
                compact = true,
            )
            return@SectionCard
        }
        if (editor.hasInvalidHands) {
            Text(
                text = stringResource(Res.string.at_least_one_hand_is_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Column {
        GridTable(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                tableHeight = coordinates.size.height.toFloat()
                if (stickyHost != null) {
                    tableContentTop = coordinates.positionInRoot().y + stickyHost.scrollState.value
                }
            },
        ) {
            GridRow(
                height = GridHeaderHeight,
                topDivider = false,
                modifier = Modifier
                    .zIndex(1f)
                    .drawWithContent {
                        drawContent()
                        // While the header is stuck, close it with a line twice as thick as the table borders.
                        if (stickyHost != null && stickyHost.scrollState.value - (tableContentTop - stickyHost.viewportTop) > 0f) {
                            val thickness = 2.dp.toPx()
                            drawRect(
                                color = headerLineColor,
                                topLeft = Offset(0f, size.height - thickness),
                                size = Size(size.width, thickness),
                            )
                        }
                    }
                    .graphicsLayer {
                        if (stickyHost != null) {
                            val hidden = stickyHost.scrollState.value - (tableContentTop - stickyHost.viewportTop)
                            translationY = hidden.coerceIn(0f, (tableHeight - headerHeightPx).coerceAtLeast(0f))
                        }
                    },
            ) {
                GridHeaderCell(text = "#", modifier = Modifier.width(HandsIndexColumnWidth))
                GridHeaderCell(text = stringResource(Res.string.winner), modifier = Modifier.weight(HandsPlayerColumnWeight))
                GridHeaderCell(text = stringResource(Res.string.loser), modifier = Modifier.weight(HandsPlayerColumnWeight))
                GridHeaderCell(text = stringResource(Res.string.score), modifier = Modifier.width(HandsScoreColumnWidth))
                if (showChicken) GridHeaderCell(text = stringResource(Res.string.chicken), modifier = Modifier.width(HandsChickenColumnWidth))
                listOf(editor.playerEastId, editor.playerSouthId, editor.playerWestId, editor.playerNorthId)
                    .forEachIndexed { seat, id ->
                        GridHeaderCell(
                            text = seatLabels[seat],
                            icon = SeatWindIcons[seat],
                            subtext = playerNameOrNull(id, playerNamesById)?.asString(),
                            modifier = Modifier.weight(HandsSeatColumnWeight),
                        )
                    }
                GridHeaderCell(text = stringResource(Res.string.penalty), modifier = Modifier.width(HandsPenaltyColumnWidth))
                GridHeaderCell(text = stringResource(Res.string.done), modifier = Modifier.width(HandsDoneColumnWidth), last = true)
            }
            editor.hands.forEachIndexed { index, hand ->
                HandTableRow(
                    index = index,
                    hand = hand,
                    playerIds = editor.playerIds,
                    playerNamesById = playerNamesById,
                    enabled = handsEnabled,
                    forceDoneChecked = editor.isCompleted,
                    result = if (hand.isBlank) SeatTextValues.EMPTY else handResults.getOrNull(index).orZero(),
                    subtotal = if (hand.isBlank) SeatTextValues.EMPTY else handSubtotals.getOrNull(index).orZero(),
                    navigation = rowNavigators.getOrNull(index) ?: HandRowKeyboardNavigator(),
                    previousNavigation = rowNavigators.getOrNull(index - 1),
                    nextNavigation = rowNavigators.getOrNull(index + 1),
                )
            }
        }
        HandsTotalsRow(totals = editor.calculatedHandScoreTotals)
        }
    }
}

/** Totals under the hands table. They have no borders and use the same columns as the table. */
@Composable
private fun HandsTotalsRow(totals: SeatTextValues) {
    GridRow(height = GridRowHeight, topDivider = false, modifier = Modifier.padding(horizontal = 1.dp)) {
        GridCell(modifier = Modifier.width(HandsIndexColumnWidth), last = true) {}
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true, alignment = Alignment.CenterStart) {
            Text(text = stringResource(Res.string.total_scores), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true) {}
        GridCell(modifier = Modifier.width(HandsScoreColumnWidth), last = true) {}
        if (LocalHandStatOptions.current.countChickenHands) {
            GridCell(modifier = Modifier.width(HandsChickenColumnWidth), last = true) {}
        }
        listOf(totals.east, totals.south, totals.west, totals.north).forEach { value ->
            GridCell(modifier = Modifier.weight(HandsSeatColumnWeight), last = true) {
                Text(
                    text = value.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth), last = true) {}
        GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {}
    }
}

/** Chicken hand count and best hand scores, next to the section title. */
@Composable
internal fun HandSummaryTitleAction(editor: TableManagerEditorState, otherTablesBestHandScores: List<Int>) {
    // Only the best hands of the tournament show. The medal is the place in the tournament.
    val tournamentScores = topHandScores(otherTablesBestHandScores + editor.bestCompletedHands.map { it.first })
    val threshold = tournamentScores.lastOrNull()
    val options = LocalHandStatOptions.current
    val bestHands = editor.bestCompletedHands.filter { options.countBestHands && threshold != null && it.first >= threshold }
    val chickenHandCount = if (options.countChickenHands) editor.chickenHandCount else 0
    if (chickenHandCount == 0 && bestHands.isEmpty()) return
    // Each stat is a group: icon, value and winner. The groups have the same space between them.
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chickenHandCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChickenIcon(contentDescription = stringResource(Res.string.chicken_hands))
                Text("$chickenHandCount", style = MaterialTheme.typography.titleLarge)
            }
        }
        bestHands.forEach { (score, winnerId) ->
            // Tied scores share the place.
            val place = tournamentScores.count { it > score } + 1
            val medal = when (place) {
                1 -> TrophyMedal.Gold
                2 -> TrophyMedal.Silver
                else -> TrophyMedal.Bronze
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TrophyIcon(contentDescription = stringResource(Res.string.tournament_best_hand_n, place), medal = medal)
                Text("$score", style = MaterialTheme.typography.titleLarge)
                winnerId?.let {
                    Text(
                        "#$it",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        InfoTooltipIcon(
            description = stringResource(Res.string.calculated_from_hands_trophies_show_the),
            contentDescription = stringResource(Res.string.show_how_the_chicken_and_best),
        )
    }
}
