package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.etologic.mahjongtournamentsuite.domain.model.byRound
import com.etologic.mahjongtournamentsuite.domain.model.playedRounds
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.GroupStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.only
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.stats_empty_message
import mahjongtournamentsuite.ui.generated.resources.stats_empty_title
import mahjongtournamentsuite.ui.generated.resources.stats_group_empty_message
import mahjongtournamentsuite.ui.generated.resources.stats_group_empty_title
import mahjongtournamentsuite.ui.generated.resources.stats_player_tournaments
import org.jetbrains.compose.resources.stringResource

/**
 * The statistics of [filters], the same in both apps. The screen loads the data of [StatisticsFilters.view]:
 * [person] for PERSON, [tournament] for TOURNAMENT, TABLE and SLOT, [group] for GROUP. Nothing shows while it is null.
 * A tap on a player or a rival changes the filters through [onFiltersChange].
 */
@Composable
fun FilteredStatistics(
    filters: StatisticsFilters,
    onFiltersChange: (StatisticsFilters) -> Unit,
    person: PersonStatisticsData?,
    tournament: TournamentStatisticsData?,
    group: GroupStatisticsData?,
    loadStandings: suspend (emaId: String, tournamentIds: List<String>) -> Map<String, Standing>,
    modifier: Modifier = Modifier,
    /** The round of the tournament view, when the screen shows [TournamentRoundChips] itself. */
    selectedRound: Int? = null,
    onSelectRound: ((Int?) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg)) {
        when (filters.view) {
            StatisticsView.PERSON -> person?.let { PersonContent(it, filters, onFiltersChange, loadStandings) }
            StatisticsView.TABLE -> tournament?.let { data ->
                data.statistics.tables.firstOrNull { it.roundId == filters.roundId && it.tableId == filters.tableId }
                    ?.let { TableStatisticsPanel(it, data.names) }
                    ?: AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_empty_message))
            }
            StatisticsView.SLOT -> tournament?.let { SlotContent(it, filters.slotId ?: return@let, onFiltersChange, filters) }
            StatisticsView.TOURNAMENT -> tournament?.let { data ->
                TournamentStatisticsPanel(
                    statistics = data.statistics,
                    names = data.names,
                    photos = remember(data) { data.profiles.mapValues { it.value.photoUrl } },
                    roundIds = (1..data.tournament.numRounds).toList(),
                    selectedRound = selectedRound,
                    onSelectRound = onSelectRound,
                    // A player opens with the statistics of this tournament: the place and the place after each round.
                    onOpenPlayer = { slot -> onFiltersChange(filters.copy(slotId = slot, emaId = null)) },
                )
            }
            StatisticsView.GROUP -> group?.let { data ->
                if (data.tournamentIds.isEmpty()) {
                    NoStatistics()
                } else {
                    TournamentStatisticsPanel(
                        statistics = data.statistics,
                        names = data.names,
                        photos = remember(data) { data.profiles.mapValues { it.value.photoUrl } },
                        tournamentCount = data.tournamentIds.size,
                        playerCount = data.profiles.size,
                        records = data.records,
                        onOpenPlayer = { id -> data.profiles[id]?.emaId?.takeIf { it.isNotBlank() }?.let { onFiltersChange(filters.withPerson(it)) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoStatistics() {
    AppEmptyState(Icons.Default.FilterListOff, stringResource(Res.string.stats_group_empty_title), stringResource(Res.string.stats_group_empty_message))
}

/** One person: the cards over the kept tournaments, then one card for each tournament. */
@Composable
private fun PersonContent(
    data: PersonStatisticsData,
    filters: StatisticsFilters,
    onFiltersChange: (StatisticsFilters) -> Unit,
    loadStandings: suspend (String, List<String>) -> Map<String, Standing>,
) {
    val kept = remember(data, filters) { data.only(filters::keeps, filters.rivalEmaId) }
    if (kept.tournaments.isEmpty()) {
        if (data.tournaments.isEmpty()) {
            AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_empty_message))
        } else {
            NoStatistics()
        }
        return
    }
    PlayerStatisticsPanel(
        kept.overall,
        tournaments = kept.tournaments.map { it.first },
        rivalNames = kept.rivalNames,
        onOpenRival = { ema -> onFiltersChange(filters.withPerson(ema)) },
    )
    Text(stringResource(Res.string.stats_player_tournaments), style = MaterialTheme.typography.titleLarge)
    PlayerTournamentsSection(
        kept.tournaments,
        loadStandings = { ids -> loadStandings(data.emaId, ids) },
        tournamentId = filters.tournamentId,
    )
}

/** One seat of one tournament: the place, and the place after each round. */
@Composable
private fun SlotContent(data: TournamentStatisticsData, slotId: Int, onFiltersChange: (StatisticsFilters) -> Unit, filters: StatisticsFilters) {
    val rankings = remember(data) {
        val calculate = CalculateTournamentRankingsUseCase()
        val rounds = data.statistics.playedRounds((1..data.tournament.numRounds).toList())
        calculate(data.slots, data.tables, data.tournament.isTeams) to
            calculate.afterEachRound(data.slots, data.tables, data.tournament.isTeams, rounds)
    }
    val ranking = rankings.first.players.firstOrNull { it.playerId == slotId }
    val places = rankings.second.mapNotNull { (round, byRound) ->
        byRound.players.firstOrNull { it.playerId == slotId }?.let { round to it.position }
    }.toMap()
    ranking?.let { PlayerStandingCard(it.position, it.points, it.score, Modifier.fillMaxWidth(), places = places, of = data.slots.size) }
    val stats = data.statistics.players.firstOrNull { it.playerId == slotId }
    if (stats == null) {
        AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_empty_message))
        return
    }
    PlayerStatisticsPanel(
        stats,
        afterPlaces = { modifier -> PlayerRoundsCard(stats.byRound((1..data.tournament.numRounds).toList()), places, data.slots.size, modifier) },
        tournaments = listOf(data.tournament),
        rivalNames = data.rivalNames,
        onOpenRival = { ema -> onFiltersChange(filters.withPerson(ema)) },
    )
}
