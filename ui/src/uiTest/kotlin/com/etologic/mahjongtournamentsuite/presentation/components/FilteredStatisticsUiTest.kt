package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.GroupRecords
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.GroupStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PersonStatisticsData
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.domain.usecase.TournamentStatisticsData
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The statistics of the filters, the same in both apps: one person alone or against a rival, one table, one seat,
 * one tournament and a group of tournaments, and what each view says without data.
 */
@OptIn(ExperimentalTestApi::class)
class FilteredStatisticsUiTest {
    private val madrid = Tournament(id = "t-1", name = "Madrid", isTeams = false, numPlayers = 4, numRounds = 3, eventStartDate = "2025-05-01")
    private val toledo = Tournament(id = "t-2", name = "Toledo", isTeams = false, numPlayers = 4, numRounds = 3, eventStartDate = "2026-02-01")

    private fun result(tournament: Tournament, round: Int, position: Int, points: Double, opponent: String, handsWon: Int = 2) = TableResult(
        tournamentId = tournament.id, roundId = round, tableId = 1, seat = 0, position = position, score = (points * 10).toInt(),
        points = points, hasHands = true, handsWon = handsWon, selfDraws = 1, bestHand = 16,
        rivals = listOf(RivalTally(opponent, tablesBehind = if (position > 2) 1 else 0, discardLosses = 1, selfDrawLosses = 0)),
    )

    private fun person(ema: String, vararg parts: Pair<Tournament, List<TableResult>>, rivalNames: Map<String, String> = emptyMap()) = PersonStatisticsData(
        ema,
        PlayerProfile("P$ema", ema, "ES", null),
        PlayerStatistics(0, parts.flatMap { it.second }),
        parts.map { it.first to PlayerStatistics(0, it.second) },
        rivalNames,
    )

    private val ana = person(
        "1",
        madrid to listOf(result(madrid, 1, 1, 4.0, "2"), result(madrid, 2, 2, 2.0, "9")),
        toledo to listOf(result(toledo, 1, 3, 1.0, "2")),
        rivalNames = mapOf("2" to "Bea Rival", "9" to "Nine Rival"),
    )
    private val bea = person(
        "2",
        madrid to listOf(result(madrid, 1, 3, 1.0, "1")),
        toledo to listOf(result(toledo, 1, 1, 4.0, "1")),
    )

    private val slots = (1..4).map { TournamentPlayer(id = it, name = "Player $it", team = 1) }
    private val names = slots.associate { it.id to "Player ${it.id}" }

    private fun tables(): List<RankingTable> = (1..3).map { round ->
        val table = TableState(
            roundId = round, tableId = 1, playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = "${20 + round}", playerSouthScore = "10", playerWestScore = "-10", playerNorthScore = "${-20 - round}",
            isCompleted = true, useTotalsOnly = false,
        )
        val hands = (1..6).map { id ->
            TableHand(
                handId = id, playerWinnerId = ((id % 4) + 1).toString(), playerLooserId = if (id % 3 == 0) "" else (((id + 1) % 4) + 1).toString(),
                handScore = (8 + id).toString(), isChickenHand = false, isDone = true,
                playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            )
        }
        RankingTable(table, hands)
    }

    private fun tournamentData(completed: Boolean = true): TournamentStatisticsData {
        val tables = if (completed) tables() else emptyList()
        return TournamentStatisticsData(
            tournament = madrid,
            slots = slots,
            statistics = CalculateStatisticsUseCase()(slots, tables),
            names = names,
            profiles = slots.associate { it.id to PlayerProfile("Player ${it.id}", "0${it.id}", "ES", null) },
            tables = tables,
        )
    }

    private class Host {
        val changes = mutableListOf<StatisticsFilters>()
        var standingsCalls = 0
    }

    private fun ComposeUiTest.show(
        filters: StatisticsFilters,
        person: PersonStatisticsData? = null,
        tournament: TournamentStatisticsData? = null,
        group: GroupStatisticsData? = null,
        standingsFail: Boolean = false,
        rival: PersonStatisticsData? = bea,
    ): Host {
        val host = Host()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(1000.dp).verticalScroll(rememberScrollState())) {
                    FilteredStatistics(
                        filters = filters,
                        onFiltersChange = { host.changes += it },
                        person = person,
                        tournament = tournament,
                        group = group,
                        loadStandings = { _, ids ->
                            host.standingsCalls++
                            if (standingsFail) error("No standings")
                            ids.associateWith { Standing(rank = 2, of = 8, points = 6.0, score = 40) }
                        },
                        loadPerson = { ema -> rival?.takeIf { it.emaId == ema } },
                    )
                }
            }
        }
        waitForIdle()
        return host
    }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun onePersonShowsTheNumbersAndTheTournamentsAndCompareOpensTheComparisonWithARival() = runComposeUiTest {
        val host = show(StatisticsFilters(emaId = "1"), person = ana)
        assertTrue(count("Tournaments") > 0)
        onNode(hasClickAction() and hasText("Compare")).performScrollTo().performClick()
        waitForIdle()
        val change = host.changes.single()
        assertEquals("1", change.emaId)
        assertEquals("2", change.rivalEmaId)
    }

    @Test
    fun aPersonWithoutTournamentsHasNoStatisticsYet() = runComposeUiTest {
        show(StatisticsFilters(emaId = "1"), person = person("1"))
        onNodeWithText("No statistics yet").assertExists()
    }

    @Test
    fun filtersThatKeepNoTournamentSaySo() = runComposeUiTest {
        show(StatisticsFilters(emaId = "1", fromYear = 2030), person = ana)
        onNodeWithText("No stats with these filters").assertExists()
    }

    @Test
    fun aRivalWithSharedTablesOffersTheirTablesTogether() = runComposeUiTest {
        val host = show(StatisticsFilters(emaId = "1", rivalEmaId = "2"), person = ana)
        waitUntil(timeoutMillis = 5_000) { host.standingsCalls >= 2 }
        waitForIdle()
        onNodeWithText("Everything").assertExists()
        onNodeWithText("Only their tables together").performClick()
        waitForIdle()
        assertEquals(true, host.changes.single().together)
        // The comparison names both players.
        assertTrue(count("P2") > 0)
    }

    @Test
    fun theTablesTogetherCompareTheTwoAndATableOpensItsStatistics() = runComposeUiTest {
        val host = show(StatisticsFilters(emaId = "1", rivalEmaId = "2", together = true), person = ana)
        waitUntil(timeoutMillis = 5_000) { host.standingsCalls >= 2 }
        waitForIdle()
        onNodeWithText("P1 at these tables").assertExists()
        // A shared table opens the statistics of that table.
        onAllNodes(hasClickAction() and hasText("Toledo", substring = true))[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals("t-2", host.changes.last().tournamentId)
    }

    @Test
    fun aRivalWithoutSharedTablesIsComparedOverAllTheirTables() = runComposeUiTest {
        val stranger = person("7", madrid to listOf(result(madrid, 3, 2, 2.0, "8")))
        val host = show(StatisticsFilters(emaId = "1", rivalEmaId = "7"), person = ana, rival = stranger)
        waitUntil(timeoutMillis = 5_000) { host.standingsCalls >= 2 }
        waitForIdle()
        assertEquals(0, count("Only their tables together"))
        assertTrue(count("P7") > 0)
    }

    @Test
    fun theComparisonStillShowsWhenThePlacesCannotLoad() = runComposeUiTest {
        val host = show(StatisticsFilters(emaId = "1", rivalEmaId = "2"), person = ana, standingsFail = true)
        waitUntil(timeoutMillis = 5_000) { host.standingsCalls >= 1 }
        waitForIdle()
        assertTrue(count("P2") > 0)
    }

    @Test
    fun anUnknownRivalShowsWithItsEmaNumber() = runComposeUiTest {
        show(StatisticsFilters(emaId = "1", rivalEmaId = "5"), person = ana, rival = null)
        onNodeWithText("P1").assertExists()
    }

    @Test
    fun oneTableShowsItsStatisticsOrSaysThereAreNone() = runComposeUiTest {
        show(StatisticsFilters(tournamentId = "t-1", roundId = 1, tableId = 1), tournament = tournamentData())
        assertEquals(0, count("No statistics yet"))
    }

    @Test
    fun aTableThatDoesNotExistHasNoStatistics() = runComposeUiTest {
        show(StatisticsFilters(tournamentId = "t-1", roundId = 9, tableId = 9), tournament = tournamentData())
        onNodeWithText("No statistics yet").assertExists()
    }

    @Test
    fun oneSeatShowsItsPlaceAndItsNumbers() = runComposeUiTest {
        show(StatisticsFilters(tournamentId = "t-1", slotId = 1), tournament = tournamentData())
        assertEquals(0, count("No statistics yet"))
    }

    @Test
    fun aSeatWithoutNumbersHasNoStatistics() = runComposeUiTest {
        show(StatisticsFilters(tournamentId = "t-1", slotId = 99), tournament = tournamentData())
        onNodeWithText("No statistics yet").assertExists()
    }

    @Test
    fun aTournamentBeforeItsFirstTableHasNoStatisticsYet() = runComposeUiTest {
        show(StatisticsFilters(tournamentId = "t-1"), tournament = tournamentData(completed = false))
        onNodeWithText("No statistics yet").assertExists()
    }

    @Test
    fun aTournamentShowsItsPanelAndAPlayerOpensItsSeat() = runComposeUiTest {
        val host = show(StatisticsFilters(tournamentId = "t-1"), tournament = tournamentData())
        onAllNodes(hasClickAction() and hasText("Player 1", substring = true))[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, host.changes.last().slotId)
    }

    @Test
    fun aGroupShowsItsPanelAndAPlayerOpensThePerson() = runComposeUiTest {
        val data = tournamentData()
        val records = GroupRecords(highestTableScore = 40, mostHandsWonAtATable = 3, tableWinRun = 2, handWinRun = 2, noDealInRun = 1)
        val host = show(StatisticsFilters(), group = GroupStatisticsData(StatisticsGroupFilter(), listOf("t-1"), data.statistics, records, data.names, data.profiles))
        assertEquals(0, count("No stats with these filters"))
        // A player of the group opens the statistics of the person, by its EMA number.
        onAllNodes(hasClickAction() and hasText("Player 2", substring = true))[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals("02", host.changes.last().emaId)
    }

    @Test
    fun anEmptyGroupSaysThereAreNoStatistics() = runComposeUiTest {
        val data = tournamentData()
        val records = GroupRecords(highestTableScore = null, mostHandsWonAtATable = null, tableWinRun = 0, handWinRun = null, noDealInRun = null)
        show(StatisticsFilters(), group = GroupStatisticsData(StatisticsGroupFilter(), emptyList(), data.statistics, records, data.names, data.profiles))
        onNodeWithText("No stats with these filters").assertExists()
    }
}
