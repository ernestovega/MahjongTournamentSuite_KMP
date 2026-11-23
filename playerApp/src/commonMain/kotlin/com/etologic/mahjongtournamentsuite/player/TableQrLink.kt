package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import kotlinx.datetime.LocalDateTime

/**
 * The round that the QR of a physical table opens at [now], a local time of the venue: the round in play or about to
 * start (the one of the reminder of the own table), else the last round that started, else the last round with
 * hands (rounds played without the Timer), else the first round. See docs/table-qr.md.
 */
fun Tournament.tableQrRound(now: LocalDateTime, tables: List<TournamentTable>): Int {
    if (numRounds < 1) return 1
    val rounds = 1..numRounds
    return reminderRound(now)
        ?: rounds.lastOrNull { matchPhase(it, now) != MatchPhase.NOT_STARTED }
        ?: tables.filter { it.hasProgress || it.isCompleted }.maxOfOrNull { it.roundId }
        ?: 1
}

/** Finds the table that the QR of a physical table opens. */
class TableQrLinkPresenter(private val tournamentRepository: TournamentRepository) {
    /**
     * The table [tableId] of tournament [tournamentId] in the round that the QR opens, or null when the tournament
     * has no such table (no draw yet, or a table number above the last one).
     */
    suspend fun find(tournamentId: String, tableId: Int): TableRoute? {
        val listed = (tournamentRepository.listTournaments() as? AppResult.Success)?.value?.firstOrNull { it.id == tournamentId }
            ?: return null
        if (listed.drawPending) return null
        // The listed tournament can be a minute old. The clocks come fresh, as on the tournament screen.
        val answer = (tournamentRepository.getRoundClocks(tournamentId) as? AppResult.Success)?.value
        answer?.let { ServerTime.learn(it.serverNowMs) }
        val tournament = answer?.let { listed.copy(roundClocks = it.clocks, roundControls = it.controls) } ?: listed
        val tables = (tournamentRepository.listTournamentTables(tournamentId) as? AppResult.Success)?.value.orEmpty()
        val roundId = tournament.tableQrRound(tournament.currentVenueDateTime(), tables)
        return TableRoute(tournamentId, roundId, tableId).takeIf { tables.any { it.roundId == roundId && it.tableId == tableId } }
    }
}
