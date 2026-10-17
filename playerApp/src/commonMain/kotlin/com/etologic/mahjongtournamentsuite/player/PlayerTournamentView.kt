package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedTime
import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.seatScores

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

    /** True when the four scores were typed by hand and every one of them is a whole number. */
    private val hasValidManualScores: Boolean
        get() = table.seatScores.all { it.trim().toIntOrNull() != null }

    /**
     * A table is complete when it was marked as completed, when all its hands are done,
     * or when it uses manual totals and the four manual scores are valid.
     */
    val isComplete: Boolean
        get() = table.isCompleted ||
            (hands.isNotEmpty() && doneHands >= hands.size) ||
            (table.useTotalsOnly && hasValidManualScores)
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
    /** The statistics of the tournament. They give the level of each player. They are computed once, when first needed. */
    private val statistics: TournamentStatistics by lazy {
        CalculateStatisticsUseCase()(
            players.values.map { TournamentPlayer(id = it.slotId, name = it.name, team = 0) },
            tables.map { RankingTable(it.table, it.hands) },
        )
    }

    private fun statisticsOf(slotId: Int?): PlayerStatistics? =
        slotId?.let { id -> statistics.players.firstOrNull { it.playerId == id } }

    /** The level of a player from the completed tables of this tournament. Null while the player has too few hands. */
    fun levelOf(slotId: Int?): PlayerLevel? = statisticsOf(slotId)?.level

    fun skillScoreOf(slotId: Int?): Double? = statisticsOf(slotId)?.skillScore

    fun isLevelProvisional(slotId: Int?): Boolean = statisticsOf(slotId)?.isLevelProvisional ?: true

    /** The statistics of a player after the round [roundId]. Later rounds do not count. */
    fun statisticsAfter(slotId: Int?, roundId: Int): PlayerStatistics? {
        val stats = statisticsOf(slotId) ?: return null
        return PlayerStatistics(stats.playerId, stats.results.filter { it.roundId <= roundId })
    }

    /**
     * How much the table of [roundId] changed the skill score of a player.
     * It compares the score after the round with the score before it.
     * Null when the player had no score before the round or has none after it.
     */
    fun levelChangeAt(slotId: Int?, roundId: Int): Double? {
        val results = statisticsOf(slotId)?.results ?: return null
        val before = PlayerStatistics(slotId ?: return null, results.filter { it.roundId < roundId }).skillScore ?: return null
        val after = PlayerStatistics(slotId, results.filter { it.roundId <= roundId }).skillScore ?: return null
        return after - before
    }

    val myTables: List<PlayerTable>
        get() = mySlotId?.let { id -> tables.filter { id in it.seatIds() } }.orEmpty()

    fun nameOf(slotId: Int?): String = slotId?.let { players[it]?.name } ?: "-"

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
