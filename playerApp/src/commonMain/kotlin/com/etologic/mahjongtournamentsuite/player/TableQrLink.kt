package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import kotlinx.datetime.LocalDateTime

/**
 * The table that the QR of the tournament opens for player [playerId] at [now], a local time of the venue: the table
 * of the round in play or about to start (the round of the reminder of the own table), else the table of the next
 * round that has not started. Null when the player has no such table: the app then stays on My tables.
 * See docs/table-qr.md.
 */
fun Tournament.myQrTable(playerId: Int, now: LocalDateTime, tables: List<TournamentTable>): TournamentTable? {
    val mine = tables.filter { playerId in it.playerIds }
    reminderRound(now)?.let { round -> mine.firstOrNull { it.roundId == round && !it.isCompleted }?.let { return it } }
    return mine.filter { !it.isCompleted && matchPhase(it.roundId, now) == MatchPhase.NOT_STARTED }.minByOrNull { it.roundId }
}

/** Finds the table that the QR of the tournament opens for the signed-in player. */
class TableQrLinkPresenter(private val tournamentRepository: TournamentRepository) {
    /** The table of the signed-in player in [tournamentId], in play or next, or null (no account, not a player, no table left). */
    suspend fun find(tournamentId: String): TableRoute? {
        val slot = (tournamentRepository.listMyTournamentSlots() as? AppResult.Success)?.value
            ?.firstOrNull { it.tournamentId == tournamentId } ?: return null
        val listed = (tournamentRepository.listTournaments() as? AppResult.Success)?.value?.firstOrNull { it.id == tournamentId }
            ?: return null
        if (listed.drawPending || listed.isCompleted) return null
        // The listed tournament can be a minute old. The clocks come fresh, as on the tournament screen.
        val answer = (tournamentRepository.getRoundClocks(tournamentId) as? AppResult.Success)?.value
        answer?.let { ServerTime.learn(it.serverNowMs) }
        val tournament = answer?.let { listed.copy(roundClocks = it.clocks, roundControls = it.controls) } ?: listed
        val tables = (tournamentRepository.listTournamentTables(tournamentId) as? AppResult.Success)?.value.orEmpty()
        val table = tournament.myQrTable(slot.playerId, tournament.currentVenueDateTime(), tables) ?: return null
        return TableRoute(tournamentId, table.roundId, table.tableId)
    }
}
