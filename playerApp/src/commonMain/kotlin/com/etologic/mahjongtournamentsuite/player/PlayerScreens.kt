package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.domain.model.seatScores
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.HandLabel
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.LastPlaceIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.PositionTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.RankTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SeatDisplay
import com.etologic.mahjongtournamentsuite.presentation.components.SeatLevelMedal
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SignInForm
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.TableSeatsView
import com.etologic.mahjongtournamentsuite.presentation.components.hideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.positionColor
import com.etologic.mahjongtournamentsuite.presentation.components.rememberHideOnScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentInt
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.platform.loadBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBrowserCredentials
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDateRange
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import mahjongtournamentsuite.playerapp.generated.resources.details_no_hands_message
import mahjongtournamentsuite.playerapp.generated.resources.details_no_hands_title
import mahjongtournamentsuite.playerapp.generated.resources.field_search
import mahjongtournamentsuite.playerapp.generated.resources.hand_discard
import mahjongtournamentsuite.playerapp.generated.resources.hand_draw
import mahjongtournamentsuite.playerapp.generated.resources.hand_penalty
import mahjongtournamentsuite.playerapp.generated.resources.hand_self_pick
import mahjongtournamentsuite.playerapp.generated.resources.info_contact
import mahjongtournamentsuite.playerapp.generated.resources.info_directions
import mahjongtournamentsuite.playerapp.generated.resources.info_open_directions
import mahjongtournamentsuite.playerapp.generated.resources.info_schedule
import mahjongtournamentsuite.playerapp.generated.resources.jump_to_me
import mahjongtournamentsuite.playerapp.generated.resources.ranking_best_hands
import mahjongtournamentsuite.playerapp.generated.resources.ranking_best_short
import mahjongtournamentsuite.playerapp.generated.resources.ranking_chicken_hands
import mahjongtournamentsuite.playerapp.generated.resources.ranking_chicken_short
import mahjongtournamentsuite.playerapp.generated.resources.ranking_empty
import mahjongtournamentsuite.playerapp.generated.resources.ranking_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.ranking_points_score
import mahjongtournamentsuite.playerapp.generated.resources.ranking_teams
import mahjongtournamentsuite.playerapp.generated.resources.round_n
import mahjongtournamentsuite.playerapp.generated.resources.schedule_hide
import mahjongtournamentsuite.playerapp.generated.resources.scope_me
import mahjongtournamentsuite.playerapp.generated.resources.scope_my_tournaments
import mahjongtournamentsuite.playerapp.generated.resources.scope_player
import mahjongtournamentsuite.playerapp.generated.resources.scope_players
import mahjongtournamentsuite.playerapp.generated.resources.scope_tournament
import mahjongtournamentsuite.playerapp.generated.resources.sign_in_enter_email
import mahjongtournamentsuite.playerapp.generated.resources.sign_in_player_description
import mahjongtournamentsuite.playerapp.generated.resources.sign_in_reset_sent
import mahjongtournamentsuite.playerapp.generated.resources.standing_best_hand
import mahjongtournamentsuite.playerapp.generated.resources.standing_team
import mahjongtournamentsuite.playerapp.generated.resources.stat_hands
import mahjongtournamentsuite.playerapp.generated.resources.stats_round_table
import mahjongtournamentsuite.playerapp.generated.resources.stats_title
import mahjongtournamentsuite.playerapp.generated.resources.status_finished
import mahjongtournamentsuite.playerapp.generated.resources.status_in_progress
import mahjongtournamentsuite.playerapp.generated.resources.status_upcoming
import mahjongtournamentsuite.playerapp.generated.resources.tab_info
import mahjongtournamentsuite.playerapp.generated.resources.tab_ranking
import mahjongtournamentsuite.playerapp.generated.resources.tab_stats
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables_all
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables_me
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables_team
import mahjongtournamentsuite.playerapp.generated.resources.table_details
import mahjongtournamentsuite.playerapp.generated.resources.table_done
import mahjongtournamentsuite.playerapp.generated.resources.table_hand_progress
import mahjongtournamentsuite.playerapp.generated.resources.table_hands_done
import mahjongtournamentsuite.playerapp.generated.resources.table_n
import mahjongtournamentsuite.playerapp.generated.resources.tables_all_empty
import mahjongtournamentsuite.playerapp.generated.resources.tables_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.tables_mine_empty
import mahjongtournamentsuite.playerapp.generated.resources.tables_not_linked
import mahjongtournamentsuite.playerapp.generated.resources.tables_player_empty_message
import mahjongtournamentsuite.playerapp.generated.resources.tables_team_empty
import mahjongtournamentsuite.playerapp.generated.resources.team_hide_players
import mahjongtournamentsuite.playerapp.generated.resources.team_n
import mahjongtournamentsuite.playerapp.generated.resources.team_show_players
import mahjongtournamentsuite.playerapp.generated.resources.tournament_logo_of
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_empty_message
import mahjongtournamentsuite.playerapp.generated.resources.tournaments_empty_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.TimeSource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton

/** The interval of the checks for new results while a tournament screen is open. */
private const val REFRESH_INTERVAL_MS = 15_000L

@Composable
fun SplashScreen(
    navController: NavHostController,
    openTournamentId: String?,
    /** The screens that were open before a page refresh, from the first to the last. */
    restoredRoutes: List<Any> = emptyList(),
) {
    val presenter = koinInject<PlayerSessionPresenter>()
    LaunchedEffect(Unit) {
        val next: Any = when {
            presenter.hasSession() -> TournamentsRoute
            else -> SignInRoute(openTournamentId = openTournamentId)
        }
        navController.navigate(next) { popUpTo(SplashRoute) { inclusive = true } }
        if (next == TournamentsRoute) {
            val restored = restoredRoutes.filterNot { it == TournamentsRoute }
            if (restored.isNotEmpty()) {
                // A page refresh returns to the screen that was open.
                restored.forEach { navController.navigate(it) }
            } else if (openTournamentId != null) {
                // The link of a registration email goes on to the tournament. Back returns to the list.
                navController.navigate(TournamentRoute(openTournamentId))
            }
        }
    }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
}

@Composable
fun SignInScreen(navController: NavHostController, openTournamentId: String?) {
    val presenter = koinInject<PlayerSessionPresenter>()
    val scope = rememberCoroutineScope()
    val emailRequester = remember { FocusRequester() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<UiText?>(null) }
    var infoMessage by remember { mutableStateOf<UiText?>(null) }

    LaunchedEffect(Unit) {
        // Chrome mediates this request through its password manager UI.
        loadBrowserCredentials { savedEmail, savedPassword ->
            email = savedEmail
            password = savedPassword
        }
        emailRequester.requestFocus()
    }

    fun submit() {
        errorMessage = null
        infoMessage = null
        busy = true
        scope.launch {
            when (val result = presenter.signIn(email, password)) {
                is AppResult.Success -> {
                    saveBrowserCredentials(email.trim(), password)
                    navController.navigate(TournamentsRoute) { popUpTo(0) }
                    if (openTournamentId != null) {
                        navController.navigate(TournamentRoute(openTournamentId))
                    }
                }
                is AppResult.Failure -> errorMessage = result.error.toUiText()
            }
            busy = false
        }
    }

    fun requestPasswordReset() {
        errorMessage = null
        infoMessage = null
        if (email.trim().isEmpty()) {
            errorMessage = UiText.of(Res.string.sign_in_enter_email)
            emailRequester.requestFocus()
            return
        }
        busy = true
        scope.launch {
            when (val result = presenter.resetPassword(email)) {
                is AppResult.Success ->
                    infoMessage = UiText.of(Res.string.sign_in_reset_sent)
                is AppResult.Failure -> errorMessage = result.error.toUiText()
            }
            busy = false
        }
    }

    SignInForm(
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        isLoading = busy,
        errorMessage = errorMessage,
        infoMessage = infoMessage,
        emailRequester = emailRequester,
        onSubmit = ::submit,
        onForgotPassword = ::requestPasswordReset,
        onErrorDismiss = { errorMessage = null },
        description = stringResource(Res.string.sign_in_player_description),
    )
}

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
    var tab by rememberPersistentInt("tournaments-tab", 0)
    // The statistics tab tells this screen when it loads, so the progress bar under the top bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val result = tournaments.listTournaments(RefreshMode.FORCE)) {
            is AppResult.Success -> items = result.value
            is AppResult.Failure -> message = result.error.toUiText()
        }
    }
    LaunchedEffect(items) {
        // The server keeps the places of the player. One call gets them for all tournaments that started.
        val started = items.orEmpty().filter { statusOf(it).let { status -> status != null && status != TournamentStatus.UPCOMING } }
        if (started.isEmpty()) return@LaunchedEffect
        val places = statistics.standings("", started.map { it.id })
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

    // The tab rows hide when the content scrolls down and show when it scrolls up.
    val hideOnScroll = rememberHideOnScrollState()
    val account = rememberAccountActions(navController)
    account.Dialogs()
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = stringResource(Res.string.scope_my_tournaments),
        isLoading = (items == null && message == null) || (tab == 1 && statisticsLoading),
        autoFocusFirst = false,
    ) {
        // The tab row touches the top bar, so the screen has no padding. The content under the tabs has its own.
        // The screen uses the full width. The tab row and the tournament list keep a narrow column. The statistics use all the width.
        CompositionLocalProvider(LocalHideOnScroll provides hideOnScroll) {
            ScreenColumn(maxWidth = Dp.Infinity, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                // The tabs use the color of the top bar.
                HideOnScrollTabs(tab) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PrimaryTabRow(
                            selectedTabIndex = tab,
                            containerColor = tabRowColor(),
                            contentColor = TabContentColor,
                            indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), color = TabContentColor) },
                            modifier = Modifier.widthIn(max = 720.dp),
                        ) {
                            listOf(stringResource(Res.string.scope_my_tournaments), stringResource(Res.string.stats_title)).forEachIndexed { index, title ->
                                CompactTab(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    title = title,
                                    trackingName = if (index == 0) "MyTournaments" else "Statistics",
                                    icon = {
                                        Icon(
                                            if (index == 0) Icons.Default.EmojiEvents else StatisticsIcon,
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
                        .then(if (tab == 1) Modifier.fillMaxWidth() else Modifier.widthIn(max = 720.dp).fillMaxWidth()),
                ) {
                    if (tab == 1) {
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
                        else -> LazyColumn(
                            state = rememberPersistentLazyListState("tournaments"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                        ) {
                            // The tournaments in progress come first. The sort is stable, so the other tournaments keep their order.
                            items(list.sortedBy { if (statusOf(it) == TournamentStatus.IN_PROGRESS) 0 else 1 }, key = { it.id }) { tournament ->
                                TournamentCard(tournament, standings[tournament.id]) { navController.navigate(TournamentRoute(tournament.id)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, message: String) {
    AppEmptyState(icon, title, message, compact = true)
}

private enum class TournamentStatus(val label: StringResource) {
    UPCOMING(Res.string.status_upcoming),
    IN_PROGRESS(Res.string.status_in_progress),
    FINISHED(Res.string.status_finished),
}

/**
 * The place of the player in a tournament. The other ranks are null when the list does not show them.
 * [chickenRank] and [bestHandRank] are from 1 to 3.
 */
internal data class TournamentStanding(
    val position: Int,
    val teamRank: Int? = null,
    val chickenRank: Int? = null,
    val bestHandRank: Int? = null,
)

/**
 * What the list shows for [place]. The team place shows in a tournament with teams. The prizes of the hands show only
 * when the tournament is over, the tournament counts them and the player is in the first three.
 */
internal fun tournamentStandingOf(
    place: Standing,
    isTeams: Boolean,
    finished: Boolean,
    countsChickenHands: Boolean,
    countsBestHands: Boolean,
) = TournamentStanding(
    position = place.rank,
    teamRank = place.teamRank.takeIf { isTeams },
    chickenRank = place.chickenRank.takeIf { finished && countsChickenHands && it in 1..3 },
    bestHandRank = place.bestHandRank.takeIf { finished && countsBestHands && it in 1..3 },
)

/** The status comes from the dates. It is null when the tournament has no dates. */
private fun statusOf(tournament: Tournament): TournamentStatus? {
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

/** A small outlined label with square corners, so it does not look like a button. */
@Composable
private fun OutlinedTag(color: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .border(BorderStroke(1.dp, color), RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** The blue of the upcoming tournaments. It is lighter in the dark theme, so it stays visible. */
@Composable
private fun upcomingBlue(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF4D9BFF) else Color(0xFF1565C0)

/** The color of the text on the blue: white on the dark blue, black on the light blue. */
@Composable
private fun onUpcomingBlue(): Color = if (upcomingBlue().luminance() > 0.4f) Color.Black else Color.White

@Composable
private fun StatusChip(status: TournamentStatus) {
    val color = when (status) {
        TournamentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        TournamentStatus.UPCOMING -> upcomingBlue()
        TournamentStatus.FINISHED -> MaterialTheme.colorScheme.secondary
    }
    OutlinedTag(color) {
        Text(stringResource(status.label), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** The place of the player: a trophy and the number. The trophy is gold, silver or bronze for the first three. */
@Composable
private fun StandingTag(standing: TournamentStanding) {
    val color = MaterialTheme.colorScheme.onSurface
    OutlinedTag(color) {
        RankTrophy(standing.position, size = 18.dp)
        Text("${standing.position}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/** The logo of the tournament. A neutral box with a trophy shows while the tournament has no logo. */
@Composable
private fun TournamentLogo(tournament: Tournament) {
    val shape = RoundedCornerShape(10.dp)
    val modifier = Modifier.size(56.dp).clip(shape)
    if (tournament.associationLogoUrl.isNullOrBlank()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                Modifier.size(32.dp),
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
private fun TournamentCard(tournament: Tournament, standing: TournamentStanding?, onClick: () -> Unit) {
    var expanded by remember(tournament.id) { mutableStateOf(false) }
    val status = statusOf(tournament)
    // A tournament in progress and an upcoming tournament have an accent color. A finished tournament has none.
    val accent = when (status) {
        TournamentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        TournamentStatus.UPCOMING -> upcomingBlue()
        else -> null
    }
    val uriHandler = LocalUriHandler.current
    val venueText = venueSummary(tournament)
    val mapLink = venueMapLink(tournament)
    val email = tournament.venue.contactEmail
    val dates = datesOf(tournament)
    val hasCity = tournament.hostCity.isNotBlank() || tournament.hostCountry.isNotBlank()
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        // A tournament in progress has a border with the accent color of its status chip.
        border = accent?.let { BorderStroke(2.dp, it) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TournamentLogo(tournament)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // The place and the prizes are at the top right, centered on the lines of the name.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tournament.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (standing != null) {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StandingTag(standing)
                                if (standing.teamRank != null || standing.chickenRank != null || standing.bestHandRank != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (standing.teamRank != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Icon(Icons.Default.Groups, contentDescription = stringResource(Res.string.standing_team), Modifier.size(22.dp))
                                                RankTrophy(standing.teamRank, size = 18.dp)
                                                Text("${standing.teamRank}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (standing.chickenRank != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                ChickenIcon(size = 22.dp)
                                                PositionTrophy(standing.chickenRank, size = 18.dp)
                                            }
                                        }
                                        if (standing.bestHandRank != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                // The hand is the icon of the best hands. The trophy is already the icon of the place.
                                                Icon(Icons.Default.PanTool, contentDescription = stringResource(Res.string.standing_best_hand), Modifier.size(20.dp))
                                                PositionTrophy(standing.bestHandRank, size = 18.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // The dates are the subtitle of the item. They stay at the right of the status badge, or alone when there is no badge.
                    if (status != null || dates != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status != null) StatusChip(status)
                            if (dates != null) {
                                Text(dates, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (hasCity || venueText.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    run {
                        // The pin and the Directions button are centered between the lines of the place.
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            // The address follows the city. It moves to a second line when the row is full.
                            FlowRow(
                                Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (hasCity) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (tournament.hostCity.isNotBlank()) {
                                            Text(tournament.hostCity, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (tournament.hostCountry.isNotBlank()) {
                                            CountryFlag(code = tournament.hostCountry, contentDescription = tournament.hostCountry)
                                        }
                                    }
                                }
                                if (venueText.isNotBlank()) {
                                    Text(
                                        venueText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (mapLink != null) {
                                LinkButton(Icons.Default.Directions, stringResource(Res.string.info_directions), stringResource(Res.string.info_open_directions)) {
                                    uriHandler.openUri(mapLink)
                                }
                            }
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
                        LinkButton(Icons.Default.Email, stringResource(Res.string.info_contact), stringResource(Res.string.contact_send_email)) { uriHandler.openUri("mailto:$email") }
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
 * Loads the tournament and checks for changes at a fixed interval while the screen is open.
 * A failed check does not show a message when the tournament is already on screen.
 * After [STALE_AFTER_FAILURES] failed checks in a row, the message tells how long the data is old.
 */
@Composable
private fun rememberTournamentView(tournamentId: String, reloadKey: Int = 0): Pair<PlayerTournamentView?, UiText?> {
    val presenter = koinInject<PlayerTournamentPresenter>()
    var view by remember(tournamentId) { mutableStateOf<PlayerTournamentView?>(null) }
    var message by remember(tournamentId) { mutableStateOf<UiText?>(null) }
    var failures by remember(tournamentId) { mutableIntStateOf(0) }
    var lastUpdate by remember(tournamentId) { mutableStateOf(TimeSource.Monotonic.markNow()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tournamentId, reloadKey) {
        // Only the first load after a manual reload reads everything again. The later checks ask for changes only.
        var forceNext = reloadKey > 0
        while (true) {
            // A hidden tab or an app in the background does not check the server. The checks go on when it returns.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            val mode = if (forceNext) RefreshMode.FORCE else RefreshMode.IF_CHANGED
            forceNext = false
            when (val result = presenter.load(tournamentId, mode)) {
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
            delay(REFRESH_INTERVAL_MS)
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

@Composable
fun TournamentScreen(navController: NavHostController, tournamentId: String) {
    val (view, message) = rememberTournamentView(tournamentId)
    // An account that is not linked to a player has no own tables. It starts on All tables.
    // A negative value means that the player did not choose a tab.
    var tab by rememberPersistentInt("mainTab", 0)
    var chosenTablesTab by rememberPersistentInt("tablesTab", -1)
    // The values are ids: 0 is My tables, 1 is All tables and 2 is Team tables (only in a tournament with teams).
    val tablesTabs = if (view?.tournament?.isTeams == true) listOf(0, 2, 1) else listOf(0, 1)
    val tablesTabId = chosenTablesTab.takeIf { it in tablesTabs } ?: if (view?.mySlotId == null) 1 else 0
    val tablesTab = tablesTabs.indexOf(tablesTabId)
    // The statistics tab tells this screen when it loads, so the progress bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }
    // The tab rows hide when the content scrolls down and show when it scrolls up.
    val hideOnScroll = rememberHideOnScrollState()

    val account = rememberAccountActions(navController)
    account.Dialogs()
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = view?.tournament?.name ?: stringResource(Res.string.scope_tournament),
        isLoading = (view == null && message == null) || (tab == 2 && statisticsLoading),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        // The tab row touches the top bar, so the screen has no padding. The content under the tabs has its own.
        // The provider is outside the column, so all the tab rows use the same state.
        CompositionLocalProvider(LocalHideOnScroll provides hideOnScroll) {
            ScreenColumn(maxWidth = 720.dp, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (view == null) {
                    MessageDialog(message)
                    return@ScreenColumn
                }
                HideOnScrollTabs(tab) {
                    PrimaryTabRow(
                        selectedTabIndex = tab,
                        containerColor = tabRowColor(),
                        contentColor = TabContentColor,
                        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), color = TabContentColor) },
                    ) {
                        listOf(stringResource(Res.string.tab_tables), stringResource(Res.string.tab_ranking), stringResource(Res.string.stats_title), stringResource(Res.string.tab_info)).forEachIndexed { index, title ->
                            CompactTab(
                                selected = tab == index,
                                onClick = { tab = index },
                                title = title,
                                trackingName = listOf("Tables", "Ranking", "Statistics", "Info")[index],
                                icon = {
                                    val icon = when (index) {
                                        0 -> Icons.Default.People
                                        1 -> Icons.Default.Leaderboard
                                        2 -> StatisticsIcon
                                        else -> Icons.Default.Info
                                    }
                                    Icon(icon, contentDescription = null, Modifier.size(20.dp))
                                },
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().hideOnScroll(hideOnScroll)) {
                    MessageDialog(message)
                    val open: (PlayerTable) -> Unit = { navController.navigate(TableRoute(tournamentId, it.roundId, it.tableId)) }
                    when (tab) {
                        0 -> {
                            HideOnScrollTabs(tablesTab) {
                                PrimaryTabRow(
                                    selectedTabIndex = tablesTab,
                                    containerColor = tabRowColor(),
                                    contentColor = TabContentColor,
                                    indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tablesTab), color = TabContentColor) },
                                ) {
                                    tablesTabs.forEachIndexed { index, id ->
                                        val title = when (id) {
                                            0 -> stringResource(Res.string.tab_tables_me)
                                            2 -> stringResource(Res.string.tab_tables_team)
                                            else -> stringResource(Res.string.tab_tables_all)
                                        }
                                        CompactTab(
                                            selected = tablesTab == index,
                                            onClick = { chosenTablesTab = id },
                                            title = title,
                                            trackingName = when (id) {
                                                0 -> "TablesMe"
                                                2 -> "TablesTeam"
                                                else -> "TablesAll"
                                            },
                                        )
                                    }
                                }
                            }
                            if (tablesTabId == 0) {
                                TableList(
                                    view,
                                    view.myTables,
                                    if (view.mySlotId == null) {
                                        stringResource(Res.string.tables_not_linked)
                                    } else {
                                        stringResource(Res.string.tables_mine_empty)
                                    },
                                    open,
                                    stickyRounds = false,
                                )
                            } else if (tablesTabId == 2) {
                                TableList(
                                    view,
                                    view.teamTables,
                                    stringResource(Res.string.tables_team_empty),
                                    open,
                                )
                            } else {
                                TableList(view, view.tables, stringResource(Res.string.tables_all_empty), open)
                            }
                        }
                        1 -> RankingTab(view) { navController.navigate(PlayerTablesRoute(tournamentId, it)) }
                        2 -> TournamentStatisticsTab(tournamentId, view.mySlotId) { statisticsLoading = it }
                        else -> TournamentInfoTab(view)
                    }
                }
            }
        }
    }
}

private fun datesOf(tournament: Tournament): String? = formatLocalizedDateRange(tournament.eventStartDate, tournament.eventEndDate)

/**
 * The four players of a table in the order of hand 1 (East, South, West, North).
 * Before the players set the seats, the list has the players of the draw and the order has no meaning.
 */
private fun PlayerTable.seatsFor(view: PlayerTournamentView, withLevelChange: Boolean = false): List<SeatDisplay> {
    val t = table
    fun penalty(select: (TableHand) -> String) = hands.sumOf { select(it).trim().toIntOrNull() ?: 0 }
    fun seat(id: String, score: String, points: String, penalty: Int): SeatDisplay {
        val slot = id.toIntOrNull()
        val after = if (withLevelChange) view.statisticsAfter(slot, roundId) else null
        return SeatDisplay(
            name = view.nameOf(slot),
            country = slot?.let { view.players[it]?.country }.orEmpty(),
            score = score,
            points = points,
            penalty = penalty,
            isMine = slot != null && slot == view.mySlotId,
            slotId = slot,
            // The table screen shows the level after this round. The table list shows the level now.
            level = after?.level ?: if (withLevelChange) null else view.levelOf(slot),
            skillScore = after?.skillScore ?: if (withLevelChange) null else view.skillScoreOf(slot),
            levelProvisional = after?.isLevelProvisional ?: view.isLevelProvisional(slot),
            levelChange = if (withLevelChange && table.isCompleted) view.levelChangeAt(slot, roundId) else null,
        )
    }
    if (!seatsAssigned) {
        return (0 until 4).map { seat(t.playerIds.getOrNull(it)?.toString().orEmpty(), "", "", 0) }
    }
    // The table points come only from the scores of the table.
    val scores = t.seatScores
    val points = t.seatPoints
    return listOf(
        seat(t.playerEastId, scores[0], points[0], penalty { it.playerEastPenalty }),
        seat(t.playerSouthId, scores[1], points[1], penalty { it.playerSouthPenalty }),
        seat(t.playerWestId, scores[2], points[2], penalty { it.playerWestPenalty }),
        seat(t.playerNorthId, scores[3], points[3], penalty { it.playerNorthPenalty }),
    )
}

@Composable
private fun TableList(
    view: PlayerTournamentView,
    tables: List<PlayerTable>,
    emptyText: String,
    onOpen: (PlayerTable) -> Unit,
    stickyRounds: Boolean = true,
) {
    if (tables.isEmpty()) {
        Box(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)) { EmptyState(Icons.Default.People, stringResource(Res.string.tables_empty_title), emptyText) }
        return
    }
    val rounds = tables.sortedBy { it.roundId }.groupBy { it.roundId }
    // The list uses all the width. Its padding is inside, so it ends at the tabs and at the bottom of the screen.
    LazyColumn(
        state = rememberPersistentLazyListState("tables-" + emptyText),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
    ) {
        rounds.forEach { (roundId, roundTables) ->
            // In the sticky case, the title of a round stays at the top while its tables scroll under it.
            if (stickyRounds) {
                stickyHeader(key = "round_$roundId") { RoundHeader(roundId, view.roundStartOf(roundId)) }
            } else {
                item(key = "round_$roundId") { RoundHeader(roundId, view.roundStartOf(roundId)) }
            }
            items(roundTables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                TableSummaryCard(view, table, highlightSlot = view.mySlotId, onClick = { onOpen(table) })
            }
        }
    }
}

/** The title of a group of tables. It has a solid background, so the tables do not show through it. */
@Composable
private fun RoundHeader(roundId: Int, start: String?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .layout { measurable, constraints ->
                // The content padding of the list narrows the header, so it grows back to the full width.
                val extra = 32.dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-16.dp.roundToPx(), 0) }
            }
            .background(colors.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.round_n, roundId),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        if (start != null) {
            Icon(Icons.Default.Schedule, contentDescription = null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            Text(start, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** A table with its four players. The points of the table show only for a complete table. */
@Composable
private fun TableSummaryCard(view: PlayerTournamentView, table: PlayerTable, highlightSlot: Int?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val complete = table.isComplete
    val ongoing = !complete && table.doneHands > 0
    // The header color shows the state of the table: finished, ongoing or not started.
    val headerColor = when {
        complete -> colors.primaryContainer
        ongoing -> colors.tertiaryContainer
        else -> colors.surfaceContainerHighest
    }
    val headerContent = when {
        complete -> colors.onPrimaryContainer
        ongoing -> colors.onTertiaryContainer
        else -> colors.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().background(headerColor).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.table_n, table.tableId),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = headerContent,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val statusIcon = when {
                    complete -> Icons.Default.CheckCircle
                    ongoing -> Icons.Default.PlayArrow
                    else -> Icons.Default.HourglassEmpty
                }
                Icon(statusIcon, contentDescription = null, Modifier.size(20.dp), tint = headerContent)
                Text(
                    when {
                        complete -> stringResource(Res.string.status_finished)
                        else -> stringResource(Res.string.table_hands_done, table.doneHands, table.hands.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = headerContent,
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Seat order (east to north) while the table is open. Winner to last when it is complete.
            val seats = table.seatsFor(view).let { all ->
                if (complete) {
                    // The table points come from the scores and use a decimal comma ("1,5"), so sort by score.
                    all.sortedByDescending { it.score.trim().toIntOrNull() ?: Int.MIN_VALUE }
                } else {
                    all
                }
            }
            seats.forEachIndexed { position, seat ->
                val highlighted = seat.slotId != null && seat.slotId == highlightSlot
                val teammate = !highlighted && view.isTeammate(seat.slotId)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                highlighted -> colors.primaryContainer.copy(alpha = 0.45f)
                                // A darker tone of the same green marks the other members of my team.
                                // The light theme uses the primary green, because the other color has the tone of the card there.
                                teammate -> if (colors.background.luminance() < 0.5f) {
                                    colors.onPrimaryContainer.copy(alpha = 0.2f)
                                } else {
                                    colors.primary.copy(alpha = 0.3f)
                                }
                                else -> Color.Transparent
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (complete) {
                        // Seats are sorted by result here. The fourth place has a sad face.
                        if (position < 3) PositionTrophy(position + 1, size = 24.dp) else LastPlaceIcon(size = 24.dp)
                    }
                    PlayerFlag(seat.country)
                    SeatLevelMedal(seat, size = 24.dp)
                    Text(
                        seat.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (highlighted) FontWeight.Bold else null,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (complete && seat.points.isNotBlank()) {
                        // The table points have the same outlined style as in the ranking.
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, colors.onSurface),
                        ) {
                            Text(
                                formatPoints(seat.points),
                                Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        seat.score.ifBlank { "-" },
                        Modifier.width(56.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** [short] is the label of the tab of the ranking. It is short because the tabs share one row. */
private enum class RankingKind(val title: StringResource, val short: StringResource) {
    PLAYERS(Res.string.scope_players, Res.string.scope_players),
    TEAMS(Res.string.ranking_teams, Res.string.ranking_teams),
    CHICKEN(Res.string.ranking_chicken_hands, Res.string.ranking_chicken_short),
    BEST(Res.string.ranking_best_hands, Res.string.ranking_best_short),
}

/** One line of a ranking list. The chip is the main number, outlined. The score is the plain number next to it. */
private data class RankingRow(
    val key: String,
    val position: Int,
    val title: String,
    val subtitle: String?,
    /** The country code of the player. The line shows its flag next to the name. */
    val country: String? = null,
    val chip: String,
    val chipChicken: Boolean = false,
    val score: String? = null,
    val mine: Boolean = false,
    val playerId: Int? = null,
    /** The players of a team, by score. Not empty only for a team. The row can be folded. */
    val members: List<RankingRow> = emptyList(),
    /** The player is excluded from the EMA ranking. The name shows with strikethrough. */
    val excluded: Boolean = false,
)

/** The ranking tab. A second row of tabs shows the rankings that the tournament has. */
@Composable
internal fun RankingTab(view: PlayerTournamentView, onPlayerClick: (Int) -> Unit) {
    val rankings = view.rankings
    val kinds = buildList {
        add(RankingKind.PLAYERS)
        if (view.tournament.isTeams && rankings.teams.isNotEmpty()) add(RankingKind.TEAMS)
        if (view.tournament.countChickenHands && rankings.chickenHands.isNotEmpty()) add(RankingKind.CHICKEN)
        if (view.tournament.countBestHands && rankings.bestHands.isNotEmpty()) add(RankingKind.BEST)
    }
    var chosen by remember { mutableStateOf(RankingKind.PLAYERS) }
    val kind = chosen.takeIf { it in kinds } ?: RankingKind.PLAYERS
    val myTeam = rankings.players.firstOrNull { it.playerId == view.mySlotId }?.teamId

    val rows = when (kind) {
        RankingKind.PLAYERS -> rankings.players.map { row ->
            RankingRow(
                key = "p${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = row.substitution?.label(),
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
                excluded = row.substitution?.struckThrough == true,
            )
        }
        RankingKind.TEAMS -> rankings.teams.map { row ->
            val members = rankings.players
                .filter { it.teamId == row.teamId }
                .sortedWith(compareByDescending<PlayerRanking> { it.score }.thenByDescending { it.points })
                .map { member ->
                    RankingRow(
                        key = "t${row.teamId}_p${member.playerId}",
                        position = member.position,
                        title = view.nameOf(member.playerId),
                        subtitle = member.substitution?.label(),
                        country = view.players[member.playerId]?.country?.takeIf { it.isNotBlank() },
                        chip = formatPoints(member.points),
                        score = "${member.score}",
                        mine = member.playerId == view.mySlotId,
                        playerId = member.playerId,
                        excluded = member.substitution?.struckThrough == true,
                    )
                }
            RankingRow(
                key = "t${row.teamId}",
                position = row.position,
                title = view.teamNames[row.teamId] ?: stringResource(Res.string.team_n, row.teamId),
                subtitle = null,
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.teamId == myTeam,
                members = members,
            )
        }
        RankingKind.CHICKEN -> rankings.chickenHands.map { row ->
            RankingRow(
                key = "c${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = stringResource(Res.string.ranking_points_score, formatPoints(row.points), row.score),
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                chip = "${row.chickenHands}",
                chipChicken = true,
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
        RankingKind.BEST -> rankings.bestHands.mapIndexed { index, row ->
            RankingRow(
                key = "b${index}_${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = stringResource(Res.string.ranking_points_score, formatPoints(row.points), row.score),
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                chip = "${row.handScore}",
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
    }

    RankingRows(
        rows,
        emptyText = stringResource(Res.string.ranking_empty),
        onPlayerClick = onPlayerClick,
        tabs = {
            if (kinds.size > 1) {
                val selectedIndex = kinds.indexOf(kind)
                HideOnScrollTabs(kinds.size) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = tabRowColor(),
                        contentColor = TabContentColor,
                        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedIndex), color = TabContentColor) },
                    ) {
                        kinds.forEach { item ->
                            CompactTab(
                                selected = item == kind,
                                onClick = { chosen = item },
                                title = stringResource(item.short),
                                trackingName = item.name,
                                icon = {
                                    when (item) {
                                        RankingKind.PLAYERS -> Icon(Icons.Default.People, contentDescription = null, Modifier.size(20.dp))
                                        RankingKind.TEAMS -> Icon(Icons.Default.Groups, contentDescription = null, Modifier.size(20.dp))
                                        RankingKind.CHICKEN -> ChickenIcon(size = 20.dp)
                                        RankingKind.BEST -> PositionTrophy(1, size = 20.dp)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** The tabs of the rankings, then the list. The button that scrolls to the own line and the search are the first item of the list. */
@Composable
private fun RankingRows(
    rows: List<RankingRow>,
    emptyText: String,
    onPlayerClick: (Int) -> Unit,
    tabs: @Composable () -> Unit,
) {
    if (rows.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs()
            EmptyState(Icons.Default.Leaderboard, stringResource(Res.string.ranking_empty_title), emptyText)
        }
        return
    }
    val visible = rows
    val options = remember(rows) { rows.map { it.key to it.title } }
    val listState = rememberPersistentLazyListState("ranking")
    val scope = rememberCoroutineScope()
    val myIndex = visible.indexOfFirst { it.mine }
    Column {
        tabs()
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        ) {
            item(key = "controls") {
                // One row that scrolls with the list: "Jump to me", then the search.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val jumpToMe = stringResource(Res.string.jump_to_me)
                    if (myIndex >= 0) {
                        Column(
                            Modifier.width(44.dp).height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { scope.launch { listState.animateScrollToItem(myIndex + 1) } }
                                .semantics { contentDescription = jumpToMe },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(Res.string.scope_me), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                    // The dropdown does not filter the list. Choosing a line scrolls the list to it.
                    SelectField<String>(
                        label = stringResource(Res.string.field_search),
                        options = options,
                        selected = null,
                        onSelect = { key ->
                            val index = visible.indexOfFirst { it.key == key }
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index + 1) }
                        },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                }
            }
            items(visible, key = { it.key }) { row ->
                RankingCard(row, onPlayerClick)
            }
        }
    }
}

/** One ranking line. A team line folds out to show its players. */
@Composable
private fun RankingCard(row: RankingRow, onPlayerClick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val foldable = row.members.isNotEmpty()
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    val playerId = row.playerId
    val onClick: (() -> Unit)? = when {
        foldable -> ({ expanded = !expanded })
        playerId != null -> ({ onPlayerClick(playerId) })
        else -> null
    }
    Card(
        if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth(),
        border = if (row.mine) BorderStroke(2.dp, colors.primary) else null,
        colors = CardDefaults.cardColors(
            // Gold, silver and bronze for the first three places, softened so the text stays readable.
            containerColor = if (row.position in 1..3) {
                // The first place uses a bright, light yellow so it is easy to tell from bronze.
                val medal = if (row.position == 1) Color(0xFFFFE259) else positionColor(row.position)
                medal.copy(alpha = if (row.position == 1) 0.55f else 0.35f).compositeOver(colors.surfaceContainerHigh)
            } else {
                colors.surfaceContainerHigh
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            RankingLine(row, trailing = if (foldable) expanded else null)
            if (foldable && expanded) {
                row.members.forEach { member ->
                    val memberId = member.playerId
                    Box(
                        if (memberId != null) Modifier.fillMaxWidth().clickable { onPlayerClick(memberId) } else Modifier.fillMaxWidth(),
                    ) { RankingLine(member, compact = true) }
                }
            }
        }
    }
}

/** The content of a ranking line. [trailing] is null for a line that cannot be folded, else it is the folded state. */
@Composable
private fun RankingLine(row: RankingRow, compact: Boolean = false, trailing: Boolean? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = if (compact) 24.dp else 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!compact) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                if (row.position <= 3) {
                    // A circle behind the trophy keeps it visible on the colored card.
                    Box(
                        Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { PositionTrophy(row.position, size = 24.dp) }
                } else {
                    Text("${row.position}", fontWeight = FontWeight.Bold)
                }
            }
        }
        // The flag comes first and has a fixed width, so the names are aligned. A team has no flag.
        if (row.playerId != null) PlayerFlag(row.country)
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                fontWeight = if (row.mine) FontWeight.Bold else null,
                textDecoration = if (row.excluded) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, colors.onSurface),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (row.chipChicken) ChickenIcon()
                Text(
                    row.chip,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
        }
        row.score?.let {
            Text(
                it,
                Modifier.widthIn(min = 56.dp),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.End,
            )
        }
        if (trailing != null) {
            Icon(
                if (trailing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (trailing) Res.string.team_hide_players else Res.string.team_show_players),
                Modifier.size(24.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TableScreen(navController: NavHostController, tournamentId: String, roundId: Int, tableId: Int) {
    var reloadKey by remember { mutableStateOf(0) }
    val (view, message) = rememberTournamentView(tournamentId, reloadKey)
    val table = view?.tables?.firstOrNull { it.roundId == roundId && it.tableId == tableId }
    var preselectWinner by remember { mutableStateOf<Int?>(null) }
    // The detailed list of hands refers to the players by id, so the seats show the ids while it is on.
    var details by rememberSaveable { mutableStateOf(false) }
    // The clock moves on, so a table that starts while the screen is open opens for hands.
    val now by produceState(currentLocalDateTime()) {
        while (true) {
            delay(30_000)
            value = currentLocalDateTime()
        }
    }

    val account = rememberAccountActions(navController)
    account.Dialogs()
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = view?.tournament?.name ?: stringResource(Res.string.scope_tournament),
        subtitle = view?.roundStartOf(roundId),
        isLoading = view == null && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        ScreenColumn(
            maxWidth = 720.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            scrollable = true,
            paddingInsideScroll = true,
        ) {
            MessageDialog(message)
            if (view == null || table == null) return@ScreenColumn
            Text(
                stringResource(Res.string.stats_round_table, roundId, tableId),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val complete = view.isFinished(table, now)
            val handNumber = minOf(table.doneHands + 1, 16)
            TableSeatsView(
                seats = table.seatsFor(view, withLevelChange = true),
                handNumber = handNumber,
                centerLabel = if (complete) stringResource(Res.string.table_done) else stringResource(Res.string.table_hand_progress, handNumber, table.hands.size),
                showPoints = complete,
                windsKnown = table.seatsAssigned,
                showIds = details,
                // The differences make sense only while the table is in play: it has hands and it is not finished.
                diffsAvailable = !complete && table.doneHands > 0,
                // On a table that takes hands, a tap on a player starts a hand won by that player.
                onPlayerClick = { slot ->
                    if (view.canAddHands(table, now)) {
                        preselectWinner = slot
                    } else {
                        navController.navigate(PlayerTablesRoute(tournamentId, slot))
                    }
                },
            )
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
            HandHistory(view, table, details) { details = it }
        }
    }
}

@Composable
private fun HandHistory(view: PlayerTournamentView, table: PlayerTable, details: Boolean, onDetailsChange: (Boolean) -> Unit) {
    // Extra space separates the list from the players and the button above.
    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.stat_hands),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.align(Alignment.CenterEnd).clickable { onDetailsChange(!details) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.table_details), style = MaterialTheme.typography.labelLarge)
            Switch(checked = details, onCheckedChange = onDetailsChange, modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (details) {
        HandDetailsTable(table) { seat -> SeatWind.entries.getOrNull(seat) }
    } else if (table.hands.none { it.isDone || it.penaltyRows().isNotEmpty() }) {
        EmptyState(Icons.Default.Edit, stringResource(Res.string.details_no_hands_title), stringResource(Res.string.details_no_hands_message))
    } else {
        HandList(view, table)
    }
}

/**
 * The newest item is at the top. Each hand is one card. The penalties of a hand were given before its result,
 * so they come under the result, with a divider. A hand that is not done shows only its penalties.
 */
@Composable
private fun HandList(view: PlayerTournamentView, table: PlayerTable) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    table.hands.sortedByDescending { it.handId }.forEach { hand ->
        val penalties = hand.penaltyRows().asReversed()
        if (!hand.isDone && penalties.isEmpty()) return@forEach
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            if (hand.isDone) HandRow(view, hand, dark)
            penalties.forEachIndexed { index, values ->
                if (hand.isDone || index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                PenaltyRow(view, table.seatIds(), hand.handId, values)
            }
        }
    }
}

/**
 * One penalty. The text is at the start and the values are at the end, in the same column as the score of a hand.
 * The penalized players are red and the players who receive points are green.
 */
@Composable
private fun PenaltyRow(view: PlayerTournamentView, seatSlots: List<Int?>, handId: Int, values: List<Int>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$handId",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(Icons.Default.Warning, contentDescription = stringResource(Res.string.hand_penalty), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEachIndexed { seat, value ->
                if (value == 0) return@forEachIndexed
                val slot = seatSlots.getOrNull(seat)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlayerFlag(view.players[slot]?.country, width = 24.dp)
                    Text(
                        view.nameOf(slot),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (value > 0) "+$value" else "$value",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (value > 0) WinnerColor else MaterialTheme.colorScheme.error,
                    )
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
private fun HandRow(view: PlayerTournamentView, hand: TableHand, dark: Boolean) {
    val winnerSlot = hand.playerWinnerId.toIntOrNull()
    val loserSlot = hand.playerLooserId.toIntOrNull()
    val kind = hand.kind()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The hand number has the wind and the place in the round under it, so the row keeps its width.
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${hand.handId}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HandLabel(hand.handId)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (kind != HandKind.DRAW) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlayerFlag(view.players[winnerSlot]?.country, width = 24.dp)
                    Text(
                        view.nameOf(winnerSlot),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WinnerColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WinTypePill(kind, kind.accent(dark, MaterialTheme.colorScheme.error))
                if (kind == HandKind.DISCARD) {
                    Row(
                        Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PlayerFlag(view.players[loserSlot]?.country, width = 20.dp)
                        Text(
                            view.nameOf(loserSlot),
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (hand.isChickenHand) ChickenIcon()
            }
        }
        if (kind != HandKind.DRAW) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
            ) {
                Text(
                    hand.handScore,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The way the hand was won. Only this pill has color, so the type reads at once without coloring the whole item. */
@Composable
private fun WinTypePill(kind: HandKind, accent: Color) {
    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.18f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(kind.icon(), contentDescription = null, Modifier.size(16.dp), tint = accent)
            Text(
                when (kind) {
                    HandKind.SELF_PICK -> stringResource(Res.string.hand_self_pick)
                    HandKind.DISCARD -> stringResource(Res.string.hand_discard)
                    HandKind.DRAW -> stringResource(Res.string.hand_draw)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

/** Green for a self-pick, the error red for a discard and grey for a draw. The dark theme gets lighter shades. */
private fun HandKind.accent(dark: Boolean, error: Color): Color = when (this) {
    HandKind.SELF_PICK -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    HandKind.DISCARD -> error
    HandKind.DRAW -> if (dark) Color(0xFFBDBDBD) else Color(0xFF757575)
}

private fun HandKind.icon(): ImageVector = when (this) {
    HandKind.SELF_PICK -> Icons.Default.Bolt
    HandKind.DISCARD -> Icons.Default.SwapHoriz
    HandKind.DRAW -> Icons.Default.HorizontalRule
}

/** Every table of one player of the tournament, with the results. */
@Composable
fun PlayerTablesScreen(navController: NavHostController, tournamentId: String, slotId: Int) {
    val (view, message) = rememberTournamentView(tournamentId)
    val ranking = view?.rankings?.players?.firstOrNull { it.playerId == slotId }
    val tables = view?.tables.orEmpty().filter { slotId in it.playerSlots() }.sortedBy { it.roundId }
    var tab by rememberPersistentInt("playerTab", 0)
    // The Stats tab tells this screen when it loads, so the progress bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }
    // The tab row hides when the content scrolls down and shows when it scrolls up.
    val hideOnScroll = rememberHideOnScrollState()

    val account = rememberAccountActions(navController)
    account.Dialogs()
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = view?.nameOf(slotId) ?: stringResource(Res.string.scope_player),
        subtitle = view?.tournament?.name,
        isLoading = (view == null && message == null) || (tab == 1 && statisticsLoading),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        CompositionLocalProvider(LocalHideOnScroll provides hideOnScroll) {
            ScreenColumn(maxWidth = 720.dp, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (view == null) {
                    MessageDialog(message)
                    return@ScreenColumn
                }
                HideOnScrollTabs(tab) {
                    PrimaryTabRow(
                        selectedTabIndex = tab,
                        containerColor = tabRowColor(),
                        contentColor = TabContentColor,
                        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), color = TabContentColor) },
                    ) {
                        listOf(stringResource(Res.string.tab_tables), stringResource(Res.string.tab_stats)).forEachIndexed { index, title ->
                            CompactTab(
                                selected = tab == index,
                                onClick = { tab = index },
                                title = title,
                                trackingName = if (index == 0) "Tables" else "Statistics",
                                icon = {
                                    Icon(
                                        if (index == 0) Icons.Default.People else StatisticsIcon,
                                        contentDescription = null,
                                        Modifier.size(20.dp),
                                    )
                                },
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().hideOnScroll(hideOnScroll)) {
                    MessageDialog(message)
                    if (tab == 1) {
                        PlayerSlotStatisticsTab(
                            tournamentId,
                            slotId,
                            ranking = ranking,
                        ) { statisticsLoading = it }
                    } else if (tables.isEmpty()) {
                        Box(Modifier.padding(16.dp)) { EmptyState(Icons.Default.People, stringResource(Res.string.tables_empty_title), stringResource(Res.string.tables_player_empty_message)) }
                    } else {
                        // The padding is inside the list, so the list ends at the tab row and at the bottom of the screen.
                        LazyColumn(
                            state = rememberPersistentLazyListState("tables"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            items(tables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                                TableSummaryCard(view, table, highlightSlot = slotId) {
                                    navController.navigate(TableRoute(tournamentId, table.roundId, table.tableId))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The color of the winner in the hand list, the counterpart of the red loser. Same green as the gains of the hand review. */
private val WinnerColor = Color(0xFF00934C)

/** Shows a polling message once. The dialog returns only after the message went away and came back. */
@Composable
private fun MessageDialog(message: UiText?) {
    var seen by remember(message != null) { mutableStateOf(false) }
    if (message != null && !seen) AppErrorDialog(message, onDismiss = { seen = true })
}
