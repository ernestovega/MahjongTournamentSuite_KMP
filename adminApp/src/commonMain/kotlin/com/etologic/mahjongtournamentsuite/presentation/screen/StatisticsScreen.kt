package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.usecase.GroupStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppLoadingCompact
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.FilteredStatistics
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollArea
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ResetScrollOnChange
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ShowTabsAtTop
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsFilterBar
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsFilters
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsView
import com.etologic.mahjongtournamentsuite.presentation.components.opponentsOf
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.components.screenLayout
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.presenter.StatisticsPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.statistics
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The filters that a route opens with. A table of a tournament opens with that table. */
internal fun StatisticsRoute.initialFilters(): StatisticsFilters = StatisticsFilters(
    emaId = emaId?.takeIf { it.isNotBlank() },
    tournamentId = tournamentId,
    slotId = slotId?.takeIf { tournamentId != null },
    roundId = roundId?.takeIf { scope == StatisticsScope.TABLE.name && tournamentId != null },
    tableId = tableId?.takeIf { scope == StatisticsScope.TABLE.name && tournamentId != null },
)

/**
 * The statistics screen: the same as in the player app, one screen with a row of filters and no tabs.
 * The route gives the filters at the start. Statistics are public data, so every role sees all of them.
 */
@Composable
fun StatisticsScreen(navController: NavHostController, route: StatisticsRoute) {
    val presenter = koinInject<StatisticsPresenter>()
    // A page refresh restores the filters that the editor chose.
    var stored by rememberPersistentText("filters", null)
    val filters = StatisticsFilters.decode(stored) ?: route.initialFilters()
    val setFilters: (StatisticsFilters) -> Unit = { stored = it.encode() }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var registry by remember { mutableStateOf<List<Player>>(emptyList()) }
    var tournament by remember { mutableStateOf<TournamentStatisticsData?>(null) }
    var person by remember { mutableStateOf<PersonStatisticsData?>(null) }
    var group by remember { mutableStateOf<GroupStatisticsData?>(null) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    // False until the first statistics load. Only that load covers the screen with the logo; another filter keeps
    // the filters on screen and shows the logo in place of the statistics.
    var loadedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (presenter.tournaments() as? AppResult.Success)?.let { tournaments = it.value }
        (presenter.registry() as? AppResult.Success)?.let { registry = it.value }
    }
    LaunchedEffect(filters) {
        isLoading = true
        message = null
        when (filters.view) {
            StatisticsView.PERSON -> {
                val emaId = filters.emaId.orEmpty()
                if (person?.emaId != emaId) {
                    person = null
                    when (val result = presenter.person(emaId)) {
                        is AppResult.Success -> person = result.value
                        is AppResult.Failure -> message = result.error.toUiText()
                    }
                }
            }
            StatisticsView.TABLE, StatisticsView.SLOT, StatisticsView.TOURNAMENT -> {
                val id = filters.tournamentId.orEmpty()
                if (tournament?.tournament?.id != id) {
                    tournament = null
                    when (val result = presenter.tournament(id)) {
                        is AppResult.Success -> tournament = result.value
                        is AppResult.Failure -> message = result.error.toUiText()
                    }
                }
            }
            StatisticsView.GROUP -> {
                group = null
                when (val result = presenter.group(filters.groupFilter())) {
                    is AppResult.Success -> group = result.value
                    is AppResult.Failure -> message = result.error.toUiText()
                }
            }
        }
        isLoading = false
        loadedOnce = true
    }

    AppScaffold(
        title = stringResource(Res.string.statistics),
        isLoading = isLoading,
        showBackgroundLogo = isLoading && !loadedOnce,
        contentLoading = isLoading && !loadedOnce,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        // The filters hide when the statistics scroll down and show when they scroll up, as in the player app.
        HideOnScrollArea {
            ScreenColumn(
                // The statistics sections use the full width and flow into columns.
                maxWidth = Dp.Infinity,
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                val scroll = rememberPersistentScrollState("statistics")
                // Other filters start at the top.
                ResetScrollOnChange(scroll, filters)
                ShowTabsAtTop(scroll)
                // Another filter shows them again.
                HideOnScrollTabs(filters) {
                    StatisticsFilterBar(
                        filters = filters,
                        onChange = setFilters,
                        players = registry,
                        tournaments = tournaments,
                        slotName = filters.slotId?.let { tournament?.names?.get(it) },
                        opponents = remember(person, filters.emaId) { opponentsOf(person?.takeIf { it.emaId == filters.emaId }) },
                    )
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScrollWithIndicators(scroll, MtsScrollbar.Overlay)
                        .padding(horizontal = screenLayout.screenGutter, vertical = screenLayout.sectionGap),
                    verticalArrangement = Arrangement.spacedBy(screenLayout.sectionGap),
                ) {
                    message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
                    val shown = when (filters.view) {
                        StatisticsView.PERSON -> person
                        StatisticsView.GROUP -> group
                        else -> tournament
                    }
                    // Another filter loads its statistics: the logo with a progress shows in their place.
                    if (isLoading && shown == null) AppLoadingCompact()
                    FilteredStatistics(
                        filters = filters,
                        onFiltersChange = setFilters,
                        person = person.takeIf { filters.view == StatisticsView.PERSON },
                        tournament = tournament,
                        group = group,
                        loadStandings = { emaId, ids -> presenter.standings(emaId, ids) },
                        loadPerson = { ema -> (presenter.person(ema) as? AppResult.Success)?.value },
                    )
                }
            }
        }
    }
}
