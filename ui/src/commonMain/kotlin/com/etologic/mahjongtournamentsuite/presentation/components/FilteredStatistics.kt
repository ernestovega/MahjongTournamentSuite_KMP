package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.byRound
import com.etologic.mahjongtournamentsuite.domain.model.playedRounds
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.GroupStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.headToHead
import com.etologic.mahjongtournamentsuite.domain.usecase.only
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.compare_scope_all
import mahjongtournamentsuite.ui.generated.resources.compare_scope_together
import mahjongtournamentsuite.ui.generated.resources.h2h_alone_title
import mahjongtournamentsuite.ui.generated.resources.stats_empty_message
import mahjongtournamentsuite.ui.generated.resources.stats_empty_title
import mahjongtournamentsuite.ui.generated.resources.stats_group_empty_message
import mahjongtournamentsuite.ui.generated.resources.stats_group_empty_title
import mahjongtournamentsuite.ui.generated.resources.stats_player_tournaments
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.cancellation.CancellationException

/**
 * The statistics of [filters], the same in both apps. The screen loads the data of [StatisticsFilters.view]:
 * [person] for PERSON, [tournament] for TOURNAMENT, TABLE and SLOT, [group] for GROUP. Nothing shows while it is null.
 * A tap on a player or a rival changes the filters through [onFiltersChange]. With a rival, [loadPerson] loads the
 * rival too, for the numbers of both players side by side.
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
    loadPerson: (suspend (emaId: String) -> PersonStatisticsData?)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg)) {
        when (filters.view) {
            StatisticsView.PERSON -> person?.let { PersonContent(it, filters, onFiltersChange, loadStandings, loadPerson) }
            StatisticsView.TABLE -> tournament?.let { data ->
                data.statistics.tables.firstOrNull { it.roundId == filters.roundId && it.tableId == filters.tableId }
                    ?.let { TableStatisticsPanel(it, data.names) }
                    ?: AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_empty_message))
            }
            StatisticsView.SLOT -> tournament?.let { SlotContent(it, filters.slotId ?: return@let) }
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
    loadPerson: (suspend (String) -> PersonStatisticsData?)?,
) {
    val rival = filters.rivalEmaId
    // With another player, the screen compares the two. "Only their tables together" is possible only when the two
    // shared a table in the kept tournaments.
    val sharedKept = remember(data, filters) { rival?.let { data.only(filters::keeps, it) } }
    val shared = sharedKept?.tournaments?.isNotEmpty() == true
    val together = filters.together && shared
    val allKept = remember(data, filters) { data.only(filters::keeps) }
    val kept = if (together) sharedKept ?: allKept else allKept
    if (kept.tournaments.isEmpty()) {
        if (data.tournaments.isEmpty()) {
            AppEmptyState(StatisticsIcon, stringResource(Res.string.stats_empty_title), stringResource(Res.string.stats_empty_message))
        } else {
            NoStatistics()
        }
        return
    }
    if (rival != null) {
        // The comparison comes first. The numbers of the person alone come after.
        val rivalData by produceState<PersonStatisticsData?>(null, rival) { value = loadPerson?.invoke(rival) }
        val rivalProfile = rivalData?.profile ?: PlayerProfile(data.rivalNames[rival] ?: "EMA $rival", rival, "", null)
        val cardModifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        if (shared) {
            ComparisonScopeChips(together) { onFiltersChange(filters.copy(together = it)) }
        }
        if (together) {
            TogetherComparison(data, rival, rivalData, rivalProfile, filters, onFiltersChange, loadStandings, cardModifier)
            Text(stringResource(Res.string.h2h_alone_title, data.profile?.name ?: "EMA ${data.emaId}"), style = MaterialTheme.typography.titleLarge)
        } else {
            GeneralComparison(kept, rival, rivalData, rivalProfile, filters, loadStandings, cardModifier)
            Text(data.profile?.name ?: "EMA ${data.emaId}", style = MaterialTheme.typography.titleLarge)
        }
        PlayerStatisticsPanel(
            kept.overall,
            tournaments = kept.tournaments.map { it.first },
            rivalNames = kept.rivalNames,
            onCompareRival = { ema -> onFiltersChange(filters.copy(rivalEmaId = ema, together = false)) },
        )
        return
    }
    PlayerStatisticsPanel(
        kept.overall,
        tournaments = kept.tournaments.map { it.first },
        rivalNames = kept.rivalNames,
        onCompareRival = { ema -> onFiltersChange(filters.copy(rivalEmaId = ema, together = false)) },
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
private fun SlotContent(data: TournamentStatisticsData, slotId: Int) {
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
    )
}

/** The choice between all the tables of each one and only the tables where the two played together. */
@Composable
private fun ComparisonScopeChips(together: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally)) {
        FilterChip(selected = !together, onClick = { onChange(false) }, label = { Text(stringResource(Res.string.compare_scope_all)) })
        FilterChip(selected = together, onClick = { onChange(true) }, label = { Text(stringResource(Res.string.compare_scope_together)) })
    }
}

/** Loads the final place of [emaId] in each of [ids]. Null when it fails: the cards that need it do not show. */
private suspend fun standingsOrNull(
    loadStandings: suspend (String, List<String>) -> Map<String, Standing>,
    emaId: String,
    ids: List<String>,
): Map<String, Standing>? = try {
    if (ids.isEmpty()) emptyMap() else loadStandings(emaId, ids)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/** The two players over all their tables: the charts first, then the numbers (Ernesto, 2026-11-25). */
@Composable
private fun GeneralComparison(
    kept: PersonStatisticsData,
    rival: String,
    rivalData: PersonStatisticsData?,
    rivalProfile: PlayerProfile,
    filters: StatisticsFilters,
    loadStandings: suspend (String, List<String>) -> Map<String, Standing>,
    cardModifier: Modifier,
) {
    val theirs = remember(rivalData, filters) { rivalData?.only(filters::keeps) } ?: return
    // The final place of each one in each of the kept tournaments.
    val places by produceState<Pair<Map<String, Standing>, Map<String, Standing>>?>(null, kept, theirs) {
        val mine = standingsOrNull(loadStandings, kept.emaId, kept.tournaments.map { it.first.id })
        val their = standingsOrNull(loadStandings, rival, theirs.tournaments.map { it.first.id })
        value = if (mine != null && their != null) mine to their else null
    }
    val common = remember(kept, theirs) {
        val ids = theirs.tournaments.map { it.first.id }.toSet()
        kept.tournaments.map { it.first }.filter { it.id in ids }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg)) {
        if (kept.overall.tablesWithHands > 0 && theirs.overall.tablesWithHands > 0) {
            CompareSkillsCard(kept.overall, theirs.overall, kept.profile, rivalProfile, cardModifier)
        }
        ComparePlacesCard(kept.overall, theirs.overall, kept.profile, rivalProfile, cardModifier)
        places?.let { (mine, their) -> HeadToHeadPlacesCard(common, mine, their, kept.profile, rivalProfile, cardModifier) }
        ComparisonCard(kept.overall, theirs.overall, kept.profile, rivalProfile, cardModifier)
        places?.let { (mine, their) ->
            CompareTournamentsCard(
                kept.tournaments.map { it.first to mine[it.first.id] },
                theirs.tournaments.map { it.first to their[it.first.id] },
                kept.profile,
                rivalProfile,
                cardModifier,
            )
        }
    }
}

/** The two players at the tables where they played together. */
@Composable
private fun TogetherComparison(
    data: PersonStatisticsData,
    rival: String,
    rivalData: PersonStatisticsData?,
    rivalProfile: PlayerProfile,
    filters: StatisticsFilters,
    onFiltersChange: (StatisticsFilters) -> Unit,
    loadStandings: suspend (String, List<String>) -> Map<String, Standing>,
    cardModifier: Modifier,
) {
    val kept = remember(data, filters) { data.only(filters::keeps, rival) }
    val headToHead = remember(data, rivalData, filters) { headToHead(data, rival, rivalData, filters::keeps) }
    // The skills of each one at these tables, and the tournaments where both played, with the place of each one.
    val theirKept = remember(rivalData, filters) { rivalData?.only(filters::keeps, data.emaId) }
    val common = remember(data, rivalData, filters) {
        val theirs = rivalData?.tournaments.orEmpty().map { it.first.id }.toSet()
        data.tournaments.map { it.first }.filter { it.id in theirs && filters.keeps(it) }
    }
    val places by produceState<Pair<Map<String, Standing>, Map<String, Standing>>?>(null, common, rival) {
        val ids = common.map { it.id }
        val mine = standingsOrNull(loadStandings, data.emaId, ids)
        val theirs = standingsOrNull(loadStandings, rival, ids)
        value = if (ids.isNotEmpty() && mine != null && theirs != null) mine to theirs else null
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg)) {
        // The charts first, then the lists of numbers (Ernesto, 2026-11-25).
        if (theirKept != null && kept.overall.tablesWithHands > 0 && theirKept.overall.tablesWithHands > 0) {
            HeadToHeadSkillsCard(kept.overall, theirKept.overall, data.profile, rivalProfile, cardModifier)
        }
        places?.let { (mine, theirs) -> HeadToHeadPlacesCard(common, mine, theirs, data.profile, rivalProfile, cardModifier) }
        HeadToHeadCard(headToHead, data.profile, rivalProfile, cardModifier)
        SharedTablesCard(headToHead, data.profile, rivalProfile, cardModifier) { table ->
            // A table opens with its own statistics, like a table of the tournament view.
            onFiltersChange(StatisticsFilters(tournamentId = table.tournament.id, roundId = table.mine.roundId, tableId = table.mine.tableId))
        }
    }
}
