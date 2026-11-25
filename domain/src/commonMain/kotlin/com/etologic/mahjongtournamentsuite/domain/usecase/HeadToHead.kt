package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament

/** One table where two players sat together. [theirs] is null when the results of the rival are not loaded. */
data class SharedTable(val tournament: Tournament, val mine: TableResult, val theirs: TableResult?)

/** What one of the two players did at the tables that they shared. A null value is not known. */
data class HeadToHeadSide(
    /** Tables where this player finished above the other one. */
    val tablesAbove: Int?,
    /** Tables that this player won, first place. */
    val tableWins: Int?,
    val averagePlace: Double?,
    val points: Double?,
    val score: Int?,
    val handsWon: Int?,
    /** Hands that this player won on a discard of the other one. */
    val winsOnTheirDiscard: Int?,
    val selfDraws: Int?,
    val bestHand: Int?,
)

/** Two players at the tables that they shared, the newest table first. */
data class HeadToHead(val tables: List<SharedTable>, val me: HeadToHeadSide, val rival: HeadToHeadSide)

/**
 * The tables of [me] against [rival] in the tournaments that [keep] keeps. [rivalData] gives the numbers of the
 * rival. Without it, the numbers of the rival that the results of [me] tell still show: how often the rival finished
 * above, the wins on the discards of [me] and the self-draws.
 */
fun headToHead(
    me: PersonStatisticsData,
    rival: String,
    rivalData: PersonStatisticsData?,
    keep: (Tournament) -> Boolean = { true },
): HeadToHead {
    val theirResults = rivalData?.tournaments.orEmpty().flatMap { it.second.results }
        .associateBy { Triple(it.tournamentId, it.roundId, it.tableId) }
    val tables = me.tournaments.filter { keep(it.first) }.flatMap { (tournament, statistics) ->
        statistics.results.filter { result -> result.rivals.any { it.opponent == rival } }.map { mine ->
            SharedTable(tournament, mine, theirResults[Triple(mine.tournamentId, mine.roundId, mine.tableId)])
        }
    }.sortedWith(compareByDescending<SharedTable> { it.tournament.eventStartDate.orEmpty() }.thenByDescending { it.mine.roundId })
    val tallies = tables.mapNotNull { table -> table.mine.rivals.firstOrNull { it.opponent == rival } }
    val theirs = tables.mapNotNull { it.theirs }.takeIf { it.size == tables.size && tables.isNotEmpty() }
    val theirTallies = theirs?.mapNotNull { result -> result.rivals.firstOrNull { it.opponent == me.emaId } }?.takeIf { it.size == tables.size }
    val mine = tables.map { it.mine }
    return HeadToHead(
        tables = tables,
        me = HeadToHeadSide(
            tablesAbove = theirs?.let { tables.count { t -> t.theirs != null && t.mine.position < t.theirs.position } }
                ?: theirTallies?.sumOf { it.tablesBehind },
            tableWins = mine.count { it.position == 1 },
            averagePlace = mine.takeIf { it.isNotEmpty() }?.map { it.position }?.average(),
            points = mine.sumOf { it.points },
            score = mine.sumOf { it.score },
            handsWon = mine.sumOf { it.handsWon },
            winsOnTheirDiscard = theirTallies?.sumOf { it.discardLosses },
            selfDraws = mine.sumOf { it.selfDraws },
            bestHand = mine.maxOfOrNull { it.bestHand }?.takeIf { it > 0 },
        ),
        rival = HeadToHeadSide(
            tablesAbove = tallies.sumOf { it.tablesBehind },
            tableWins = theirs?.count { it.position == 1 },
            averagePlace = theirs?.map { it.position }?.average(),
            points = theirs?.sumOf { it.points },
            score = theirs?.sumOf { it.score },
            handsWon = theirs?.sumOf { it.handsWon },
            winsOnTheirDiscard = tallies.sumOf { it.discardLosses },
            selfDraws = tallies.sumOf { it.selfDrawLosses },
            bestHand = theirs?.maxOfOrNull { it.bestHand }?.takeIf { it > 0 },
        ),
    )
}
