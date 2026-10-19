package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    data class Result(val hand: TableHand, val change: List<Int>?) : DetailRow
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
        rows += DetailRow.Result(hand, change)
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
        Text("The seats of this table are not set yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val rows = detailRows(table, seatIds)
    if (rows.isEmpty()) {
        Text("No hand saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Hand", HAND_WEIGHT)
            HeaderCell("W", ID_WEIGHT)
            HeaderCell("L", ID_WEIGHT)
            HeaderCell("Pts", POINTS_WEIGHT)
            seatIds.forEachIndexed { seat, id ->
                Column(Modifier.weight(SEAT_WEIGHT), horizontalAlignment = Alignment.CenterHorizontally) {
                    windOf(seat)?.let { WindIcon(it, size = 14.dp) }
                    Text("$id", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        HorizontalDivider()
        rows.forEach { row ->
            when (row) {
                is DetailRow.Result -> ResultLine(row)
                is DetailRow.Penalty -> PenaltyLine(row)
                is DetailRow.Subtotal -> SubtotalLine(row)
            }
        }
    }
}

private const val HAND_WEIGHT = 1.1f
private const val ID_WEIGHT = 0.6f
private const val POINTS_WEIGHT = 0.8f
private const val SEAT_WEIGHT = 1f

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NumberCell(
    value: Int?,
    bold: Boolean = false,
    muted: Boolean = false,
) {
    val color = when {
        value == null || value == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        muted -> MaterialTheme.colorScheme.onSurfaceVariant
        value > 0 -> DetailGain
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        if (value == null) "" else if (value > 0) "+$value" else "$value",
        Modifier.weight(SEAT_WEIGHT),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun ResultLine(row: DetailRow.Result) {
    val hand = row.hand
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(HAND_WEIGHT), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            HandLabel(hand.handId)
        }
        Text(hand.playerWinnerId.ifBlank { "-" }, Modifier.weight(ID_WEIGHT), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(hand.playerLooserId.ifBlank { "-" }, Modifier.weight(ID_WEIGHT), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Row(Modifier.weight(POINTS_WEIGHT), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(hand.handScore.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            if (hand.isChickenHand) ChickenIcon(12.dp)
        }
        repeat(4) { seat -> NumberCell(row.change?.get(seat)) }
    }
}

@Composable
private fun PenaltyLine(row: DetailRow.Penalty) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Pen",
            Modifier.weight(HAND_WEIGHT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        // The penalty has no winner, loser or hand score.
        Text("", Modifier.weight(ID_WEIGHT * 2 + POINTS_WEIGHT))
        repeat(4) { seat -> NumberCell(row.values[seat], muted = !row.counted) }
    }
}

@Composable
private fun SubtotalLine(row: DetailRow.Subtotal) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Total",
            Modifier.weight(HAND_WEIGHT + ID_WEIGHT * 2 + POINTS_WEIGHT),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        repeat(4) { seat -> NumberCell(row.totals[seat], bold = true) }
    }
}
