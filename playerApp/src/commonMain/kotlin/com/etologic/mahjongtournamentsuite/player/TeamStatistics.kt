package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStandingCard
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerStatisticsPanel
import com.etologic.mahjongtournamentsuite.presentation.components.StatCard
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentScrollState
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.team_member_points
import mahjongtournamentsuite.playerapp.generated.resources.team_member_score
import mahjongtournamentsuite.playerapp.generated.resources.team_member_win_rate
import mahjongtournamentsuite.playerapp.generated.resources.team_members
import mahjongtournamentsuite.playerapp.generated.resources.team_statistics_note
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * The statistics of a team: one player made of all the tables of its [members]. The tables are in play order, round by
 * round. Null when no member has statistics.
 */
internal fun teamStatistics(teamId: Int, members: Collection<Int>, players: List<PlayerStatistics>): PlayerStatistics? {
    val results = players.filter { it.playerId in members }.flatMap { it.results }
    if (results.isEmpty()) return null
    return PlayerStatistics(playerId = -teamId, results = results.sortedWith(compareBy({ it.roundId }, { it.tableId })))
}

/**
 * The Stats tab of a team: the place of the team, one line for each player, and the statistics of all the tables of the
 * team together. A tap on a player opens that player.
 */
@Composable
internal fun TeamStatisticsTab(
    tournamentId: String,
    members: List<Int>,
    ranking: TeamRanking?,
    onOpenPlayer: (Int) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
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
    val loading = data == null && message == null
    LaunchedEffect(loading) { onLoadingChange(loading) }
    DisposableEffect(Unit) { onDispose { onLoadingChange(false) } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberPersistentScrollState("team-statistics-" + members.joinToString("-")))
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
    ) {
        message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
        val loaded = data ?: return@Column
        val teamId = loaded.slots.firstOrNull { it.id in members }?.team ?: 0
        val standing: (@Composable (Modifier) -> Unit)? = ranking?.let { { modifier -> PlayerStandingCard(it.position, it.points, it.score, modifier) } }
        val combined = teamStatistics(teamId, members, loaded.statistics.players)
        // The place of the team first, then its players, then the statistics of all their tables together.
        standing?.invoke(Modifier.fillMaxWidth())
        MembersCard(loaded, members, onOpenPlayer)
        Text(
            stringResource(Res.string.team_statistics_note),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        combined?.let {
            PlayerStatisticsPanel(it, tournaments = listOf(loaded.tournament), rivalNames = loaded.rivalNames)
        }
    }
}

/** One line for each player of the team: the name, then the points, the score and the win rate in the tournament. */
@Composable
private fun MembersCard(data: TournamentStatisticsData, members: List<Int>, onOpenPlayer: (Int) -> Unit) {
    val stats = data.statistics.players.associateBy { it.playerId }
    val rows = members.sortedByDescending { stats[it]?.totalPoints ?: 0.0 }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    StatCard(stringResource(Res.string.team_members), Modifier.fillMaxWidth()) {
        rows.forEach { slot ->
            val player = stats[slot]
            Column(
                Modifier.fillMaxWidth().clickable { onOpenPlayer(slot) }.padding(vertical = MtsTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                    PlayerFlag(data.profiles[slot]?.country?.takeIf { it.isNotBlank() } ?: data.slots.firstOrNull { it.id == slot }?.country)
                    Text(data.names[slot] ?: "-", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
                val numbers = listOf(
                    stringResource(Res.string.team_member_points) to (player?.let { formatPoints(it.totalPoints) } ?: "-"),
                    stringResource(Res.string.team_member_score) to (player?.totalScore?.toString() ?: "-"),
                    stringResource(Res.string.team_member_win_rate) to (player?.winRate?.let { "${(it * 100).roundToInt()}%" } ?: "-"),
                )
                Text(
                    numbers.joinToString("  ·  ") { (label, value) -> "$label $value" },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
