package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDate
import kotlinx.datetime.LocalDateTime
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedTime
import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
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
    /** The first name only. The hand entry dialogs show it, so they stay short. */
    val firstName: String = name,
    val country: String,
    /** Set when a substitute plays for this person from a round on. Statistics ignore those rounds. */
    val substitution: PlayerSubstitution? = null,
    /** Team id of the person. Zero for a tournament without teams. */
    val team: Int = 0,
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

    /** True when the players set the four seats. Before that, the table only has the players of the draw. */
    val seatsAssigned: Boolean get() = seatIds().none { it == null }

    /** The players of the table: the seats when they are set, otherwise the players of the draw. */
    fun playerSlots(): List<Int> = if (seatsAssigned) seatIds().filterNotNull() else table.playerIds
}

/** How far the tournament is. */
data class TournamentProgress(
    val completedRounds: Int,
    val totalRounds: Int,
    val completedTables: Int,
    val totalTables: Int,
    val ongoingTables: Int,
)

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
            players.values.map { TournamentPlayer(id = it.slotId, name = it.name, team = 0, substitution = it.substitution) },
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
        get() = mySlotId?.let { id -> tables.filter { id in it.playerSlots() } }.orEmpty()

    /** The rounds and tables that are finished, and the tables that are ongoing. */
    val progress: TournamentProgress
        get() {
            val rounds = tournament.numRounds
            val tablesPerRound = tournament.numPlayers / 4
            return TournamentProgress(
                completedRounds = (1..rounds).count { round ->
                    tablesPerRound > 0 && tables.count { it.roundId == round && it.isComplete } >= tablesPerRound
                },
                totalRounds = rounds,
                completedTables = tables.count { it.isComplete },
                totalTables = rounds * tablesPerRound,
                ongoingTables = tables.count { !it.isComplete && it.doneHands > 0 },
            )
        }

    /** The tables where a member of my team sits. Empty for a tournament without teams. */
    val teamTables: List<PlayerTable>
        get() {
            val myTeam = mySlotId?.let { players[it]?.team }?.takeIf { tournament.isTeams && it > 0 } ?: return emptyList()
            return tables.filter { table -> table.playerSlots().any { players[it]?.team == myTeam } }
        }

    /** True when the person is another member of my team. False for me and in a tournament without teams. */
    fun isTeammate(slotId: Int?): Boolean {
        if (slotId == null || slotId == mySlotId || !tournament.isTeams) return false
        val myTeam = mySlotId?.let { players[it]?.team }?.takeIf { it > 0 } ?: return false
        return players[slotId]?.team == myTeam
    }

    fun nameOf(slotId: Int?): String = slotId?.let { players[it]?.name } ?: "-"

    /** The first name of a player, without the surname. */
    fun firstNameOf(slotId: Int?): String = slotId?.let { players[it]?.firstName } ?: "-"

    /** True when the round has started at [now]. A round with no date counts as started. */
    fun roundHasStarted(roundId: Int, now: LocalDateTime): Boolean =
        tournament.matchPhase(roundId, now) != MatchPhase.NOT_STARTED

    fun matchPhaseOf(roundId: Int, now: LocalDateTime): MatchPhase = tournament.matchPhase(roundId, now)

    /** True when the table is complete, or when its time is over and the players played hands. */
    fun isFinished(table: PlayerTable, now: LocalDateTime): Boolean =
        table.isComplete || (table.doneHands > 0 && matchPhaseOf(table.roundId, now) == MatchPhase.OVER)

    /**
     * A table is ongoing from the start of its round until it is finished or its time is over.
     * Players enter hands only then.
     */
    fun isOngoing(table: PlayerTable, now: LocalDateTime): Boolean =
        !table.isComplete && matchPhaseOf(table.roundId, now).let { it == MatchPhase.PLAYING || it == MatchPhase.EXTRA_TIME }

    /** True when the signed-in player can add hands to the table now. */
    fun canAddHands(table: PlayerTable, now: LocalDateTime): Boolean {
        val mine = mySlotId ?: return false
        return mine in table.table.playerIds && !table.table.useTotalsOnly &&
            table.seatsAssigned && isOngoing(table, now)
    }

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
            firstName = (registered?.firstName ?: slot.nonMember?.firstName)?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: name,
            country = registered?.country ?: slot.nonMember?.country.orEmpty(),
            substitution = slot.substitution,
            team = slot.team,
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
