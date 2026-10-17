package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.SearchableDropdownField
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.model.Player
import androidx.compose.material3.TextButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.EmojiEvents
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerDropdownField
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerTournamentsSection
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.TableStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val hasTable = hasTournament && route.roundId != null && route.tableId != null
    val scopes = buildList {
        add(StatisticsScope.PERSON)
        add(StatisticsScope.PERSON_TOURNAMENTS)
        if (hasTournament) add(StatisticsScope.TOURNAMENT)
        if (hasTable) add(StatisticsScope.TABLE)
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
    var message by remember { mutableStateOf<String?>(null) }
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
                    is AppResult.Failure -> message = result.error.toUiMessage()
                }
                tournamentFinished = true
            }
            when (val result = personRequest.await()) {
                is AppResult.Success -> {
                    person = result.value
                    mySlotId = tournament?.slots.orEmpty().firstOrNull { it.assignedEmaId == result.value.emaId }?.id
                }
                is AppResult.Failure -> if (scope == StatisticsScope.PERSON || scope == StatisticsScope.PERSON_TOURNAMENTS || route.tournamentId == null) {
                    message = result.error.toUiMessage()
                }
            }
            personFinished = true
        }
    }

    val shown = tournament
    val title = when (scope) {
        StatisticsScope.PERSON, StatisticsScope.PERSON_TOURNAMENTS -> "My statistics"
        StatisticsScope.TOURNAMENT -> shown?.tournament?.name ?: "Tournament"
        StatisticsScope.TABLE -> "Round ${route.roundId} - Table ${route.tableId}"
        StatisticsScope.PLAYER -> shown?.names?.get(chosenSlotId ?: mySlotId) ?: "Player"
    }

    AppScaffold(
        title = title,
        subtitle = "Statistics",
        isLoading = !finished && message == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = { ThemeAction() },
    ) {
        ScreenColumn(
            // The statistics sections use the full width and flow into columns.
            maxWidth = Dp.Infinity,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            scrollable = true,
        ) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                        CompactTab(selected = item == scope, onClick = { scope = item }, title = item.title)
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
                    )
                } ?: Text("Your statistics are not available yet.")

                StatisticsScope.PERSON_TOURNAMENTS -> person?.let { data ->
                    PlayerTournamentsSection(data.tournaments, loadStandings = { ids -> presenter.standings(data.emaId, ids) })
                } ?: Text("Your statistics are not available yet.")

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

                StatisticsScope.TABLE -> shown?.statistics?.tables
                    ?.firstOrNull { it.roundId == route.roundId && it.tableId == route.tableId }
                    ?.let { TableStatisticsPanel(it, shown.names) }
                    ?: Text("This table has no statistics yet.")

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
 * The statistics tab of the tournament screen. It shows the tournament view of the statistics.
 * A tap on a player opens the statistics of that player.
 */
@Composable
fun TournamentStatisticsTab(
    navController: NavHostController,
    tournamentId: String,
    mySlotId: Int?,
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val presenter = koinInject<PlayerStatisticsPresenter>()
    var data by remember(tournamentId) { mutableStateOf<TournamentStatisticsData?>(null) }
    var message by remember(tournamentId) { mutableStateOf<String?>(null) }
    LaunchedEffect(tournamentId) {
        when (val result = presenter.tournament(tournamentId)) {
            is AppResult.Success -> data = result.value
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
    }
    ReportLoading(data == null && message == null, onLoadingChange)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberPersistentScrollState("statistics")),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val loaded = data
        if (loaded == null) return@Column
        val openPlayer = { slotId: Int ->
            navController.navigate(StatisticsRoute(StatisticsScope.PLAYER.name, tournamentId = tournamentId, slotId = slotId))
        }
        if (mySlotId != null) {
            StatSection("Me") {
                StatRow("My statistics in this tournament", "Open", onClick = { openPlayer(mySlotId) })
            }
        }
        TournamentStatisticsPanel(
            statistics = loaded.statistics,
            names = loaded.names,
            onOpenPlayer = openPlayer,
        )
    }
}

/** The tabs inside the statistics tab of the tournaments screen. */
private enum class MyStatisticsKind(val title: String, val icon: ImageVector) {
    ME("Me", Icons.Default.Person),
    MY_TOURNAMENTS("My tournaments", Icons.Default.EmojiEvents),
    OTHERS("Others", Icons.Default.Group),
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
    var message by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var othersLoading by remember { mutableStateOf(false) }
    val tabs = MyStatisticsKind.entries
    var tabName by rememberPersistentText("my-statistics-tab", MyStatisticsKind.ME.name)
    val tab = tabs.firstOrNull { it.name == tabName } ?: MyStatisticsKind.ME
    var otherEma by rememberPersistentText("others-player", null)
    LaunchedEffect(Unit) {
        when (val result = presenter.me()) {
            is AppResult.Success -> person = result.value
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
        finished = true
    }
    ReportLoading(!finished || othersLoading, onLoadingChange)
    // A rival opens in the "Others" tab.
    val openRival: (String) -> Unit = { ema -> otherEma = ema; tabName = MyStatisticsKind.OTHERS.name }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The tab row is as wide as its tabs and sits in the middle, like in the admin app.
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
                        title = item.title,
                        icon = { Icon(item.icon, contentDescription = null, Modifier.size(20.dp)) },
                    )
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
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!finished) return@Column
            val data = person
            if (data == null) {
                if (message == null) Text("Your statistics are not available yet.")
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
                )
            }
        }
    }
}

/** The inside tabs of "Others". */
private enum class OthersView(val title: String, val icon: ImageVector) {
    OVERALL("Overall", StatisticsIcon),
    BY_TOURNAMENTS("By tournaments", Icons.Default.EmojiEvents),
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
    var message by remember { mutableStateOf<String?>(null) }
    var changing by remember { mutableStateOf(false) }
    var viewName by rememberPersistentText("others-view", OthersView.OVERALL.name)
    val view = OthersView.entries.firstOrNull { it.name == viewName } ?: OthersView.OVERALL
    LaunchedEffect(Unit) {
        when (val result = presenter.players()) {
            is AppResult.Success -> players = result.value
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
    }
    LaunchedEffect(emaId) {
        val id = emaId ?: return@LaunchedEffect
        onLoadingChange(true)
        when (val result = presenter.person(id)) {
            is AppResult.Success -> { data = result.value; message = null }
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
        onLoadingChange(false)
    }
    DisposableEffect(Unit) { onDispose { onLoadingChange(false) } }
    val chosen = players.firstOrNull { it.emaId == emaId }
    val profile = data?.profile ?: chosen?.let { PlayerProfile(it.displayName, it.emaId, it.country, it.photoUrl) }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (profile != null && !changing) {
                PlayerStatisticsHeader(
                    profile,
                    fillWidth = false,
                    action = {
                        OutlinedButton(onClick = { changing = true }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Change")
                        }
                    },
                )
            } else {
                val options = remember(players) { players.sortedBy { it.displayName }.map { it.emaId to "${it.displayName} (${it.emaId})" } }
                Row(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchableDropdownField(
                        "Player (name or EMA number)", options, emaId,
                        { onEmaIdChange(it); changing = false },
                        Modifier.weight(1f),
                    )
                    if (profile != null) TextButton(onClick = { changing = false }) { Text("Cancel") }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (emaId == null) {
            StatisticsEmptyState(
                icon = Icons.Default.PersonSearch,
                title = "Choose a player",
                message = "Use the field above to search a player by name or EMA number. " +
                    "You see the results of that player in the tournaments that you can see.",
            )
            return@Column
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val views = OthersView.entries
            PrimaryTabRow(
                selectedTabIndex = views.indexOf(view),
                containerColor = tabRowColor(), contentColor = TabContentColor,
                indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(views.indexOf(view)), color = TabContentColor) },
                modifier = Modifier.widthIn(max = StatisticsTabWidth * views.size),
            ) {
                views.forEach { item ->
                    CompactTab(
                        selected = item == view,
                        onClick = { viewName = item.name },
                        title = item.title,
                        icon = { Icon(item.icon, contentDescription = null, Modifier.size(20.dp)) },
                    )
                }
            }
        }
        val loaded = data ?: return@Column
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
            )
        }
    }
}
