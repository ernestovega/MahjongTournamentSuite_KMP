package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlagAndNumber
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.SelfDrawIcon
import com.etologic.mahjongtournamentsuite.presentation.components.WindIcon
import com.etologic.mahjongtournamentsuite.presentation.components.chairOf
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.playerNumber
import com.etologic.mahjongtournamentsuite.presentation.components.signedColor
import com.etologic.mahjongtournamentsuite.presentation.components.signedText
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.windOfChair
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.action_cancel
import mahjongtournamentsuite.playerapp.generated.resources.changed_already
import mahjongtournamentsuite.playerapp.generated.resources.changed_call_staff
import mahjongtournamentsuite.playerapp.generated.resources.changed_close
import mahjongtournamentsuite.playerapp.generated.resources.changed_entered_chicken
import mahjongtournamentsuite.playerapp.generated.resources.changed_entered_from
import mahjongtournamentsuite.playerapp.generated.resources.changed_entered_self
import mahjongtournamentsuite.playerapp.generated.resources.changed_last_hand_from
import mahjongtournamentsuite.playerapp.generated.resources.changed_last_hand_self
import mahjongtournamentsuite.playerapp.generated.resources.changed_not_saved
import mahjongtournamentsuite.playerapp.generated.resources.changed_saved_hands
import mahjongtournamentsuite.playerapp.generated.resources.changed_table_now
import mahjongtournamentsuite.playerapp.generated.resources.changed_table_row
import mahjongtournamentsuite.playerapp.generated.resources.changed_title
import mahjongtournamentsuite.playerapp.generated.resources.changed_your_hand
import mahjongtournamentsuite.playerapp.generated.resources.chicken_hand
import mahjongtournamentsuite.playerapp.generated.resources.details_col_hand
import mahjongtournamentsuite.playerapp.generated.resources.entry_add_draw
import mahjongtournamentsuite.playerapp.generated.resources.entry_add_penalty
import mahjongtournamentsuite.playerapp.generated.resources.entry_add_win
import mahjongtournamentsuite.playerapp.generated.resources.entry_back
import mahjongtournamentsuite.playerapp.generated.resources.entry_check_hand
import mahjongtournamentsuite.playerapp.generated.resources.entry_check_penalty
import mahjongtournamentsuite.playerapp.generated.resources.entry_chicken_limit
import mahjongtournamentsuite.playerapp.generated.resources.entry_closed_by_organizer
import mahjongtournamentsuite.playerapp.generated.resources.entry_confirm_save
import mahjongtournamentsuite.playerapp.generated.resources.entry_edit
import mahjongtournamentsuite.playerapp.generated.resources.entry_extra_time
import mahjongtournamentsuite.playerapp.generated.resources.entry_hand_score
import mahjongtournamentsuite.playerapp.generated.resources.entry_loser
import mahjongtournamentsuite.playerapp.generated.resources.entry_numpad_clear
import mahjongtournamentsuite.playerapp.generated.resources.entry_numpad_delete
import mahjongtournamentsuite.playerapp.generated.resources.entry_penalized_player
import mahjongtournamentsuite.playerapp.generated.resources.entry_penalized_short
import mahjongtournamentsuite.playerapp.generated.resources.entry_points
import mahjongtournamentsuite.playerapp.generated.resources.entry_points_abbr
import mahjongtournamentsuite.playerapp.generated.resources.entry_review
import mahjongtournamentsuite.playerapp.generated.resources.entry_review_pays
import mahjongtournamentsuite.playerapp.generated.resources.entry_review_wins
import mahjongtournamentsuite.playerapp.generated.resources.entry_round_not_started
import mahjongtournamentsuite.playerapp.generated.resources.entry_saving_hand
import mahjongtournamentsuite.playerapp.generated.resources.entry_saving_penalty
import mahjongtournamentsuite.playerapp.generated.resources.entry_seats_first
import mahjongtournamentsuite.playerapp.generated.resources.entry_seats_not_assigned
import mahjongtournamentsuite.playerapp.generated.resources.entry_seats_open_soon
import mahjongtournamentsuite.playerapp.generated.resources.entry_self_draw
import mahjongtournamentsuite.playerapp.generated.resources.entry_share_short
import mahjongtournamentsuite.playerapp.generated.resources.entry_shared_multiple
import mahjongtournamentsuite.playerapp.generated.resources.entry_time_over
import mahjongtournamentsuite.playerapp.generated.resources.entry_total
import mahjongtournamentsuite.playerapp.generated.resources.entry_totals_only
import mahjongtournamentsuite.playerapp.generated.resources.entry_updating_table
import mahjongtournamentsuite.playerapp.generated.resources.entry_winner
import mahjongtournamentsuite.playerapp.generated.resources.hand_draw
import mahjongtournamentsuite.playerapp.generated.resources.hand_penalty
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** A penalty that the player is writing. The amount is a positive number. The app shows the minus sign. */
private data class PenaltyDraft(val seat: Int? = null, val amount: String = "", val shared: Boolean = false) {
    fun toEntry() = PenaltyEntry(seat ?: -1, amount.toIntOrNull() ?: 0, shared)
}


/** The three things that a player can add to the table. */
private enum class EntryMode { WIN, DRAW, PENALTY }

/** The steps of a win: one small dialog each. The review comes after the score. */
private enum class WinStep { WINNER, LOSER, SCORE }

/**
 * Entry for the player who writes the scores down. It has three buttons: add a win, add a draw and add a penalty.
 * A win or a draw ends the hand in play. A penalty does not end it.
 * The player enters the data, reviews it with everyone at the table and then confirms.
 * Before the first hand, a player of the table sets the seats ([SeatsAssignment]). The hand buttons appear with the
 * Play of the Timer, the starting gong.
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
    if (mine == null || mine !in table.table.playerIds) return
    if (table.isComplete) {
        // The app closes a table with hands left when its time is over. The player learns why the buttons are gone.
        val closedByTime = table.doneHands < table.hands.size && view.matchPhaseOf(table.roundId, now, table.table.tableId) == MatchPhase.OVER
        if (closedByTime) Text(stringResource(Res.string.entry_time_over), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val seatsAssigned = seats.none { it == null }
    val seatIds = seats.filterNotNull()

    var conflict by remember { mutableStateOf<UiText?>(null) }
    var winner by remember { mutableStateOf<Int?>(null) }
    var loser by remember { mutableStateOf<Int?>(null) }
    var selfDraw by remember { mutableStateOf(false) }
    val penaltyDrafts = remember { mutableStateListOf<PenaltyDraft>() }
    var penaltyReview by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf("") }
    var chicken by remember { mutableStateOf(false) }
    // When the time is over, the players can record the hand in play. That hand is the last hand of the table.
    val phase = view.matchPhaseOf(table.roundId, now, table.table.tableId)
    val extraTime = phase == MatchPhase.EXTRA_TIME
    var preview by remember { mutableStateOf<HandPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var modeName by rememberSaveable { mutableStateOf("") }
    var winStep by remember { mutableStateOf(WinStep.WINNER) }
    val mode = EntryMode.entries.firstOrNull { it.name == modeName }
    val isDraw = mode == EntryMode.DRAW
    // What the table shows when the server accepted the data. The dialog stays open until the table shows something new.
    val progressKey = table.doneHands * 100 + table.penaltyCount
    var awaitingKey by remember { mutableStateOf<Int?>(null) }

    val parsed = score.trim().toIntOrNull()
    val chickenTooHigh = chicken && parsed != null && parsed > 12
    val penaltyEntries = penaltyDrafts.map { it.toEntry() }
    val valid = isDraw ||
        winner != null &&
        parsed != null &&
        parsed >= 8 &&
        (selfDraw || (loser != null && loser != winner)) &&
        (!chicken || (!selfDraw && !chickenTooHigh))
    fun proposal() = if (isDraw) {
        HandProposal(winnerSlotId = null, loserSlotId = null, score = 0, isLastHand = extraTime)
    } else {
        HandProposal(
            winnerSlotId = winner!!,
            loserSlotId = if (selfDraw) null else loser,
            score = parsed!!,
            isChickenHand = chicken,
            isLastHand = extraTime,
        )
    }

    fun finishEntry() {
        preview = null
        penaltyReview = false
        winner = null
        loser = null
        selfDraw = false
        penaltyDrafts.clear()
        score = ""
        chicken = false
        awaitingKey = null
        message = null
        modeName = ""
        winStep = WinStep.WINNER
    }

    // The table is not what the player saw, for example because a staff member saved a hand.
    // The entered data stays in the form, so it is not lost. The screen loads the server data and
    // the dialog shows both. The player closes it to go on, or calls a staff member first.
    fun tableChanged(text: UiText) {
        conflict = text
        preview = null
        penaltyReview = false
        onSaved()
    }

    conflict?.let { text ->
        TableChangedDialog(
            serverMessage = text.asString(),
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

    if (table.table.useTotalsOnly) {
        Text(stringResource(Res.string.entry_totals_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    // The players set the seats from a few minutes before the planned start until the first hand or penalty.
    val seatsEditable = table.doneHands == 0 && table.penaltyCount == 0 && view.tournament.seatingIsOpen(table.roundId, now)
    if (phase == MatchPhase.NOT_STARTED) {
        // A round that never got its gong and whose time is over was not played in time: nothing can change any more,
        // unless an organizer opened the seats by hand.
        if (!seatsEditable && view.tournament.unstartedRoundIsPast(table.roundId, now)) {
            RoundNotStartedNote(stringResource(Res.string.entry_time_over))
            return
        }
        val seatsSoon = !seatsEditable && !seatsAssigned && view.tournament.seatingState(table.roundId, now) == SeatingState.NOT_YET
        val note = if (seatsSoon) {
            stringResource(Res.string.entry_round_not_started) + "\n" + stringResource(Res.string.entry_seats_open_soon)
        } else {
            stringResource(Res.string.entry_round_not_started)
        }
        // No gap of the column between the note and the seats button: the button has room of its own around its text,
        // so the note has the same space above and below (Ernesto, 2026-11-25).
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RoundNotStartedNote(note)
            if (seatsEditable) SeatsAssignment(tournamentId, view, table, onSaved)
        }
        return
    }
    if (phase == MatchPhase.OVER || phase == MatchPhase.CLOSED) {
        Text(
            stringResource(if (phase == MatchPhase.CLOSED) Res.string.entry_closed_by_organizer else Res.string.entry_time_over),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (phase == MatchPhase.CLOSED && seatsEditable) SeatsAssignment(tournamentId, view, table, onSaved)
        return
    }
    if (!seatsAssigned) {
        if (seatsEditable) {
            Text(stringResource(Res.string.entry_seats_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
            SeatsAssignment(tournamentId, view, table, onSaved)
        } else {
            // Hands need the winds, so the table takes hands only once the four seats are set. The player learns why.
            Text(stringResource(Res.string.entry_seats_not_assigned), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (seatsEditable) SeatsAssignment(tournamentId, view, table, onSaved)
    if (extraTime) {
        Text(
            stringResource(Res.string.entry_extra_time),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // The draw is on the left and the penalty on the right. The win is the main action, so it is the widest, and the
    // penalty, with the longest text, is a bit wider than the draw.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { modeName = EntryMode.DRAW.name },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text(stringResource(Res.string.entry_add_draw), textAlign = TextAlign.Center) }
        Button(
            onClick = { modeName = EntryMode.WIN.name },
            modifier = Modifier.weight(1.4f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text(stringResource(Res.string.entry_add_win), textAlign = TextAlign.Center) }
        OutlinedButton(
            onClick = {
                if (penaltyDrafts.isEmpty()) penaltyDrafts.add(PenaltyDraft())
                modeName = EntryMode.PENALTY.name
            },
            modifier = Modifier.weight(1.25f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text(stringResource(Res.string.entry_add_penalty), textAlign = TextAlign.Center) }
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
            // The tap on the seat already says who won.
            winStep = WinStep.LOSER
        }
        onPreselectHandled()
    }

    val review = preview
    // The notice of a changed table goes first. The entry dialogs wait behind it with the data kept, so a tap on the
    // notice never lands on them, and they come back when the player closes it.
    val showEntry = conflict == null
    val saving = busy || awaitingKey != null

    // The server computes the result of the hand. The review shows it, and a change of "last hand" asks again,
    // because the last hand also gives the points of the table.
    fun requestPreview() {
        scope.launch {
            busy = true
            message = null
            when (val result = presenter.preview(tournamentId, roundId, tableId, proposal())) {
                is AppResult.Success -> preview = result.value
                is AppResult.Failure ->
                    if (result.error is AppError.Conflict) {
                        tableChanged(result.error.toUiText())
                    } else {
                        message = result.error.toUiText()
                    }
            }
            busy = false
        }
    }

    val entryTitle: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Text(stringResource(if (isDraw) Res.string.entry_add_draw else Res.string.entry_add_win))
            HandNumberLabel(handInPlay)
        }
    }
    val cancelButton: @Composable () -> Unit = {
        TextButton(onClick = { finishEntry() }) { Text(stringResource(Res.string.action_cancel)) }
    }
    fun backButton(to: WinStep): @Composable () -> Unit = {
        TextButton(onClick = { winStep = to }) { Text(stringResource(Res.string.entry_back)) }
    }
    // A draw has nothing to ask: "Add draw" opens the check of the hand at once (Ernesto, 2026-11-25). An error of the
    // preview shows alone, and closing it ends the entry.
    val drawPending = showEntry && mode == EntryMode.DRAW && review == null
    LaunchedEffect(drawPending) {
        if (drawPending && !busy) requestPreview()
    }
    if (drawPending) {
        message?.let { AppErrorDialog(it, onDismiss = { finishEntry() }) }
    }
    // A win is asked in small steps: who won, who discarded, and the score. A tap on a player goes to the next step.
    if (showEntry && mode == EntryMode.WIN && review == null) {
        TrackedDialog(
            when (winStep) {
                WinStep.WINNER -> "EntryWinWinner"
                WinStep.LOSER -> "EntryWinLoser"
                WinStep.SCORE -> "EntryWinScore"
            },
        ) {
            AlertDialog(
                onDismissRequest = { finishEntry() },
                title = entryTitle,
                text = {
                    Column(Modifier.verticalScrollWithIndicators(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (winStep) {
                            WinStep.WINNER -> {
                                StepQuestion(stringResource(Res.string.entry_winner))
                                PlayerChoices(byWind(seats, handInPlay), winner, view, columns = 1, seats = seats, handNumber = handInPlay) { slot ->
                                    winner = slot
                                    if (loser == slot) loser = null
                                    winStep = WinStep.LOSER
                                }
                            }
                            WinStep.LOSER -> {
                                WinSoFar(view, winner)
                                StepQuestion(stringResource(Res.string.entry_loser))
                                SelfDrawButton(stringResource(Res.string.entry_self_draw)) {
                                    selfDraw = true
                                    loser = null
                                    chicken = false
                                    winStep = WinStep.SCORE
                                }
                                PlayerChoices(
                                    byWind(seats, handInPlay).filter { it != winner },
                                    if (selfDraw) null else loser,
                                    view,
                                    columns = 1,
                                    seats = seats,
                                    handNumber = handInPlay,
                                ) {
                                    selfDraw = false
                                    loser = it
                                    winStep = WinStep.SCORE
                                }
                            }
                            WinStep.SCORE -> {
                                WinSoFar(view, winner, loser = if (selfDraw) null else loser, selfDraw = selfDraw)
                                // On a phone or a tablet app the numpad below types the score, so the system keyboard never
                                // opens: the field cannot take the focus.
                                val numpad = LocalNumpadEntry.current
                                OutlinedTextField(
                                    value = score,
                                    onValueChange = { score = it.filter(Char::isDigit) },
                                    label = { Text(stringResource(Res.string.entry_hand_score)) },
                                    singleLine = true,
                                    readOnly = numpad,
                                    isError = chickenTooHigh,
                                    supportingText = if (chickenTooHigh) {
                                        { Text(stringResource(Res.string.entry_chicken_limit)) }
                                    } else {
                                        null
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    // A short field in the middle: a score has a few digits (Ernesto, 2026-11-25).
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                    modifier = Modifier.width(ScoreFieldWidth).align(Alignment.CenterHorizontally).then(if (numpad) Modifier.focusProperties { canFocus = false } else Modifier),
                                )
                                // A self-drawn hand is never a chicken hand.
                                // The box at the end of the line, where the thumb reaches it on a phone (Ernesto, 2026-11-25).
                                // In the middle, under the score (Ernesto, 2026-11-25).
                                if (!selfDraw) {
                                    CheckRow(
                                        stringResource(Res.string.chicken_hand),
                                        chicken,
                                        icon = { ChickenIcon(20.dp) },
                                        boxLast = true,
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                        fillWidth = false,
                                    ) { chicken = it }
                                }
                                if (numpad) ScoreNumpad(score) { score = it }
                            }
                        }
                        message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
                    }
                },
                confirmButton = {
                    MtsDialogButtons {
                        if (winStep == WinStep.SCORE) {
                            Button(enabled = valid && !busy, onClick = { requestPreview() }) { Text(stringResource(Res.string.entry_review)) }
                        }
                        when (winStep) {
                            WinStep.WINNER -> cancelButton()
                            // Back goes one step back, and Cancel below it ends the whole entry (Ernesto, 2026-11-25).
                            WinStep.LOSER -> {
                                backButton(WinStep.WINNER)()
                                cancelButton()
                            }
                            WinStep.SCORE -> {
                                backButton(WinStep.LOSER)()
                                cancelButton()
                            }
                        }
                    }
                },
            )
        }
    }
    if (showEntry && (mode == EntryMode.WIN || mode == EntryMode.DRAW) && review != null) {
        TrackedDialog("EntryCheckHand") {
            AlertDialog(
                onDismissRequest = {
                    if (!saving) {
                        // A draw has no step before the check, so going back ends the entry.
                        if (isDraw) {
                            finishEntry()
                        } else {
                            preview = null
                            message = null
                        }
                    }
                },
                // Wider than a usual dialog on a phone, so the summary and the table of the hand have room.
                modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                title = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        Text(stringResource(Res.string.entry_check_hand))
                        HandNumberLabel(review.handId)
                    }
                },
                text = {
                    Column(Modifier.verticalScrollWithIndicators(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReviewHand(review, view, isDraw, selfDraw, winner, loser, parsed ?: 0, chicken, extraTime)
                        if (saving) SavingRow(waitingForTable = awaitingKey != null, text = stringResource(Res.string.entry_saving_hand))
                        message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
                    }
                },
                // Hand 16 and a hand in the extra time end the table on their own. No other hand is the last one.
                // The buttons centered, one under the other: Confirm, and below it Edit (Cancel for a draw) (Ernesto, 2026-11-25).
                confirmButton = {
                    MtsDialogButtons(stacked = true) {
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
                                                tableChanged(result.error.toUiText())
                                            } else {
                                                message = result.error.toUiText()
                                                preview = null
                                            }
                                    }
                                    busy = false
                                }
                            },
                        ) { Text(stringResource(Res.string.entry_confirm_save)) }
                        // Edit goes back to the score of a win. A draw has nothing to edit. Cancel ends the entry.
                        if (!isDraw) {
                            TextButton(enabled = !saving, onClick = {
                                preview = null
                                message = null
                            }) { Text(stringResource(Res.string.entry_edit)) }
                        }
                        TextButton(enabled = !saving, onClick = { finishEntry() }) { Text(stringResource(Res.string.action_cancel)) }
                    }
                },
            )
        }
    }
    if (showEntry && mode == EntryMode.PENALTY && !penaltyReview) {
        TrackedDialog("EntryAddPenalty") {
            AlertDialog(
                onDismissRequest = { finishEntry() },
                title = { MtsDialogTitle(stringResource(Res.string.entry_add_penalty)) },
                text = {
                    Column(Modifier.verticalScrollWithIndicators(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // One penalty per entry: for another one, the player adds it again (Ernesto, 2026-11-25).
                        penaltyDrafts.firstOrNull()?.let { draft ->
                            PenaltyForm(draft = draft, seats = seats, view = view, onChange = { penaltyDrafts[0] = it })
                        }
                    }
                },
                confirmButton = {
                    MtsDialogButtons {
                        Button(
                            enabled = penaltyEntries.isNotEmpty() && penaltyEntries.all { it.isValid },
                            onClick = { penaltyReview = true },
                        ) { Text(stringResource(Res.string.entry_review)) }
                        TextButton(onClick = { finishEntry() }) { Text(stringResource(Res.string.action_cancel)) }
                    }
                },
            )
        }
    }
    if (showEntry && mode == EntryMode.PENALTY && penaltyReview) {
        TrackedDialog("EntryCheckPenalty") {
            AlertDialog(
                onDismissRequest = {
                    if (!saving) {
                        penaltyReview = false
                        message = null
                    }
                },
                title = { MtsDialogTitle(stringResource(Res.string.entry_check_penalty)) },
                text = {
                    Column(Modifier.verticalScrollWithIndicators(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReviewPenalty(penaltyEntries, view, table, seats, handInPlay)
                        if (saving) SavingRow(waitingForTable = awaitingKey != null, text = stringResource(Res.string.entry_saving_penalty))
                        message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
                    }
                },
                confirmButton = {
                    MtsDialogButtons {
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
                                            if (result.error is AppError.Conflict) {
                                                tableChanged(result.error.toUiText())
                                            } else {
                                                message = result.error.toUiText()
                                            }
                                    }
                                    busy = false
                                }
                            },
                        ) { Text(stringResource(Res.string.entry_confirm_save)) }
                        TextButton(enabled = !saving, onClick = {
                            penaltyReview = false
                            message = null
                        }) { Text(stringResource(Res.string.entry_edit)) }
                        TextButton(enabled = !saving, onClick = { finishEntry() }) { Text(stringResource(Res.string.action_cancel)) }
                    }
                },
            )
        }
    }
}

/** The progress of a save. After the server accepted the data, the app waits for the new table data. */
@Composable
private fun SavingRow(waitingForTable: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(if (waitingForTable) stringResource(Res.string.entry_updating_table) else text)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** True where the score of a hand is typed on [ScoreNumpad]: the phone and tablet apps, not the web. */
val LocalNumpadEntry = staticCompositionLocalOf { false }

/** The longest score the numpad types. */
private const val NUMPAD_MAX_DIGITS = 4

/**
 * A numpad for the score of a hand: 1 to 9, then clear, 0 and delete. The keys answer at once, with no debounce,
 * so fast typing keeps every digit.
 */
@Composable
private fun ScoreNumpad(value: String, onChange: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", NUMPAD_CLEAR, "0", NUMPAD_DELETE)
    Column(Modifier.fillMaxWidth().testTag(SCORE_NUMPAD_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isDigit = key.first().isDigit()
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable {
                                onChange(
                                    when (key) {
                                        NUMPAD_CLEAR -> ""
                                        NUMPAD_DELETE -> value.dropLast(1)
                                        // No leading zero, and no more digits than a score has.
                                        else -> if (value.length >= NUMPAD_MAX_DIGITS || (value.isEmpty() && key == "0")) value else value + key
                                    },
                                )
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isDigit) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (key) {
                                NUMPAD_DELETE -> Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = stringResource(Res.string.entry_numpad_delete),
                                    Modifier.size(24.dp),
                                )
                                NUMPAD_CLEAR -> Text(stringResource(Res.string.entry_numpad_clear), style = MaterialTheme.typography.titleMedium.inTextFont(), fontWeight = FontWeight.Bold)
                                else -> Text(key, style = MaterialTheme.typography.headlineSmall.inTextFont(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val NUMPAD_CLEAR = "C"
private const val NUMPAD_DELETE = "DEL"

/** The tag of the numpad of the score, for the tests. */
const val SCORE_NUMPAD_TAG = "score-numpad"

/** The number of the hand in the title of the dialogs, without the wind: "#5". */
@Composable
private fun HandNumberLabel(handId: Int) {
    // In the text font, like every number of the table and its dialogs (Ernesto, 2026-11-25).
    Text("#$handId", color = MaterialTheme.colorScheme.onSurfaceVariant, style = LocalTextStyle.current.inTextFont())
}

/** The tag of each player to choose in the dialogs of a hand, for the tests. */
const val PLAYER_CHOICE_TAG = "player-choice"

/** The question of a step of a win. */
@Composable
private fun StepQuestion(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.inTextFont(), fontWeight = FontWeight.Bold)
}

/** What the steps before chose, in the boxes of the review of the hand: who wins, and on the score step who pays. */
@Composable
private fun WinSoFar(view: PlayerTournamentView, winner: Int?, loser: Int? = null, selfDraw: Boolean = false) {
    WinnerPayerBoxes(view, winner, loser, selfDraw, score = null)
}

/**
 * Self-draw, above the players who could have discarded, in the same card as a player: the mushroom cloud and the word
 * take the place of the wind, the flag and the name (Ernesto, 2026-11-25).
 */
@Composable
private fun SelfDrawButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelfDrawIcon(22.dp)
            Text(label, fontWeight = FontWeight.Bold, color = MtsTheme.colors.selfPick, maxLines = 1)
        }
    }
}

/** The wind of [slot] in hand [handNumber]. [seats] are the slots in the order east, south, west, north of hand 1. */
private fun currentWindOf(seats: List<Int?>, slot: Int, handNumber: Int): SeatWind? {
    val index = seats.indexOf(slot).takeIf { it >= 0 } ?: return null
    return windOfChair(chairOf(index, handNumber), handNumber)
}

/** The players of the table in the order of their winds in hand [handNumber]: East first. */
private fun byWind(seats: List<Int?>, handNumber: Int): List<Int> = seats.filterNotNull().sortedBy { currentWindOf(seats, it, handNumber)?.ordinal ?: 0 }

/** The players to choose from, [columns] per row. Each choice shows the first name. */
@Composable
private fun PlayerChoices(
    slots: List<Int>,
    selected: Int?,
    view: PlayerTournamentView,
    columns: Int = 2,
    /** The seats of the table in the order east, south, west, north of hand 1. With them, each choice shows its wind. */
    seats: List<Int?>? = null,
    /** The hand in play, for the wind of each player now. */
    handNumber: Int = 1,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(columns).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { slot ->
                    val isSelected = slot == selected
                    Surface(
                        modifier = Modifier.weight(1f).testTag(PLAYER_CHOICE_TAG).clickable { onSelect(slot) },
                        shape = MaterialTheme.shapes.medium,
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
                            // The wind of the player in this hand, then the flag with the number of the player and the
                            // name, as on the table.
                            // The wind glyph has its own room on the right, so the flag sits in the middle.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (seats != null) {
                                    currentWindOf(seats, slot, handNumber)?.let { WindIcon(it, size = 20.dp) }
                                }
                                PlayerFlagAndNumber(view.players[slot]?.country, slot)
                            }
                            Text(
                                view.firstNameOf(slot),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // A short last row keeps the width of the other choices.
                repeat(columns - group.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The penalty. The player chooses who is penalized and the amount. The amount is positive and the app shows the minus
 * sign. A shared penalty gives a third of the amount to each of the other players.
 */
@Composable
private fun PenaltyForm(
    draft: PenaltyDraft,
    seats: List<Int?>,
    view: PlayerTournamentView,
    onChange: (PenaltyDraft) -> Unit,
) {
    val amount = draft.amount.toIntOrNull()
    val notSplittable = draft.shared && amount != null && amount % 3 != 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(Res.string.entry_penalized_player))
        val slots = seats.filterNotNull()
        // One player per row, so the flag and the number leave the whole name visible.
        PlayerChoices(slots, draft.seat?.let { seats.getOrNull(it) }, view, columns = 1) { slot ->
            onChange(draft.copy(seat = seats.indexOf(slot)))
        }
        OutlinedTextField(
            value = draft.amount,
            onValueChange = { onChange(draft.copy(amount = it.filter(Char::isDigit).take(3))) },
            label = { Text(stringResource(Res.string.entry_points)) },
            // The minus goes with the number, in the middle of the field.
            visualTransformation = MinusPrefix,
            singleLine = true,
            isError = notSplittable,
            // Only the error goes under the field, no explanation (Ernesto, 2026-11-25).
            supportingText = if (notSplittable) {
                { Text(stringResource(Res.string.entry_shared_multiple)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            // A short field in the middle, and the box on the right with its short label (Ernesto, 2026-11-25).
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(ScoreFieldWidth).align(Alignment.CenterHorizontally),
        )
        // The short line goes in the middle, under the field (Ernesto, 2026-11-25).
        CheckRow(
            stringResource(Res.string.entry_share_short),
            draft.shared,
            boxLast = true,
            // The touch target of the line keeps room below the box; without it, the space down to the first button
            // is the same as the space up to the field (Ernesto, 2026-11-25).
            modifier = Modifier.align(Alignment.CenterHorizontally).layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val trim = PenaltyShareTrim.roundToPx().coerceAtMost(placeable.height)
                layout(placeable.width, placeable.height - trim) { placeable.place(0, 0) }
            },
            fillWidth = false,
        ) { onChange(draft.copy(shared = it)) }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    hint: String? = null,
    icon: (@Composable () -> Unit)? = null,
    boxLast: Boolean = false,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    MtsCheckboxRow(
        checked = checked,
        onCheckedChange = onChange,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        gap = MtsTheme.spacing.none,
        contentPadding = if (boxLast) PaddingValues(start = MtsTheme.spacing.md) else PaddingValues(),
        boxLast = boxLast,
        // No gray background on the line after a tap, only the box changes (Ernesto, 2026-11-25).
        indication = null,
    ) {
        // With the box at the end, the icon, the text and the box go together on the right (Ernesto, 2026-11-25).
        Column(if (fillWidth) Modifier.weight(1f) else Modifier, horizontalAlignment = if (boxLast) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                if (boxLast) icon?.invoke()
                Text(label)
                if (!boxLast) icon?.invoke()
            }
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * Who wins and who pays, side by side: a green box "Wins" on the left and a red box "Pays" on the right (or a
 * self-draw, with its mushroom cloud). With [score], the score goes in a circle between them, as in the review of a
 * hand; the steps of a win show the boxes without it (Ernesto, 2026-11-25). No step shows a box with nothing chosen.
 */
@Composable
private fun WinnerPayerBoxes(view: PlayerTournamentView, winner: Int?, loser: Int?, selfDraw: Boolean, score: Int?, chicken: Boolean = false) {
    val winnerSlot = winner ?: return
    val hasSecond = selfDraw || loser != null
    // The boxes keep room for the circle in their inner side.
    val circleRoom = if (score != null) CircleRoom else 0.dp
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            ReviewSideBox(
                Modifier.weight(1f).fillMaxHeight(),
                color = MtsTheme.colors.gain,
                word = stringResource(Res.string.entry_review_wins),
                endRoom = if (hasSecond) circleRoom else 0.dp,
            ) {
                SmallFlagAndNumber(view.players[winnerSlot]?.country, winnerSlot)
                ReviewSideName(view.firstNameOf(winnerSlot), small = score != null)
            }
            if (selfDraw) {
                ReviewSideBox(
                    Modifier.weight(1f).fillMaxHeight(),
                    // Red as the box of who pays: the other three pay (Ernesto, 2026-11-25).
                    color = MaterialTheme.colorScheme.error,
                    word = stringResource(Res.string.entry_self_draw),
                    startRoom = circleRoom,
                    icon = { SelfDrawIcon(28.dp) },
                ) {}
            } else if (loser != null) {
                ReviewSideBox(
                    Modifier.weight(1f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.error,
                    word = stringResource(Res.string.entry_review_pays),
                    startRoom = circleRoom,
                ) {
                    SmallFlagAndNumber(view.players[loser]?.country, loser)
                    ReviewSideName(view.firstNameOf(loser), small = score != null)
                }
            }
        }
        if (score != null && hasSecond) {
            ScoreCircle("$score", Modifier.align(Alignment.Center))
            // A chicken hand adds a small circle with the chicken under the score (Ernesto, 2026-11-25).
            if (chicken) ChickenCircle(Modifier.align(Alignment.Center).offset(y = CircleSize / 2 + ChickenCircleSize / 2 - 6.dp))
        }
    }
}

/** A box of [WinnerPayerBoxes]: the word on top in the color of the side, then [content], centered. */
@Composable
private fun ReviewSideBox(
    modifier: Modifier,
    color: Color,
    word: String,
    startRoom: Dp = 0.dp,
    endRoom: Dp = 0.dp,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        // The same room on both sides, so the text is centered in its box; the circle of the points simply goes on
        // top (Ernesto, 2026-11-25).
        val side = 8.dp + maxOf(startRoom, endRoom) * 0.75f
        // The word at the top of every box, so the words of the two boxes line up; below it, the icon and the
        // content in the middle of the room that is left (Ernesto, 2026-11-25).
        Column(
            Modifier.fillMaxHeight().padding(start = side, top = 12.dp, end = side, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
        ) {
            Text(word, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs, Alignment.CenterVertically),
            ) {
                icon?.invoke()
                content()
            }
        }
    }
}

/**
 * The name of a player in a box of [WinnerPayerBoxes], in up to two lines. A word never breaks: when the longest word
 * does not fit, the name takes a smaller size (Ernesto, 2026-11-25).
 */
@Composable
private fun ReviewSideName(name: String, small: Boolean = false) {
    val label = MaterialTheme.typography.labelSmall
    val sizes = listOfNotNull(
        MaterialTheme.typography.titleMedium.takeIf { !small },
        MaterialTheme.typography.titleSmall,
        MaterialTheme.typography.labelLarge,
        MaterialTheme.typography.labelMedium,
        label,
        label.copy(fontSize = 10.sp, lineHeight = 12.sp),
        label.copy(fontSize = 9.sp, lineHeight = 11.sp),
    ).map { it.inTextFont().copy(fontWeight = FontWeight.Bold) }
    // The real layout decides: while a line breaks inside a word, the name takes the next smaller size.
    var index by remember(name, small) { mutableStateOf(0) }
    Text(
        name,
        style = sizes[index],
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout -> if (index < sizes.lastIndex && (layout.hasVisualOverflow || layout.breaksAWord(name))) index++ },
    )
}

/** True when a line of the text ends in the middle of a word. */
private fun TextLayoutResult.breaksAWord(text: String): Boolean = (0 until lineCount - 1).any { line ->
    val end = getLineEnd(line)
    end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
}

/** The flag and the number of a player, small and not bold: they help but are not the message (Ernesto, 2026-11-25). */
@Composable
private fun SmallFlagAndNumber(country: String?, slot: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        PlayerFlag(country, width = 16.dp)
        Text(playerNumber(slot), style = MaterialTheme.typography.labelMedium.inTextFont(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (isDraw) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(Res.string.hand_draw),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            WinnerPayerBoxes(view, winner, loser, selfDraw, score, chicken)
        }
    }
    ReviewScoreTable(
        view = view,
        seats = preview.seatSlotIds,
        handNumber = preview.handId,
        changeHeader = stringResource(Res.string.details_col_hand),
        changes = preview.handScores,
        totals = preview.tableScores,
        points = preview.tablePoints.takeIf { showPoints },
    )
}

/**
 * Every player of the table with what changes now ([changes], under [changeHeader]) and the total after it, and the
 * table points when the table ends. The players go from the best total to the worst. Used by the check
 * of a hand and of a penalty (Ernesto, 2026-11-25).
 */
@Composable
private fun ReviewScoreTable(
    view: PlayerTournamentView,
    seats: List<Int?>,
    handNumber: Int,
    changeHeader: String,
    changes: List<Int>,
    totals: List<String>,
    points: List<String>?,
) {
    // The number columns are as wide as their widest text, so the name keeps the rest of the row (Ernesto, 2026-11-25).
    // From the best total to the worst, like a ranking; players with the same total keep the order of their winds
    // (Ernesto, 2026-11-25).
    val order = seats.indices.filter { seats[it] != null }
        .sortedBy { index -> seats[index]?.let { currentWindOf(seats, it, handNumber)?.ordinal } ?: index }
        .sortedByDescending { index -> totals.getOrNull(index)?.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
    val headerStyle = MaterialTheme.typography.labelMedium
    val valueStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun columnWidth(header: String, values: List<String>): Dp = with(density) {
        maxOf(measurer.measure(header, headerStyle).size.width, values.maxOfOrNull { measurer.measure(it, valueStyle).size.width } ?: 0).toDp()
    }
    val totalHeader = stringResource(Res.string.entry_total)
    val pointsHeader = stringResource(Res.string.entry_points)
    val changeWidth = columnWidth(changeHeader, changes.map(::signedText))
    val totalWidth = columnWidth(totalHeader, totals)
    val pointsWidth = columnWidth(pointsHeader, points.orEmpty())
    val columnGap = MtsTheme.spacing.md
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(columnGap)) {
            Spacer(Modifier.weight(1f))
            HeaderText(changeHeader, Modifier.width(changeWidth))
            HeaderText(totalHeader, Modifier.width(totalWidth))
            if (points != null) HeaderText(pointsHeader, Modifier.width(pointsWidth))
        }
        order.forEach { index ->
            val slot = seats[index] ?: return@forEach
            val change = changes.getOrNull(index) ?: 0
            // The numbers sit on the line of the name, not in the middle of the two lines (Ernesto, 2026-11-25).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                // Small and above the name, the wind, the flag and the number: they help to find the player but are not
                // the message. The name has the whole width of the column (Ernesto, 2026-11-25).
                Column(Modifier.weight(1f)) {
                    // The wind glyph has its own room on the right, so no gap after it: the flag sits in the middle.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        currentWindOf(seats, slot, handNumber)?.let { WindIcon(it, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        SmallFlagAndNumber(view.players[slot]?.country, slot)
                    }
                    Text(view.firstNameOf(slot), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    signedText(change),
                    Modifier.width(changeWidth),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold,
                    color = signedColor(change),
                    maxLines = 1,
                )
                Text(totals.getOrNull(index).orEmpty(), Modifier.width(totalWidth), textAlign = TextAlign.End, maxLines = 1)
                if (points != null) {
                    Text(
                        points.getOrNull(index).orEmpty(),
                        Modifier.width(pointsWidth),
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** What the penalties change for each seat (east, south, west, north of hand 1): minus the amount, and a third to the others when shared. */
internal fun penaltyChanges(penalties: List<PenaltyEntry>): List<Int> {
    val changes = MutableList(4) { 0 }
    penalties.forEach { penalty ->
        if (penalty.seatIndex !in 0..3) return@forEach
        changes[penalty.seatIndex] -= penalty.amount
        if (penalty.shared) (0..3).filter { it != penalty.seatIndex }.forEach { changes[it] += penalty.amount / 3 }
    }
    return changes
}

/**
 * The check of a penalty, like the check of a hand: a red box with the penalized player and the points, a green box
 * when the other three share them, and the table of every player with the penalty and the total after it.
 */
@Composable
private fun ReviewPenalty(penalties: List<PenaltyEntry>, view: PlayerTournamentView, table: PlayerTable, seats: List<Int?>, handNumber: Int) {
    val penalty = penalties.firstOrNull() ?: return
    val slot = seats.getOrNull(penalty.seatIndex) ?: return
    val changes = penaltyChanges(penalties)
    val t = table.table
    val scores = listOf(t.playerEastScore, t.playerSouthScore, t.playerWestScore, t.playerNorthScore).map { it.trim().toIntOrNull() ?: 0 }
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            ReviewSideBox(
                Modifier.weight(1f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.error,
                word = stringResource(Res.string.entry_penalized_short),
                endRoom = if (penalty.shared) CircleRoom else 0.dp,
            ) {
                SmallFlagAndNumber(view.players[slot]?.country, slot)
                ReviewSideName(view.firstNameOf(slot), small = penalty.shared)
                if (!penalty.shared) {
                    Text(
                        "-${penalty.amount} ${stringResource(Res.string.entry_points_abbr)}",
                        style = MaterialTheme.typography.headlineSmall.inTextFont(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (penalty.shared) {
                // The table below shows what each of the others gets, so the box only says that it is shared (Ernesto, 2026-11-25).
                ReviewSideBox(Modifier.weight(1f).fillMaxHeight(), color = MtsTheme.colors.gain, word = stringResource(Res.string.entry_share_short), startRoom = CircleRoom) {}
            }
        }
        if (penalty.shared) ScoreCircle("-${penalty.amount}", Modifier.align(Alignment.Center))
    }
    ReviewScoreTable(
        view = view,
        seats = seats,
        handNumber = handNumber,
        changeHeader = stringResource(Res.string.hand_penalty),
        changes = changes,
        totals = scores.zip(changes) { score, change -> signedText(score + change) },
        points = null,
    )
}

/** The size of [ScoreCircle], and the room each box leaves for it on its inner side. */
private val ScoreFieldWidth = 180.dp
private val PenaltyShareTrim = 16.dp
private val CircleSize = 60.dp
private val CircleRoom = 22.dp

/** The chicken of a chicken hand in a small circle, under the circle of the points. */
@Composable
private fun ChickenCircle(modifier: Modifier) {
    val label = stringResource(Res.string.chicken_hand)
    Surface(
        modifier.size(ChickenCircleSize).semantics { contentDescription = label },
        shape = CircleShape,
        // A light warm background, so the chicken does not get lost on the grey of the screen (Ernesto, 2026-11-25).
        color = MtsTheme.colors.warning.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MtsTheme.colors.warning),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { ChickenIcon(20.dp) }
    }
}

private val ChickenCircleSize = 30.dp

/** The points in a circle between the two boxes, with the short word for points below them (Ernesto, 2026-11-25). */
@Composable
private fun ScoreCircle(score: String, modifier: Modifier) {
    Surface(
        modifier.size(CircleSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        // A little overlap of the two lines: "pts" sits close under the number (Ernesto, 2026-11-25).
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-3).dp, Alignment.CenterVertically)) {
            // The lines are trimmed to their letters, so "pts" sits right under the number (Ernesto, 2026-11-25).
            Text(score, style = MaterialTheme.typography.headlineSmall.inTextFont().tight(), fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.entry_points_abbr),
                style = MaterialTheme.typography.labelSmall.inTextFont().tight(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        Triple(SeatWind.EAST.label(), t.playerEastId, t.playerEastScore to points[0]),
        Triple(SeatWind.SOUTH.label(), t.playerSouthId, t.playerSouthScore to points[1]),
        Triple(SeatWind.WEST.label(), t.playerWestId, t.playerWestScore to points[2]),
        Triple(SeatWind.NORTH.label(), t.playerNorthId, t.playerNorthScore to points[3]),
    )
    val lastHand = table.hands.filter { it.isDone }.maxByOrNull { it.handId }
    TrackedDialog("EntryTableChanged") {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onClose,
            title = { MtsDialogTitle(stringResource(Res.string.changed_title)) },
            text = {
                Column(Modifier.verticalScrollWithIndicators(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(serverMessage)
                    Text(stringResource(Res.string.changed_not_saved))
                    Text(stringResource(Res.string.changed_table_now), fontWeight = FontWeight.Bold)
                    rows.forEach { (wind, id, result) ->
                        Text(
                            stringResource(Res.string.changed_table_row, wind, view.nameOf(id.toIntOrNull()), result.first.ifBlank { "—" }, result.second.ifBlank { "—" }),
                        )
                    }
                    Text(stringResource(Res.string.changed_saved_hands, table.doneHands, table.hands.size))
                    lastHand?.let { hand ->
                        val loser = hand.playerLooserId.toIntOrNull()
                        Text(
                            if (loser != null) {
                                stringResource(Res.string.changed_last_hand_from, hand.handId, view.nameOf(hand.playerWinnerId.toIntOrNull()), hand.handScore, view.nameOf(loser))
                            } else {
                                stringResource(Res.string.changed_last_hand_self, hand.handId, view.nameOf(hand.playerWinnerId.toIntOrNull()), hand.handScore)
                            },
                        )
                    }
                    if (enteredHand != null) {
                        Text(stringResource(Res.string.changed_your_hand), fontWeight = FontWeight.Bold)
                        Text(
                            if (enteredHand.winner == null) {
                                stringResource(Res.string.hand_draw)
                            } else {
                                val won = if (enteredHand.loser != null) {
                                    stringResource(Res.string.changed_entered_from, view.nameOf(enteredHand.winner), enteredHand.score, view.nameOf(enteredHand.loser))
                                } else {
                                    stringResource(Res.string.changed_entered_self, view.nameOf(enteredHand.winner), enteredHand.score)
                                }
                                if (enteredHand.chicken) stringResource(Res.string.changed_entered_chicken, won) else won
                            },
                        )
                        Text(stringResource(Res.string.changed_already))
                    }
                    Text(stringResource(Res.string.changed_call_staff))
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(onClick = onClose) { Text(stringResource(Res.string.changed_close)) }
                }
            },
        )
    }
}

/** Before the gong, a centered warning chip says when the hand buttons appear (Ernesto, 2026-11-25). */
@Composable
private fun RoundNotStartedNote(text: String) {
    val warning = MtsTheme.colors.warning
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = warning.copy(alpha = 0.16f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, warning.copy(alpha = 0.6f)),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = warning)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** A style whose line is as tall as its letters, without the room above and below. */
private fun TextStyle.tight(): TextStyle = copy(
    lineHeight = fontSize,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** Shows a minus before a number that is typed, so a penalty reads as "-30" in the middle of its field. */
private val MinusPrefix = VisualTransformation { text ->
    if (text.isEmpty()) {
        TransformedText(text, OffsetMapping.Identity)
    } else {
        TransformedText(
            AnnotatedString("-") + text,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset + 1
                override fun transformedToOriginal(offset: Int) = (offset - 1).coerceIn(0, text.length)
            },
        )
    }
}
