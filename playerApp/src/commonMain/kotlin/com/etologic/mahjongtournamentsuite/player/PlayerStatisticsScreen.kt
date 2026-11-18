package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.BoxWithConstraints

import com.etologic.mahjongtournamentsuite.presentation.components.CompactTab
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollableTabRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTabRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.LocalScreenScroll
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerDropdownField
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStandingCard
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerTournamentsSection
import com.etologic.mahjongtournamentsuite.presentation.components.ResetScrollOnChange
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.mtsTabContentColor
import com.etologic.mahjongtournamentsuite.presentation.components.mtsTabRowColor
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentRoundChips
import androidx.compose.runtime.saveable.rememberSaveable
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.components.title
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.action_cancel
import mahjongtournamentsuite.playerapp.generated.resources.action_change
import mahjongtournamentsuite.playerapp.generated.resources.scope_by_tournaments
import mahjongtournamentsuite.playerapp.generated.resources.scope_overall
import mahjongtournamentsuite.playerapp.generated.resources.scope_player
import mahjongtournamentsuite.playerapp.generated.resources.scope_tournament
import mahjongtournamentsuite.playerapp.generated.resources.stats_choose_player_message
import mahjongtournamentsuite.playerapp.generated.resources.stats_choose_player_title
import mahjongtournamentsuite.playerapp.generated.resources.stats_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.stats_my_statistics
import mahjongtournamentsuite.playerapp.generated.resources.stats_player_empty_message
import mahjongtournamentsuite.playerapp.generated.resources.stats_player_field
import mahjongtournamentsuite.playerapp.generated.resources.stats_title
import mahjongtournamentsuite.playerapp.generated.resources.stats_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.domain.model.byRound
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerRoundsCard

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
 * Statistics of the player app. The route gives the first scope and its context.
 * The player can switch to every scope that has a context.
 */
@Composable
fun StatisticsScreen(navController: NavHostController, route: StatisticsRoute) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    val session = koinInject<PlayerSessionPresenter>()
    // Without an account there are no own statistics. Everything else is public.
    val signedIn by produceState(true) { value = session.hasSession() }
    val hasTournament = route.tournamentId != null
    val scopes = buildList {
        if (signedIn) add(StatisticsScope.PERSON)
        if (signedIn) add(StatisticsScope.PERSON_TOURNAMENTS)
        if (hasTournament) add(StatisticsScope.TOURNAMENT)
        if (hasTournament) add(StatisticsScope.PLAYER)
    }
    // A page refresh restores the scope that the player chose.
    var scopeName by rememberPersistentText("scope", route.scope)
    var scope by remember {
        mutableStateOf(scopes.firstOrNull { it.name == scopeName } ?: StatisticsScope.PERSON)
    }
    LaunchedEffect(scope) { scopeName = scope.name }
    LaunchedEffect(scopes) { if (scope !in scopes) scopes.firstOrNull()?.let { scope = it } }
    var person by remember { mutableStateOf<PersonStatisticsData?>(null) }
    var tournament by remember { mutableStateOf<TournamentStatisticsData?>(null) }
    var mySlotId by remember { mutableStateOf<Int?>(null) }
    var chosenSlotText by rememberPersistentText("slot", route.slotId?.toString())
    val chosenSlotId = chosenSlotText?.toIntOrNull()
    var message by remember { mutableStateOf<UiText?>(null) }
    var tournamentFinished by remember { mutableStateOf(false) }
    var personFinished by remember { mutableStateOf(false) }
    val finished = when {
        scope == StatisticsScope.PERSON || scope == StatisticsScope.PERSON_TOURNAMENTS -> personFinished
        scope == StatisticsScope.PLAYER && chosenSlotId == null -> tournamentFinished && personFinished
        else -> tournamentFinished
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The tournament shows as soon as it is ready. "My statistics" loads at the same time.
    LaunchedEffect(route.tournamentId) {
        coroutineScope {
            val personRequest = async { if (session.hasSession()) presenter.me() else null }
            route.tournamentId?.let { id ->
                when (val result = presenter.tournament(id)) {
                    is AppResult.Success -> tournament = result.value
                    is AppResult.Failure -> message = result.error.toUiText()
                }
                tournamentFinished = true
            }
            when (val result = personRequest.await()) {
                null -> Unit
                is AppResult.Success -> {
                    person = result.value
                    mySlotId = tournament?.slots.orEmpty().firstOrNull { it.assignedEmaId == result.value.emaId }?.id
                }
                is AppResult.Failure -> if (scope == StatisticsScope.PERSON || scope == StatisticsScope.PERSON_TOURNAMENTS || route.tournamentId == null) {
                    message = result.error.toUiText()
                }
            }
            personFinished = true
        }
        // The statistics of the tournament are checked again while the screen is open. The first check reuses the
        // revisions of the load above, so it asks the server nothing new.
        val id = route.tournamentId ?: return@LaunchedEffect
        pollForChanges(
            isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
            forceFirst = false,
            load = { mode -> presenter.tournament(id, mode, track = false) },
            nextDelay = { tournament?.tournament?.let { PollSchedule.nextDelay(it, null, it.currentVenueDateTime()) } ?: PollSchedule.UNKNOWN },
        ) { result ->
            if (result is AppResult.Success) tournament = result.value
        }
    }

    val shown = tournament
    val title = when (scope) {
        StatisticsScope.PERSON, StatisticsScope.PERSON_TOURNAMENTS -> stringResource(Res.string.stats_my_statistics)
        StatisticsScope.TOURNAMENT -> shown?.tournament?.name ?: stringResource(Res.string.scope_tournament)
        // The player app has no table scope. The scope list never holds it.
        StatisticsScope.TABLE -> stringResource(Res.string.scope_tournament)
        StatisticsScope.PLAYER -> shown?.names?.get(chosenSlotId ?: mySlotId) ?: stringResource(Res.string.scope_player)
    }

    val account = rememberAccountActions(navController)
    account.Dialogs()
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = title,
        subtitle = stringResource(Res.string.stats_title),
        isLoading = !finished && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        ScreenColumn(
            // The statistics sections use the full width and flow into columns.
            maxWidth = Dp.Infinity,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
            scrollable = true,
        ) {
            // Another view or another player starts at the top.
            ResetScrollOnChange(LocalScreenScroll.current?.state, scope to chosenSlotText)
            message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
            if (scopes.size > 1) {
                // On a phone the tabs and the player picker do not fit in one row: the picker goes below the tabs.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val narrow = maxWidth < ScopeRowMinWidth
                    val tabs: @Composable (Modifier) -> Unit = { modifier ->
                        MtsScrollableTabRow(
                            modifier = modifier,
                            selectedTabIndex = scopes.indexOf(scope),
                        ) {
                            scopes.forEach { item ->
                                CompactTab(selected = item == scope, onClick = { scope = item }, title = item.title(), trackingName = item.name)
                            }
                        }
                    }
                    val pickerData = shown
                    val picker: @Composable (Modifier) -> Unit = { modifier ->
                        if (scope == StatisticsScope.PLAYER && pickerData != null) {
                            PlayerDropdownField(
                                names = pickerData.names,
                                selectedId = chosenSlotId ?: mySlotId ?: pickerData.slots.firstOrNull()?.id,
                                onChoose = { chosenSlotText = it.toString() },
                                modifier = modifier,
                            )
                        }
                    }
                    if (narrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
                            tabs(Modifier.fillMaxWidth())
                            picker(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            tabs(Modifier.weight(1f))
                            picker(Modifier.padding(start = 12.dp).width(240.dp))
                        }
                    }
                }
            }
            if (!finished) return@ScreenColumn
            when (scope) {
                StatisticsScope.PERSON -> person?.let { data ->
                    PlayerStatisticsPanel(
                        data.overall,
                        tournaments = data.tournaments.map { it.first },
                        rivalNames = data.rivalNames,
                    )
                } ?: Text(stringResource(Res.string.stats_unavailable))

                StatisticsScope.PERSON_TOURNAMENTS -> person?.let { data ->
                    PlayerTournamentsSection(data.tournaments, loadStandings = { ids -> presenter.standings(data.emaId, ids) })
                } ?: Text(stringResource(Res.string.stats_unavailable))

                StatisticsScope.TOURNAMENT -> shown?.let {
                    TournamentStatisticsHeader(it.tournament)
                    TournamentStatisticsPanel(
                        statistics = it.statistics,
                        names = it.names,
                        onOpenPlayer = { slot ->
                            chosenSlotText = slot.toString()
                            scope = StatisticsScope.PLAYER
                        },
                    )
                }

                StatisticsScope.TABLE -> Unit

                StatisticsScope.PLAYER -> shown?.let { data ->
                    val slotId = chosenSlotId ?: mySlotId ?: data.slots.firstOrNull()?.id
                    data.statistics.players.firstOrNull { it.playerId == slotId }
                        ?.let { stats ->
                            PlayerStatisticsPanel(
                                stats,
                                header = data.profiles[slotId]?.let { profile -> { PlayerStatisticsHeader(profile) } },
                                tournaments = listOf(data.tournament),
                                rivalNames = data.rivalNames,
                            )
                        }
                }
            }
        }
    }
}

/**
 * The statistics tab of the tournament screen: the statistics of the whole tournament.
 * A tap on a player opens that player in the Players tab of the tournament ([onOpenPlayer]).
 */
@Composable
fun TournamentStatisticsTab(
    tournamentId: String,
    onOpenPlayer: (Int) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    val polled = rememberTournamentStatistics(presenter, tournamentId)
    val data = polled.data
    val message = polled.message
    ReportLoading(data == null && message == null, onLoadingChange)
    message?.let { AppErrorDialog(it, onDismiss = polled.dismissMessage) }
    val loaded = data ?: return
    val roundIds = loaded.tournament.roundIds()
    var round by rememberSaveable(tournamentId) { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        // The chips of the rounds stay above the statistics while they scroll. Every card shows the chosen round.
        TournamentRoundChips(
            loaded.statistics,
            roundIds,
            round,
            { round = it },
            Modifier.padding(top = MtsTheme.spacing.md),
            edge = MtsTheme.spacing.lg,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberPersistentScrollState("statistics-TOURNAMENT"))
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
        ) {
            TournamentStatisticsPanel(
                statistics = loaded.statistics,
                names = loaded.names,
                onOpenPlayer = onOpenPlayer,
                roundIds = roundIds,
                selectedRound = round,
                onSelectRound = { round = it },
            )
        }
    }
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
        Modifier.fillMaxSize().verticalScroll(rememberPersistentScrollState("person-statistics-$emaId"))
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
        Modifier.fillMaxSize().verticalScroll(rememberPersistentScrollState("player-statistics-$slotId"))
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
    ) {
        message?.let { AppErrorDialog(it, onDismiss = polled.dismissMessage) }
        top?.invoke()
        val loaded = data ?: return@Column
        val stats = loaded.statistics.players.firstOrNull { it.playerId == slotId }
        val standing: (@Composable (Modifier) -> Unit)? = ranking?.let { { modifier -> PlayerStandingCard(it.position, it.points, it.score, modifier) } }
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
 * The statistics tab of the tournaments screen: the statistics of one player, with the player picker on top.
 * With an account the own player is chosen at the start. Without one, the screen asks to choose a player.
 * Anyone can look at the statistics of any player.
 */
@Composable
fun MyStatisticsTab(onLoadingChange: (Boolean) -> Unit = {}) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    val session = koinInject<PlayerSessionPresenter>()
    // The player that the user chose. Without a choice, the own player shows.
    var chosenEma by rememberPersistentText("statistics-player", null)
    var myEma by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var playerLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        myEma = session.myEmaId()
        ready = true
    }
    ReportLoading(!ready || playerLoading, onLoadingChange)
    if (!ready) return
    ChosenPlayerStatistics(
        presenter = presenter,
        emaId = chosenEma ?: myEma,
        onEmaIdChange = { chosenEma = it },
        onLoadingChange = { playerLoading = it },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The inside tabs of the chosen player. */
private enum class PlayerView(val title: StringResource, val icon: ImageVector) {
    OVERALL(Res.string.scope_overall, StatisticsIcon),
    BY_TOURNAMENTS(Res.string.scope_by_tournaments, Icons.Default.EmojiEvents),
}

/**
 * The statistics of the chosen player, with a picker of the players on top.
 * The card of the chosen player has a button to choose another one. The server gives only the results
 * of the tournaments that the account can see.
 */
@Composable
private fun ChosenPlayerStatistics(
    presenter: PlayerStatisticsPresenter,
    emaId: String?,
    onEmaIdChange: (String) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var players by remember { mutableStateOf<List<Player>>(emptyList()) }
    var data by remember(emaId) { mutableStateOf<PersonStatisticsData?>(null) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var changing by remember { mutableStateOf(false) }
    var viewName by rememberPersistentText("others-view", PlayerView.OVERALL.name)
    val view = PlayerView.entries.firstOrNull { it.name == viewName } ?: PlayerView.OVERALL
    LaunchedEffect(Unit) {
        when (val result = presenter.players()) {
            is AppResult.Success -> players = result.value
            is AppResult.Failure -> message = result.error.toUiText()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(emaId) {
        val id = emaId ?: return@LaunchedEffect
        onLoadingChange(true)
        // Checked again at the slow pace of the lists while the player is on screen.
        var first = true
        pollForChanges(
            isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
            forceFirst = false,
            load = { mode -> presenter.person(id, mode, track = first) },
            nextDelay = { PollSchedule.LIST_IDLE },
        ) { result ->
            when (result) {
                is AppResult.Success -> {
                    data = result.value
                    message = null
                }
                is AppResult.Failure -> if (first) message = result.error.toUiText()
            }
            if (first) onLoadingChange(false)
            first = false
        }
    }
    DisposableEffect(Unit) { onDispose { onLoadingChange(false) } }
    val chosen = players.firstOrNull { it.emaId == emaId }
    val profile = data?.profile ?: chosen?.let { PlayerProfile(it.displayName, it.emaId, it.country, it.photoUrl) }

    val scroll = rememberScrollState()
    // A rival opened from a list starts at the top.
    ResetScrollOnChange(scroll, emaId)

    Column(modifier) {
        if (profile == null || changing) {
            val options = remember(players) { players.sortedBy { it.displayName }.map { it.emaId to "${it.displayName} (${it.emaId})" } }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectField(
                        stringResource(Res.string.stats_player_field),
                        options,
                        emaId,
                        {
                            onEmaIdChange(it)
                            changing = false
                        },
                        Modifier.weight(1f),
                    )
                    if (profile != null) TextButton(onClick = { changing = false }) { Text(stringResource(Res.string.action_cancel)) }
                }
            }
        }
        if (emaId != null) {
            // The bar with the player and the tabs hide and show with the tab rows above them.
            HideOnScrollTabs(emaId) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (profile != null && !changing) {
                        ChosenPlayerBar(profile) { changing = true }
                        // The same line that a tab row has at its bottom, so the bar and the tabs under it do not merge.
                        HorizontalDivider(Modifier.widthIn(max = PlayerTabsMaxWidth).fillMaxWidth())
                    }
                    PlayerViewTabs(view) { viewName = it.name }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = 12.dp)) {
            message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
            if (emaId == null) {
                AppEmptyState(
                    icon = Icons.Default.PersonSearch,
                    title = stringResource(Res.string.stats_choose_player_title),
                    message = stringResource(Res.string.stats_choose_player_message),
                )
                return@Column
            }
            val loaded = data ?: return@Column
            Column(
                Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (view) {
                    PlayerView.BY_TOURNAMENTS -> PlayerTournamentsSection(
                        loaded.tournaments,
                        loadStandings = { ids -> presenter.standings(loaded.emaId, ids) },
                    )
                    PlayerView.OVERALL -> PlayerStatisticsPanel(
                        loaded.overall,
                        tournaments = loaded.tournaments.map { it.first },
                        rivalNames = loaded.rivalNames,
                        onOpenRival = onEmaIdChange,
                    )
                }
            }
        }
    }
}

/** The bar with the flag and the name of the player, and the button to choose another player. The name never hides the button. */
@Composable
private fun ChosenPlayerBar(profile: PlayerProfile, onChange: () -> Unit) {
    Row(
        Modifier.widthIn(max = PlayerTabsMaxWidth).fillMaxWidth().height(PlayerBarHeight).background(mtsTabRowColor()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            // A player of the EMA list without country is a guest: the flag is the globe.
            if (profile.country.isNotBlank() || profile.emaId.isNotBlank()) {
                CountryFlag(profile.country, width = 24.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                profile.name,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium.inTextFont(),
                fontWeight = FontWeight.Bold,
                color = mtsTabContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onChange,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = mtsTabContentColor()),
            border = BorderStroke(1.dp, mtsTabContentColor()),
        ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.action_change), maxLines = 1, softWrap = false)
        }
    }
}

/** The height of the bar with the flag and the name of the player. */
private val PlayerBarHeight = 44.dp

/** The maximum width of the header and the tab rows of the chosen player. It is the width of the tab rows above. */
private val PlayerTabsMaxWidth = 720.dp

/** The inside tabs of the chosen player. */
@Composable
private fun PlayerViewTabs(view: PlayerView, onChoose: (PlayerView) -> Unit) {
    val views = PlayerView.entries
    MtsTabRow(
        selectedTabIndex = views.indexOf(view),
        modifier = Modifier.widthIn(max = PlayerTabsMaxWidth),
    ) {
        views.forEach { item ->
            CompactTab(
                selected = item == view,
                onClick = { onChoose(item) },
                title = stringResource(item.title),
                trackingName = item.name,
                icon = { Icon(item.icon, contentDescription = null, Modifier.size(20.dp)) },
            )
        }
    }
}

/** Below this width the scope tabs and the player picker of the Statistics screen go on two rows. */
private val ScopeRowMinWidth = 600.dp

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
