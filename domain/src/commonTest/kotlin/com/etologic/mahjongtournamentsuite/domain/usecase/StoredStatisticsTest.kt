package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.DigestResult
import com.etologic.mahjongtournamentsuite.domain.model.GroupPerson
import com.etologic.mahjongtournamentsuite.domain.model.GroupRecords
import com.etologic.mahjongtournamentsuite.domain.model.GroupTables
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerTotals
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroup
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsGroupFilter
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.groupPersonOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredStatisticsTest {
    private val first = tournament("a", "2026-01-10")
    private val second = tournament("b", "2026-03-10")
    private val registry = listOf(
        Player(emaId = "1", firstName = "Ana", lastName = "Ruiz", country = "ES"),
        Player(emaId = "2", firstName = "Bo", lastName = "Li", country = "CN"),
    )

    @Test
    fun overviewBuildsPlayersFromTheSumsOfTheServer() {
        val overview = StatisticsOverview(
            tournaments = 3, tablesCompleted = 12, tablesWithHands = 10, handsPlayed = 120, drawnHands = 12,
            winningHands = 108, winningScoreSum = 1080, chickenHands = 4, penaltyCount = 2, highestHand = 24,
            players = listOf(
                totals("2", handsPlayed = 40, handsWon = 20, selfDraws = 5, dealIns = 4),
                totals("1", handsPlayed = 40, handsWon = 10, selfDraws = 8, dealIns = 10),
            ),
        )

        val data = overviewFrom(overview, registry)

        assertEquals(3, data.tournamentCount)
        assertEquals(10.0, data.statistics.averageHandScore)
        assertEquals(24, data.statistics.highestHand)
        assertEquals(12, data.statistics.tablesCompleted)
        // The leaders come from the sums: Bo has the better win rate and the lower deal-in rate.
        val best = data.statistics.winRateLeaders.first()
        assertEquals("BO LI", data.names[best.playerId])
        assertEquals(0.5, best.winRate)
        assertEquals("ANA RUIZ", data.names[data.statistics.selfDrawLeaders.first().playerId])
        assertEquals("CN", data.profiles.getValue(best.playerId).country)
    }

    @Test
    fun overviewNamesAPlayerThatIsNotInTheRegistry() {
        val overview = StatisticsOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, listOf(totals("99", handsPlayed = 8)))

        val data = overviewFrom(overview, registry)

        assertEquals(listOf("EMA 99"), data.names.values.toList())
    }

    @Test
    fun personKeepsTournamentOrderAndSkipsTournamentsWithoutResults() {
        val digests = listOf(
            PersonTournamentDigest("b", listOf(result("1", position = 3, points = 1.0))),
            PersonTournamentDigest("a", listOf(result("1", position = 1, points = 4.0, opponent = "2"))),
        )

        val person = personFrom("1", listOf(second, first, tournament("c", "2026-05-10")), digests, registry)

        assertEquals(listOf("a", "b"), person.tournaments.map { it.first.id })
        assertEquals(2, person.overall.tablesPlayed)
        assertEquals(5.0, person.overall.totalPoints)
        assertEquals("ANA RUIZ", person.profile?.name)
        assertEquals(mapOf("2" to "BO LI"), person.rivalNames)
        assertNull(personFrom("9", listOf(first), digests, registry).profile)
    }

    @Test
    fun aRivalKeepsOnlyTheTablesAgainstThatRival() {
        val digests = listOf(
            PersonTournamentDigest("b", listOf(result("1", position = 3, points = 1.0))),
            PersonTournamentDigest("a", listOf(result("1", position = 1, points = 4.0, opponent = "2"))),
        )
        val person = personFrom("1", listOf(second, first), digests, registry)

        val against = person.only({ true }, rival = "2")

        assertEquals(listOf("a"), against.tournaments.map { it.first.id })
        assertEquals(4.0, against.overall.totalPoints)
        assertEquals(emptyList(), person.only({ true }, rival = "3").tournaments)
    }

    @Test
    fun groupGivesEachPersonAllTheSumsAndKeepsTheBestValuesApart() {
        val group = StatisticsGroup(
            tournamentIds = listOf("t-1", "t-2"),
            tables = GroupTables(tablesCompleted = 8, tablesWithHands = 8, handsPlayed = 100, winningHands = 80, winningScoreSum = 1600, highestHand = 88),
            persons = listOf(
                GroupPerson(emaId = "2", tables = 4, handsPlayed = 50, handsWon = 10, winningScoreSum = 300, tablesWithHands = 4, bestTableScore = 40, handWinRun = 3),
                GroupPerson(emaId = "1", tables = 4, handsPlayed = 50, handsWon = 5, winningScoreSum = 100, tablesWithHands = 4, bestTableScore = 70, tableWinRun = 2),
            ),
        )
        val ana = Player(emaId = "1", firstName = "Ana", lastName = "Lopez", country = "ESP")
        val data = groupFrom(StatisticsGroupFilter(tournamentCountry = "ES"), group, listOf(ana))

        assertEquals(listOf("t-1", "t-2"), data.tournamentIds)
        assertEquals(20.0, data.statistics.averageHandScore)
        // The ids follow the sorted EMA numbers.
        assertEquals(ana.displayName, data.names[1])
        assertEquals("EMA 2", data.names[2])
        val second = data.statistics.players.first { it.playerId == 2 }
        assertEquals(30.0, second.averageWinningScore)
        assertEquals(0.2, second.winRate)
        assertEquals(GroupRecords(highestTableScore = 70, mostHandsWonAtATable = null, tableWinRun = 2, handWinRun = 3, noDealInRun = null), data.records)
    }

    @Test
    fun aPersonOfAGroupCountsRunsInsideEachTournament() {
        val win = TableResult(roundId = 1, tableId = 1, seat = 0, position = 1, score = 30, points = 4.0, hasHands = true, handsPlayed = 4, handsWon = 2)
        val person = groupPersonOf("1", listOf(listOf(win, win.copy(roundId = 2)), listOf(win.copy(score = 50))))

        assertEquals(2, person.tournaments)
        assertEquals(3, person.tables)
        assertEquals(listOf(3, 0, 0, 0), person.places)
        // Two tables in a row in the first tournament. The third one is another tournament.
        assertEquals(2, person.tableWinRun)
        assertEquals(50, person.bestTableScore)
        assertEquals(2, person.mostHandsWonAtATable)
    }

    private fun totals(emaId: String, handsPlayed: Int, handsWon: Int = 0, selfDraws: Int = 0, dealIns: Int = 0) = PlayerTotals(
        emaId = emaId, tables = 4, points = 0.0, score = 0, tablesWithHands = 4,
        handsPlayed = handsPlayed, handsWon = handsWon, selfDraws = selfDraws, dealIns = dealIns, penaltyCount = 0,
    )

    private fun tournament(id: String, start: String) = Tournament(id = id, name = "Open $id", isTeams = false, numPlayers = 16, numRounds = 4, eventStartDate = start)

    private fun result(emaId: String, position: Int, points: Double, opponent: String? = null) = DigestResult(
        emaId = emaId,
        slotId = 1,
        result = TableResult(
            roundId = 1,
            tableId = 1,
            seat = 0,
            position = position,
            score = 0,
            points = points,
            hasHands = false,
            rivals = listOfNotNull(opponent?.let { RivalTally(it, tablesBehind = 1) }),
        ),
    )
}
