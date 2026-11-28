package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/** The mouse over the charts of two players shows the bubble of the tournament or the place under it. */
@OptIn(ExperimentalTestApi::class)
class HeadToHeadChartsPointerUiTest {
    private val open = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 1)
    private val cup = Tournament(id = "t2", name = "Cup", isTeams = false, numPlayers = 4, numRounds = 1)
    private val ana = PlayerProfile("Ana López", "1", "ES", null)
    private val bea = PlayerProfile("Bea Ruiz", "2", "ES", null)

    private fun stats(vararg places: Int) = PlayerStatistics(
        0,
        places.mapIndexed { index, place ->
            TableResult(tournamentId = "t1", roundId = index + 1, tableId = 1, seat = 0, position = place, score = 0, points = 0.0, hasHands = false)
        },
    )

    @Test
    fun theMouseOverTheChartsShowsTheirBubbles() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(400.dp).testTag("charts")) {
                    HeadToHeadPlacesCard(
                        listOf(open, cup),
                        mine = mapOf("t1" to Standing(rank = 1, of = 4, points = 4.0, score = 30), "t2" to Standing(rank = 3, of = 4, points = 1.0, score = -5)),
                        theirs = mapOf("t1" to Standing(rank = 2, of = 4, points = 2.0, score = 10), "t2" to Standing(rank = 1, of = 4, points = 4.0, score = 20)),
                        me = ana,
                        rival = bea,
                    )
                    ComparePlacesCard(stats(1, 2, 1), stats(3, 4, 2), ana, bea)
                }
            }
        }
        waitForIdle()
        onNodeWithTag("charts").performMouseInput {
            (0..140).forEach { step -> moveTo(Offset(if (step % 2 == 0) 60f else 300f, step * 10f)) }
        }
        waitForIdle()
    }
}
