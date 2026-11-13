package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
        MembersCard(loaded, members, onOpenPlayer)
        Text(
            stringResource(Res.string.team_statistics_note),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (combined == null) {
            standing?.invoke(Modifier.fillMaxWidth())
        } else {
            PlayerStatisticsPanel(
                combined,
                leading = standing,
                tournaments = listOf(loaded.tournament),
                rivalNames = loaded.rivalNames,
            )
        }
    }
}

/** One line for each player of the team: points, score and win rate in the tournament. */
@Composable
private fun MembersCard(data: TournamentStatisticsData, members: List<Int>, onOpenPlayer: (Int) -> Unit) {
    val stats = data.statistics.players.associateBy { it.playerId }
    val rows = members.sortedByDescending { stats[it]?.totalPoints ?: 0.0 }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    StatCard(stringResource(Res.string.team_members), Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("", Modifier.weight(1f))
            HeaderCell(stringResource(Res.string.team_member_points))
            HeaderCell(stringResource(Res.string.team_member_score))
            HeaderCell(stringResource(Res.string.team_member_win_rate))
        }
        rows.forEach { slot ->
            val player = stats[slot]
            Row(
                Modifier.fillMaxWidth().clickable { onOpenPlayer(slot) }.padding(vertical = MtsTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerFlag(data.slots.firstOrNull { it.id == slot }?.country)
                    Text(data.names[slot] ?: "-", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
                ValueCell(player?.let { formatPoints(it.totalPoints) } ?: "-")
                ValueCell(player?.totalScore?.toString() ?: "-")
                ValueCell(player?.winRate?.let { "${(it * 100).roundToInt()}%" } ?: "-", color = muted)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String) = Text(
    text,
    Modifier.width(64.dp),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.End,
    maxLines = 2,
)

@Composable
private fun ValueCell(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) = Text(
    text,
    Modifier.width(64.dp),
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    textAlign = TextAlign.End,
)
