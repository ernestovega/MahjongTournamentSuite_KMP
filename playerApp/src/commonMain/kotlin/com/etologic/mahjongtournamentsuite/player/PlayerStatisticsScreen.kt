package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.byRound
import com.etologic.mahjongtournamentsuite.domain.usecase.GroupStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.FilteredStatistics
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerRoundsCard
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStandingCard
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.ResetScrollOnChange
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsFilterBar
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsFilters
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsView
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentRoundChips
import com.etologic.mahjongtournamentsuite.presentation.components.opponentsOf
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.components.title
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlinx.coroutines.flow.map
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.stats_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.stats_player_empty_message
import mahjongtournamentsuite.playerapp.generated.resources.stats_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Tells the screen that the tab is loading. The screen shows its progress bar under the top bar while this is true.
 * The bar goes away when the tab leaves the screen.
 */
@Composable
private fun ReportLoading(loading: Boolean, onChange: (Boolean) -> Unit) {
    LaunchedEffect(loading) { onChange(loading) }
    DisposableEffect(Unit) { onDispose { onChange(false) } }
}

/**
 * The old Statistics route. A page refresh can still open it: it shows the statistics with the filters of the route.
 */
@Composable
fun StatisticsScreen(navController: NavHostController, route: StatisticsRoute) {
    val account = rememberAccountActions(navController)
    account.Dialogs()
    var loading by remember { mutableStateOf(true) }
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = stringResource(Res.string.stats_title),
        isLoading = loading,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        if (route.scope == StatisticsScope.PERSON.name && route.tournamentId == null) {
            // The old route of the own statistics: the own player is chosen at the start, as in the Stats tab.
            MyStatisticsTab(onLoadingChange = { loading = it })
        } else {
            FilteredStatisticsTab(
                initial = StatisticsFilters(tournamentId = route.tournamentId, slotId = route.slotId?.takeIf { route.tournamentId != null }),
                persistKey = "statistics-route-filters",
                onLoadingChange = { loading = it },
            )
        }
    }
}

/** The statistics tab of the tournament screen: the statistics with the filter of this tournament at the start. */
@Composable
fun TournamentStatisticsTab(
    tournamentId: String,
    onLoadingChange: (Boolean) -> Unit = {},
) {
    FilteredStatisticsTab(
        initial = StatisticsFilters(tournamentId = tournamentId),
        persistKey = "statistics-filters-$tournamentId",
        onLoadingChange = onLoadingChange,
    )
}

/**
 * The statistics of the person with the EMA number [emaId] over all the public tournaments, for the choice "All" of
 * the screen of a player. [onLoadingChange] tells the screen when the data loads.
 */
@Composable
fun PersonStatisticsTab(
    emaId: String,
    onLoadingChange: (Boolean) -> Unit = {},
    /** Above the cards, for example the choice of a tournament of the player. */
    top: (@Composable () -> Unit)? = null,
) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    // The statistics of a person change slowly, so they are checked at the slow pace of the lists.
    val polled = rememberPolledLoad(emaId, load = { mode, first -> presenter.person(emaId, mode, track = first) }, nextDelay = { PollSchedule.LIST_IDLE })
    val data = polled.data
    val message = polled.message
    ReportLoading(data == null && message == null, onLoadingChange)
    Column(
        Modifier.fillMaxSize().verticalScrollWithIndicators(rememberPersistentScrollState("person-statistics-$emaId"), MtsScrollbar.Overlay)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
    ) {
        message?.let { AppErrorDialog(it, onDismiss = polled.dismissMessage) }
        top?.invoke()
        val loaded = data ?: return@Column
        PlayerStatisticsPanel(
            loaded.overall,
            tournaments = loaded.tournaments.map { it.first },
            rivalNames = loaded.rivalNames,
        )
    }
}

/**
 * The statistics of one player in one tournament. The screen of a player shows it in its Stats tab.
 * [onLoadingChange] tells the screen when the data loads, so the progress bar stays until the content shows.
 */
@Composable
fun PlayerSlotStatisticsTab(
    tournamentId: String,
    slotId: Int,
    /** The place, points and score of the player in the tournament. They show in the first card, also when there are no statistics. */
    ranking: PlayerRanking? = null,
    /** The place of the player at the end of each round, for the "By round" card. */
    places: Map<Int, Int>? = null,
    playerCount: Int = 0,
    onLoadingChange: (Boolean) -> Unit = {},
    /** Above the cards, for example the choice of another tournament of the player. */
    top: (@Composable () -> Unit)? = null,
) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    val polled = rememberTournamentStatistics(presenter, tournamentId)
    val data = polled.data
    val message = polled.message
    ReportLoading(data == null && message == null, onLoadingChange)
    Column(
        Modifier.fillMaxSize().verticalScrollWithIndicators(rememberPersistentScrollState("player-statistics-$slotId"), MtsScrollbar.Overlay)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
    ) {
        message?.let { AppErrorDialog(it, onDismiss = polled.dismissMessage) }
        top?.invoke()
        val loaded = data ?: return@Column
        val stats = loaded.statistics.players.firstOrNull { it.playerId == slotId }
        val standing: (@Composable (Modifier) -> Unit)? = ranking?.let { { modifier -> PlayerStandingCard(it.position, it.points, it.score, modifier, places = places, of = playerCount) } }
        if (stats == null) {
            standing?.invoke(Modifier.fillMaxWidth())
            AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_player_empty_message))
        } else {
            // The place goes over the whole width, then the other cards, with the rounds after the places.
            // The top bar shows who the player is.
            standing?.invoke(Modifier.fillMaxWidth())
            PlayerStatisticsPanel(
                stats,
                afterPlaces = { modifier -> PlayerRoundsCard(stats.byRound(loaded.tournament.roundIds()), places, playerCount, modifier) },
                tournaments = listOf(loaded.tournament),
                rivalNames = loaded.rivalNames,
            )
        }
    }
}

/**
 * The statistics tab of the tournaments screen. With an account linked to a player, the own player is chosen at the
 * start. Without one, the statistics of all tournaments and players show. Anyone can look at the statistics of anyone.
 */
@Composable
fun MyStatisticsTab(onLoadingChange: (Boolean) -> Unit = {}) {
    val session = koinInject<PlayerSessionPresenter>()
    var myEma by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        myEma = session.myEmaId()
        ready = true
    }
    if (!ready) {
        ReportLoading(true, onLoadingChange)
        return
    }
    FilteredStatisticsTab(
        initial = StatisticsFilters(emaId = myEma),
        persistKey = "statistics-filters",
        onLoadingChange = onLoadingChange,
    )
}

/**
 * The statistics with the row of filters, the same as in the admin app. [initial] are the filters at the start.
 * A change of the filters is kept under [persistKey], so a page refresh shows the same statistics.
 * What shows is checked again while the screen is open, at the pace of [PollSchedule].
 */
@Composable
internal fun FilteredStatisticsTab(
    initial: StatisticsFilters,
    persistKey: String,
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    var stored by rememberPersistentText(persistKey, null)
    val filters = StatisticsFilters.decode(stored) ?: initial
    val setFilters: (StatisticsFilters) -> Unit = { stored = it.encode() }
    var players by remember { mutableStateOf<List<Player>>(emptyList()) }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    LaunchedEffect(Unit) {
        (presenter.players() as? AppResult.Success)?.let { players = it.value }
        (presenter.tournaments() as? AppResult.Success)?.let { tournaments = it.value }
    }
    var person: PersonStatisticsData? = null
    var tournament: TournamentStatisticsData? = null
    var group: GroupStatisticsData? = null
    var message: UiText? = null
    var dismiss: () -> Unit = {}
    val view = filters.view
    when (view) {
        StatisticsView.PERSON -> {
            val emaId = filters.emaId.orEmpty()
            // The statistics of a person change slowly, so they are checked at the slow pace of the lists.
            val polled = rememberPolledLoad(emaId, load = { mode, first -> presenter.person(emaId, mode, track = first) }, nextDelay = { PollSchedule.LIST_IDLE })
            person = polled.data
            message = polled.message
            dismiss = polled.dismissMessage
        }
        StatisticsView.TABLE, StatisticsView.SLOT, StatisticsView.TOURNAMENT -> {
            val polled = rememberTournamentStatistics(presenter, filters.tournamentId.orEmpty())
            tournament = polled.data
            message = polled.message
            dismiss = polled.dismissMessage
        }
        StatisticsView.GROUP -> {
            val groupFilter = filters.groupFilter()
            val polled = rememberPolledLoad(groupFilter, load = { _, first -> presenter.group(groupFilter, track = first) }, nextDelay = { PollSchedule.LIST_IDLE })
            group = polled.data
            message = polled.message
            dismiss = polled.dismissMessage
        }
    }
    val loaded = person != null || tournament != null || group != null
    ReportLoading(!loaded && message == null, onLoadingChange)
    var round by rememberSaveable(filters.tournamentId) { mutableStateOf<Int?>(null) }
    val scroll = rememberPersistentScrollState("statistics-$persistKey")
    // Other filters start at the top.
    ResetScrollOnChange(scroll, filters)
    Column(Modifier.fillMaxSize()) {
        // The filters hide with the tab rows when the statistics scroll down, and show again with another filter.
        HideOnScrollTabs(filters) {
            StatisticsFilterBar(
                filters = filters,
                onChange = setFilters,
                players = players,
                tournaments = tournaments,
                slotName = filters.slotId?.let { tournament?.names?.get(it) },
                opponents = remember(person, filters.emaId) { opponentsOf(person?.takeIf { it.emaId == filters.emaId }) },
            )
        }
        val shown = tournament
        if (view == StatisticsView.TOURNAMENT && shown != null) {
            // The chips of the rounds stay above the statistics while they scroll. Every card shows the chosen round.
            TournamentRoundChips(shown.statistics, shown.tournament.roundIds(), round, { round = it }, edge = MtsTheme.spacing.lg)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScrollWithIndicators(scroll, MtsScrollbar.Overlay)
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
        ) {
            message?.let { AppErrorDialog(it, onDismiss = dismiss) }
            FilteredStatistics(
                filters = filters,
                onFiltersChange = setFilters,
                person = person,
                tournament = tournament,
                group = group,
                loadStandings = { emaId, ids -> presenter.standings(emaId, ids) },
                selectedRound = round,
                onSelectRound = { round = it },
            )
        }
    }
}

/**
 * The statistics of a tournament, checked again while the screen is open: every 15 seconds while the tournament runs,
 * and at the slower pace of [PollSchedule] before and after. Nothing downloads again when no hand changed.
 */
@Composable
private fun rememberTournamentStatistics(presenter: PlayerStatisticsPresenter, tournamentId: String): PolledLoad<TournamentStatisticsData> = rememberPolledLoad(
    tournamentId,
    load = { mode, first -> presenter.tournament(tournamentId, mode, track = first) },
    nextDelay = { data -> PollSchedule.nextDelay(data?.tournament, null, data?.tournament?.currentVenueDateTime() ?: currentLocalDateTime()) },
)
