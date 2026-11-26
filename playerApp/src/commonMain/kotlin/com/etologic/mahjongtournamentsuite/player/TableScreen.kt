package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.BestHandIcon
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenHandMark
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsSwitchRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTintedChip
import com.etologic.mahjongtournamentsuite.presentation.components.ProvidePlayerNumberDigits
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.SelfDrawIcon
import com.etologic.mahjongtournamentsuite.presentation.components.playerNumber
import com.etologic.mahjongtournamentsuite.presentation.components.TableSeatsView
import com.etologic.mahjongtournamentsuite.presentation.components.rememberReduceMotion
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.details_no_hands_message
import mahjongtournamentsuite.playerapp.generated.resources.details_no_hands_title
import mahjongtournamentsuite.playerapp.generated.resources.entry_points_abbr
import mahjongtournamentsuite.playerapp.generated.resources.hand_discard
import mahjongtournamentsuite.playerapp.generated.resources.hand_draw
import mahjongtournamentsuite.playerapp.generated.resources.hand_penalty
import mahjongtournamentsuite.playerapp.generated.resources.hand_self_pick
import mahjongtournamentsuite.playerapp.generated.resources.round_clock_over
import mahjongtournamentsuite.playerapp.generated.resources.round_clock_paused
import mahjongtournamentsuite.playerapp.generated.resources.round_clock_remaining
import mahjongtournamentsuite.playerapp.generated.resources.round_will_start_at
import mahjongtournamentsuite.playerapp.generated.resources.round_will_start_on_at
import mahjongtournamentsuite.playerapp.generated.resources.stat_hands
import mahjongtournamentsuite.playerapp.generated.resources.stats_round_table
import mahjongtournamentsuite.playerapp.generated.resources.table_details
import mahjongtournamentsuite.playerapp.generated.resources.table_hand_progress
import mahjongtournamentsuite.playerapp.generated.resources.table_sign_in_to_enter_hands
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The rules at hand during the game, and the contact with support as in the other screens. On a phone they go into the
 * menu, after the account buttons.
 */
@Composable
internal fun TableTopBarActions(navController: NavHostController) {
    RulesTopBarButton(navController, dividerAbove = true, dividerBelow = true)
    ContactTopBarButton(navController)
}

@Composable
fun TableScreen(navController: NavHostController, tournamentId: String, roundId: Int, tableId: Int) {
    var reloadKey by remember { mutableStateOf(0) }
    val (view, message) = rememberTournamentView(tournamentId, reloadKey, tableRoundId = roundId, tableId = tableId)
    val table = view?.tables?.firstOrNull { it.roundId == roundId && it.tableId == tableId }
    // A hand that arrives while the screen is open animates in, and the totals count to their new values. The phone that
    // saved it animates too, so the four players see the same.
    val reduceMotion = rememberReduceMotion()
    val changes = remember { TableChangeTracker() }
    val change = remember(table?.hands) { table?.let { changes.update(it.hands, animate = !reduceMotion) } }
    var preselectWinner by remember { mutableStateOf<Int?>(null) }
    // The detailed list of hands is a switch under the seats.
    var details by rememberSaveable { mutableStateOf(false) }
    // The clock moves on, so a table whose time ends while the screen is open closes for hands. The Play of the
    // Timer comes with the next check of the server.
    // The time of the venue from the first frame: a device in another zone would show another phase for a moment.
    val venue = view?.tournament
    val now by produceState(venue?.currentVenueDateTime() ?: currentLocalDateTime(), venue) {
        while (true) {
            value = venue?.currentVenueDateTime() ?: currentLocalDateTime()
            delay(5_000)
        }
    }

    val session = koinInject<PlayerSessionPresenter>()
    // Anyone can follow the table. Writing hands needs the account of a player who sits at it.
    val signedIn by produceState(true) { value = session.hasSession() }

    val account = rememberAccountActions(navController)
    account.Dialogs()
    // The numbers of the players have as many digits as the number of players of the tournament: "#07", or "#007".
    ProvidePlayerNumberDigits(view?.tournament?.numPlayers ?: 0) {
        AppScaffold(
            rootLeadingActions = { account.Buttons() },
            // The round and the table are the title. The tournament is the subtitle.
            title = stringResource(Res.string.stats_round_table, roundId, tableId),
            subtitle = view?.tournament?.name,
            shortSubtitle = view?.tournament?.shortName,
            isLoading = view == null && message == null,
            autoFocusFirst = false,
            onBack = { navController.popBackStack() },
            actions = { TableTopBarActions(navController) },
        ) {
            ScreenColumn(
                maxWidth = 720.dp,
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                scrollable = true,
                paddingInsideScroll = true,
            ) {
                MessageDialog(message)
                if (view == null || table == null) return@ScreenColumn
                val complete = view.isFinished(table, now)
                val handNumber = minOf(table.doneHands + 1, 16)
                // A hand that arrives, a correction or a deletion is said by the screen reader, also without animations.
                // The announcer shares a box with the seats: alone it would be one more item of the column, with its
                // 16 dp gap above the row of the time left (Ernesto, 2026-11-25).
                Box {
                    HandChangeAnnouncement(changes.latest)
                    TableSeatsView(
                        seats = table.seatsFor(view, withLevelChange = true),
                        handNumber = handNumber,
                        // Before the round starts and on a finished table there is no hand in play, so the circle in the middle is hidden.
                        centerLabel = when {
                            complete -> ""
                            view.matchPhaseOf(roundId, now) == MatchPhase.NOT_STARTED -> ""
                            else -> stringResource(Res.string.table_hand_progress, handNumber, table.hands.size)
                        },
                        showPoints = complete,
                        windsKnown = table.seatsAssigned,
                        // The switch shows only while the table is in play: not before the gong, and not on a finished table
                        // (Ernesto, 2026-11-25).
                        diffsAvailable = view.tournament.showTableDiffs && !complete &&
                            view.matchPhaseOf(roundId, now) != MatchPhase.NOT_STARTED,
                        // The time left of the round is at the top left, above the seats.
                        leading = if (complete) null else ({ RoundClockLine(view.tournament, roundId) }),
                        animateScoreChanges = change != null,
                        asList = !complete && view.matchPhaseOf(roundId, now) == MatchPhase.NOT_STARTED,
                        // On a table that takes hands, a tap on a player starts a hand won by that player.
                        onPlayerClick = { slot ->
                            if (view.canAddHands(table, now)) {
                                preselectWinner = slot
                            } else {
                                navController.navigate(TournamentPlayerRoute(tournamentId, slot))
                            }
                        },
                    )
                }
                HandEntryPanel(
                    tournamentId = tournamentId,
                    roundId = roundId,
                    tableId = tableId,
                    view = view,
                    table = table,
                    now = now,
                    preselectWinner = preselectWinner,
                    onPreselectHandled = { preselectWinner = null },
                    onSaved = { reloadKey++ },
                )
                if (!signedIn && view.isOngoing(table, now) && !table.table.useTotalsOnly) {
                    OutlinedButton(onClick = { navController.navigate(SignInRoute()) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, Modifier.size(18.dp))
                        ButtonText(stringResource(Res.string.table_sign_in_to_enter_hands), Modifier.padding(start = 8.dp))
                    }
                }
                HandHistory(view, table, details, change) { details = it }
            }
        }
    }
}

@Composable
private fun HandHistory(
    view: PlayerTournamentView,
    table: PlayerTable,
    details: Boolean,
    change: HandListChange?,
    onDetailsChange: (Boolean) -> Unit,
) {
    // A divider and extra space separate the list from the players and the buttons above.
    HorizontalDivider(Modifier.padding(top = MtsTheme.spacing.lg))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.stat_hands),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        MtsSwitchRow(checked = details, onCheckedChange = onDetailsChange, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text(stringResource(Res.string.table_details), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (details) {
        HandDetailsTable(table, change) { seat -> SeatWind.entries.getOrNull(seat) }
    } else if (table.hands.none { it.isDone || it.penaltyRows().isNotEmpty() }) {
        EmptyState(Icons.Default.Edit, stringResource(Res.string.details_no_hands_title), stringResource(Res.string.details_no_hands_message))
    } else {
        HandList(view, table, change)
    }
}

/**
 * The newest item is at the top. Each hand is one card. The penalties of a hand were given before its result,
 * so they come under the result, with a divider. A hand that is not done shows only its penalties.
 *
 * A [change] from another device moves: a new hand slides in from the top with a green glow that fades, a changed
 * hand glows twice softly, and a hand that is gone folds away.
 */
@Composable
private fun HandList(view: PlayerTournamentView, table: PlayerTable, change: HandListChange?) {
    val shown = table.hands.filter { it.isListed() }.map { it to false }
    // A gone hand stays in the list as it was until it has folded away.
    val gone = change?.removed.orEmpty().filter { old -> shown.none { it.first.handId == old.handId } }.map { it to true }
    (shown + gone).sortedByDescending { it.first.handId }.forEach { (hand, isGone) ->
        key(hand.handId, isGone) {
            val isNew = change != null && hand.handId in change.added
            val visible = remember { MutableTransitionState(!isNew) }
            visible.targetState = !isGone
            AnimatedVisibility(
                visibleState = visible,
                enter = expandVertically(tween(HAND_ENTER_MILLIS), expandFrom = Alignment.Top) +
                    slideInVertically(tween(HAND_ENTER_MILLIS)) { -it / 2 } +
                    fadeIn(tween(HAND_ENTER_MILLIS)),
                exit = shrinkVertically(tween(HAND_EXIT_MILLIS), shrinkTowards = Alignment.Top) + fadeOut(tween(HAND_EXIT_MILLIS)),
            ) {
                HandCard(view, table, hand, change)
            }
        }
    }
}

@Composable
private fun HandCard(view: PlayerTournamentView, table: PlayerTable, hand: TableHand, change: HandListChange?) {
    val penalties = hand.penaltyRows().asReversed()
    val glow = rememberHandGlow(hand.handId, change)
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val accent = handGlowColor(hand.handId, change)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = lerp(base, accent.copy(alpha = 0.35f).compositeOver(base), glow)),
        border = if (glow > 0f) BorderStroke(2.dp, accent.copy(alpha = glow)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        // The hand number is in a narrow column at the start, centered on the whole card. The dividers stay in
        // the content column, so they do not cross the column of the number.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 8.dp).widthIn(min = 32.dp), contentAlignment = Alignment.Center) {
                HandNumber(hand.handId)
            }
            Column(Modifier.weight(1f)) {
                if (hand.isDone) HandRow(view, hand)
                penalties.forEachIndexed { index, values ->
                    if (hand.isDone || index > 0) HorizontalDivider(Modifier.padding(start = 8.dp, end = 12.dp))
                    PenaltyRow(view, table.seatIds(), values)
                }
            }
        }
    }
}

/**
 * One penalty. A red "Penalty" chip is at the top and each player with points is on a line under it. The values are
 * at the end, in the same column and size as the score of a hand.
 */
@Composable
private fun PenaltyRow(view: PlayerTournamentView, seatSlots: List<Int?>, values: List<Int>) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = MtsTheme.spacing.md, end = 12.dp, bottom = MtsTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            // The penalized players come first with a red chip. The players who receive points come under them,
            // smaller, slightly indented and with a green chip.
            val lines = values.withIndex().filter { it.value != 0 }.sortedBy { it.value > 0 }
            lines.forEach { (seat, value) ->
                val receives = value > 0
                val accent = if (receives) MtsTheme.colors.gain else MaterialTheme.colorScheme.error
                Row(
                    Modifier.padding(start = if (receives) 16.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                ) {
                    MtsTintedChip(Icons.Default.Warning, stringResource(Res.string.hand_penalty), accent, small = receives)
                    Text(
                        view.nameOf(seatSlots.getOrNull(seat)),
                        Modifier.weight(1f),
                        style = if (receives) MaterialTheme.typography.titleSmall.inTextFont() else MaterialTheme.typography.titleMedium.inTextFont(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // In the column of the scores of the hands, so both line up.
                    Box(Modifier.widthIn(min = ScoreColumnWidth), contentAlignment = Alignment.Center) {
                        ScoreValue(if (receives) "+$value" else "$value", color = accent, small = receives)
                    }
                }
            }
        }
    }
}

/**
 * The result of a hand. The winner is on the first line. The way of winning, the player who discarded and the
 * chicken are on the second line. The score is at the end of the row.
 */
@Composable
private fun HandRow(view: PlayerTournamentView, hand: TableHand) {
    val winnerSlot = hand.playerWinnerId.toIntOrNull()
    val loserSlot = hand.playerLooserId.toIntOrNull()
    val kind = hand.kind()
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = MtsTheme.spacing.md, end = 12.dp, bottom = MtsTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            if (kind != HandKind.DRAW) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                    // The number of the player goes before the name, small (Ernesto, 2026-11-25).
                    SmallPlayerNumber(winnerSlot, MtsTheme.colors.gain)
                    Text(
                        view.nameOf(winnerSlot),
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium.inTextFont(),
                        fontWeight = FontWeight.Bold,
                        color = MtsTheme.colors.gain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A discard shows only its icon before the player who discarded, with no pill (Ernesto, 2026-11-25).
                if (kind != HandKind.DISCARD) WinTypePill(kind, kind.accent())
                if (kind == HandKind.DISCARD) {
                    Row(
                        Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            kind.icon(),
                            contentDescription = stringResource(Res.string.hand_discard),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        SmallPlayerNumber(loserSlot, MaterialTheme.colorScheme.error)
                        // A little smaller than the winner above (Ernesto, 2026-11-25).
                        Text(
                            view.nameOf(loserSlot),
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyMedium.inTextFont(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // The chicken marks the winning hand, so it goes on the left of its score. The best hands of the tournament
        // belong to the ranking, not to the hands of a table (Ernesto, 2026-11-25).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // It says what it is on a touch, a long touch or a hover (Ernesto, 2026-11-25).
            if (hand.isChickenHand) ChickenHandMark()
            // The scores of all the hands form a column, each one centered in it (Ernesto, 2026-11-25).
            Box(Modifier.widthIn(min = ScoreColumnWidth), contentAlignment = Alignment.Center) {
                // A draw has no points: a dash in their place (Ernesto, 2026-11-25).
                if (kind != HandKind.DRAW) {
                    ScoreValue(hand.handScore, MaterialTheme.colorScheme.onSurface, withPoints = true)
                } else {
                    ScoreValue("-", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The width of the column of the scores in the list of hands: a score of three digits in its pill. */
private val ScoreColumnWidth = 80.dp

/** The number of a hand, with the normal font and a "#" in front. */
@Composable
private fun HandNumber(handId: Int) {
    Text(
        "#$handId",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )
}

/** The number of a player as "#NN", small and in the color of the name after it. */
@Composable
private fun SmallPlayerNumber(slot: Int?, color: Color) {
    if (slot == null) return
    Text(playerNumber(slot), style = MaterialTheme.typography.labelMedium.inTextFont(), color = color.copy(alpha = 0.8f))
}

/** A score without outline. The score of a hand and the value of a penalty use it, so they line up. */
@Composable
private fun ScoreValue(text: String, color: Color, small: Boolean = false, withPoints: Boolean = false) {
    // No outline, and the score of a hand says "pts" below, as in the review of a hand (Ernesto, 2026-11-25).
    Column(
        Modifier.padding(vertical = if (small) 2.dp else MtsTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = (if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).inTextFont().tight(),
            fontWeight = FontWeight.Bold,
            color = color,
        )
        if (withPoints) {
            Text(
                stringResource(Res.string.entry_points_abbr),
                style = MaterialTheme.typography.labelSmall.inTextFont().tight(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A style whose line is as tall as its letters, without the room above and below. */
private fun TextStyle.tight(): TextStyle = copy(
    lineHeight = fontSize,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/**
 * The way the hand was won: its icon and its name in its color, with no background (Ernesto, 2026-11-25). Only this
 * has color, so the type reads at once without coloring the whole item.
 */
@Composable
private fun WinTypePill(kind: HandKind, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // A self-draw shows the explosion, the same as in the dialogs of a hand (Ernesto, 2026-11-25).
        if (kind == HandKind.SELF_PICK) SelfDrawIcon(18.dp) else Icon(kind.icon(), contentDescription = null, Modifier.size(18.dp), tint = accent)
        Text(
            when (kind) {
                HandKind.SELF_PICK -> stringResource(Res.string.hand_self_pick)
                HandKind.DISCARD -> stringResource(Res.string.hand_discard)
                HandKind.DRAW -> stringResource(Res.string.hand_draw)
            },
            // The text font, as the player who discarded: the decorative font looked too heavy here (Ernesto, 2026-11-25).
            style = MaterialTheme.typography.bodyMedium.inTextFont(),
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
    }
}

/** Green for a self-pick, the error red for a discard and grey for a draw. */
@Composable
private fun HandKind.accent(): Color = when (this) {
    HandKind.SELF_PICK -> MtsTheme.colors.selfPick
    HandKind.DISCARD -> MaterialTheme.colorScheme.error
    HandKind.DRAW -> MtsTheme.colors.draw
}

/** The icon of a discard or a draw. A self-draw uses [SelfDrawIcon]. */
private fun HandKind.icon(): ImageVector = when (this) {
    HandKind.SELF_PICK, HandKind.DISCARD -> Icons.Default.SwapHoriz
    HandKind.DRAW -> Icons.Default.HorizontalRule
}

/** Shows a polling message once. The dialog returns only after the message went away and came back. */
@Composable
internal fun MessageDialog(message: UiText?) {
    var seen by remember(message != null) { mutableStateOf(false) }
    if (message != null && !seen) AppErrorDialog(message, onDismiss = { seen = true })
}

/** The time left of the round, every second, from the clock that the Timer started. Nothing before the Play. */
@Composable
private fun RoundClockLine(tournament: Tournament, roundId: Int) {
    // Before the gong the place of the time left shows when the round starts.
    val clock = tournament.clockOf(roundId) ?: return RoundStartLine(tournament, roundId)
    val nowMs by produceState(ServerTime.nowMs(), clock) {
        while (true) {
            value = ServerTime.nowMs()
            delay(1_000)
        }
    }
    val remaining = clock.remainingMs(tournament.matchDurationMinutes, nowMs)
    val text = when {
        remaining <= 0 -> stringResource(Res.string.round_clock_over)
        clock.isPaused -> stringResource(Res.string.round_clock_paused, formatClock(remaining))
        // Only the time: its place at the top of the table says what it is.
        else -> formatClock(remaining)
    }
    // A screen reader still says what the time is.
    val description = if (remaining > 0 && !clock.isPaused) stringResource(Res.string.round_clock_remaining, formatClock(remaining)) else text
    // And it announces the time left by itself only every 15 minutes, at 5 minutes and at the end: every second would
    // tire the players.
    val milestone = roundClockMilestone(remaining, clock.isPaused)
    val announcement = when {
        milestone == null -> ""
        milestone <= 0 -> stringResource(Res.string.round_clock_over)
        else -> stringResource(Res.string.round_clock_remaining, formatClock(milestone * 60_000L))
    }
    val red = remaining <= 15 * 60_000L
    val color = if (red) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    // An hourglass before the time, in the same color. The announcer is a dot over it that takes no room.
    Box {
    Box(
        Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = announcement
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        Icon(Icons.Default.HourglassTop, contentDescription = null, Modifier.size(24.dp), tint = color)
        Text(
            text,
            Modifier.testTag("round-clock").semantics { contentDescription = description },
            style = MaterialTheme.typography.headlineSmall.inTextFont(),
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
    }
}

/**
 * The minutes that the screen reader announces for [remainingMs]: the quarter hour that has just started (45, 30, 15),
 * 5 in the last five minutes, 0 when the time is over, and null while the clock is paused.
 */
internal fun roundClockMilestone(remainingMs: Long, paused: Boolean): Int? {
    if (paused) return null
    if (remainingMs <= 0) return 0
    val minutes = ((remainingMs + 59_999) / 60_000).toInt()
    return if (minutes <= 5) 5 else (minutes + 14) / 15 * 15
}

/**
 * The planned start of a round that has not started: a clock icon and the time, with the date before it when the round
 * is not today. Nothing when the round has no planned time.
 */
@Composable
private fun RoundStartLine(tournament: Tournament, roundId: Int) {
    val schedule = tournament.roundSchedules.firstOrNull { it.roundId == roundId } ?: return
    val time = schedule.startTime?.ifBlank { null } ?: return
    val date = schedule.date?.ifBlank { null }
    val today = tournament.currentVenueDateTime().date.toString()
    // A sentence, not a bare clock: alone above the seats a time did not say what it was (Ernesto, 2026-11-26).
    val text = when (val day = date?.takeIf { it != today }) {
        null -> stringResource(Res.string.round_will_start_at, formatLocalizedTime(time))
        else -> stringResource(Res.string.round_will_start_on_at, formatLocalizedDate(day, withYear = false), formatLocalizedTime(time))
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.testTag("round-start"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
    ) {
        Icon(Icons.Default.Schedule, contentDescription = null, Modifier.size(24.dp), tint = color)
        Text(text, style = MaterialTheme.typography.titleMedium.inTextFont(), fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
    }
}

/** Milliseconds as "h:mm:ss", rounded up, so the line shows 0:00:00 only at the end. */
internal fun formatClock(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 999) / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
