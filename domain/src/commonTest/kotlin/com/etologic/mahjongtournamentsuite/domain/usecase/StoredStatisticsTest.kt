package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.DigestResult
import com.etologic.mahjongtournamentsuite.domain.model.PersonTournamentDigest
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerTotals
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.StatisticsOverview
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
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

    private fun totals(emaId: String, handsPlayed: Int, handsWon: Int = 0, selfDraws: Int = 0, dealIns: Int = 0) = PlayerTotals(
        emaId = emaId, tables = 4, points = 0.0, score = 0, tablesWithHands = 4,
        handsPlayed = handsPlayed, handsWon = handsWon, selfDraws = selfDraws, dealIns = dealIns, penaltyCount = 0,
    )

    private fun tournament(id: String, start: String) =
        Tournament(id = id, name = "Open $id", isTeams = false, numPlayers = 16, numRounds = 4, eventStartDate = start)

    private fun result(emaId: String, position: Int, points: Double, opponent: String? = null) = DigestResult(
        emaId = emaId,
        slotId = 1,
        result = TableResult(
            roundId = 1, tableId = 1, seat = 0, position = position, score = 0, points = points, hasHands = false,
            rivals = listOfNotNull(opponent?.let { RivalTally(it, tablesBehind = 1) }),
        ),
    )
}
