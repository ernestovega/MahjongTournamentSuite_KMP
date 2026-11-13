package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.age_minutes
import mahjongtournamentsuite.playerapp.generated.resources.my_table_awaiting_gong
import mahjongtournamentsuite.playerapp.generated.resources.my_table_dismiss
import mahjongtournamentsuite.playerapp.generated.resources.my_table_go
import mahjongtournamentsuite.playerapp.generated.resources.my_table_started
import mahjongtournamentsuite.playerapp.generated.resources.my_table_starts_in
import mahjongtournamentsuite.playerapp.generated.resources.my_table_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** Where a round of the player is in time, for the reminder of the own table. */
sealed interface RoundMoment {
    /** The round starts in [ms] milliseconds, by its planned time. */
    data class StartsIn(val ms: Long) : RoundMoment

    /** The planned time passed, but nobody pressed Play yet. */
    data object AwaitingGong : RoundMoment

    /** The Play of the Timer was [ms] milliseconds of play ago. Pauses do not count. */
    data class StartedAgo(val ms: Long) : RoundMoment
}

/**
 * What the reminder of the own table says about round [roundId] at [now], a local time of the venue. Null when it is
 * not the time of the round: from [SEATING_LEAD_MINUTES] before the planned start (the seats open then) until the end
 * of the match. A round with no planned time shows from the Play on. A late gong keeps the reminder for one match
 * duration after the planned time, so an old round without a clock never shows.
 */
fun Tournament.reminderMoment(roundId: Int, now: LocalDateTime): RoundMoment? {
    val nowMs = epochMsAt(now)
    return when (matchPhase(roundId, now)) {
        MatchPhase.PLAYING, MatchPhase.EXTRA_TIME -> clockOf(roundId)?.let { RoundMoment.StartedAgo(it.elapsedMs(nowMs)) }
        MatchPhase.OVER -> null
        MatchPhase.NOT_STARTED -> {
            val planned = roundStartTime(roundId) ?: return null
            val untilStart = epochMsAt(planned) - nowMs
            when {
                untilStart > SEATING_LEAD_MINUTES * 60_000L -> null
                untilStart > 0 -> RoundMoment.StartsIn(untilStart)
                -untilStart < matchDurationMinutes * 60_000L -> RoundMoment.AwaitingGong
                else -> null
            }
        }
    }
}

/** The round of [tournament] to remind: a round in play first, then one about to start. */
fun Tournament.reminderRound(now: LocalDateTime): Int? {
    val rounds = (1..numRounds)
    return rounds.lastOrNull { reminderMoment(it, now) is RoundMoment.StartedAgo }
        ?: rounds.firstOrNull { reminderMoment(it, now) != null }
}

/** The table of the signed-in player in a round that starts soon or is in play. */
data class MyActiveTable(
    /** The tournament with fresh round clocks, to count the time on screen. */
    val tournament: Tournament,
    val roundId: Int,
    val tableId: Int,
) {
    /** One reminder per round: closing it or opening the table hides it until the next round. */
    val key: String get() = "${tournament.id}/$roundId"

    val route: TableRoute get() = TableRoute(tournament.id, roundId, tableId)
}

class MyTableReminderPresenter(private val tournamentRepository: TournamentRepository) {
    /** The table of the signed-in player that the reminder points to, or null. Null without an account. */
    suspend fun find(): MyActiveTable? {
        val slots = (tournamentRepository.listMyTournamentSlots() as? AppResult.Success)?.value.orEmpty()
        if (slots.isEmpty()) return null
        val tournaments = (tournamentRepository.listTournaments() as? AppResult.Success)?.value.orEmpty().associateBy { it.id }
        for (slot in slots) {
            val listed = tournaments[slot.tournamentId]?.takeUnless { it.isCompleted } ?: continue
            // The listed tournament can be a minute old. The clocks come fresh, as on the tournament screen.
            val clocks = (tournamentRepository.getRoundClocks(listed.id) as? AppResult.Success)?.value?.clocks ?: listed.roundClocks
            val tournament = listed.copy(roundClocks = clocks)
            val roundId = tournament.reminderRound(tournament.currentVenueDateTime()) ?: continue
            val tables = (tournamentRepository.listTournamentTables(tournament.id, roundId) as? AppResult.Success)?.value ?: continue
            val table = tables.firstOrNull { it.roundId == roundId && slot.playerId in it.playerIds && !it.isCompleted } ?: continue
            return MyActiveTable(tournament, roundId, table.tableId)
        }
        return null
    }
}

/** The rounds whose reminder the player closed or followed. The web keeps them through a page refresh. */
internal class HandledReminders(private val storage: SessionStateStorage?) {
    private val keys: MutableSet<String> = if (storage == null) {
        memory
    } else {
        storage.read(STORAGE_NAME)?.lines()?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
    }

    operator fun contains(key: String): Boolean = key in keys

    fun add(key: String) {
        if (keys.add(key)) storage?.write(STORAGE_NAME, keys.joinToString("\n"))
    }

    private companion object {
        const val STORAGE_NAME = "handledTableReminders"

        /** Without a storage the keys live while the app process lives, so they survive the screens. */
        val memory = mutableSetOf<String>()
    }
}

/** Minimum time between two checks of the server while the app shows a reminder that waits for the gong. */
private const val RECHECK_MS = 60_000L

/** How often the time on the reminder updates. */
private const val TICK_MS = 15_000L

/**
 * A reminder at the bottom of every screen: when the app opens or returns to the front and the signed-in player has a
 * round in play or about to start, it says how long until the start, or since the gong, and opens the table.
 */
@Composable
internal fun BoxScope.MyTableReminder(navController: NavHostController, sessionStorage: SessionStateStorage?) {
    val presenter = koinInject<MyTableReminderPresenter>()
    val handled = remember(sessionStorage) { HandledReminders(sessionStorage) }
    var active by remember { mutableStateOf<MyActiveTable?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }

    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val outsideTheApp = destination == null ||
        destination.hasRoute<SplashRoute>() ||
        destination.hasRoute<SignInRoute>() ||
        destination.hasRoute<SupportRoute>()
    // A sign-in or a sign-out opens the screens again from a new bottom entry, so the check runs again for the account.
    val rootId = navController.currentBackStack.value.firstOrNull { it.destination !is NavGraph }?.id
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(resumes, rootId, outsideTheApp) {
        if (outsideTheApp) return@LaunchedEffect
        while (true) {
            active = presenter.find()?.takeUnless { it.key in handled }
            // Before the gong the reminder checks again, so it turns into "started" without leaving the app.
            val current = active ?: break
            val moment = current.tournament.reminderMoment(current.roundId, current.tournament.currentVenueDateTime())
            if (moment !is RoundMoment.StartsIn && moment != RoundMoment.AwaitingGong) break
            delay(RECHECK_MS)
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        }
    }

    val shown = active ?: return
    // The player is at the table already: the reminder did its job.
    val onTheTable = destination?.hasRoute<TableRoute>() == true && entry?.toRoute<TableRoute>() == shown.route
    LaunchedEffect(onTheTable, shown.key) {
        if (onTheTable) {
            handled.add(shown.key)
            active = null
        }
    }
    if (onTheTable || outsideTheApp) return

    var now by remember { mutableStateOf(shown.tournament.currentVenueDateTime()) }
    LaunchedEffect(shown) {
        while (true) {
            now = shown.tournament.currentVenueDateTime()
            delay(TICK_MS)
        }
    }
    val moment = shown.tournament.reminderMoment(shown.roundId, now)
    if (moment == null) {
        // The match is over.
        LaunchedEffect(shown.key) { active = null }
        return
    }

    MyTableReminderCard(
        tournamentName = shown.tournament.shortName.ifBlank { shown.tournament.name },
        roundId = shown.roundId,
        tableId = shown.tableId,
        moment = moment,
        onOpen = {
            handled.add(shown.key)
            active = null
            navController.navigate(shown.route)
        },
        onDismiss = {
            handled.add(shown.key)
            active = null
        },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

/** When the round starts, or since when it plays, for example "Started 12 minutes ago". */
@Composable
internal fun myTableReminderTime(moment: RoundMoment): String = when (moment) {
    is RoundMoment.StartsIn -> {
        // Rounded up: "in 1 minute" until the planned time.
        val minutes = ((moment.ms + 59_999) / 60_000).toInt().coerceAtLeast(1)
        stringResource(Res.string.my_table_starts_in, pluralStringResource(Res.plurals.age_minutes, minutes, minutes))
    }
    RoundMoment.AwaitingGong -> stringResource(Res.string.my_table_awaiting_gong)
    is RoundMoment.StartedAgo -> {
        val minutes = (moment.ms / 60_000).toInt().coerceAtLeast(1)
        stringResource(Res.string.my_table_started, pluralStringResource(Res.plurals.age_minutes, minutes, minutes))
    }
}

@Composable
internal fun MyTableReminderCard(
    tournamentName: String,
    roundId: Int,
    tableId: Int,
    moment: RoundMoment,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    // The text on the green of the brand is white, like the top bar.
    val content = MtsTheme.colors.onHeader
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(MtsTheme.spacing.md),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = container,
            contentColor = content,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = MtsTheme.contentWidth.form).fillMaxWidth().testTag(MY_TABLE_REMINDER_TAG),
        ) {
            Row(
                Modifier.padding(start = MtsTheme.spacing.lg, top = MtsTheme.spacing.xs, bottom = MtsTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.TableRestaurant, contentDescription = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = MtsTheme.spacing.md, vertical = MtsTheme.spacing.sm)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(stringResource(Res.string.my_table_title, tableId, roundId), style = MaterialTheme.typography.titleSmall)
                    Text("$tournamentName · ${myTableReminderTime(moment)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onOpen, colors = ButtonDefaults.textButtonColors(contentColor = content)) {
                    Text(stringResource(Res.string.my_table_go))
                }
                IconButton(onClick = onDismiss, colors = IconButtonDefaults.iconButtonColors(contentColor = content)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.my_table_dismiss))
                }
            }
        }
    }
}

internal const val MY_TABLE_REMINDER_TAG = "myTableReminder"
