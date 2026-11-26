package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadToHeadTest {
    private val madrid = Tournament(id = "t-1", name = "Madrid", isTeams = false, numPlayers = 8, numRounds = 3, eventStartDate = "2025-05-01")
    private val toledo = Tournament(id = "t-2", name = "Toledo", isTeams = false, numPlayers = 8, numRounds = 3, eventStartDate = "2026-02-01")

    private fun result(
        tournament: Tournament,
        round: Int,
        table: Int,
        position: Int,
        points: Double,
        opponent: String,
        behind: Int = 0,
        discardLosses: Int = 0,
        selfDrawLosses: Int = 0,
        handsWon: Int = 0,
        selfDraws: Int = 0,
        bestHand: Int = 0,
    ) = TableResult(
        tournamentId = tournament.id, roundId = round, tableId = table, seat = 0, position = position, score = (points * 10).toInt(),
        points = points, hasHands = true, handsWon = handsWon, selfDraws = selfDraws, bestHand = bestHand,
        rivals = listOf(RivalTally(opponent, tablesBehind = behind, discardLosses = discardLosses, selfDrawLosses = selfDrawLosses)),
    )

    private fun person(ema: String, vararg parts: Pair<Tournament, List<TableResult>>) = PersonStatisticsData(
        ema,
        PlayerProfile("P$ema", ema, "ESP", null),
        PlayerStatistics(0, parts.flatMap { it.second }),
        parts.map { it.first to PlayerStatistics(0, it.second) },
    )

    private val ana = person(
        "1",
        madrid to listOf(
            result(madrid, 1, 1, 1, 4.0, "2", discardLosses = 1, handsWon = 3, selfDraws = 1, bestHand = 24),
            result(madrid, 2, 3, 2, 2.0, "9"),
        ),
        toledo to listOf(result(toledo, 1, 2, 3, 1.0, "2", behind = 1, selfDrawLosses = 2, handsWon = 1)),
    )
    private val bea = person(
        "2",
        madrid to listOf(result(madrid, 1, 1, 3, 1.0, "1", behind = 1, discardLosses = 2, handsWon = 2, bestHand = 40)),
        toledo to listOf(result(toledo, 1, 2, 1, 4.0, "1", handsWon = 4, selfDraws = 2)),
    )

    @Test
    fun bothSidesAtTheSharedTables() {
        val h2h = headToHead(ana, "2", bea)
        assertEquals(listOf("t-2", "t-1"), h2h.tables.map { it.tournament.id }, "The newest table first.")
        assertEquals(1, h2h.me.tablesAbove)
        assertEquals(1, h2h.rival.tablesAbove)
        assertEquals(5.0, h2h.me.points)
        assertEquals(5.0, h2h.rival.points)
        assertEquals(2.0, h2h.me.averagePlace)
        assertEquals(4, h2h.me.handsWon)
        assertEquals(6, h2h.rival.handsWon)
        assertEquals(2, h2h.me.winsOnTheirDiscard, "Bea dealt in twice to Ana.")
        assertEquals(1, h2h.rival.winsOnTheirDiscard, "Ana dealt in once to Bea.")
        assertEquals(2, h2h.rival.selfDraws)
        assertEquals(40, h2h.rival.bestHand)
    }

    @Test
    fun withoutTheRivalDataOnlyWhatTheResultsOfThePersonTell() {
        val h2h = headToHead(ana, "2", rivalData = null)
        assertEquals(2, h2h.tables.size)
        assertNull(h2h.me.winsOnTheirDiscard)
        assertNull(h2h.rival.points)
        assertEquals(1, h2h.rival.tablesAbove)
        assertEquals(1, h2h.rival.winsOnTheirDiscard)
        assertEquals(2, h2h.rival.selfDraws)
        assertNull(h2h.tables.first().theirs)
    }

    @Test
    fun theFiltersOfTheTournamentsApply() {
        val h2h = headToHead(ana, "2", bea) { it.id == "t-1" }
        assertEquals(1, h2h.tables.size)
        assertEquals(1, h2h.me.tablesAbove)
        assertEquals(0, h2h.rival.tablesAbove)
    }

    @Test
    fun withoutSharedTablesThereIsNothingToCompare() {
        val h2h = headToHead(ana, "7", bea)

        assertEquals(emptyList(), h2h.tables)
        assertNull(h2h.me.averagePlace)
        assertNull(h2h.me.bestHand)
        assertNull(h2h.me.tablesAbove, "without shared tables the rival's results tell nothing")
        assertNull(h2h.rival.points)
    }

    @Test
    fun theRivalDataCountsOnlyWhenItHasEverySharedTable() {
        val partial = person("2", madrid to listOf(result(madrid, 1, 1, 3, 1.0, "1")))

        val h2h = headToHead(ana, "2", partial)

        assertNull(h2h.rival.points, "one shared table of the rival is missing")
        assertEquals(1, h2h.tables.count { it.theirs != null })
    }

    @Test
    fun theTalliesOfTheRivalCountOnlyWhenEveryTableNamesThePerson() {
        val other = person(
            "2",
            madrid to listOf(result(madrid, 1, 1, 3, 1.0, "5")),
            toledo to listOf(result(toledo, 1, 2, 1, 4.0, "1")),
        )

        val h2h = headToHead(ana, "2", other)

        assertNull(h2h.me.winsOnTheirDiscard)
        assertEquals(1, h2h.me.tablesAbove, "the places at the tables still tell who finished above")
    }

    @Test
    fun tablesOfATournamentWithoutDatesGoByRound() {
        val undated = madrid.copy(id = "t-3", eventStartDate = null)
        val me = person("1", undated to listOf(result(undated, 1, 1, 1, 4.0, "2"), result(undated, 3, 1, 2, 2.0, "2")))

        assertEquals(listOf(3, 1), headToHead(me, "2", rivalData = null).tables.map { it.mine.roundId })
    }

    @Test
    fun aRivalWithoutAWonHandHasNoBestHand() {
        val quiet = person(
            "2",
            madrid to listOf(result(madrid, 1, 1, 3, 1.0, "1")),
            toledo to listOf(result(toledo, 1, 2, 1, 4.0, "1")),
        )

        assertNull(headToHead(ana, "2", quiet).rival.bestHand)
    }
}
