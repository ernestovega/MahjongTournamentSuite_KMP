package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.presentation.components.AppTooltipBox
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.icon_chicken_mono
import mahjongtournamentsuite.playerapp.generated.resources.standing_chicken_hands
import mahjongtournamentsuite.playerapp.generated.resources.standing_hand_value
import mahjongtournamentsuite.playerapp.generated.resources.standing_place
import mahjongtournamentsuite.playerapp.generated.resources.standing_place_of_best_hands
import mahjongtournamentsuite.playerapp.generated.resources.standing_place_of_chicken
import mahjongtournamentsuite.playerapp.generated.resources.standing_place_of_players
import mahjongtournamentsuite.playerapp.generated.resources.standing_place_of_teams
import mahjongtournamentsuite.playerapp.generated.resources.standing_points
import mahjongtournamentsuite.playerapp.generated.resources.standing_score
import mahjongtournamentsuite.playerapp.generated.resources.standing_show_details
import mahjongtournamentsuite.playerapp.generated.resources.standing_title_best_hand
import mahjongtournamentsuite.playerapp.generated.resources.standing_title_chicken
import mahjongtournamentsuite.playerapp.generated.resources.standing_title_ranking
import mahjongtournamentsuite.playerapp.generated.resources.standing_title_team
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The places of the player in a tournament, as the list of tournaments shows them.
 * - [position]: the place in the ranking, of [of] players, with [points] and [score].
 * - [teamRank]: the place of the team, of [teamOf] teams, with [teamPoints]. Only in a tournament with teams.
 * - [bestHandRank]: the place of the best hand ([bestHand] points) among [bestHandOf] best hands.
 * - [chickenRank]: the place by chicken hands ([chickenHands]) among the [chickenOf] players with a chicken hand.
 * A count of 0 means that the server did not send it. The hand places show only when the tournament is over.
 */
internal data class TournamentStanding(
    val position: Int,
    val of: Int = 0,
    val points: Double = 0.0,
    val score: Int = 0,
    val teamRank: Int? = null,
    val teamOf: Int? = null,
    val teamPoints: Double? = null,
    val chickenRank: Int? = null,
    val chickenHands: Int = 0,
    val chickenOf: Int = 0,
    val bestHandRank: Int? = null,
    val bestHand: Int = 0,
    val bestHandOf: Int = 0,
)

/**
 * What the list shows for [place]. The team place shows in a tournament with teams. The places of the hands show only
 * when the tournament is over and the tournament counts them.
 */
internal fun tournamentStandingOf(
    place: Standing,
    isTeams: Boolean,
    finished: Boolean,
    countsChickenHands: Boolean,
    countsBestHands: Boolean,
): TournamentStanding {
    val chicken = finished && countsChickenHands && place.chickenRank != null
    val best = finished && countsBestHands && place.bestHandRank != null
    return TournamentStanding(
        position = place.rank,
        of = place.of,
        points = place.points,
        score = place.score,
        teamRank = place.teamRank.takeIf { isTeams },
        teamOf = place.teamOf.takeIf { isTeams },
        teamPoints = place.teamPoints.takeIf { isTeams },
        chickenRank = place.chickenRank.takeIf { chicken },
        chickenHands = if (chicken) place.chickenHands else 0,
        chickenOf = if (chicken) place.chickenOf else 0,
        bestHandRank = place.bestHandRank.takeIf { best },
        bestHand = if (best) place.bestHand else 0,
        bestHandOf = if (best) place.bestHandOf else 0,
    )
}

/** The color of a place: gold, silver and bronze for the first three, the color of the text for the others. */
@Composable
internal fun standingColor(rank: Int): Color = MtsTheme.colors.position(rank, MaterialTheme.colorScheme.onSurface)

/**
 * One line for each place of the player: the ranking, the team, the best hand and the chicken hands. Each line has the
 * icon of the stat in the color of the place, the place, and a small info icon whose tooltip says the details.
 */
@Composable
internal fun StandingBadges(standing: TournamentStanding, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StandingLine(
            icon = rememberVectorPainter(Icons.Default.Leaderboard),
            rank = standing.position,
            title = Res.string.standing_title_ranking,
            details = listOf(
                placeLine(standing.position, standing.of, Res.string.standing_place_of_players),
                stringResource(Res.string.standing_points, formatPoints(standing.points)),
                stringResource(Res.string.standing_score, standing.score),
            ),
        )
        standing.teamRank?.let { rank ->
            StandingLine(
                icon = rememberVectorPainter(Icons.Default.Groups),
                rank = rank,
                title = Res.string.standing_title_team,
                details = listOfNotNull(
                    placeLine(rank, standing.teamOf ?: 0, Res.string.standing_place_of_teams),
                    standing.teamPoints?.let { stringResource(Res.string.standing_points, formatPoints(it)) },
                ),
            )
        }
        standing.bestHandRank?.let { rank ->
            StandingLine(
                icon = rememberVectorPainter(Icons.Default.PanTool),
                rank = rank,
                title = Res.string.standing_title_best_hand,
                details = listOfNotNull(
                    placeLine(rank, standing.bestHandOf, Res.string.standing_place_of_best_hands),
                    standing.bestHand.takeIf { it > 0 }?.let { stringResource(Res.string.standing_hand_value, it) },
                ),
            )
        }
        standing.chickenRank?.let { rank ->
            StandingLine(
                icon = painterResource(Res.drawable.icon_chicken_mono),
                rank = rank,
                title = Res.string.standing_title_chicken,
                details = listOfNotNull(
                    placeLine(rank, standing.chickenOf, Res.string.standing_place_of_chicken),
                    standing.chickenHands.takeIf { it > 0 }?.let { stringResource(Res.string.standing_chicken_hands, it) },
                ),
            )
        }
    }
}

/** "Place: 3 of 40 players", or "Place: 3" when the total is not known. */
@Composable
private fun placeLine(rank: Int, of: Int, withTotal: StringResource): String = if (of > 0) stringResource(withTotal, rank, of) else stringResource(Res.string.standing_place, rank)

@Composable
private fun StandingLine(icon: Painter, rank: Int, title: StringResource, details: List<String>) {
    val name = stringResource(title)
    // The title is the first line of the tooltip. The details are bullets, one on each line.
    val tooltip = (listOf(name) + details).joinToString("\n")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = name, Modifier.size(20.dp), tint = standingColor(rank))
        Text(
            "$rank",
            style = MaterialTheme.typography.titleSmall.inTextFont(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AppTooltipBox(tooltip, alignEnd = true) {
            // The tap shows the tooltip. It does not reach the card, so it does not open the tournament.
            Box(
                Modifier.size(24.dp).clip(CircleShape).clickable(onClickLabel = stringResource(Res.string.standing_show_details)) { toggleTooltip() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(Res.string.standing_show_details),
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
