package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.stats_empty_message
import mahjongtournamentsuite.ui.generated.resources.stats_empty_title
import mahjongtournamentsuite.ui.generated.resources.stats_hand_stats_missing
import mahjongtournamentsuite.ui.generated.resources.stats_hand_values_use
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Two cards side by side need at least this width. Narrower screens stack the cards. */
private val WideLayoutWidth = 760.dp

/**
 * The statistics of one player in big cards: [header], skills, places and totals.
 * On a wide screen the cards flow into several columns, like a staggered grid.
 */
@Composable
fun PlayerStatisticsPanel(
    statistics: PlayerStatistics,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    /** A card above all other cards, for example the standing of the player in the tournament. */
    leading: (@Composable (Modifier) -> Unit)? = null,
    /** A card after the places, for example the rounds of the player in the tournament. */
    afterPlaces: (@Composable (Modifier) -> Unit)? = null,
    /** The tournaments of the results. They give the date and time of each table in the place history. */
    tournaments: List<Tournament> = emptyList(),
    /** Display name of every rival key. See `RivalTally.opponent`. */
    rivalNames: Map<String, String> = emptyMap(),
    /** Compares the person with a rival. The key is the EMA number of the rival. Without it the rivals have no button. */
    onCompareRival: ((String) -> Unit)? = null,
) {
    if (statistics.tablesPlayed == 0) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            header?.invoke()
            leading?.invoke(Modifier.fillMaxWidth())
            AppEmptyState(
                icon = StatisticsIcon,
                title = stringResource(Res.string.stats_empty_title),
                message = stringResource(Res.string.stats_empty_message),
            )
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= WideLayoutWidth
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (wide) {
                StaggeredColumns(minColumnWidth = 480.dp) {
                    header?.invoke()
                    leading?.invoke(Modifier.fillMaxWidth())
                    SkillsCard(statistics, Modifier.fillMaxWidth())
                    OverviewCard(statistics, Modifier.fillMaxWidth())
                    PlacesCard(statistics, tournaments, Modifier.fillMaxWidth())
                    afterPlaces?.invoke(Modifier.fillMaxWidth())
                    RecordsCard(statistics, Modifier.fillMaxWidth())
                    RivalsCard(statistics, rivalNames, Modifier.fillMaxWidth(), onCompareRival)
                }
            } else {
                header?.invoke()
                leading?.invoke(Modifier.fillMaxWidth())
                SkillsCard(statistics, Modifier.fillMaxWidth())
                OverviewCard(statistics, Modifier.fillMaxWidth())
                PlacesCard(statistics, tournaments, Modifier.fillMaxWidth())
                afterPlaces?.invoke(Modifier.fillMaxWidth())
                RecordsCard(statistics, Modifier.fillMaxWidth())
                RivalsCard(statistics, rivalNames, Modifier.fillMaxWidth(), onCompareRival)
            }
            if (statistics.tablesWithHands < statistics.tablesPlayed) {
                Text(
                    if (statistics.tablesWithHands == 0) {
                        stringResource(Res.string.stats_hand_stats_missing)
                    } else {
                        pluralStringResource(Res.plurals.stats_hand_values_use, statistics.tablesPlayed, statistics.tablesWithHands, statistics.tablesPlayed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
