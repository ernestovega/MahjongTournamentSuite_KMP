package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundControl
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TABLE_HAS_ALL_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalTelemetry
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.enter_remaining_time_in_minutes
import mahjongtournamentsuite.adminapp.generated.resources.remaining_time
import mahjongtournamentsuite.adminapp.generated.resources.reset_timer
import mahjongtournamentsuite.adminapp.generated.resources.round_control_auto
import mahjongtournamentsuite.adminapp.generated.resources.round_control_clock
import mahjongtournamentsuite.adminapp.generated.resources.round_control_clock_help
import mahjongtournamentsuite.adminapp.generated.resources.round_control_closed
import mahjongtournamentsuite.adminapp.generated.resources.round_control_completed_notice
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_badge
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_notice
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_review
import mahjongtournamentsuite.adminapp.generated.resources.round_control_hands
import mahjongtournamentsuite.adminapp.generated.resources.round_control_hands_help
import mahjongtournamentsuite.adminapp.generated.resources.round_control_manual_warning
import mahjongtournamentsuite.adminapp.generated.resources.round_control_no_tables
import mahjongtournamentsuite.adminapp.generated.resources.round_control_not_started
import mahjongtournamentsuite.adminapp.generated.resources.round_control_open
import mahjongtournamentsuite.adminapp.generated.resources.round_control_over
import mahjongtournamentsuite.adminapp.generated.resources.round_control_paused
import mahjongtournamentsuite.adminapp.generated.resources.round_control_players
import mahjongtournamentsuite.adminapp.generated.resources.round_control_round_in_play
import mahjongtournamentsuite.adminapp.generated.resources.round_control_running
import mahjongtournamentsuite.adminapp.generated.resources.round_control_seats
import mahjongtournamentsuite.adminapp.generated.resources.round_control_seats_help
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_all_hands_done
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_completed
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_hands
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_in_play
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_round
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_status
import mahjongtournamentsuite.adminapp.generated.resources.round_control_tables
import mahjongtournamentsuite.adminapp.generated.resources.round_control_tables_help
import mahjongtournamentsuite.adminapp.generated.resources.round_control_title
import mahjongtournamentsuite.adminapp.generated.resources.round_n
import mahjongtournamentsuite.adminapp.generated.resources.set_time
import mahjongtournamentsuite.adminapp.generated.resources.table
import mahjongtournamentsuite.adminapp.generated.resources.timer_gong_resume
import mahjongtournamentsuite.adminapp.generated.resources.timer_gong_start
import mahjongtournamentsuite.adminapp.generated.resources.timer_late_start
import mahjongtournamentsuite.adminapp.generated.resources.timer_offline
import mahjongtournamentsuite.adminapp.generated.resources.timer_pause
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago_title
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop_confirm
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop_confirm_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop_confirm_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Round control: the plan B of the Timer, for admins. A dialog over the tournament screen. It holds the clock buttons
 * of a round, so nobody depends on the Timer, and the manual control of what the players can do: hand entry and seats
 * for the whole round, the hand entry of one table, and whether a table is completed (the same mark as the Completed
 * switch of the table editor). A manual mode stays until an admin sets it back to automatic.
 */
@Composable
fun RoundControlDialog(
    tournamentId: String,
    initialRound: Int,
    onDismiss: () -> Unit,
) {
    val repository = koinInject<TournamentRepository>()
    val telemetry = LocalTelemetry.current
    val controller = remember(tournamentId) { RoundTimerController(repository, tournamentId, initialRound, telemetry = telemetry) }
    RoundControlDialogContent(
        controller = controller,
        loadTables = { mode -> repository.listTournamentTables(tournamentId, null, mode) },
        loadRoundHands = { roundId -> repository.getRoundTablesWithHands(tournamentId, roundId, RefreshMode.FORCE) },
        onDismiss = onDismiss,
    )
}

/** How often the dialog reads the clocks and the tables, to follow the other admins and the players. */
private const val ROUND_CONTROL_POLL_MS = 5_000L

/** From this width the clock and the rules of the players sit side by side. */
private val TWO_COLUMNS_MIN_WIDTH = 760.dp

private enum class RoundControlPrompt { SetTime, StartedAgo, ConfirmStop }

@Composable
internal fun RoundControlDialogContent(
    controller: RoundTimerController,
    /** Every table of the tournament: the round strip shows the status of each round. */
    loadTables: suspend (mode: RefreshMode) -> AppResult<List<TournamentTable>>,
    /** The tables of a round with their hands: the review of the open tables counts the hands entered. */
    loadRoundHands: suspend (roundId: Int) -> AppResult<List<Pair<TableState, List<TableHand>>>>,
    onDismiss: () -> Unit,
    deviceNowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsState()
    var allTables by remember { mutableStateOf<List<TournamentTable>?>(null) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var prompt by remember { mutableStateOf<RoundControlPrompt?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The review of the open tables of the finished rounds. It follows the tables, so the closed ones leave it.
    var reviewing by remember { mutableStateOf(false) }
    // The table to scroll to after "Go to the round" in the review.
    var scrollToTable by remember { mutableStateOf<TableKey?>(null) }
    // Bumped after a table changes its completed mark, so the tables load again at once.
    var tablesRevision by remember { mutableStateOf(0) }

    LaunchedEffect(controller) {
        while (true) {
            controller.refresh()
            delay(ROUND_CONTROL_POLL_MS)
        }
    }
    LaunchedEffect(state.tournament != null, tablesRevision) {
        if (state.tournament == null) return@LaunchedEffect
        var mode = if (tablesRevision > 0) RefreshMode.FORCE else RefreshMode.IF_CHANGED
        while (true) {
            (loadTables(mode) as? AppResult.Success)?.let { result -> allTables = result.value }
            mode = RefreshMode.IF_CHANGED
            delay(ROUND_CONTROL_POLL_MS)
        }
    }
    val now by produceState(deviceNowMs()) {
        while (true) {
            delay(500)
            value = deviceNowMs()
        }
    }

    fun change(change: RoundControlChange) {
        if (busy) return
        busy = true
        scope.launch {
            val result = controller.changeControl(change)
            busy = false
            if (result is AppResult.Failure) error = result.error
            if (change.reopenTables.isNotEmpty() || change.completeTables.isNotEmpty()) tablesRevision += 1
        }
    }

    // Busy until the tables load again, so the review never shows a closed table as open.
    fun closeTables(tables: List<TableKey>) {
        if (busy) return
        busy = true
        scope.launch {
            val result = controller.completeTables(tables)
            (loadTables(RefreshMode.FORCE) as? AppResult.Success)?.let { loaded -> allTables = loaded.value }
            busy = false
            if (result is AppResult.Failure) error = result.error
            tablesRevision += 1
        }
    }

    val remaining = state.remainingSeconds(now)
    val finished = state.isStarted && remaining <= 0L
    // The round whose clock runs now, if any.
    val runningRound = state.clocks.runningRound(state.durationMinutes, state.serverNow(now))
    // The tables that stayed open in the rounds that are over: a badge on their rounds and a button closes them.
    val finishedOpenTables = allTables?.let { tables ->
        val finished = finishedRounds(
            numRounds = state.numRounds,
            tables = tables,
            clocks = state.clocks,
            durationMinutes = state.durationMinutes,
            extraMs = state.extraMs,
            serverNowMs = state.serverNow(now),
            tournamentCompleted = state.tournamentCompleted,
        )
        openTablesOf(finished, tables)
    }.orEmpty()

    error?.let {
        // A table with every hand done always stays completed. The text says how to open it.
        if (it == AppError.Conflict(TABLE_HAS_ALL_HANDS)) {
            AppErrorDialog(stringResource(Res.string.round_control_table_all_hands_done), onDismiss = { error = null })
        } else {
            AppErrorDialog(it.toUiText(), onDismiss = { error = null })
        }
    }
    when (prompt) {
        RoundControlPrompt.SetTime -> MinutesDialog(
            name = "RoundControlSetTime",
            title = stringResource(Res.string.remaining_time),
            message = stringResource(Res.string.enter_remaining_time_in_minutes),
            initialMinutes = ((remaining + 59) / 60).coerceAtLeast(1),
            allowZero = false,
            onApply = { minutes ->
                prompt = null
                scope.launch { controller.setRemainingMinutes(minutes) }
            },
            onDismiss = { prompt = null },
        )
        RoundControlPrompt.StartedAgo -> MinutesDialog(
            name = "RoundControlStartedAgo",
            title = stringResource(Res.string.timer_started_ago_title),
            message = stringResource(Res.string.timer_started_ago_message),
            initialMinutes = 0,
            allowZero = true,
            onApply = { minutes ->
                prompt = null
                scope.launch { controller.startedMinutesAgo(minutes.toInt()) }
            },
            onDismiss = { prompt = null },
        )
        RoundControlPrompt.ConfirmStop -> ConfirmDialog(
            name = "RoundControlConfirmStop",
            title = stringResource(Res.string.timer_stop_confirm_title),
            message = stringResource(Res.string.timer_stop_confirm_message),
            confirm = stringResource(Res.string.timer_stop_confirm),
            onConfirm = {
                prompt = null
                scope.launch { controller.stop() }
            },
            onDismiss = { prompt = null },
        )
        null -> Unit
    }
    // Once every table is closed, the review has nothing left to show.
    if (reviewing && finishedOpenTables.isEmpty() && !busy) reviewing = false
    if (reviewing) {
        OpenTablesReviewDialog(
            tables = finishedOpenTables,
            busy = busy,
            loadRoundHands = loadRoundHands,
            onGoTo = { table ->
                reviewing = false
                controller.selectRound(table.roundId)
                scrollToTable = table
            },
            onCloseSelected = { selected -> closeTables(selected) },
            onDismiss = { reviewing = false },
        )
    }
    if (state.confirmReset) {
        ConfirmDialog(
            name = "RoundControlConfirmReset",
            title = stringResource(Res.string.timer_reset_confirm_title),
            message = stringResource(Res.string.timer_reset_confirm_message),
            confirm = stringResource(Res.string.timer_reset_confirm),
            onConfirm = { scope.launch { controller.reset(force = true) } },
            onDismiss = { controller.dismissResetConfirmation() },
        )
    }

    val scrollState = rememberScrollState()
    TrackedDialog("RoundControl") {
        AlertDialog(
            // Twice the standard width (560 dp), so the clock and the rules of the players fit side by side.
            // A phone keeps 16 dp on each side.
            modifier = Modifier.appFocusGroup().padding(16.dp).widthIn(max = 1120.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text(stringResource(Res.string.round_control_title))
                    state.tournament?.name?.ifBlank { null }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Above the rounds: it is about the whole tournament, not the selected round.
                    if (finishedOpenTables.isNotEmpty()) {
                        FinishedRoundsNotice(
                            openTables = finishedOpenTables,
                            enabled = !busy,
                            onReview = { reviewing = true },
                        )
                    }
                    // The rounds stay on top while the rest scrolls.
                    RoundStrip(
                        numRounds = state.numRounds,
                        selected = state.roundId,
                        runningRound = runningRound,
                        allTables = allTables,
                        openTablesByRound = finishedOpenTables.groupingBy { it.roundId }.eachCount(),
                        onSelect = controller::selectRound,
                    )
                    ScrollableColumnWithScrollbar(state = scrollState, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.tournamentCompleted) Notice(stringResource(Res.string.round_control_completed_notice), warning = false)
                            if (state.control.isManual) Notice(stringResource(Res.string.round_control_manual_warning), warning = true)
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val clock: @Composable (Modifier) -> Unit = { modifier ->
                                    ClockCard(state, now, remaining, finished, modifier, onPrompt = { prompt = it }, scope = scope, controller = controller)
                                }
                                val rules: @Composable (Modifier) -> Unit = { modifier ->
                                    PlayersCard(state.control.hands, state.control.seats, enabled = !busy, modifier = modifier, onChange = ::change)
                                }
                                if (maxWidth >= TWO_COLUMNS_MIN_WIDTH) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                                        clock(Modifier.weight(1f).fillMaxHeight())
                                        rules(Modifier.weight(1f).fillMaxHeight())
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        clock(Modifier.fillMaxWidth())
                                        rules(Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            TablesCard(
                                tables = allTables?.filter { it.roundId == state.roundId }?.sortedBy { it.tableId },
                                control = state.control,
                                enabled = !busy,
                                onChange = ::change,
                                scrollTo = scrollToTable?.takeIf { it.roundId == state.roundId }?.tableId,
                                onScrolled = { scrollToTable = null },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FocusedButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
            },
        )
    }
}

/** One chip per round, with the status of its tables and a play mark on the round whose clock runs. */
@Composable
private fun RoundStrip(
    numRounds: Int,
    selected: Int,
    runningRound: Int?,
    allTables: List<TournamentTable>?,
    /** Open tables of each finished round. A round with any gets a badge with the count. */
    openTablesByRound: Map<Int, Int>,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // Keyed on numRounds too: it is 1 until the tournament loads, so the first scroll had no chip to reach.
    var scrolledOnce by remember { mutableStateOf(false) }
    LaunchedEffect(selected, numRounds) {
        if (selected > numRounds) return@LaunchedEffect
        val target = (selected - 2).coerceAtLeast(0)
        if (scrolledOnce) listState.animateScrollToItem(target) else listState.scrollToItem(target)
        scrolledOnce = true
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items((1..numRounds.coerceAtLeast(1)).toList()) { round ->
            FilterChip(
                selected = round == selected,
                onClick = { onSelect(round) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(Res.string.round_n, round))
                        openTablesByRound[round]?.takeIf { it > 0 }?.let { count ->
                            val description = stringResource(Res.string.round_control_finished_badge, count)
                            Badge(
                                modifier = Modifier.testTag("round_chip_${round}_open_tables").semantics { contentDescription = description },
                            ) { Text(count.toString()) }
                        }
                    }
                },
                leadingIcon = if (round == runningRound) {
                    {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(Res.string.round_control_round_in_play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
                trailingIcon = allTables?.let { tables ->
                    { StatusIconWithTooltip(roundCompletionStatus(tables.filter { it.roundId == round })) }
                },
                modifier = Modifier.testTag("round_chip_$round"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockCard(
    state: RoundTimerState,
    now: Long,
    remaining: Long,
    finished: Boolean,
    modifier: Modifier,
    onPrompt: (RoundControlPrompt) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    controller: RoundTimerController,
) {
    val canClock = state.canControl && !state.isOffline
    Card(
        title = stringResource(Res.string.round_control_clock),
        info = stringResource(Res.string.round_control_clock_help),
        modifier = modifier,
    ) {
        val status = when {
            !state.isStarted -> Res.string.round_control_not_started
            finished -> Res.string.round_control_over
            !state.isRunning -> Res.string.round_control_paused
            else -> Res.string.round_control_running
        }
        // Like the Timer: an ended round shows its time and its state in red.
        val color = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(formatHms(remaining), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = color)
            Text(
                stringResource(status),
                style = MaterialTheme.typography.titleMedium,
                color = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isOffline) Text(stringResource(Res.string.timer_offline), color = MaterialTheme.colorScheme.error)
        state.latePlannedStart(now)?.let { Text(stringResource(Res.string.timer_late_start, it), color = MaterialTheme.colorScheme.error) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !state.isStarted -> {
                    FocusedButton(enabled = canClock, onClick = { scope.launch { controller.play() } }) {
                        Text(stringResource(Res.string.timer_gong_start))
                    }
                    FocusedOutlinedButton(enabled = canClock, onClick = { onPrompt(RoundControlPrompt.StartedAgo) }) {
                        Text(stringResource(Res.string.timer_started_ago))
                    }
                }
                !finished -> {
                    if (state.isRunning) {
                        FocusedButton(enabled = canClock, onClick = { scope.launch { controller.pause() } }) {
                            Text(stringResource(Res.string.timer_pause))
                        }
                    } else {
                        FocusedButton(enabled = canClock, onClick = { scope.launch { controller.play() } }) {
                            Text(stringResource(Res.string.timer_gong_resume))
                        }
                    }
                    FocusedOutlinedButton(enabled = canClock, onClick = { onPrompt(RoundControlPrompt.ConfirmStop) }) {
                        Text(stringResource(Res.string.timer_stop))
                    }
                }
            }
            if (state.isStarted) {
                FocusedOutlinedButton(enabled = canClock, onClick = { onPrompt(RoundControlPrompt.SetTime) }) {
                    Text(stringResource(Res.string.set_time))
                }
                FocusedOutlinedButton(enabled = canClock, onClick = { scope.launch { controller.askReset() } }) {
                    Text(stringResource(Res.string.reset_timer))
                }
            }
        }
    }
}

/** What the players of the round can do: add hands and set the seats. */
@Composable
private fun PlayersCard(
    hands: ControlMode,
    seats: ControlMode,
    enabled: Boolean,
    modifier: Modifier,
    onChange: (RoundControlChange) -> Unit,
) {
    Card(title = stringResource(Res.string.round_control_players), info = null, modifier = modifier) {
        LabeledModes(
            label = stringResource(Res.string.round_control_hands),
            info = stringResource(Res.string.round_control_hands_help),
        ) { ModeChips(tag = "hands", selected = hands, enabled = enabled) { onChange(RoundControlChange(hands = it)) } }
        LabeledModes(
            label = stringResource(Res.string.round_control_seats),
            info = stringResource(Res.string.round_control_seats_help),
        ) { ModeChips(tag = "seats", selected = seats, enabled = enabled) { onChange(RoundControlChange(seats = it)) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TablesCard(
    tables: List<TournamentTable>?,
    control: RoundControl,
    enabled: Boolean,
    onChange: (RoundControlChange) -> Unit,
    /** A table to bring into view once, after "Go to the round" in the review of the open tables. */
    scrollTo: Int? = null,
    onScrolled: () -> Unit = {},
) {
    Card(title = stringResource(Res.string.round_control_tables), info = stringResource(Res.string.round_control_tables_help), modifier = Modifier.fillMaxWidth()) {
        when {
            tables == null -> Unit
            tables.isEmpty() -> Text(stringResource(Res.string.round_control_no_tables))
            else -> tables.forEachIndexed { index, table ->
                if (index > 0) HorizontalDivider()
                val requester = remember { BringIntoViewRequester() }
                if (scrollTo == table.tableId) {
                    LaunchedEffect(scrollTo) {
                        // Let the round's tables lay out first.
                        delay(100)
                        requester.bringIntoView()
                        onScrolled()
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.bringIntoViewRequester(requester).testTag("table_row_${table.tableId}"),
                ) {
                    Text("${stringResource(Res.string.table)} ${table.tableId}", fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 72.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        // The same mark as the Completed switch of the table editor. A completed table takes no hand.
                        SubCard(stringResource(Res.string.round_control_table_status)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !table.isCompleted,
                                    enabled = enabled,
                                    onClick = { if (table.isCompleted) onChange(RoundControlChange(reopenTables = listOf(table.tableId))) },
                                    label = { Text(stringResource(Res.string.round_control_table_in_play)) },
                                    modifier = Modifier.testTag("table_${table.tableId}_in_play"),
                                )
                                FilterChip(
                                    selected = table.isCompleted,
                                    enabled = enabled,
                                    onClick = { if (!table.isCompleted) onChange(RoundControlChange(completeTables = listOf(table.tableId))) },
                                    label = { Text(stringResource(Res.string.round_control_table_completed)) },
                                    modifier = Modifier.testTag("table_${table.tableId}_completed"),
                                )
                            }
                        }
                        SubCard(stringResource(Res.string.round_control_table_hands)) {
                            ModeChips(
                                tag = "table_${table.tableId}",
                                selected = control.tables[table.tableId] ?: ControlMode.AUTO,
                                enabled = enabled,
                                autoLabel = Res.string.round_control_table_round,
                            ) { mode -> onChange(RoundControlChange(table = table.tableId to mode)) }
                        }
                    }
                }
            }
        }
    }
}

/** A small titled group inside a table row. */
@Composable
private fun SubCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** A label with an info button, above its chips. */
@Composable
private fun LabeledModes(label: String, info: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            InfoTooltipIcon(info)
        }
        chips()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(
    tag: String,
    selected: ControlMode,
    enabled: Boolean,
    autoLabel: StringResource = Res.string.round_control_auto,
    onSelect: (ControlMode) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlMode.entries.forEach { mode ->
            val label = when (mode) {
                ControlMode.AUTO -> autoLabel
                ControlMode.OPEN -> Res.string.round_control_open
                ControlMode.CLOSED -> Res.string.round_control_closed
            }
            FilterChip(
                selected = mode == selected,
                enabled = enabled,
                onClick = { if (mode != selected) onSelect(mode) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("${tag}_${mode.name.lowercase()}"),
            )
        }
    }
}

/** A titled block with an outline. [info] adds an info button after the title. */
@Composable
private fun Card(title: String, info: String?, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                info?.let { InfoTooltipIcon(it) }
            }
            content()
        }
    }
}

/** The open tables of the rounds that are over, with the button that opens their review. About the whole tournament. */
@Composable
private fun FinishedRoundsNotice(openTables: List<TableKey>, enabled: Boolean, onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("finished_rounds_open_tables"),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                pluralStringResource(Res.plurals.round_control_finished_notice, openTables.size, openTables.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            FocusedButton(enabled = enabled, onClick = onReview) {
                Text(stringResource(Res.string.round_control_finished_review))
            }
        }
    }
}

@Composable
private fun Notice(text: String, warning: Boolean) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConfirmDialog(
    name: String,
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackedDialog(name) {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { FocusedButton(onClick = onConfirm) { Text(confirm) } },
            dismissButton = { FocusedButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}
