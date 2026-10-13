package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedTime
import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.displayName

/** One person at the tournament, as a player sees them. */
data class PlayerEntry(
    val slotId: Int,
    val name: String,
    val country: String,
)

data class PlayerTable(
    val table: TableState,
    val hands: List<TableHand>,
) {
    val roundId: Int get() = table.roundId
    val tableId: Int get() = table.tableId
    val doneHands: Int get() = hands.count { it.isDone }
    fun seatIds(): List<Int?> = listOf(
        table.playerEastId, table.playerSouthId, table.playerWestId, table.playerNorthId,
    ).map { it.toIntOrNull() }
}

/** Everything the player screens show for one tournament. */
data class PlayerTournamentView(
    val tournament: Tournament,
    val players: Map<Int, PlayerEntry>,
    val tables: List<PlayerTable>,
    val rankings: TournamentRankings,
    /** Slot of the signed-in player, or null when the slot is not known. */
    val mySlotId: Int?,
    /** Names of the teams by team id. Empty for a tournament without teams. */
    val teamNames: Map<Int, String> = emptyMap(),
) {
    val myTables: List<PlayerTable>
        get() = mySlotId?.let { id -> tables.filter { id in it.seatIds() } }.orEmpty()

    fun nameOf(slotId: Int?): String = slotId?.let { players[it]?.name } ?: "—"

    /** Date and start time of the round (for example "Mon, 12 Oct 10:00", in the device language), or null when none is configured. */
    fun roundStartOf(roundId: Int): String? =
        tournament.roundSchedules.firstOrNull { it.roundId == roundId }
            ?.let {
                listOfNotNull(
                    it.date?.ifBlank { null }?.let { date -> formatLocalizedDate(date, withWeekday = true, withYear = false) },
                    it.startTime?.ifBlank { null }?.let(::formatLocalizedTime),
                ).joinToString(" ")
            }
            ?.ifBlank { null }
}

fun buildPlayerTournamentView(
    tournament: Tournament,
    slots: List<TournamentPlayer>,
    registry: List<Player>,
    tables: List<Pair<TableState, List<TableHand>>>,
    rankings: TournamentRankings,
    myEmaId: String?,
    teams: List<TournamentTeam> = emptyList(),
): PlayerTournamentView {
    val byEma = registry.associateBy { it.emaId }
    val entries = slots.associate { slot ->
        val registered = slot.assignedEmaId?.let(byEma::get)
        val name = registered?.displayName
            ?: slot.nonMember?.let(NonMemberPlayer::displayName)
            ?: "Player ${slot.id}"
        slot.id to PlayerEntry(
            slotId = slot.id,
            name = name,
            country = registered?.country ?: slot.nonMember?.country.orEmpty(),
        )
    }
    val mySlot = myEmaId?.let { ema -> slots.firstOrNull { it.assignedEmaId == ema }?.id }
    return PlayerTournamentView(
        tournament = tournament,
        players = entries,
        tables = tables.map { (table, hands) -> PlayerTable(table, hands) },
        rankings = rankings,
        mySlotId = mySlot,
        teamNames = teams.associate { it.id to it.name },
    )
}

/** How a saved hand ended. */
enum class HandKind { SELF_PICK, DISCARD, DRAW }

/** A hand with no winner is a draw. A winner with no discarder is a self-pick. */
fun TableHand.kind(): HandKind = when {
    playerWinnerId.toIntOrNull() == null -> HandKind.DRAW
    playerLooserId.toIntOrNull() == null -> HandKind.SELF_PICK
    else -> HandKind.DISCARD
}
