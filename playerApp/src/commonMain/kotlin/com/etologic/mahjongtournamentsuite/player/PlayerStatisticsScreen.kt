package com.etologic.mahjongtournamentsuite.player

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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import mahjongtournamentsuite.playerapp.generated.resources.country_n
import mahjongtournamentsuite.playerapp.generated.resources.scope_by_tournaments
import mahjongtournamentsuite.playerapp.generated.resources.scope_me
import mahjongtournamentsuite.playerapp.generated.resources.scope_my_tournaments
import mahjongtournamentsuite.playerapp.generated.resources.scope_others
import mahjongtournamentsuite.playerapp.generated.resources.scope_overall
import mahjongtournamentsuite.playerapp.generated.resources.scope_player
import mahjongtournamentsuite.playerapp.generated.resources.scope_players
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
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentRoundsCard

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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MtsScrollableTabRow(
                        modifier = Modifier.weight(1f),
                        selectedTabIndex = scopes.indexOf(scope),
                    ) {
                        scopes.forEach { item ->
                            CompactTab(selected = item == scope, onClick = { scope = item }, title = item.title(), trackingName = item.name)
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
        Modifier.fillMaxSize().verticalScroll(rememberPersistentScrollState("statistics-TOURNAMENT"))
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
    ) {
        message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
        val loaded = data ?: return@Column
        TournamentRoundsCard(loaded.statistics.byRound(loaded.tournament.roundIds()), loaded.names, Modifier.fillMaxWidth(), onOpenPlayer)
        TournamentStatisticsPanel(
            statistics = loaded.statistics,
            names = loaded.names,
            onOpenPlayer = onOpenPlayer,
        )
    }
}

/**
 * The statistics of one player in one tournament. The player in the Players tab shows it in its Stats tab.
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
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
    ) {
        message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
        val loaded = data ?: return@Column
        val stats = loaded.statistics.players.firstOrNull { it.playerId == slotId }
        val standing: (@Composable (Modifier) -> Unit)? = ranking?.let { { modifier -> PlayerStandingCard(it.position, it.points, it.score, modifier) } }
        if (stats == null) {
            standing?.invoke(Modifier.fillMaxWidth())
            AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_player_empty_message))
        } else {
            // The header, the place and the rounds go over the whole width, then the other cards.
            loaded.profiles[slotId]?.let { PlayerStatisticsHeader(it) }
            standing?.invoke(Modifier.fillMaxWidth())
            PlayerRoundsCard(stats.byRound(loaded.tournament.roundIds()), places, playerCount, Modifier.fillMaxWidth())
            PlayerStatisticsPanel(
                stats,
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

/**
 * The statistics tab of the tournaments screen. It has three tabs inside:
 * "Me" has the cards of the whole history, "My tournaments" has one card for each tournament,
 * and "Others" shows the same for another player that the player chooses.
 */
@Composable
fun MyStatisticsTab(onLoadingChange: (Boolean) -> Unit = {}) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    val session = koinInject<PlayerSessionPresenter>()
    var person by remember { mutableStateOf<PersonStatisticsData?>(null) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var finished by remember { mutableStateOf(false) }
    var othersLoading by remember { mutableStateOf(false) }
    // Without an account only "Others" is there: anyone can look at the statistics of any player.
    var signedIn by remember { mutableStateOf(true) }
    val tabs = if (signedIn) MyStatisticsKind.entries else listOf(MyStatisticsKind.OTHERS)
    var tabName by rememberPersistentText("my-statistics-tab", MyStatisticsKind.ME.name)
    val tab = tabs.firstOrNull { it.name == tabName } ?: tabs.first()
    var otherEma by rememberPersistentText("others-player", null)
    LaunchedEffect(Unit) {
        signedIn = session.hasSession()
        if (!signedIn) {
            finished = true
            return@LaunchedEffect
        }
        when (val result = presenter.me()) {
            is AppResult.Success -> person = result.value
            is AppResult.Failure -> message = result.error.toUiText()
        }
        finished = true
    }
    ReportLoading(!finished || othersLoading, onLoadingChange)
    // A rival opens in the "Others" tab.
    val openRival: (String) -> Unit = { ema ->
        otherEma = ema
        tabName = MyStatisticsKind.OTHERS.name
    }
    Column(Modifier.fillMaxSize()) {
        HideOnScrollTabs(tab) {
            // The tab row touches the top tab row and has the same width. The content under it has its own padding.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MtsTabRow(
                    selectedTabIndex = tabs.indexOf(tab),
                    modifier = Modifier.widthIn(max = 720.dp),
                ) {
                    tabs.forEach { item ->
                        CompactTab(
                            selected = item == tab,
                            onClick = { tabName = item.name },
                            // Without an account there is no "Me", so the other players are just the players.
                            title = stringResource(if (item == MyStatisticsKind.OTHERS && !signedIn) Res.string.scope_players else item.title),
                            trackingName = item.name,
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
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
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
            is AppResult.Success -> {
                data = result.value
                message = null
            }
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
                        loadStandings = { ids -> presenter.standings(loaded.emaId, ids) },
                    )
                    OthersView.OVERALL -> PlayerStatisticsPanel(
                        loaded.overall,
                        tournaments = loaded.tournaments.map { it.first },
                        rivalNames = loaded.rivalNames,
                        onOpenRival = onEmaIdChange,
                        loadStandings = { ids -> presenter.standings(loaded.emaId, ids) },
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
        Modifier.widthIn(max = OthersMaxWidth).fillMaxWidth().height(OthersBarHeight).background(mtsTabRowColor()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (profile.country.isNotBlank()) {
                CountryFlag(profile.country, width = 24.dp, contentDescription = stringResource(Res.string.country_n, profile.country))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                profile.name,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium.inTextFont(),
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
private val OthersBarHeight = 44.dp

/** The maximum width of the header and the tab rows of "Others". It is the width of the tab rows above. */
private val OthersMaxWidth = 720.dp

/** The inside tabs of "Others". */
@Composable
private fun OthersViewTabs(view: OthersView, onChoose: (OthersView) -> Unit) {
    val views = OthersView.entries
    MtsTabRow(
        selectedTabIndex = views.indexOf(view),
        modifier = Modifier.widthIn(max = OthersMaxWidth),
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
