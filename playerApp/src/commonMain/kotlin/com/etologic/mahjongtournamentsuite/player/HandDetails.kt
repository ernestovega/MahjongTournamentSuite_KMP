package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.etologic.mahjongtournamentsuite.presentation.components.LocalScreenScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.HandLabel
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.WindIcon

private const val MIN_HAND_SCORE = 8
private const val LOSER_COUNT = 3

private val DetailGain = Color(0xFF00934C)

/**
 * The penalties of a hand. Each ";" separated part of the seat texts is one penalty.
 * A row has the values of the seats in the order east, south, west, north. Rows with only zeros are dropped.
 */
fun TableHand.penaltyRows(): List<List<Int>> {
    val parts = listOf(playerEastPenalty, playerSouthPenalty, playerWestPenalty, playerNorthPenalty)
        .map { text -> text.split(';').map { it.trim() } }
    val rows = parts.maxOf { it.size }
    return (0 until rows)
        .map { row -> parts.map { seat -> seat.getOrNull(row)?.toIntOrNull() ?: 0 } }
        .filter { values -> values.any { it != 0 } }
}

/** The number of penalties of all hands of the table. The screen uses it to see that a new penalty arrived. */
val PlayerTable.penaltyCount: Int get() = hands.sumOf { it.penaltyRows().size }

/**
 * The score change of the four seats from the result of a hand, without penalties.
 * Null when the hand has no result that counts. A draw changes nothing.
 */
fun TableHand.resultChange(seatIds: List<Int>): List<Int>? {
    val winnerText = playerWinnerId.trim()
    val loserText = playerLooserId.trim().let { if (it == "-") "" else it }
    val scoreText = handScore.trim()
    if (winnerText.isEmpty() && loserText.isEmpty() && (scoreText.isEmpty() || scoreText == "0")) {
        return if (scoreText == "0" && !isChickenHand) List(4) { 0 } else null
    }
    if (winnerText.isEmpty() || scoreText.isEmpty()) return null
    val score = scoreText.toIntOrNull()?.takeIf { it >= MIN_HAND_SCORE } ?: return null
    val winnerSeat = seatIds.indexOf(winnerText.toIntOrNull() ?: return null).takeIf { it >= 0 } ?: return null
    val loserSeat = if (loserText.isEmpty()) {
        null
    } else {
        seatIds.indexOf(loserText.toIntOrNull() ?: return null).takeIf { it >= 0 && it != winnerSeat } ?: return null
    }
    return List(4) { seat ->
        when {
            loserSeat == null -> if (seat == winnerSeat) (score + MIN_HAND_SCORE) * LOSER_COUNT else -(score + MIN_HAND_SCORE)
            seat == winnerSeat -> score + MIN_HAND_SCORE * LOSER_COUNT
            seat == loserSeat -> -(score + MIN_HAND_SCORE)
            else -> -MIN_HAND_SCORE
        }
    }
}

/** One line of the detail table. */
private sealed interface DetailRow {
    /** [winnerSeat] and [loserSeat] are seat positions. A null [loserSeat] with a winner means a win from the wall. */
    data class Result(val hand: TableHand, val change: List<Int>?, val winnerSeat: Int?, val loserSeat: Int?) : DetailRow
    data class Penalty(val handId: Int, val values: List<Int>, val counted: Boolean) : DetailRow
    data class Subtotal(val totals: List<Int>) : DetailRow
}

private fun detailRows(table: PlayerTable, seatIds: List<Int>): List<DetailRow> {
    val rows = mutableListOf<DetailRow>()
    val totals = MutableList(4) { 0 }
    table.hands.sortedBy { it.handId }.forEach { hand ->
        // The penalties were given before the result of the hand, so they come first.
        hand.penaltyRows().forEach { values ->
            rows += DetailRow.Penalty(hand.handId, values, counted = hand.isDone)
            if (hand.isDone) values.forEachIndexed { seat, value -> totals[seat] += value }
        }
        if (!hand.isDone) return@forEach
        val change = hand.resultChange(seatIds)
        val winnerSeat = seatIds.indexOf(hand.playerWinnerId.trim().toIntOrNull()).takeIf { change != null && it >= 0 }
        val loserSeat = seatIds.indexOf(hand.playerLooserId.trim().toIntOrNull()).takeIf { change != null && it >= 0 }
        rows += DetailRow.Result(hand, change, winnerSeat, loserSeat)
        change?.forEachIndexed { seat, value -> totals[seat] += value }
        rows += DetailRow.Subtotal(totals.toList())
    }
    return rows
}

/**
 * The detailed list of the hands: the result of each hand with its penalties and the running totals of the table.
 * The space is small, so the winner and the loser are player ids. The header shows the id of each seat.
 */
@Composable
fun HandDetailsTable(table: PlayerTable, windOf: (Int) -> SeatWind?) {
    val seatIds = table.seatIds().filterNotNull()
    if (seatIds.size != 4) {
        AppEmptyState(Icons.Default.People, "No seats yet", "The seats of this table are not set yet.", compact = true)
        return
    }
    val rows = detailRows(table, seatIds)
    if (rows.isEmpty()) {
        AppEmptyState(Icons.Default.Edit, "No hands yet", "The hands show here when the players save them.", compact = true)
        return
    }
    val screen = LocalScreenScroll.current
    var headerTop by remember { mutableStateOf(0f) }
    var headerHeight by remember { mutableStateOf(0) }
    var tableHeight by remember { mutableStateOf(0) }
    var headerInTable by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().onSizeChanged { tableHeight = it.height }) {
        // The header sticks at the top of the screen while the list scrolls. It stops at the end of the table.
        Column(
            Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .onGloballyPositioned {
                    headerTop = it.positionInRoot().y - (screen?.viewportTop ?: 0f) + (screen?.state?.value ?: 0)
                    headerInTable = it.positionInParent().y.toInt()
                }
                .onSizeChanged { headerHeight = it.height }
                .offset {
                    val limit = (tableHeight - headerInTable - headerHeight).coerceAtLeast(0)
                    val pushed = ((screen?.state?.value ?: 0) - headerTop).toInt()
                    // The offset moves the header, not the position that the next frame measures.
                    IntOffset(0, pushed.coerceIn(0, limit))
                }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell("Hand", HAND_WEIGHT)
                HeaderCell("W", ID_WEIGHT)
                HeaderCell("L", ID_WEIGHT)
                HeaderCell("Pts", POINTS_WEIGHT)
                Spacer(Modifier.weight(CHICKEN_WEIGHT))
                seatIds.forEachIndexed { seat, id ->
                    Row(Modifier.weight(SEAT_WEIGHT), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        windOf(seat)?.let { WindIcon(it, size = 16.dp) }
                        Text(
                            "$id",
                            Modifier.padding(start = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
        rows.forEach { row ->
            when (row) {
                is DetailRow.Result -> ResultLine(row)
                is DetailRow.Penalty -> PenaltyLine(row)
                is DetailRow.Subtotal -> SubtotalLine(row)
            }
        }
    }
}

private const val HAND_WEIGHT = 1.0f
private const val ID_WEIGHT = 0.6f
private const val POINTS_WEIGHT = 0.8f
private const val CHICKEN_WEIGHT = 0.65f
private const val SEAT_WEIGHT = 1f

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NumberCell(
    value: Int?,
    color: Color,
    bold: Boolean = false,
) {
    Text(
        if (value == null) "" else if (value > 0) "+$value" else "$value",
        Modifier.weight(SEAT_WEIGHT),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** The color of a penalty: red for the penalized player and green for a player who receives points. */
@Composable
private fun penaltyColor(value: Int, muted: Boolean): Color = when {
    value == 0 || muted -> MaterialTheme.colorScheme.onSurfaceVariant
    value > 0 -> DetailGain
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun ResultLine(row: DetailRow.Result) {
    val hand = row.hand
    val bodyStyle = MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(HAND_WEIGHT), horizontalAlignment = Alignment.CenterHorizontally) {
            HandLabel(hand.handId)
            Text("#${hand.handId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(hand.playerWinnerId.ifBlank { "-" }, Modifier.weight(ID_WEIGHT), style = bodyStyle, textAlign = TextAlign.Center)
        Text(hand.playerLooserId.ifBlank { "-" }, Modifier.weight(ID_WEIGHT), style = bodyStyle, textAlign = TextAlign.Center)
        Text(hand.handScore.ifBlank { "-" }, Modifier.weight(POINTS_WEIGHT), style = bodyStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        // The chicken has its own column, so it keeps its size and does not move the score.
        Box(Modifier.weight(CHICKEN_WEIGHT), contentAlignment = Alignment.Center) {
            if (hand.isChickenHand) ChickenIcon(CHICKEN_SIZE)
        }
        // Only the winner is green. The loser of a discard is red. After a win from the wall all the others are red.
        repeat(4) { seat ->
            val color = when {
                row.winnerSeat == null -> MaterialTheme.colorScheme.onSurface
                seat == row.winnerSeat -> DetailGain
                row.loserSeat == null || seat == row.loserSeat -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            NumberCell(row.change?.get(seat), color)
        }
    }
}

private val CHICKEN_SIZE = 20.dp

@Composable
private fun PenaltyLine(row: DetailRow.Penalty) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Pen",
            Modifier.weight(HAND_WEIGHT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        // The penalty has no winner, loser or hand score.
        Text("", Modifier.weight(ID_WEIGHT * 2 + POINTS_WEIGHT + CHICKEN_WEIGHT))
        repeat(4) { seat -> NumberCell(row.values[seat], penaltyColor(row.values[seat], muted = !row.counted)) }
    }
}

@Composable
private fun SubtotalLine(row: DetailRow.Subtotal) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(HAND_WEIGHT + ID_WEIGHT * 2 + POINTS_WEIGHT + CHICKEN_WEIGHT))
        repeat(4) { seat -> NumberCell(row.totals[seat], MaterialTheme.colorScheme.onSurface, bold = true) }
    }
}
