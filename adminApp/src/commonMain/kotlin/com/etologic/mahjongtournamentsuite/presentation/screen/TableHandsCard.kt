package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.etologic.mahjongtournamentsuite.domain.model.topHandScores
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.BestHandIcon
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
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
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

@Composable
internal fun TableHandsCard(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
) {
    val isHandsActive = !editor.useTotalsOnly
    var handsExpanded by remember { mutableStateOf(isHandsActive) }
    LaunchedEffect(isHandsActive) { handsExpanded = isHandsActive }
    val handsEnabled = enabled && isHandsActive
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
    // The top shadow of the scroll goes under the stuck header, not over it.
    if (stickyHost != null) {
        DisposableEffect(stickyHost) {
            stickyHost.pinnedHeaderEnd = {
                val hidden = stickyHost.scrollState.value - (tableContentTop - stickyHost.viewportTop)
                if (hidden <= 0f) {
                    0f
                } else {
                    hidden.coerceIn(0f, (tableHeight - headerHeightPx).coerceAtLeast(0f)) - hidden + headerHeightPx
                }
            }
            onDispose { stickyHost.pinnedHeaderEnd = { 0f } }
        }
    }

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
        val compact = LocalCompactLayout.current
        val seatIds = listOf(editor.playerEastId, editor.playerSouthId, editor.playerWestId, editor.playerNorthId).map { it.trim() }
        val seatFullNames = seatIds.map { playerNameOrNull(it, playerNamesById)?.asString() ?: "-" }
        // The buttons and the summaries use the first name: four of them fit in the width of a phone.
        val seatShortNames = seatFullNames.map { it.substringBefore(' ') }
        // One hand is open: the first one that is not done. Ticking Done closes it and opens the next one.
        val doneStates = editor.hands.map { it.isDone }
        var expandedIds by remember { mutableStateOf(setOfNotNull(editor.hands.firstOrNull { !it.isDone }?.handId)) }
        var previousDone by remember { mutableStateOf(doneStates) }
        LaunchedEffect(doneStates) {
            val newlyDone = editor.hands.filterIndexed { i, hand -> hand.isDone && previousDone.getOrNull(i) == false }
            if (newlyDone.isNotEmpty() && previousDone.size == doneStates.size) {
                val next = editor.hands.firstOrNull { !it.isDone }?.handId
                expandedIds = expandedIds - newlyDone.map { it.handId }.toSet() + setOfNotNull(next)
            }
            previousDone = doneStates
        }
        val gap = if (compact) 8.dp else 6.dp
        Column(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                tableHeight = coordinates.size.height.toFloat()
                if (stickyHost != null) {
                    tableContentTop = coordinates.positionInRoot().y + stickyHost.scrollState.value
                }
            },
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            HandsHeader(
                seatNames = seatFullNames,
                wide = !compact,
                modifier = Modifier
                    .zIndex(1f)
                    .drawWithContent {
                        drawContent()
                        // While the header is stuck, close it with a line twice as thick as the borders.
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
            )
            editor.hands.forEachIndexed { index, hand ->
                HandCard(
                        index = index,
                        hand = hand,
                        seatPlayerIds = seatIds,
                        seatNames = seatShortNames,
                        enabled = handsEnabled,
                        forceDoneChecked = editor.isCompleted,
                        canMarkDone = editor.canMarkDone(hand),
                        result = if (hand.isBlank) SeatTextValues.EMPTY else handResults.getOrNull(index) ?: SeatTextValues.EMPTY,
                        subtotal = handSubtotals.getOrNull(index) ?: SeatTextValues.EMPTY,
                        preview = if (hand.handId in expandedIds) editor.previewFor(index) else null,
                        expanded = hand.handId in expandedIds,
                        onExpandedChange = { open -> expandedIds = if (open) expandedIds + hand.handId else expandedIds - hand.handId },
                        wide = !compact,
                        navigation = rowNavigators.getOrNull(index) ?: HandRowKeyboardNavigator(),
                        previousNavigation = rowNavigators.getOrNull(index - 1),
                        nextNavigation = rowNavigators.getOrNull(index + 1),
                    )
            }
        }
        HandsTotalsRow(totals = editor.calculatedHandScoreTotals, wide = !compact, seatNames = seatFullNames)
    }
}

/**
 * The wind and the player of each seat, over the hands. On a wide screen it has the columns of the hands: the number,
 * the summary, the Σ sign, the four seats, Done and the arrow. It stays at the top while the hands scroll.
 */
@Composable
private fun HandsHeader(seatNames: List<String>, wide: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
            .height(GridHeaderHeight)
            .padding(horizontal = if (wide) 5.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            Text("#", Modifier.width(HandNumberWidth), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(HandSummaryWeight))
        }
        Spacer(Modifier.width(SigmaColumnWidth))
        seatNames.forEachIndexed { seat, name ->
            Column(Modifier.weight(1f).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SeatWindIcon(seat, Modifier.size(18.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (wide) {
            Text(
                text = stringResource(Res.string.done),
                modifier = Modifier.width(HandDoneWidth),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(HandArrowWidth))
        }
    }
}

/** The total of each seat under the hands, in the seat columns of the header. */
@Composable
private fun HandsTotalsRow(totals: SeatTextValues, wide: Boolean, seatNames: List<String>) {
    if (!wide) {
        Text(
            text = stringResource(Res.string.total_scores),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 8.dp),
        )
        SeatValuesRow(values = totals, names = seatNames)
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(HandNumberWidth))
        Text(
            text = stringResource(Res.string.total_scores),
            modifier = Modifier.weight(HandSummaryWeight),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(SigmaColumnWidth))
        listOf(totals.east, totals.south, totals.west, totals.north).forEach { value ->
            Text(
                text = value.ifBlank { "-" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.inTextFont().copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(HandDoneWidth + HandArrowWidth))
    }
}

/** True when [HandSummaryTitleAction] shows something: a chicken hand or a best hand of the tournament. */
@Composable
internal fun hasHandSummary(editor: TableManagerEditorState, otherTablesBestHandScores: List<Int>): Boolean {
    val options = LocalHandStatOptions.current
    val chickenHandCount = if (options.countChickenHands) editor.chickenHandCount else 0
    return chickenHandCount > 0 || summaryBestHands(editor, otherTablesBestHandScores).isNotEmpty()
}

/** The best hands of this table that are among the best hands of the tournament. */
@Composable
private fun summaryBestHands(editor: TableManagerEditorState, otherTablesBestHandScores: List<Int>) = run {
    // Only the best hands of the tournament show. The medal is the place in the tournament.
    val tournamentScores = topHandScores(otherTablesBestHandScores + editor.bestCompletedHands.map { it.first })
    val threshold = tournamentScores.lastOrNull()
    val options = LocalHandStatOptions.current
    editor.bestCompletedHands.filter { options.countBestHands && threshold != null && it.first >= threshold }
}

/** Chicken hand count and best hand scores, next to the section title. */
@Composable
internal fun HandSummaryTitleAction(editor: TableManagerEditorState, otherTablesBestHandScores: List<Int>) {
    val tournamentScores = topHandScores(otherTablesBestHandScores + editor.bestCompletedHands.map { it.first })
    val options = LocalHandStatOptions.current
    val bestHands = summaryBestHands(editor, otherTablesBestHandScores)
    val chickenHandCount = if (options.countChickenHands) editor.chickenHandCount else 0
    if (chickenHandCount == 0 && bestHands.isEmpty()) return
    // Each stat is a group: icon, value and winner. The groups have the same space between them.
    Row(
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chickenHandCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChickenIcon(contentDescription = stringResource(Res.string.chicken_hands))
                Text("$chickenHandCount", style = MaterialTheme.typography.titleLarge.inTextFont())
            }
        }
        bestHands.forEach { (score, winnerId) ->
            // Tied scores share the place.
            val place = tournamentScores.count { it > score } + 1
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BestHandIcon(place, contentDescription = stringResource(Res.string.tournament_best_hand_n, place))
                Text("$score", style = MaterialTheme.typography.titleLarge.inTextFont())
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
