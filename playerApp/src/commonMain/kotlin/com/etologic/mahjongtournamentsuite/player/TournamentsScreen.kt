package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryLink
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.CompactTab
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DraggableScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTabRow
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.hideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberHideOnScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentInt
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import com.etologic.mahjongtournamentsuite.presentation.telemetry.rememberOpenLink
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.action_open
import mahjongtournamentsuite.playerapp.generated.resources.age_hours
import mahjongtournamentsuite.playerapp.generated.resources.age_minutes
import mahjongtournamentsuite.playerapp.generated.resources.age_seconds
import mahjongtournamentsuite.playerapp.generated.resources.contact_send_email
import mahjongtournamentsuite.playerapp.generated.resources.data_stale
import mahjongtournamentsuite.playerapp.generated.resources.info_contact
import mahjongtournamentsuite.playerapp.generated.resources.info_open_directions
import mahjongtournamentsuite.playerapp.generated.resources.info_schedule
import mahjongtournamentsuite.playerapp.generated.resources.preg_closed
import mahjongtournamentsuite.playerapp.generated.resources.preg_open
import mahjongtournamentsuite.playerapp.generated.resources.preg_register
import mahjongtournamentsuite.playerapp.generated.resources.schedule_hide
import mahjongtournamentsuite.playerapp.generated.resources.scope_my_tournaments
import mahjongtournamentsuite.playerapp.generated.resources.stats_title
import mahjongtournamentsuite.playerapp.generated.resources.status_finished
import mahjongtournamentsuite.playerapp.generated.resources.status_in_progress
import mahjongtournamentsuite.playerapp.generated.resources.status_upcoming
import mahjongtournamentsuite.playerapp.generated.resources.tournament_logo_of
import mahjongtournamentsuite.playerapp.generated.resources.tournament_mark_played
import mahjongtournamentsuite.playerapp.generated.resources.tournament_mark_plays
import mahjongtournamentsuite.playerapp.generated.resources.tournament_mark_registered
import mahjongtournamentsuite.playerapp.generated.resources.tournament_mark_waiting
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_empty_message
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_no_match
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_tab_all
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_tab_live
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.TimeSource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton

@Composable
fun TournamentsScreen(navController: NavHostController) {
    val tournaments = koinInject<PlayerTournamentPresenter>()
    val statistics = koinInject<PlayerStatisticsPresenter>()
    val session = koinInject<PlayerSessionPresenter>()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Tournament>?>(null) }
    var message by remember { mutableStateOf<UiText?>(null) }
    // The place of the player in each tournament that started. It fills in while the list is on screen.
    var standings by remember { mutableStateOf<Map<String, TournamentStanding>>(emptyMap()) }
    // The chosen tab. The In play tab shows only while a tournament is in play. A new key, so an old saved index of
    // the two tabs before it does not open the wrong one.
    var tabId by rememberPersistentInt("tournaments-tab-v2", StartTab.ALL.ordinal)
    // The own tournaments in play are filtered by default in the In play tab. Null until the person changes it.
    var liveMineOnly by remember { mutableStateOf<Boolean?>(null) }
    // The statistics tab tells this screen when it loads, so the progress bar under the top bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }
    // Every public tournament shows. With an account, its own tournaments are marked and can be filtered.
    var signedIn by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf<Set<String>>(emptySet()) }
    // How the account takes part in each own tournament: it plays or played, or it is registered before the draw.
    var marks by remember { mutableStateOf<Map<String, MyTournamentMark>>(emptyMap()) }
    var myEmaId by remember { mutableStateOf<String?>(null) }
    var filterText by rememberPersistentText("tournaments-filter", null)
    val filter = TournamentFilter.decode(filterText)

    LaunchedEffect(Unit) {
        signedIn = session.hasSession()
        if (signedIn) {
            marks = tournaments.myTournamentMarks()
            mine = marks.keys
            myEmaId = session.myEmaId()
        }
    }
    // The places change during play, also when the list does not. A check of an unchanged list asks for them again
    // only when the revision of the hands, players or teams of an own tournament being played changed.
    var standingsCheck by remember { mutableIntStateOf(0) }
    var standingsRevision by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        // The first load reads everything. The later checks ask for changes only. [PollSchedule.listDelay] sets how often.
        // A hidden tab or an app in the background does not check. The return checks at once.
        pollForChanges(
            isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
            forceFirst = true,
            load = { mode -> tournaments.listTournaments(mode) },
            nextDelay = { PollSchedule.listDelay(items, currentLocalDateTime()) },
        ) { result ->
            when (result) {
                is AppResult.Success -> {
                    val unchanged = result.value == items
                    items = result.value
                    message = null
                    val playing = result.value.filter { it.id in mine && statusOf(it) == TournamentStatus.IN_PROGRESS }.map { it.id }
                    if (myEmaId != null && playing.isNotEmpty()) {
                        val revision = tournaments.standingsRevision(playing)
                        // A changed list asks for the places anyway. A failed check (null) asks too, so no change is missed.
                        if (unchanged && (revision == null || revision != standingsRevision)) standingsCheck++
                        standingsRevision = revision
                    }
                }
                // A failed check does not show a message when the list is already on screen. The next check tries again.
                is AppResult.Failure -> if (items == null) message = result.error.toUiText()
            }
        }
    }
    LaunchedEffect(items, mine, myEmaId, standingsCheck) {
        // The server keeps the places of the player. One call gets them for all own tournaments that started.
        val emaId = myEmaId ?: return@LaunchedEffect
        val started = items.orEmpty().filter { it.id in mine && statusOf(it).let { status -> status != null && status != TournamentStatus.UPCOMING } }
        if (started.isEmpty()) return@LaunchedEffect
        val places = statistics.standings(emaId, started.map { it.id })
        standings = started.mapNotNull { tournament ->
            val place = places[tournament.id] ?: return@mapNotNull null
            tournament.id to tournamentStandingOf(
                place,
                isTeams = tournament.isTeams,
                finished = statusOf(tournament) == TournamentStatus.FINISHED,
                countsChickenHands = tournament.countChickenHands,
                countsBestHands = tournament.countBestHands,
            )
        }.toMap()
    }

    val anyLive = items.orEmpty().any { statusOf(it) == TournamentStatus.IN_PROGRESS }
    val ownLive = items.orEmpty().any { it.id in mine && statusOf(it) == TournamentStatus.IN_PROGRESS }
    val tabs = listOfNotNull(StartTab.LIVE.takeIf { anyLive }, StartTab.ALL, StartTab.STATS)
    val tab = StartTab.entries.getOrNull(tabId)?.takeIf { it in tabs } ?: StartTab.ALL
    // The app opens on the In play tab when the account plays a tournament now, once each time the app starts, so
    // coming back from a tournament keeps the tab that the person chose.
    LaunchedEffect(ownLive) {
        if (ownLive && !StartTab.liveOpened) {
            StartTab.liveOpened = true
            tabId = StartTab.LIVE.ordinal
        }
    }

    // The tab rows hide when the content scrolls down and show when it scrolls up.
    val hideOnScroll = rememberHideOnScrollState()
    val account = rememberAccountActions(navController)
    account.Dialogs()
    val drawExplanation = rememberDrawExplanationState()
    drawExplanation.Dialog()
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = stringResource(Res.string.tournaments_title),
        actions = { InfoTopBarButtons(navController, drawExplanation) },
        isLoading = (items == null && message == null) || (tab == StartTab.STATS && statisticsLoading),
        autoFocusFirst = false,
    ) {
        // The tab row touches the top bar, so the screen has no padding. The content under the tabs has its own.
        // The screen uses the full width. The tab row and the tournament list keep a narrow column. The statistics use all the width.
        CompositionLocalProvider(LocalHideOnScroll provides hideOnScroll) {
            ScreenColumn(maxWidth = Dp.Infinity, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                // The tabs use the color of the top bar.
                HideOnScrollTabs(tab) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MtsTabRow(
                            selectedTabIndex = tabs.indexOf(tab),
                            modifier = Modifier.widthIn(max = 720.dp),
                        ) {
                            tabs.forEach { option ->
                                CompactTab(
                                    selected = tab == option,
                                    onClick = { tabId = option.ordinal },
                                    title = stringResource(option.title),
                                    trackingName = option.trackingName,
                                    icon = {
                                        Icon(
                                            when (option) {
                                                StartTab.LIVE -> Icons.Default.PlayCircle
                                                StartTab.ALL -> Icons.Default.EmojiEvents
                                                StartTab.STATS -> StatisticsIcon
                                            },
                                            contentDescription = null,
                                            Modifier.size(20.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).align(Alignment.CenterHorizontally).hideOnScroll(hideOnScroll)
                        .then(if (tab == StartTab.STATS) Modifier.fillMaxWidth() else Modifier.widthIn(max = 720.dp).fillMaxWidth()),
                ) {
                    if (tab == StartTab.STATS) {
                        MyStatisticsTab { statisticsLoading = it }
                        return@Column
                    }
                    message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
                    val list = items
                    when {
                        list == null -> Unit
                        list.isEmpty() -> Box(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
                            EmptyState(Icons.Default.EmojiEvents, stringResource(Res.string.tournaments_empty_title), stringResource(Res.string.tournaments_empty_message))
                        }
                        // The padding is inside the list, so the list ends at the tabs and at the bottom of the screen.
                        else -> {
                            val liveOnly = tab == StartTab.LIVE
                            val mineOnlyLive = liveMineOnly ?: ownLive
                            val shown = if (liveOnly) {
                                // The In play tab: the tournaments in play, only the own ones while its chip is on.
                                list.filter { statusOf(it) == TournamentStatus.IN_PROGRESS && (!mineOnlyLive || it.id in mine) }.sortedForList(mine, ::statusOf)
                            } else {
                                list.filter { filter.accepts(it, statusOf(it), mine) }.sortedForList(mine, ::statusOf)
                            }
                            // Each tab keeps its own scroll.
                            val listState = rememberPersistentLazyListState(if (liveOnly) "tournaments-live" else "tournaments")
                            Box(Modifier.weight(1f)) {
                                LazyColumn(
                                    state = listState,
                                    // The draggable scrollbar below is the scrollbar of this list.
                                    modifier = Modifier.fillMaxSize().scrollIndicators(listState, scrollbar = MtsScrollbar.None),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                                ) {
                                    item(key = "filters") {
                                        if (liveOnly) {
                                            // Only the chip of the own tournaments. Without an account there is nothing to filter.
                                            if (signedIn) {
                                                Chip(stringResource(Res.string.scope_my_tournaments), mineOnlyLive) { liveMineOnly = !mineOnlyLive }
                                            }
                                        } else {
                                            TournamentFilterBar(
                                                filter = filter,
                                                onChange = { filterText = it.encode() },
                                                tournaments = list,
                                                signedIn = signedIn,
                                            )
                                        }
                                    }
                                    if (shown.isEmpty()) {
                                        item(key = "no-match") {
                                            EmptyState(Icons.Default.EmojiEvents, stringResource(Res.string.tournaments_no_match), "")
                                        }
                                    }
                                    items(shown, key = { it.id }) { tournament ->
                                        TournamentCard(tournament, standings[tournament.id], mark = marks[tournament.id]) {
                                            navController.navigate(TournamentRoute(tournament.id))
                                        }
                                    }
                                }
                                DraggableScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, message: String) {
    AppEmptyState(icon, title, message, compact = true)
}

/** The tabs of the start screen. [LIVE] shows only while a tournament is in play. */
internal enum class StartTab(val title: StringResource, val trackingName: String) {
    LIVE(Res.string.tournaments_tab_live, "In play"),
    ALL(Res.string.tournaments_tab_all, "Tournaments"),
    STATS(Res.string.stats_title, "Statistics"),
    ;

    companion object {
        /** True once the app opened the In play tab by itself, so it does it once each time the app starts. */
        var liveOpened = false
    }
}

internal enum class TournamentStatus(val label: StringResource) {
    UPCOMING(Res.string.status_upcoming),
    IN_PROGRESS(Res.string.status_in_progress),
    FINISHED(Res.string.status_finished),
}

/** The status comes from the dates. It is null when the tournament has no dates. */
internal fun statusOf(tournament: Tournament): TournamentStatus? {
    if (tournament.isCompleted) return TournamentStatus.FINISHED
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val start = tournament.eventStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = tournament.eventEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
    return when {
        start == null -> null
        today < start -> TournamentStatus.UPCOMING
        end != null && today > end -> TournamentStatus.FINISHED
        else -> TournamentStatus.IN_PROGRESS
    }
}

/** The blue of the upcoming tournaments. The design system makes it lighter in the dark theme. */
@Composable
private fun upcomingBlue(): Color = MtsTheme.colors.info

/** The color of the text on the blue. */
@Composable
private fun onUpcomingBlue(): Color = MtsTheme.colors.onInfo

/** The color of a tournament status, in the chips of the cards and in the status filter. */
@Composable
internal fun statusColor(status: TournamentStatus): Color = when (status) {
    TournamentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
    TournamentStatus.UPCOMING -> upcomingBlue()
    TournamentStatus.FINISHED -> MaterialTheme.colorScheme.secondary
}

/** The space between the three lines of the header of a tournament card. */
private val CardHeaderSpacing = 4.dp

/** The line height of the status and the name of a tournament card. */
private val CardHeadlineLine = 24.dp

/**
 * The size of the logo of a tournament card: the height of its three lines (status, name and dates), so the logo is
 * as tall as the text beside it.
 */
private val CardLogoSize = CardHeadlineLine * 2 + 20.dp + CardHeaderSpacing * 2

/**
 * The text style of the status and the name of a tournament card: the same size for both, 20 sp on a 24 dp line.
 * The line is trimmed, so the status is as tall as the old bordered tag.
 */
@Composable
private fun cardHeadlineStyle() = MaterialTheme.typography.titleMedium.copy(
    fontSize = 20.sp,
    lineHeight = 24.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** The status of a tournament: a text in the color of the status, with no border, as big as the name. */
@Composable
private fun StatusChip(status: TournamentStatus) {
    Text(
        stringResource(status.label),
        style = cardHeadlineStyle(),
        color = statusColor(status),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** The text of the mark of an own tournament: it plays or played, or it has a place or waits before the draw. */
private fun MyTournamentMark.label(status: TournamentStatus?): StringResource = when (this) {
    MyTournamentMark.PLAYS -> if (status == TournamentStatus.FINISHED) Res.string.tournament_mark_played else Res.string.tournament_mark_plays
    MyTournamentMark.REGISTERED -> Res.string.tournament_mark_registered
    MyTournamentMark.WAITING -> Res.string.tournament_mark_waiting
}

/** Where the stamp of an own tournament sits on a card without the line of the place: in the right half. */
private val StampAlignment = BiasAlignment(horizontalBias = 0.85f, verticalBias = 0.1f)

/**
 * The mark of an own tournament, drawn as an ink stamp: tilted, a double frame, capital letters and a bit of
 * transparency so the card shows through like ink on paper. Every tournament where the account plays, played or is
 * registered has it.
 */
@Composable
private fun InkStamp(text: String, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .testTag("tournament-mark")
            .graphicsLayer {
                rotationZ = -12f
                alpha = 0.7f
            }
            .border(3.dp, ink, MaterialTheme.shapes.small)
            .padding(3.dp)
            .border(1.dp, ink, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text.uppercase(),
            color = ink,
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

/** The logo of the tournament. A neutral box with a trophy shows while the tournament has no logo. */
@Composable
private fun TournamentLogo(tournament: Tournament) {
    val shape = MaterialTheme.shapes.medium
    val modifier = Modifier.size(CardLogoSize).clip(shape)
    if (tournament.associationLogoUrl.isNullOrBlank()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = tournament.associationLogoUrl,
            contentDescription = stringResource(Res.string.tournament_logo_of, tournament.name),
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

/**
 * A tournament of the list. The text is information only. The links are buttons under a divider, so they are not
 * confused with the card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TournamentCard(
    tournament: Tournament,
    standing: TournamentStanding?,
    mark: MyTournamentMark? = null,
    onClick: () -> Unit,
) {
    var expanded by remember(tournament.id) { mutableStateOf(false) }
    val status = statusOf(tournament)
    // A tournament in progress and an upcoming tournament have an accent color. A finished tournament has none.
    val accent = when (status) {
        TournamentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        TournamentStatus.UPCOMING -> upcomingBlue()
        else -> null
    }
    val openDirections = rememberOpenLink(TelemetryLink.VENUE_DIRECTIONS)
    val openEmail = rememberOpenLink(TelemetryLink.ORGANIZER_EMAIL)
    val mapLink = venueMapLink(tournament)
    val email = tournament.venue.contactEmail
    val dates = datesOf(tournament)
    val hasCity = tournament.hostCity.isNotBlank() || tournament.hostCountry.isNotBlank()
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        // No border (Ernesto, 20-nov): the status and the Open button keep the color of the status.
    ) {
        Box {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TournamentLogo(tournament)
                    // Three lines: the status, the name and the dates (Ernesto, 17-nov). The status and the name have the same size.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CardHeaderSpacing)) {
                        if (status != null) {
                            if (status != null) StatusChip(status)
                        }
                        Text(
                            tournament.name,
                            style = cardHeadlineStyle(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (dates != null) {
                            Text(dates, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // The places of the player are at the top right, one line for each.
                    if (standing != null) StandingBadges(standing)
                }
                // A tournament before its draw shows its registrations. The button opens the tournament on its Players tab.
                tournament.registration?.takeIf { tournament.inRegistration }?.let { registration ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HowToReg, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (registration.isOpen) Res.string.preg_open else Res.string.preg_closed),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                registrationPlacesText(registration.confirmed, registration.maxPlayers, registration.waiting),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        // Already registered: no button. The stamp of the card says it (Ernesto, 21-nov).
                        if (registration.isOpen && mark != MyTournamentMark.REGISTERED && mark != MyTournamentMark.WAITING) {
                            Button(onClick = onClick, modifier = Modifier.testTag("tournament-register")) { Text(stringResource(Res.string.preg_register)) }
                        }
                    }
                }
                // Only the city and the flag of the country. The exact address is behind Directions and in the Info tab.
                if (hasCity || mapLink != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tournament.hostCity.isNotBlank()) {
                                Text(
                                    tournament.hostCity,
                                    Modifier.weight(1f, fill = false),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (tournament.hostCountry.isNotBlank()) {
                                CountryFlag(code = tournament.hostCountry, contentDescription = tournament.hostCountry)
                            }
                            // Directions is the usual map icon, right after the place (Ernesto, 21-nov): an outlined
                            // round button with only the outlined icon, in the color of the text (white in the dark
                            // theme, dark in the light one, where white would not show), as the other link buttons.
                            if (mapLink != null) {
                                OutlinedButton(
                                    onClick = { openDirections(mapLink) },
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                ) {
                                    Icon(
                                        Icons.Outlined.Directions,
                                        contentDescription = stringResource(Res.string.info_open_directions),
                                        Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        // The stamp of an own tournament is at the end of this line, under the dates (Ernesto, 21-nov).
                        // It may draw past the line: a stamp overlaps a little, like ink on paper.
                        if (mark != null) {
                            // The height of the line, so the stamp does not make the line taller.
                            Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterEnd) {
                                InkStamp(stringResource(mark.label(status)), Modifier.wrapContentSize(unbounded = true))
                            }
                        }
                    }
                }
                // The link buttons are at the left. The Open button is at the bottom right, in line with them.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    FlowRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (email.isNotBlank()) {
                            LinkButton(Icons.Default.Email, stringResource(Res.string.info_contact), stringResource(Res.string.contact_send_email)) { openEmail("mailto:$email") }
                        }
                        if (hasSchedule(tournament)) {
                            LinkButton(Icons.Default.Schedule, stringResource(if (expanded) Res.string.schedule_hide else Res.string.info_schedule)) { expanded = !expanded }
                        }
                    }
                    Button(
                        onClick = onClick,
                        // The button has the accent color of the card.
                        colors = if (accent != null) {
                            ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = if (status == TournamentStatus.IN_PROGRESS) MaterialTheme.colorScheme.onTertiary else onUpcomingBlue(),
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(Res.string.action_open), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (expanded) TournamentSchedule(tournament)
            }
            // A card without the line of the place has its stamp over the right half.
            if (mark != null && !hasCity && mapLink == null) {
                InkStamp(stringResource(mark.label(status)), Modifier.align(StampAlignment))
            }
        }
    }
}

/** A link of a tournament card. It is an outlined button, so it does not look like the text of the card. */
@Composable
private fun LinkButton(icon: ImageVector, label: String, description: String? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Loads the tournament and checks for changes while the screen is open. [PollSchedule] sets how often: every
 * 5 minutes for a finished tournament, every minute before it starts, every 15 seconds while it runs, and every
 * 5 seconds while a round is being played on the table of that round ([tableRoundId]) and, for a player of the
 * tournament, on all its screens. A hidden tab or an app in the background does not check.
 * Coming back always checks at once.
 * A failed check does not show a message when the tournament is already on screen.
 * After [STALE_AFTER_FAILURES] failed checks in a row, the message tells how long the data is old.
 */
@Composable
internal fun rememberTournamentView(
    tournamentId: String,
    reloadKey: Int = 0,
    tableRoundId: Int? = null,
    tableId: Int? = null,
): Pair<PlayerTournamentView?, UiText?> {
    val presenter = koinInject<PlayerTournamentPresenter>()
    var view by remember(tournamentId) { mutableStateOf<PlayerTournamentView?>(null) }
    var message by remember(tournamentId) { mutableStateOf<UiText?>(null) }
    var failures by remember(tournamentId) { mutableIntStateOf(0) }
    var lastUpdate by remember(tournamentId) { mutableStateOf(TimeSource.Monotonic.markNow()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tournamentId, reloadKey, tableRoundId, tableId) {
        // Only the first load after a manual reload reads everything again. The later checks ask for changes only.
        // A hidden tab or an app in the background does not check the server. The checks go on when it returns.
        pollForChanges(
            isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
            forceFirst = reloadKey > 0,
            load = { mode -> presenter.load(tournamentId, mode) },
            nextDelay = {
                val tournament = view?.tournament
                PollSchedule.nextDelay(
                    tournament,
                    tableRoundId,
                    tournament?.currentVenueDateTime() ?: currentLocalDateTime(),
                    playsHere = view?.mySlotId != null,
                    ownTable = view?.let { current ->
                        val mine = current.mySlotId ?: return@let false
                        current.tables.any { it.roundId == tableRoundId && it.tableId == tableId && mine in it.playerSlots() }
                    } ?: false,
                    tableCompleted = view?.tables?.any { it.roundId == tableRoundId && it.tableId == tableId && it.table.isCompleted } ?: false,
                )
            },
        ) { result ->
            when (result) {
                is AppResult.Success -> {
                    view = result.value
                    message = null
                    failures = 0
                    lastUpdate = TimeSource.Monotonic.markNow()
                }
                is AppResult.Failure -> {
                    failures++
                    if (view == null) {
                        message = result.error.toUiText()
                    } else if (failures >= STALE_AFTER_FAILURES) {
                        message = UiText.of(Res.string.data_stale, lastUpdate.elapsedNow().toAgeText())
                    }
                }
            }
        }
    }
    return view to message
}

/** The number of failed checks in a row after which the screen tells that the data is old. */
private const val STALE_AFTER_FAILURES = 3

/** The age as plain text, for example "45 seconds", "1 minute" or "2 hours". */
private fun Duration.toAgeText(): UiText {
    val seconds = inWholeSeconds
    return when {
        seconds < 60 -> UiText.Plural(Res.plurals.age_seconds, seconds.toInt(), listOf(seconds))
        seconds < 3600 -> (seconds / 60).let { UiText.Plural(Res.plurals.age_minutes, it.toInt(), listOf(it)) }
        else -> (seconds / 3600).let { UiText.Plural(Res.plurals.age_hours, it.toInt(), listOf(it)) }
    }
}
