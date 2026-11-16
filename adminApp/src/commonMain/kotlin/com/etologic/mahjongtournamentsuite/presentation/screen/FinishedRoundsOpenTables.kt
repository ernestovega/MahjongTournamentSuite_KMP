package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable

/**
 * The rounds that are over, for the round control. A round is over when:
 * - the tournament is completed,
 * - its clock ran out, extra time included, or
 * - a later round started: it has a clock, or one of its tables has data.
 *
 * The last condition covers the rounds played without the Timer.
 */
internal fun finishedRounds(
    numRounds: Int,
    tables: List<TournamentTable>,
    clocks: List<RoundClock>,
    durationMinutes: Int,
    extraMs: Long,
    serverNowMs: Long,
    tournamentCompleted: Boolean,
): Set<Int> {
    val rounds = 1..numRounds
    if (tournamentCompleted) return rounds.toSet()
    val clockOver = clocks
        .filter { it.elapsedMs(serverNowMs) >= it.durationMs(durationMinutes) + extraMs }
        .map { it.roundId }
        .toSet()
    val started = clocks.map { it.roundId }.toSet() + tables.filter { it.hasProgress || it.isCompleted }.map { it.roundId }
    val lastStarted = started.filter { it in rounds }.maxOrNull() ?: 0
    return rounds.filter { it in clockOver || it < lastStarted }.toSet()
}

/** The tables of the [finished] rounds that are not completed yet, sorted by round and table. */
internal fun openTablesOf(finished: Set<Int>, tables: List<TournamentTable>): List<TableKey> = tables
    .filter { it.roundId in finished && !it.isCompleted }
    .map { TableKey(it.roundId, it.tableId) }
    .sortedWith(compareBy(TableKey::roundId, TableKey::tableId))
