package com.etologic.mahjongtournamentsuite.player

import mahjongtournamentsuite.playerapp.generated.resources.scope_players
import mahjongtournamentsuite.playerapp.generated.resources.scope_me
import mahjongtournamentsuite.playerapp.generated.resources.scope_my_tournaments
import mahjongtournamentsuite.playerapp.generated.resources.scope_others
import mahjongtournamentsuite.playerapp.generated.resources.scope_overall
import mahjongtournamentsuite.playerapp.generated.resources.scope_by_tournaments
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
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
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerTournamentsSection
import com.etologic.mahjongtournamentsuite.presentation.components.ResetScrollOnChange
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SearchableDropdownField
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.components.title
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.action_cancel
import mahjongtournamentsuite.playerapp.generated.resources.action_change
import mahjongtournamentsuite.playerapp.generated.resources.country_n
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
    val hasTournament = route.tournamentId != null
    val scopes = buildList {
        add(StatisticsScope.PERSON)
        add(StatisticsScope.PERSON_TOURNAMENTS)
        if (hasTournament) add(StatisticsScope.TOURNAMENT)
        if (hasTournament) add(StatisticsScope.PLAYER)
    }
    // A page refresh restores the scope that the player chose.
    var scopeName by rememberPersistentText("scope", route.scope)
    var scope by remember {
        mutableStateOf(scopes.firstOrNull { it.name == scopeName } ?: StatisticsScope.PERSON)
    }
    LaunchedEffect(scope) { scopeName = scope.name }
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

    // The tournament shows as soon as it is ready. "My statistics" loads at the same time.
    LaunchedEffect(route.tournamentId) {
        coroutineScope {
            val personRequest = async { presenter.me() }
            route.tournamentId?.let { id ->
                when (val result = presenter.tournament(id)) {
                    is AppResult.Success -> tournament = result.value
                    is AppResult.Failure -> message = result.error.toUiText()
                }
                tournamentFinished = true
            }
            when (val result = personRequest.await()) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            scrollable = true,
        ) {
            // Another view or another player starts at the top.
            ResetScrollOnChange(LocalScreenScroll.current?.state, scope to chosenSlotText)
            message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
            if (scopes.size > 1) {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PrimaryScrollableTabRow(
                    modifier = Modifier.weight(1f),
                    selectedTabIndex = scopes.indexOf(scope),
                    edgePadding = 0.dp,
                    containerColor = tabRowColor(), contentColor = TabContentColor,
                    indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(scopes.indexOf(scope)), color = TabContentColor) },
                ) {
                    scopes.forEach { item ->
                        CompactTab(selected = item == scope, onClick = { scope = item }, title = item.title())
                    }
                }
                val pickerData = shown
                if (scope == StatisticsScope.PLAYER && pickerData != null) {
                    PlayerDropdownField(
                        names = pickerData.names,
                        selectedId = chosenSlotId ?: mySlotId ?: pickerData.slots.firstOrNull()?.id,
                        onChoose = { chosenSlotText = it.toString() },
                        modifier = Modifier.padding(start = 12.dp).width(240.dp),
                    )
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
                        loadStandings = { ids -> presenter.standings(data.emaId, ids) },
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

/** The tabs inside the statistics tab of a tournament. */
private enum class TournamentStatisticsKind(val title: StringResource, val icon: ImageVector) {
    TOURNAMENT(Res.string.scope_tournament, Icons.Default.EmojiEvents),
    PLAYERS(Res.string.scope_players, Icons.Default.Group),
}

/**
 * The statistics tab of the tournament screen. It has two tabs inside:
 * "Tournament" shows the statistics of the tournament, and "Players" shows the statistics of one player.
 * "Players" starts with the signed-in player. A tap on a player in "Tournament" opens that player in "Players".
 */
@Composable
fun TournamentStatisticsTab(
    tournamentId: String,
    mySlotId: Int?,
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    var data by remember(tournamentId) { mutableStateOf<TournamentStatisticsData?>(null) }
    var message by remember(tournamentId) { mutableStateOf<UiText?>(null) }
    val tabs = TournamentStatisticsKind.entries
    var tabName by rememberPersistentText("tournament-statistics-tab", TournamentStatisticsKind.TOURNAMENT.name)
    val tab = tabs.firstOrNull { it.name == tabName } ?: TournamentStatisticsKind.TOURNAMENT
    var chosenSlotText by rememberPersistentText("tournament-statistics-player", null)
    LaunchedEffect(tournamentId) {
        when (val result = presenter.tournament(tournamentId)) {
            is AppResult.Success -> data = result.value
            is AppResult.Failure -> message = result.error.toUiText()
        }
    }
    ReportLoading(data == null && message == null, onLoadingChange)
    // The scroll starts at the line under the tabs. The padding is inside the scroll, so the content moves under that line.
    Column(Modifier.fillMaxSize()) {
        HideOnScrollTabs(tab) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PrimaryTabRow(
                    selectedTabIndex = tabs.indexOf(tab),
                    containerColor = tabRowColor(), contentColor = TabContentColor,
                    indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tabs.indexOf(tab)), color = TabContentColor) },
                    modifier = Modifier.widthIn(max = StatisticsTabWidth * tabs.size),
                ) {
                    tabs.forEach { item ->
                        CompactTab(
                            selected = item == tab,
                            onClick = { tabName = item.name },
                            title = stringResource(item.title),
                            icon = { Icon(item.icon, contentDescription = null, Modifier.size(20.dp)) },
                        )
                    }
                }
            }
        }
        val tabScroll = rememberPersistentScrollState("statistics-" + tab.name)
        // A player opened from a list starts at the top of the Players tab.
        ResetScrollOnChange(tabScroll, chosenSlotText)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(tabScroll)
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
            val loaded = data ?: return@Column
            when (tab) {
                TournamentStatisticsKind.TOURNAMENT -> TournamentStatisticsPanel(
                    statistics = loaded.statistics,
                    names = loaded.names,
                    onOpenPlayer = { slotId ->
                        chosenSlotText = slotId.toString()
                        tabName = TournamentStatisticsKind.PLAYERS.name
                    },
                )
                TournamentStatisticsKind.PLAYERS -> {
                    val slotId = chosenSlotText?.toIntOrNull() ?: mySlotId ?: loaded.slots.firstOrNull()?.id
                    PlayerDropdownField(
                        names = loaded.names,
                        selectedId = slotId,
                        onChoose = { chosenSlotText = it.toString() },
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                    )
                    val stats = loaded.statistics.players.firstOrNull { it.playerId == slotId }
                    if (stats == null) {
                        AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_player_empty_message))
                    } else {
                        PlayerStatisticsPanel(
                            stats,
                            header = loaded.profiles[slotId]?.let { profile -> { PlayerStatisticsHeader(profile) } },
                            tournaments = listOf(loaded.tournament),
                            rivalNames = loaded.rivalNames,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The statistics of one player in one tournament. The player screen shows it in its Stats tab.
 * [onLoadingChange] tells the screen when the data loads, so the progress bar stays until the content shows.
 */
@Composable
fun PlayerSlotStatisticsTab(
    tournamentId: String,
    slotId: Int,
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    var data by remember(tournamentId) { mutableStateOf<TournamentStatisticsData?>(null) }
    var message by remember(tournamentId) { mutableStateOf<UiText?>(null) }
    LaunchedEffect(tournamentId) {
        when (val result = presenter.tournament(tournamentId)) {
            is AppResult.Success -> data = result.value
            is AppResult.Failure -> message = result.error.toUiText()
        }
    }
    ReportLoading(data == null && message == null, onLoadingChange)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberPersistentScrollState("player-statistics-$slotId"))
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
        val loaded = data ?: return@Column
        val stats = loaded.statistics.players.firstOrNull { it.playerId == slotId }
        if (stats == null) {
            AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_player_empty_message))
        } else {
            PlayerStatisticsPanel(
                stats,
                header = loaded.profiles[slotId]?.let { profile -> { PlayerStatisticsHeader(profile) } },
                tournaments = listOf(loaded.tournament),
                rivalNames = loaded.rivalNames,
            )
        }
    }
}

/** The tabs inside the statistics tab of the tournaments screen. */
private enum class MyStatisticsKind(val title: StringResource, val icon: ImageVector) {
    ME(Res.string.scope_me, Icons.Default.Person),
    MY_TOURNAMENTS(Res.string.scope_my_tournaments, Icons.Default.EmojiEvents),
    OTHERS(Res.string.scope_others, Icons.Default.Group),
}

/** The width of one tab on a wide screen. */
private val StatisticsTabWidth = 260.dp

/**
 * The statistics tab of the tournaments screen. It has three tabs inside:
 * "Me" has the cards of the whole history, "My tournaments" has one card for each tournament,
 * and "Others" shows the same for another player that the player chooses.
 */
@Composable
fun MyStatisticsTab(onLoadingChange: (Boolean) -> Unit = {}) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    var person by remember { mutableStateOf<PersonStatisticsData?>(null) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var finished by remember { mutableStateOf(false) }
    var othersLoading by remember { mutableStateOf(false) }
    val tabs = MyStatisticsKind.entries
    var tabName by rememberPersistentText("my-statistics-tab", MyStatisticsKind.ME.name)
    val tab = tabs.firstOrNull { it.name == tabName } ?: MyStatisticsKind.ME
    var otherEma by rememberPersistentText("others-player", null)
    LaunchedEffect(Unit) {
        when (val result = presenter.me()) {
            is AppResult.Success -> person = result.value
            is AppResult.Failure -> message = result.error.toUiText()
        }
        finished = true
    }
    ReportLoading(!finished || othersLoading, onLoadingChange)
    // A rival opens in the "Others" tab.
    val openRival: (String) -> Unit = { ema -> otherEma = ema; tabName = MyStatisticsKind.OTHERS.name }
    Column(Modifier.fillMaxSize()) {
        HideOnScrollTabs(tab) {
            // The tab row touches the top tab row and has the same width. The content under it has its own padding.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PrimaryTabRow(
                    selectedTabIndex = tabs.indexOf(tab),
                    containerColor = tabRowColor(), contentColor = TabContentColor,
                    indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tabs.indexOf(tab)), color = TabContentColor) },
                    modifier = Modifier.widthIn(max = 720.dp),
                ) {
                    tabs.forEach { item ->
                        CompactTab(
                            selected = item == tab,
                            onClick = { tabName = item.name },
                            title = stringResource(item.title),
                            icon = { Icon(item.icon, contentDescription = null, Modifier.size(20.dp)) },
                        )
                    }
                }
            }
        }
        if (tab == MyStatisticsKind.OTHERS) {
            OthersStatistics(
                presenter = presenter,
                emaId = otherEma,
                onEmaIdChange = { otherEma = it },
                onLoadingChange = { othersLoading = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            return@Column
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
            if (!finished) return@Column
            val data = person
            if (data == null) {
                if (message == null) Text(stringResource(Res.string.stats_unavailable))
                return@Column
            }
            when (tab) {
                MyStatisticsKind.MY_TOURNAMENTS ->
                    PlayerTournamentsSection(data.tournaments, loadStandings = { ids -> presenter.standings(data.emaId, ids) })
                else -> PlayerStatisticsPanel(
                    data.overall,
                    tournaments = data.tournaments.map { it.first },
                    rivalNames = data.rivalNames,
                    onOpenRival = openRival,
                    loadStandings = { ids -> presenter.standings(data.emaId, ids) },
                )
            }
        }
    }
}

/** The inside tabs of "Others". */
private enum class OthersView(val title: StringResource, val icon: ImageVector) {
    OVERALL(Res.string.scope_overall, StatisticsIcon),
    BY_TOURNAMENTS(Res.string.scope_by_tournaments, Icons.Default.EmojiEvents),
}

/**
 * The statistics of another player. The player chooses from the people that the signed-in player plays with.
 * The card of the chosen player has a button to choose another one. The server gives only the results
 * of the tournaments that the account can see.
 */
@Composable
private fun OthersStatistics(
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
    var viewName by rememberPersistentText("others-view", OthersView.OVERALL.name)
    val view = OthersView.entries.firstOrNull { it.name == viewName } ?: OthersView.OVERALL
    LaunchedEffect(Unit) {
        when (val result = presenter.players()) {
            is AppResult.Success -> players = result.value
            is AppResult.Failure -> message = result.error.toUiText()
        }
    }
    LaunchedEffect(emaId) {
        val id = emaId ?: return@LaunchedEffect
        onLoadingChange(true)
        when (val result = presenter.person(id)) {
            is AppResult.Success -> { data = result.value; message = null }
            is AppResult.Failure -> message = result.error.toUiText()
        }
        onLoadingChange(false)
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
                    SearchableDropdownField(
                        stringResource(Res.string.stats_player_field), options, emaId,
                        { onEmaIdChange(it); changing = false },
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
                    if (profile != null && !changing) OthersPlayerBar(profile) { changing = true }
                    OthersViewTabs(view) { viewName = it.name }
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
                    OthersView.BY_TOURNAMENTS -> PlayerTournamentsSection(
                        loaded.tournaments,
                        loadStandings = { ids -> presenter.standings(loaded.emaId, ids, own = false) },
                    )
                    OthersView.OVERALL -> PlayerStatisticsPanel(
                        loaded.overall,
                        tournaments = loaded.tournaments.map { it.first },
                        rivalNames = loaded.rivalNames,
                        onOpenRival = onEmaIdChange,
                        loadStandings = { ids -> presenter.standings(loaded.emaId, ids, own = false) },
                    )
                }
            }
        }
    }
}

/** The bar with the flag and the name of the player, and the button to choose another player. The name never hides the button. */
@Composable
private fun OthersPlayerBar(profile: PlayerProfile, onChange: () -> Unit) {
    Row(
        Modifier.widthIn(max = OthersMaxWidth).fillMaxWidth().height(OthersBarHeight).background(tabRowColor()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (profile.country.isNotBlank()) {
                CountryFlag(profile.country, width = 24.dp, contentDescription = stringResource(Res.string.country_n, profile.country))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                profile.name, Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium, color = TabContentColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onChange,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TabContentColor),
            border = BorderStroke(1.dp, TabContentColor),
        ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.action_change), maxLines = 1, softWrap = false)
        }
    }
}

/** The height of the bar with the flag and the name of the player. */
private val OthersBarHeight = 44.dp

/** The maximum width of the header and the tab rows of "Others". It is the width of the tab rows above. */
private val OthersMaxWidth = 720.dp

/** The inside tabs of "Others". */
@Composable
private fun OthersViewTabs(view: OthersView, onChoose: (OthersView) -> Unit) {
    val views = OthersView.entries
    PrimaryTabRow(
        selectedTabIndex = views.indexOf(view),
        containerColor = tabRowColor(), contentColor = TabContentColor,
        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(views.indexOf(view)), color = TabContentColor) },
        modifier = Modifier.widthIn(max = OthersMaxWidth),
    ) {
        views.forEach { item ->
            CompactTab(
                selected = item == view,
                onClick = { onChoose(item) },
                title = stringResource(item.title),
                icon = { Icon(item.icon, contentDescription = null, Modifier.size(20.dp)) },
            )
        }
    }
}
