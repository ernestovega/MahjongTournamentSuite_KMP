package com.etologic.mahjongtournamentsuite.domain.model

import com.etologic.mahjongtournamentsuite.domain.validation.TournamentPlayerCountValidator

/**
 * A change of the number of players of a tournament before its first hand (docs/player-count-change.md): whole
 * tables leave or come, and the tables of every round are drawn again. The server keeps the people who stay.
 */
data class PlayerCountChangeRequest(
    val tournamentId: String,
    val numTries: Long,
    val players: List<TournamentPlayer>,
    val tables: List<TournamentTable>,
    /** The slots that leave, when the number goes down. With teams, the slots of whole teams. */
    val removedSlotIds: List<Int> = emptyList(),
    /** People of the waiting list of the registrations who take the new slots, in this order. */
    val waitingEmaIds: List<String> = emptyList(),
)

/** The rules of the change, shared by the dialog and its tests. The server checks them again. */
object PlayerCountRules {
    /** One table: the number of players always changes by 4. */
    const val STEP = 4
    const val MAX_PLAYERS = 400

    /** Why the change cannot start, or null when it can. */
    fun blockerOf(tournament: Tournament): PlayerCountBlocker? = when {
        tournament.inRegistration || tournament.drawPending -> PlayerCountBlocker.DRAW_PENDING
        tournament.isCompleted -> PlayerCountBlocker.COMPLETED
        tournament.hasTableData -> PlayerCountBlocker.STARTED
        else -> null
    }

    fun canDecrease(target: Int): Boolean = target - STEP >= TournamentPlayerCountValidator.MIN_PLAYERS

    fun canIncrease(target: Int): Boolean = target + STEP <= MAX_PLAYERS

    /** A valid new number: a multiple of 4, at least 16, and not the current one. */
    fun isValidTarget(current: Int, target: Int): Boolean =
        target != current && target <= MAX_PLAYERS && TournamentPlayerCountValidator.isValid(target)

    /** The slots that must leave to reach [target]. */
    fun slotsToRemove(current: Int, target: Int): Int = (current - target).coerceAtLeast(0)

    /** The new slots of [target]. */
    fun newSlots(current: Int, target: Int): Int = (target - current).coerceAtLeast(0)

    /**
     * The slots that leave: the people the organizer marks, also in a team tournament (Ernesto, 24-nov). Teams that
     * lose someone stay incomplete, and are reorganized afterwards.
     */
    fun removedSlots(chosen: Set<Int>): List<Int> = chosen.sorted()

    /**
     * The teams after the removal and the moves of the organizer ([teamMoves]: slot to its new team), with their
     * players: every team that keeps at least one person.
     */
    fun teamsAfter(slots: List<TournamentPlayer>, removedSlotIds: Collection<Int>, teamMoves: Map<Int, Int> = emptyMap()): Map<Int, List<Int>> {
        val removed = removedSlotIds.toSet()
        return slots.filter { it.team > 0 && it.id !in removed }
            .groupBy({ teamMoves[it.id] ?: it.team }, { it.id })
            .mapValues { (_, ids) -> ids.sorted() }
            .toSortedMap().toMap()
    }

    /** The teams that have fewer than 4 players after the removal and the moves. */
    fun incompleteTeams(slots: List<TournamentPlayer>, removedSlotIds: Collection<Int>, teamMoves: Map<Int, Int> = emptyMap()): Map<Int, List<Int>> =
        teamsAfter(slots, removedSlotIds, teamMoves).filterValues { it.size < STEP }

    /**
     * A proposal to complete the teams (Ernesto, 22 and 24-nov): the most complete of the incomplete teams takes, one by
     * one, the players of the least complete one, until every team has 4 or nobody. The people who leave are a multiple
     * of 4, so the teams always close. Returns the moves, slot to new team, starting from [teamMoves]; a slot back in
     * its own team is not a move.
     */
    fun proposeTeams(slots: List<TournamentPlayer>, removedSlotIds: Collection<Int>, teamMoves: Map<Int, Int> = emptyMap()): Map<Int, Int> {
        val teams = incompleteTeams(slots, removedSlotIds, teamMoves).mapValues { it.value.toMutableList() }.toMutableMap()
        val moves = teamMoves.toMutableMap()
        while (true) {
            val open = teams.filterValues { it.isNotEmpty() && it.size < STEP }
            if (open.size < 2) break
            // Ties go to the lower team number: the same proposal every time.
            val target = open.entries.sortedWith(compareByDescending<Map.Entry<Int, MutableList<Int>>> { it.value.size }.thenBy { it.key }).first().key
            val donor = open.entries.filter { it.key != target }.sortedWith(compareBy<Map.Entry<Int, MutableList<Int>>> { it.value.size }.thenByDescending { it.key }).first().key
            val moved = teams.getValue(donor).max()
            teams.getValue(donor).remove(moved)
            teams.getValue(target).add(moved)
            moves[moved] = target
        }
        val own = slots.associate { it.id to it.team }
        return moves.filter { (slot, team) -> own[slot] != team }
    }

    /**
     * The slots of the new draw: the slots that stay keep their number and their team, and the new ones take the
     * numbers after the highest one (and with teams, the team numbers after the highest team), in groups of 4.
     * Nobody who stays changes number (Ernesto, 22-nov), so the numbers can have gaps after a removal.
     */
    fun targetSlots(
        current: List<TournamentPlayer>,
        removedSlotIds: Collection<Int>,
        target: Int,
        isTeams: Boolean,
        /** With teams, the new team of the people who stay and change team (the step Teams). */
        teamMoves: Map<Int, Int> = emptyMap(),
    ): List<TournamentPlayer> {
        val removed = removedSlotIds.toSet()
        val kept = current.filter { it.id !in removed }.sortedBy { it.id }
        val added = (target - kept.size).coerceAtLeast(0)
        val firstId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val firstTeam = (current.maxOfOrNull { it.team } ?: 0) + 1
        val keptSlots = kept.map { TournamentPlayer(id = it.id, name = "Player ${it.id}", team = if (isTeams) teamMoves[it.id] ?: it.team else 0) }
        val newSlots = (0 until added).map { index ->
            val id = firstId + index
            TournamentPlayer(id = id, name = "Player $id", team = if (isTeams) firstTeam + index / STEP else 0)
        }
        return keptSlots + newSlots
    }

    /** The number after the change: the current one, less the slots that leave, plus the tables added. */
    fun targetOf(current: Int, removed: Int, addedTables: Int): Int = current - removed + STEP * addedTables

    /** How many slots can leave: the tournament keeps at least the minimum of players. */
    fun maxRemovable(current: Int): Int = (current - TournamentPlayerCountValidator.MIN_PLAYERS).coerceAtLeast(0)

    /** True when one more table fits under the maximum. */
    fun canAddTable(current: Int, addedTables: Int): Boolean = current + STEP * (addedTables + 1) <= MAX_PLAYERS

    /**
     * How the people marked to leave fit in whole tables: [missing] more to mark, or [extra] to unmark. Both are 0 when
     * they fit. [missing] is 0 too when marking more would leave fewer players than the minimum.
     */
    fun markedFit(current: Int, marked: Int): MarkedFit {
        val extra = marked % STEP
        if (extra == 0) return MarkedFit(0, 0)
        val missing = STEP - extra
        return MarkedFit(if (marked + missing <= maxRemovable(current)) missing else 0, extra)
    }

    data class MarkedFit(val missing: Int, val extra: Int) {
        val fits: Boolean get() = missing == 0 && extra == 0
    }

    /** True when the choice fits the new number: exactly the slots to remove, and no more waiting people than new slots. */
    fun isChoiceComplete(
        current: Int,
        target: Int,
        chosen: Set<Int>,
        waiting: List<String>,
    ): Boolean {
        if (!isValidTarget(current, target)) return false
        val removed = removedSlots(chosen)
        return if (target < current) {
            removed.size == slotsToRemove(current, target) && waiting.isEmpty()
        } else {
            removed.isEmpty() && waiting.size <= newSlots(current, target)
        }
    }
}

enum class PlayerCountBlocker { DRAW_PENDING, COMPLETED, STARTED }

/**
 * The result of a change of the number of players: the tournament as it is now, and the emails to the people
 * affected. [noEmail] and [emailFailed] hold the EMA numbers of the people who got none, so the organizer tells them.
 */
data class PlayerCountOutcome(
    val tournament: Tournament,
    val emailsSent: Int = 0,
    val noEmail: List<String> = emptyList(),
    val emailFailed: List<String> = emptyList(),
)
