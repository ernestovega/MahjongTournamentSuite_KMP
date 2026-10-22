package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.HandLabel
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.WindIcon
import com.etologic.mahjongtournamentsuite.presentation.components.chairOf
import com.etologic.mahjongtournamentsuite.presentation.components.windOfChair
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import org.koin.compose.koinInject

private val GainColor = Color(0xFF00934C)

/** A penalty that the player is writing. The amount is a positive number. The app shows the minus sign. */
private data class PenaltyDraft(val seat: Int? = null, val amount: String = "", val shared: Boolean = false) {
    fun toEntry() = PenaltyEntry(seat ?: -1, amount.toIntOrNull() ?: 0, shared)
}

private const val MAX_PENALTIES = 5

/** The three things that a player can add to the table. */
private enum class EntryMode { WIN, DRAW, PENALTY }

/**
 * Entry for the player who writes the scores down. It has three buttons: add a win, add a draw and add a penalty.
 * A win or a draw ends the hand in play. A penalty does not end it.
 * The player enters the data, reviews it with everyone at the table and then confirms.
 * The seats come from the staff. The player cannot change them.
 */
@Composable
fun HandEntryPanel(
    tournamentId: String,
    roundId: Int,
    tableId: Int,
    view: PlayerTournamentView,
    table: PlayerTable,
    now: LocalDateTime,
    /** A player that the table screen chose as the winner. The win dialog opens with this player selected. */
    preselectWinner: Int? = null,
    onPreselectHandled: () -> Unit = {},
    onSaved: () -> Unit,
) {
    val presenter = koinInject<PlayerTablePresenter>()
    val scope = rememberCoroutineScope()
    val mine = view.mySlotId
    val seats = table.seatIds()
    // Only a player of the table can write for it. A table takes hands only while it is ongoing.
    if (mine == null || mine !in table.table.playerIds || table.isComplete) return
    val seatsAssigned = seats.none { it == null }
    val seatIds = seats.filterNotNull()

    var conflict by remember { mutableStateOf<String?>(null) }
    var winner by remember { mutableStateOf<Int?>(null) }
    var loser by remember { mutableStateOf<Int?>(null) }
    var selfDraw by remember { mutableStateOf(false) }
    val penaltyDrafts = remember { mutableStateListOf<PenaltyDraft>() }
    var penaltyReview by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf("") }
    var chicken by remember { mutableStateOf(false) }
    var lastHand by remember { mutableStateOf(false) }
    // When the time is over, the players can record the hand in play. That hand is the last hand of the table.
    val phase = view.matchPhaseOf(table.roundId, now)
    val extraTime = phase == MatchPhase.EXTRA_TIME
    var preview by remember { mutableStateOf<HandPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var modeName by rememberSaveable { mutableStateOf("") }
    val mode = EntryMode.entries.firstOrNull { it.name == modeName }
    val isDraw = mode == EntryMode.DRAW
    // What the table shows when the server accepted the data. The dialog stays open until the table shows something new.
    val progressKey = table.doneHands * 100 + table.penaltyCount
    var awaitingKey by remember { mutableStateOf<Int?>(null) }

    val parsed = score.trim().toIntOrNull()
    val chickenTooHigh = chicken && parsed != null && parsed > 12
    val penaltyEntries = penaltyDrafts.map { it.toEntry() }
    val valid = isDraw || winner != null && parsed != null && parsed >= 8 &&
        (selfDraw || (loser != null && loser != winner)) &&
        (!chicken || (!selfDraw && !chickenTooHigh))
    fun proposal() = if (isDraw) {
        HandProposal(winnerSlotId = null, loserSlotId = null, score = 0, isLastHand = lastHand || extraTime)
    } else HandProposal(
        winnerSlotId = winner!!,
        loserSlotId = if (selfDraw) null else loser,
        score = parsed!!,
        isChickenHand = chicken,
        isLastHand = lastHand || extraTime,
    )

    fun finishEntry() {
        preview = null
        penaltyReview = false
        winner = null
        loser = null
        selfDraw = false
        penaltyDrafts.clear()
        score = ""
        chicken = false
        lastHand = false
        awaitingKey = null
        message = null
        modeName = ""
    }

    // The table is not what the player saw, for example because a staff member saved a hand.
    // The entered data stays in the form, so it is not lost. The screen loads the server data and
    // the dialog shows both. The player closes it to go on, or calls a staff member first.
    fun tableChanged(text: String) {
        conflict = text
        preview = null
        penaltyReview = false
        onSaved()
    }

    conflict?.let { text ->
        TableChangedDialog(
            serverMessage = text,
            view = view,
            table = table,
            enteredHand = if (mode == EntryMode.PENALTY) {
                null
            } else if (isDraw) {
                EnteredHand(null, null, 0, false)
            } else if (winner != null && parsed != null) {
                EnteredHand(winner!!, if (selfDraw) null else loser, parsed, chicken)
            } else {
                null
            },
            onClose = { conflict = null },
        )
    }

    // The winds turn after every hand, so the wind follows the hand in play.
    val handInPlay = minOf(table.doneHands + 1, 16)
    fun windOf(slot: Int): SeatWind = windOfChair(chairOf(seats.indexOf(slot), handInPlay), handInPlay)

    if (table.table.useTotalsOnly) {
        Text("The organizers enter only the final scores of this table. You cannot add hands here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (!view.roundHasStarted(table.roundId, now)) {
        Text("This round has not started yet. You can add hands when it starts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (phase == MatchPhase.OVER) {
        Text(
            "The time of this match is over. You cannot add hands now. The organizers enter the missing data.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (!seatsAssigned) return
    if (extraTime) {
        Text(
            "The time is over. You can add the hand in play. After it, the table is completed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // The draw is on the left and the penalty on the right. The win is the main action, so it is twice as wide.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { modeName = EntryMode.DRAW.name },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text("Add draw", textAlign = TextAlign.Center) }
        Button(
            onClick = { modeName = EntryMode.WIN.name },
            modifier = Modifier.weight(2f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text("Add win", textAlign = TextAlign.Center) }
        OutlinedButton(
            onClick = {
                if (penaltyDrafts.isEmpty()) penaltyDrafts.add(PenaltyDraft())
                modeName = EntryMode.PENALTY.name
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text("Add penalty", textAlign = TextAlign.Center) }
    }
    // The new totals arrive after a short time. The dialog shows progress until the table has the new data.
    LaunchedEffect(progressKey, awaitingKey) {
        val before = awaitingKey ?: return@LaunchedEffect
        if (progressKey != before) finishEntry()
    }
    LaunchedEffect(awaitingKey) {
        if (awaitingKey == null) return@LaunchedEffect
        delay(15_000)
        finishEntry()
        onSaved()
    }
    LaunchedEffect(preselectWinner) {
        val slot = preselectWinner ?: return@LaunchedEffect
        if (slot in seatIds && preview == null && awaitingKey == null) {
            winner = slot
            if (loser == slot) loser = null
            modeName = EntryMode.WIN.name
        }
        onPreselectHandled()
    }

    val review = preview
    val saving = busy || awaitingKey != null
    if ((mode == EntryMode.WIN || mode == EntryMode.DRAW) && review == null) {
        AlertDialog(
            onDismissRequest = { finishEntry() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isDraw) "Add draw" else "Add win")
                    HandLabel(handInPlay)
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isDraw) {
                        SectionLabel("Winner")
                        PlayerChoices(seatIds, winner, view, ::windOf) { slot -> winner = slot; if (loser == slot) loser = null }
                        CheckRow("Self draw", selfDraw) { selfDraw = it; if (it) { loser = null; chicken = false } }
                        if (!selfDraw) {
                            SectionLabel("Loser (who discarded)")
                            PlayerChoices(seatIds.filter { it != winner }, loser, view, ::windOf) { loser = it }
                        }
                        OutlinedTextField(
                            value = score,
                            onValueChange = { score = it.filter(Char::isDigit) },
                            label = { Text("Hand score (8 or more)") },
                            singleLine = true,
                            isError = chickenTooHigh,
                            supportingText = if (chickenTooHigh) {
                                { Text("A chicken hand has 12 points or less.") }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!selfDraw) CheckRow("Chicken hand", chicken, icon = { ChickenIcon(20.dp) }) { chicken = it }
                    } else {
                        Text("No player wins this hand. Every seat scores zero.")
                    }
                    if (extraTime) {
                        Text("The time is over. This hand is the last hand of the table.")
                    } else if (handInPlay < 16) {
                        CheckRow("Last hand of the table", lastHand) { lastHand = it }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
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
                ) { Text("Review") }
            },
            dismissButton = { TextButton(onClick = { finishEntry() }) { Text("Cancel") } },
        )
    }
    if ((mode == EntryMode.WIN || mode == EntryMode.DRAW) && review != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) { preview = null; message = null } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check hand")
                    HandLabel(review.handId)
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReviewHand(review, view, isDraw, selfDraw, winner, loser, parsed ?: 0, chicken, lastHand || extraTime)
                    if (saving) SavingRow(waitingForTable = awaitingKey != null, text = "Saving the hand...")
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            when (val result = presenter.record(tournamentId, roundId, tableId, proposal(), review.tableVersion)) {
                                is AppResult.Success -> {
                                    awaitingKey = progressKey
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
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { preview = null; message = null }) { Text("Edit") } },
        )
    }
    if (mode == EntryMode.PENALTY && !penaltyReview) {
        AlertDialog(
            onDismissRequest = { finishEntry() },
            title = { Text("Add penalty") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    penaltyDrafts.forEachIndexed { index, draft ->
                        PenaltyCard(
                            number = index + 1,
                            draft = draft,
                            seats = seats,
                            view = view,
                            windOf = ::windOf,
                            onChange = { penaltyDrafts[index] = it },
                            onRemove = { penaltyDrafts.removeAt(index); if (penaltyDrafts.isEmpty()) finishEntry() },
                        )
                    }
                    if (penaltyDrafts.size < MAX_PENALTIES) {
                        OutlinedButton(onClick = { penaltyDrafts.add(PenaltyDraft()) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add another penalty")
                        }
                    }
                    Text(
                        "A penalty does not end the hand. Its points count when the hand is saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = penaltyEntries.isNotEmpty() && penaltyEntries.all { it.isValid },
                    onClick = { penaltyReview = true },
                ) { Text("Review") }
            },
            dismissButton = { TextButton(onClick = { finishEntry() }) { Text("Cancel") } },
        )
    }
    if (mode == EntryMode.PENALTY && penaltyReview) {
        AlertDialog(
            onDismissRequest = { if (!saving) { penaltyReview = false; message = null } },
            title = { Text("Check penalty") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    penaltyEntries.forEach { PenaltyLine(it, view, seats, ::windOf) }
                    if (saving) SavingRow(waitingForTable = awaitingKey != null, text = "Saving the penalty...")
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            when (val result = presenter.addPenalties(tournamentId, roundId, tableId, penaltyEntries, table.table.version)) {
                                is AppResult.Success -> {
                                    awaitingKey = progressKey
                                    onSaved()
                                }
                                is AppResult.Failure ->
                                    if (result.error is AppError.Conflict) tableChanged(result.error.toUiMessage())
                                    else message = result.error.toUiMessage()
                            }
                            busy = false
                        }
                    },
                ) { Text("Confirm and save") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { penaltyReview = false; message = null }) { Text("Edit") } },
        )
    }
}

/** The progress of a save. After the server accepted the data, the app waits for the new table data. */
@Composable
private fun SavingRow(waitingForTable: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(if (waitingForTable) "Updating the table..." else text)
    }
}

/** One penalty in the review: who loses the points and who receives them. */
@Composable
private fun PenaltyLine(penalty: PenaltyEntry, view: PlayerTournamentView, seats: List<Int?>, windOf: (Int) -> SeatWind) {
    val slot = seats.getOrNull(penalty.seatIndex)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (slot != null) WindIcon(windOf(slot), size = 24.dp)
        Text(view.firstNameOf(slot), Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, maxLines = 1)
        Text("-${penalty.amount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        if (penalty.shared) {
            Text("others +${penalty.amount / 3} each", color = GainColor)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Two players per row. Each choice shows the wind icon and the first name. */
@Composable
private fun PlayerChoices(
    slots: List<Int>,
    selected: Int?,
    view: PlayerTournamentView,
    windOf: (Int) -> SeatWind,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { slot ->
                    val isSelected = slot == selected
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onSelect(slot) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WindIcon(windOf(slot), size = 24.dp)
                            Text(
                                view.firstNameOf(slot),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // An odd last row keeps the width of one choice.
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One penalty. The player chooses who is penalized and the amount. The amount is positive and the app
 * shows the minus sign. A shared penalty gives a third of the amount to each of the other players.
 */
@Composable
private fun PenaltyCard(
    number: Int,
    draft: PenaltyDraft,
    seats: List<Int?>,
    view: PlayerTournamentView,
    windOf: (Int) -> SeatWind,
    onChange: (PenaltyDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val amount = draft.amount.toIntOrNull()
    val notSplittable = draft.shared && amount != null && amount % 3 != 0
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Penalty $number", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove penalty $number") }
            }
            SectionLabel("Penalized player")
            val slots = seats.filterNotNull()
            PlayerChoices(slots, draft.seat?.let { seats.getOrNull(it) }, view, windOf) { slot ->
                onChange(draft.copy(seat = seats.indexOf(slot)))
            }
            OutlinedTextField(
                value = draft.amount,
                onValueChange = { onChange(draft.copy(amount = it.filter(Char::isDigit).take(3))) },
                label = { Text("Points") },
                prefix = { Text("-") },
                singleLine = true,
                isError = notSplittable,
                supportingText = when {
                    notSplittable -> {{ Text("A shared penalty must be a multiple of 3.") }}
                    draft.shared && amount != null && amount > 0 -> {{ Text("The other players get +${amount / 3} each.") }}
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            CheckRow("Share it with the other players", draft.shared) { onChange(draft.copy(shared = it)) }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    hint: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label)
                icon?.invoke()
            }
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * Shows the result clearly so that the player can show it to the table before saving.
 * The table points show only when the table ends with this hand.
 */
@Composable
private fun ReviewHand(
    preview: HandPreview,
    view: PlayerTournamentView,
    isDraw: Boolean,
    selfDraw: Boolean,
    winner: Int?,
    loser: Int?,
    score: Int,
    chicken: Boolean,
    lastHand: Boolean,
) {
    val showPoints = preview.handId >= 16 || lastHand
    fun windOf(slot: Int?): SeatWind? = preview.seatSlotIds.indexOf(slot).takeIf { it >= 0 }
        ?.let { windOfChair(chairOf(it, preview.handId), preview.handId) }
    if (isDraw) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Draw",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    windOf(winner)?.let { WindIcon(it, size = 28.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Text(view.firstNameOf(winner), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("wins $score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (chicken) ChickenIcon(24.dp)
                }
                if (selfDraw) {
                    Text("Self draw")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("from")
                        windOf(loser)?.let { WindIcon(it, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Text(view.firstNameOf(loser), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1.4f))
        HeaderText("Hand", Modifier.weight(1f))
        HeaderText("Table", Modifier.weight(1f))
        if (showPoints) HeaderText("Points", Modifier.weight(0.9f))
    }
    preview.seatSlotIds.forEachIndexed { index, slot ->
        val change = preview.handScores.getOrNull(index) ?: 0
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1.4f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                windOf(slot)?.let { WindIcon(it, size = 20.dp) }
                Text(view.firstNameOf(slot), maxLines = 1)
            }
            Text(
                if (change > 0) "+$change" else "$change",
                Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Bold,
                color = when {
                    change > 0 -> GainColor
                    change < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(preview.tableScores.getOrNull(index).orEmpty(), Modifier.weight(1f), textAlign = TextAlign.End)
            if (showPoints) {
                Text(preview.tablePoints.getOrNull(index).orEmpty(), Modifier.weight(0.9f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (lastHand && preview.handId < 16) {
        Text(
            "This is the last hand. After you save it, the table is completed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text,
        modifier,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class EnteredHand(val winner: Int?, val loser: Int?, val score: Int, val chicken: Boolean)

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
                        if (enteredHand.winner == null) {
                            "Draw"
                        } else {
                            "${view.nameOf(enteredHand.winner)} won with ${enteredHand.score}" +
                                (if (enteredHand.loser != null) " from ${view.nameOf(enteredHand.loser)}" else " (self draw)") +
                                (if (enteredHand.chicken) " - chicken hand" else "")
                        },
                    )
                    Text("If the hand is already in the table, do not enter it again.")
                }
                Text("You can call a staff member to check the table, or close this window and go on.")
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("Close and continue") } },
    )
}
