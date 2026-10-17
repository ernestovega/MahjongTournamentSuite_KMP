package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.presentation.components.chairOf
import com.etologic.mahjongtournamentsuite.presentation.components.windOfChair
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val WINDS = listOf("East", "South", "West", "North")

/**
 * Hand entry for the player who writes the scores down.
 * The player enters the hand, reviews the result with everyone at the table and then confirms.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HandEntryPanel(
    tournamentId: String,
    roundId: Int,
    tableId: Int,
    view: PlayerTournamentView,
    table: PlayerTable,
    onSaved: () -> Unit,
) {
    val presenter = koinInject<PlayerTablePresenter>()
    val scope = rememberCoroutineScope()
    val mine = view.mySlotId
    val seats = table.seatIds()
    val tablePlayers = table.table.playerIds
    // Only a player of the table can write for it. A completed table is closed.
    if (mine == null || mine !in tablePlayers || table.table.isCompleted) return
    val seatsAssigned = seats.none { it == null }
    var changingSeats by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<String?>(null) }
    var seatBusy by remember { mutableStateOf(false) }
    var seatMessage by remember { mutableStateOf<String?>(null) }
    val seatIds = seats.filterNotNull()

    var winner by remember { mutableStateOf<Int?>(null) }
    var loser by remember { mutableStateOf<Int?>(null) }
    var selfDraw by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf("") }
    var chicken by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<HandPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val parsed = score.trim().toIntOrNull()
    val valid = winner != null && parsed != null && parsed >= 8 &&
        (selfDraw || (loser != null && loser != winner)) &&
        (!chicken || (!selfDraw && parsed <= 12))
    fun proposal() = HandProposal(
        winnerSlotId = winner!!,
        loserSlotId = if (selfDraw) null else loser,
        score = parsed!!,
        isChickenHand = chicken,
    )

    // The table is not what the player saw, for example because a staff member saved a hand.
    // The entered hand stays in the form, so it is not lost. The screen loads the server data and
    // the dialog shows both. The player closes it to go on, or calls a staff member first.
    fun tableChanged(text: String) {
        conflict = text
        preview = null
        onSaved()
    }

    conflict?.let { text ->
        TableChangedDialog(
            serverMessage = text,
            view = view,
            table = table,
            enteredHand = if (winner != null && parsed != null) {
                EnteredHand(winner!!, if (selfDraw) null else loser, parsed, chicken)
            } else {
                null
            },
            onClose = { conflict = null },
        )
    }

    var open by rememberSaveable { mutableStateOf(false) }
    // The winds turn after every hand, so the label follows the hand in play.
    val handInPlay = minOf(table.doneHands + 1, 16)
    fun windOf(slot: Int): String = seats.indexOf(slot).takeIf { it >= 0 }
        ?.let { windOfChair(chairOf(it, handInPlay), handInPlay).label + " -" }
        .orEmpty()
    if (table.table.useTotalsOnly) {
        Text("The organizers enter only the final scores of this table. You cannot add hands here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Button(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text(if (seatsAssigned) "Add hand ${table.doneHands + 1}" else "Set the seats")
        }
    }
    if (open) ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val review = preview
            if (!seatsAssigned || changingSeats) {
                SeatEditor(
                    view = view,
                    tablePlayers = tablePlayers,
                    current = seats,
                    busy = seatBusy,
                    message = seatMessage,
                    canCancel = seatsAssigned,
                    onCancel = { changingSeats = false; seatMessage = null },
                    onSave = { chosen ->
                        scope.launch {
                            seatBusy = true
                            seatMessage = null
                            when (val result = presenter.assignSeats(tournamentId, roundId, tableId, chosen, table.table.version)) {
                                is AppResult.Success -> {
                                    changingSeats = false
                                    onSaved()
                                }
                                is AppResult.Failure ->
                                    if (result.error is AppError.Conflict) tableChanged(result.error.toUiMessage())
                                    else seatMessage = result.error.toUiMessage()
                            }
                            seatBusy = false
                        }
                    },
                )
            } else if (table.table.useTotalsOnly) {
                Text("The organizers enter only the final scores of this table. You cannot add hands here.")
            } else if (review == null) {
                Text("Enter the next hand", style = MaterialTheme.typography.titleMedium)
                Text("Winner")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seatIds.forEach { slot ->
                        FilterChip(
                            selected = winner == slot,
                            onClick = { winner = slot; if (loser == slot) loser = null },
                            label = { Text("${windOf(slot)} ${view.nameOf(slot)}".trim()) },
                        )
                    }
                }
                Row {
                    Checkbox(checked = selfDraw, onCheckedChange = { selfDraw = it; if (it) { loser = null; chicken = false } })
                    Text("Self draw", Modifier.padding(top = 12.dp))
                }
                if (!selfDraw) {
                    Text("Loser (who discarded)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seatIds.filter { it != winner }.forEach { slot ->
                            FilterChip(
                                selected = loser == slot,
                                onClick = { loser = slot },
                                label = { Text("${windOf(slot)} ${view.nameOf(slot)}".trim()) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = score,
                    onValueChange = { score = it.filter(Char::isDigit) },
                    label = { Text("Hand score (8 or more)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!selfDraw) {
                    Row {
                        Checkbox(checked = chicken, onCheckedChange = { chicken = it })
                        Text("Chicken hand (12 or less)", Modifier.padding(top = 12.dp))
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = valid && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            when (val result = presenter.preview(tournamentId, roundId, tableId, proposal())) {
                                is AppResult.Success -> preview = result.value
                                is AppResult.Failure ->
                                    if (result.error is AppError.Conflict) tableChanged(result.error.toUiMessage())
                                    else message = result.error.toUiMessage()
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Review") }
                if (table.doneHands == 0) {
                    TextButton(enabled = !busy, onClick = { changingSeats = true }) { Text("Change seats") }
                }
            } else {
                ReviewHand(review, view, selfDraw, winner, loser, parsed ?: 0, chicken)
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { preview = null; message = null }) { Text("Edit") }
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                when (val result = presenter.record(tournamentId, roundId, tableId, proposal(), review.tableVersion)) {
                                    is AppResult.Success -> {
                                        preview = null
                                        winner = null
                                        loser = null
                                        selfDraw = false
                                        score = ""
                                        chicken = false
                                        open = false
                                        onSaved()
                                    }
                                    is AppResult.Failure ->
                                        if (result.error is AppError.Conflict) {
                                            tableChanged(result.error.toUiMessage())
                                        } else {
                                            message = result.error.toUiMessage()
                                            preview = null
                                        }
                                }
                                busy = false
                            }
                        },
                    ) { Text("Confirm and save") }
                }
            }
        }
    }
}

/** Shows the result clearly so that the player can show it to the table before saving. */
@Composable
private fun ReviewHand(
    preview: HandPreview,
    view: PlayerTournamentView,
    selfDraw: Boolean,
    winner: Int?,
    loser: Int?,
    score: Int,
    chicken: Boolean,
) {
    Text("Check hand ${preview.handId} with the table", style = MaterialTheme.typography.titleMedium)
    Text("Winner: ${view.nameOf(winner)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        if (selfDraw) "Self draw" else "Loser: ${view.nameOf(loser)}",
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        "Hand score: $score" + if (chicken) " (chicken hand)" else "",
        style = MaterialTheme.typography.titleLarge,
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Player", Modifier.weight(1.6f), fontWeight = FontWeight.Bold)
        Text("This hand", Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text("Table", Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text("Points", Modifier.weight(0.8f), fontWeight = FontWeight.Bold)
    }
    preview.seatSlotIds.forEachIndexed { index, slot ->
        val change = preview.handScores.getOrNull(index) ?: 0
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${windOfChair(chairOf(index, preview.handId), preview.handId).label} ${view.nameOf(slot)}",
                Modifier.weight(1.6f),
            )
            Text(if (change > 0) "+$change" else "$change", Modifier.weight(1f))
            Text(preview.tableScores.getOrNull(index).orEmpty(), Modifier.weight(1f))
            Text(preview.tablePoints.getOrNull(index).orEmpty(), Modifier.weight(0.8f))
        }
    }
}

/** A player of the table chooses who sits at each wind. Every player takes one seat. */
@Composable
private fun SeatEditor(
    view: PlayerTournamentView,
    tablePlayers: List<Int>,
    current: List<Int?>,
    busy: Boolean,
    message: String?,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (List<Int>) -> Unit,
) {
    var chosen by remember { mutableStateOf(current.toList()) }
    Text("Seats of the table", style = MaterialTheme.typography.titleMedium)
    Text("Choose who sits at each wind. You can change it until the first hand is saved.")
    WINDS.forEachIndexed { index, wind ->
        Text(wind, fontWeight = FontWeight.Bold)
        tablePlayers.forEach { slot ->
            FilterChip(
                selected = chosen[index] == slot,
                onClick = {
                    // A player has one seat. Taking a player from another wind frees that wind.
                    chosen = chosen.mapIndexed { i, value ->
                        when {
                            i == index -> slot
                            value == slot -> null
                            else -> value
                        }
                    }
                },
                label = { Text(view.nameOf(slot)) },
            )
        }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (canCancel) OutlinedButton(enabled = !busy, onClick = onCancel) { Text("Cancel") }
        Button(
            enabled = !busy && chosen.none { it == null },
            onClick = { onSave(chosen.filterNotNull()) },
        ) { Text("Save seats") }
    }
}

private data class EnteredHand(val winner: Int, val loser: Int?, val score: Int, val chicken: Boolean)

/**
 * Tells the player that the table changed on the server. It shows the data of the server and the hand that
 * the player entered, so nothing is lost. The player can close it and go on, or call a staff member.
 */
@Composable
private fun TableChangedDialog(
    serverMessage: String,
    view: PlayerTournamentView,
    table: PlayerTable,
    enteredHand: EnteredHand?,
    onClose: () -> Unit,
) {
    val t = table.table
    val points = t.seatPoints
    val rows = listOf(
        Triple("East", t.playerEastId, t.playerEastScore to points[0]),
        Triple("South", t.playerSouthId, t.playerSouthScore to points[1]),
        Triple("West", t.playerWestId, t.playerWestScore to points[2]),
        Triple("North", t.playerNorthId, t.playerNorthScore to points[3]),
    )
    val lastHand = table.hands.filter { it.isDone }.maxByOrNull { it.handId }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("The table changed") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(serverMessage)
                Text("Your hand is not saved. Check the data of the server below.")
                Text("Table now", fontWeight = FontWeight.Bold)
                rows.forEach { (wind, id, result) ->
                    Text(
                        "$wind ${view.nameOf(id.toIntOrNull())}: ${result.first.ifBlank { "—" }}" +
                            " (${result.second.ifBlank { "—" }} points)",
                    )
                }
                Text("Saved hands: ${table.doneHands} of ${table.hands.size}")
                lastHand?.let { hand ->
                    val loser = hand.playerLooserId.toIntOrNull()
                    Text(
                        "Last hand ${hand.handId}: ${view.nameOf(hand.playerWinnerId.toIntOrNull())} won with ${hand.handScore}" +
                            if (loser != null) " from ${view.nameOf(loser)}" else " (self draw)",
                    )
                }
                if (enteredHand != null) {
                    Text("Your hand, not saved", fontWeight = FontWeight.Bold)
                    Text(
                        "${view.nameOf(enteredHand.winner)} won with ${enteredHand.score}" +
                            (if (enteredHand.loser != null) " from ${view.nameOf(enteredHand.loser)}" else " (self draw)") +
                            (if (enteredHand.chicken) " - chicken hand" else ""),
                    )
                    Text("If the hand is already in the table, do not enter it again.")
                }
                Text("You can call a staff member to check the table, or close this window and go on.")
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("Close and continue") } },
    )
}
