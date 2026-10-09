package com.etologic.mahjongtournamentsuite.domain.model

/** The tournament has up to this many best hands. Hands tied with the last one also count. */
const val MAX_BEST_HANDS = 3

/**
 * Keeps the [MAX_BEST_HANDS] highest scores, from highest to lowest.
 * It also keeps every score tied with the last kept score, so a tie never hides a hand.
 */
fun topHandScores(scores: List<Int>): List<Int> {
    val sorted = scores.sortedDescending()
    if (sorted.size <= MAX_BEST_HANDS) return sorted
    val threshold = sorted[MAX_BEST_HANDS - 1]
    return sorted.filter { it >= threshold }
}

/** Best hand scores of the whole tournament. A single table can give more than one score. */
fun tournamentBestHandScores(tables: List<TournamentTable>): List<Int> =
    topHandScores(tables.flatMap { it.bestHandScores })

/** Number of hands of this table that are among the tournament best hands. */
fun TournamentTable.tournamentBestHandCount(tournamentBestScores: List<Int>): Int {
    val threshold = tournamentBestScores.lastOrNull() ?: return 0
    return bestHandScores.count { it >= threshold }
}

/** Scores of the hands of this table that are among the tournament best hands, from highest to lowest. */
fun TournamentTable.tournamentBestHandScores(tournamentBestScores: List<Int>): List<Int> {
    val threshold = tournamentBestScores.lastOrNull() ?: return emptyList()
    return bestHandScores.filter { it >= threshold }
}
