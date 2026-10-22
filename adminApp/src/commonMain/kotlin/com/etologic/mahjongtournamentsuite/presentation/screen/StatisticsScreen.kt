package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.SwapHoriz
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.presentation.components.LocalScreenScroll
import com.etologic.mahjongtournamentsuite.presentation.components.ResetScrollOnChange
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.usecase.OverviewStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.CompactTab
import com.etologic.mahjongtournamentsuite.presentation.components.TabContentColor
import com.etologic.mahjongtournamentsuite.presentation.components.tabRowColor
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerDropdownField
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerTournamentsSection
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SearchableDropdownField
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.TableStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsHeader
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.presenter.StatisticsPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import org.koin.compose.koinInject

/** The width of one tab on a wide screen. */
private val StatisticsTabWidth = 260.dp

private fun StatisticsScope.adminIcon(): ImageVector = when (this) {
    StatisticsScope.TOURNAMENT -> Icons.Default.EmojiEvents
    StatisticsScope.TABLE -> Icons.Default.GridView
    else -> Icons.Default.Person
}

/** The names of the scopes on this screen. The player app uses other words for some of them. */
private fun StatisticsScope.adminTitle(): String = when (this) {
    StatisticsScope.TOURNAMENT -> "Tournaments"
    StatisticsScope.TABLE -> "Table"
    StatisticsScope.PLAYER, StatisticsScope.PERSON -> "Players"
    StatisticsScope.PERSON_TOURNAMENTS -> "By tournaments"
}

/**
 * Statistics for editors. The route gives the first scope and its context.
 * The tournaments scope shows all tournaments together until the editor picks one.
 */
@Composable
fun StatisticsScreen(navController: NavHostController, route: StatisticsRoute) {
    val presenter = koinInject<StatisticsPresenter>()
    var tournamentId by rememberPersistentText("tournament", route.tournamentId)
    val hasTable = tournamentId != null && tournamentId == route.tournamentId &&
        route.roundId != null && route.tableId != null
    // The main tabs. The player tab opens the overall scope of one player.
    val mainTabs = buildList {
        add(StatisticsScope.TOURNAMENT)
        if (hasTable) add(StatisticsScope.TABLE)
        add(StatisticsScope.PERSON)
    }
    // The scopes that the screen can show. The tournament player scope has no tab. A player of a tournament without EMA number opens it.
    val scopes = buildList {
        addAll(mainTabs)
        if (tournamentId != null) add(StatisticsScope.PLAYER)
        add(StatisticsScope.PERSON_TOURNAMENTS)
    }
    // A page refresh restores the scope that the editor chose.
    var scopeName by rememberPersistentText("scope", route.scope)
    var scope by remember {
        mutableStateOf(
            StatisticsScope.entries.firstOrNull { it.name == scopeName }
                ?.takeIf { it in scopes } ?: StatisticsScope.TOURNAMENT,
        )
    }
    LaunchedEffect(scope) { scopeName = scope.name }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var registry by remember { mutableStateOf<List<Player>>(emptyList()) }
    var tournamentData by remember { mutableStateOf<TournamentStatisticsData?>(null) }
    var overview by remember { mutableStateOf<OverviewStatisticsData?>(null) }
    var overviewAllowed by remember { mutableStateOf(true) }
    var person by remember { mutableStateOf<PersonStatisticsData?>(null) }
    var emaId by rememberPersistentText("person", route.emaId)
    var chosenSlotText by rememberPersistentText("slot", route.slotId?.toString())
    val chosenSlotId = chosenSlotText?.toIntOrNull()
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(tournamentId) {
        isLoading = true
        message = null
        (presenter.tournaments() as? AppResult.Success)?.let { tournaments = it.value }
        (presenter.registry() as? AppResult.Success)?.let { registry = it.value }
        val id = tournamentId
        if (id != null) {
            overview = null
            when (val result = presenter.tournament(id)) {
                is AppResult.Success -> tournamentData = result.value
                is AppResult.Failure -> message = result.error.toUiMessage()
            }
        } else {
            tournamentData = null
            overviewAllowed = presenter.canSeeOverview()
            if (overviewAllowed) {
                when (val result = presenter.overview()) {
                    is AppResult.Success -> overview = result.value
                    is AppResult.Failure -> message = result.error.toUiMessage()
                }
            }
        }
        isLoading = false
    }
    // The EMA number of a chosen slot gives the player of the person scope.
    val effectiveSlot = chosenSlotId
    val slotEma = tournamentData?.slots?.firstOrNull { it.id == effectiveSlot }?.assignedEmaId
    val personEma = emaId ?: slotEma
    LaunchedEffect(personEma) {
        person = null
        val id = personEma ?: return@LaunchedEffect
        when (val result = presenter.person(id)) {
            is AppResult.Success -> person = result.value
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
    }
    // A scope that is not available any more gives way to the first one.
    if (scope !in scopes) scope = mainTabs.first()
    val isPlayerScope = scope == StatisticsScope.PERSON || scope == StatisticsScope.PERSON_TOURNAMENTS || scope == StatisticsScope.PLAYER
    val selectedMainTab = if (isPlayerScope) StatisticsScope.PERSON else scope

    val names = registry.associate { it.emaId to it.displayName }
    val chosenTournament = tournaments.firstOrNull { it.id == tournamentId }
    // The name of the tournament or of the player moves to the top bar while the card with the name is scrolled out of view.
    val playerName = when (scope) {
        StatisticsScope.TOURNAMENT -> chosenTournament?.name
        StatisticsScope.PERSON, StatisticsScope.PERSON_TOURNAMENTS -> personEma?.let { names[it] }
        StatisticsScope.PLAYER -> tournamentData?.names?.get(effectiveSlot)
        else -> null
    }
    var nameScrolledOut by remember { mutableStateOf(false) }
    // The player card sits between the tab rows. The button on it opens the search field to choose another player.
    var changingPlayer by remember { mutableStateOf(false) }
    var changingTournament by remember { mutableStateOf(false) }
    val personProfile = person?.profile ?: registry.firstOrNull { it.emaId == personEma }?.let {
        PlayerProfile(it.displayName, it.emaId, it.country, it.photoUrl)
    }

    AppScaffold(
        title = "Statistics",
        subtitle = playerName.takeIf { nameScrolledOut },
        isLoading = isLoading,
        showBackgroundLogo = isLoading && tournamentData == null && overview == null,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = {
        },
    ) {
        ScreenColumn(
            // The statistics sections use the full width and flow into columns.
            maxWidth = Dp.Infinity,
            // The tab row touches the top bar, so the screen has no padding. The content under the tabs has its own.
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            scrollable = true,
        ) {
            // Another view or another player starts at the top.
            ResetScrollOnChange(LocalScreenScroll.current?.state, Triple(scope, emaId, chosenSlotText))
            // The tab row is as wide as its tabs, like in the player app, and sits in the middle.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PrimaryTabRow(
                    selectedTabIndex = mainTabs.indexOf(selectedMainTab),
                    containerColor = tabRowColor(), contentColor = TabContentColor,
                    indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(mainTabs.indexOf(selectedMainTab)), color = TabContentColor) },
                    modifier = Modifier.widthIn(max = StatisticsTabWidth * mainTabs.size),
                ) {
                    mainTabs.forEach { item ->
                        CompactTab(
                            selected = item == selectedMainTab,
                            onClick = { if (item != selectedMainTab) scope = item },
                            title = item.adminTitle(),
                            icon = { Icon(item.adminIcon(), contentDescription = null, Modifier.size(20.dp)) },
                        )
                    }
                }
            }
            // The selector sits between the tab rows.
            val pickerData = tournamentData
            if (scope == StatisticsScope.TOURNAMENT || scope == StatisticsScope.PERSON || scope == StatisticsScope.PERSON_TOURNAMENTS ||
                (scope == StatisticsScope.PLAYER && pickerData != null)
            ) {
                // The name moves to the top bar when this card, with the name of the player, scrolls out of view.
                TrackScrollOut(
                    enabled = when (scope) {
                        StatisticsScope.TOURNAMENT -> chosenTournament != null && !changingTournament
                        StatisticsScope.PERSON, StatisticsScope.PERSON_TOURNAMENTS -> personProfile != null && !changingPlayer
                        else -> false
                    },
                    onChange = { nameScrolledOut = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val fieldModifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                    when (scope) {
                        StatisticsScope.TOURNAMENT -> {
                            if (chosenTournament != null && !changingTournament) {
                                // The card is as wide as its content and sits in the middle.
                                TournamentStatisticsHeader(
                                    chosenTournament,
                                    fillWidth = false,
                                    action = {
                                        OutlinedButton(onClick = { changingTournament = true }) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Change")
                                        }
                                    },
                                )
                            } else {
                                val options = remember(tournaments) {
                                    listOf<Pair<String?, String>>(null to "All tournaments") + tournaments.map { it.id to it.name }
                                }
                                Row(
                                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SearchableDropdownField(
                                        "Tournament", options, tournamentId,
                                        { tournamentId = it; changingTournament = false },
                                        Modifier.weight(1f),
                                    )
                                    if (chosenTournament != null) TextButton(onClick = { changingTournament = false }) { Text("Cancel") }
                                }
                            }
                        }
                        StatisticsScope.PLAYER -> PlayerDropdownField(
                            names = pickerData!!.names,
                            selectedId = effectiveSlot ?: pickerData.slots.firstOrNull()?.id,
                            onChoose = { chosenSlotText = it.toString() },
                            modifier = fieldModifier,
                        )
                        else -> {
                            if (personProfile != null && !changingPlayer) {
                                // The card is as wide as its content and sits in the middle.
                                PlayerStatisticsHeader(
                                    personProfile,
                                    fillWidth = false,
                                    action = {
                                        OutlinedButton(onClick = { changingPlayer = true }) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Change")
                                        }
                                    },
                                )
                            } else {
                                val options = remember(registry) {
                                    registry.sortedBy { it.displayName }.map { it.emaId to "${it.displayName} (${it.emaId})" }
                                }
                                Row(
                                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SearchableDropdownField(
                                        "Player (name or EMA number)", options, personEma,
                                        { emaId = it; changingPlayer = false },
                                        Modifier.weight(1f),
                                    )
                                    if (personProfile != null) TextButton(onClick = { changingPlayer = false }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            }
            if (scope == StatisticsScope.PERSON || scope == StatisticsScope.PERSON_TOURNAMENTS) {
                val subTabs = listOf(
                    Triple(StatisticsScope.PERSON, "Overall", StatisticsIcon),
                    Triple(StatisticsScope.PERSON_TOURNAMENTS, "By tournaments", Icons.Default.EmojiEvents),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PrimaryTabRow(
                        selectedTabIndex = subTabs.indexOfFirst { it.first == scope },
                        containerColor = tabRowColor(), contentColor = TabContentColor,
                        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(subTabs.indexOfFirst { it.first == scope }), color = TabContentColor) },
                        modifier = Modifier.widthIn(max = StatisticsTabWidth * subTabs.size),
                    ) {
                        subTabs.forEach { (item, title, icon) ->
                            CompactTab(
                                selected = item == scope,
                                onClick = { scope = item },
                                title = title,
                                icon = { Icon(icon, contentDescription = null, Modifier.size(20.dp)) },
                            )
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
            when (scope) {
                StatisticsScope.TOURNAMENT -> {
                    val specific = tournamentData
                    val all = overview
                    if (tournamentId != null && specific != null && specific.tournament.id == tournamentId) {
                        TournamentStatisticsPanel(
                            statistics = specific.statistics,
                            names = specific.names,
                            onOpenPlayer = { slot ->
                                chosenSlotText = slot.toString()
                                val ema = specific.slots.firstOrNull { it.id == slot }?.assignedEmaId?.takeIf { it.isNotBlank() }
                                if (ema != null) emaId = ema
                                scope = if (ema != null) StatisticsScope.PERSON else StatisticsScope.PLAYER
                            },
                        )
                    } else if (tournamentId == null && !overviewAllowed) {
                        Text(
                            "The statistics of all tournaments are for administrators. Search a tournament to see its statistics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (tournamentId == null && all != null) {
                        TournamentStatisticsPanel(
                            statistics = all.statistics,
                            names = all.names,
                            tournamentCount = all.tournamentCount,
                            playerCount = all.playerCount,
                            onOpenPlayer = { playerId ->
                                emaId = all.profiles[playerId]?.emaId?.takeIf { it.isNotBlank() }
                                scope = StatisticsScope.PERSON
                            },
                        )
                    }
                }

                StatisticsScope.TABLE -> tournamentData?.statistics?.tables
                    ?.firstOrNull { it.roundId == route.roundId && it.tableId == route.tableId }
                    ?.let { TableStatisticsPanel(it, tournamentData!!.names) }
                    ?: Text("This table has no statistics yet.")

                StatisticsScope.PLAYER -> tournamentData?.let { data ->
                    val slotId = effectiveSlot ?: data.slots.firstOrNull()?.id
                    data.statistics.players.firstOrNull { it.playerId == slotId }?.let { stats ->
                        PlayerStatisticsPanel(
                stats,
                header = data.profiles[slotId]?.let { profile -> { NameHeader(profile) { nameScrolledOut = it } } },
                tournaments = listOf(data.tournament),
                rivalNames = data.rivalNames,
                onOpenRival = { ema -> emaId = ema; scope = StatisticsScope.PERSON },
            )
                    }
                }

                StatisticsScope.PERSON -> {
                    person?.let { data ->
                        PlayerStatisticsPanel(
                            data.overall,
                            tournaments = data.tournaments.map { it.first },
                            rivalNames = data.rivalNames,
                            onOpenRival = { ema -> emaId = ema },
                        )
                    } ?: if (personEma == null) PickPlayerHint() else Unit
                }

                StatisticsScope.PERSON_TOURNAMENTS -> {
                    person?.let { data ->
                        PlayerTournamentsSection(data.tournaments, loadStandings = { ids -> presenter.standings(data.emaId, ids) })
                    } ?: if (personEma == null) PickPlayerHint() else Unit
                }
            }
            }
        }
    }
}

@Composable
private fun NameHeader(profile: PlayerProfile, onScrolledOut: (Boolean) -> Unit) {
    TrackScrollOut(enabled = true, onChange = onScrolledOut) { PlayerStatisticsHeader(profile) }
}

/**
 * Reports to [onChange] whether the content has scrolled above the top of the screen.
 * The report goes back to false when the content leaves the composition or when [enabled] is false.
 */
@Composable
private fun TrackScrollOut(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val screen = LocalScreenScroll.current
    var bottom by remember { mutableStateOf(Float.MAX_VALUE) }
    val currentOnChange by rememberUpdatedState(onChange)
    if (screen != null) {
        LaunchedEffect(screen, enabled) {
            snapshotFlow { enabled && screen.state.value > bottom }.collect { currentOnChange(it) }
        }
        DisposableEffect(Unit) { onDispose { currentOnChange(false) } }
    }
    Box(
        modifier.onGloballyPositioned { coordinates ->
            if (screen != null) {
                bottom = coordinates.positionInRoot().y - screen.viewportTop + screen.state.value + coordinates.size.height
            }
        },
        contentAlignment = contentAlignment,
    ) { content() }
}

@Composable
private fun PickPlayerHint() {
    AppEmptyState(
        icon = Icons.Default.PersonSearch,
        title = "Choose a player",
        message = "Use the field above to search a player by name or EMA number and see his/her skills. ",
    )
}
