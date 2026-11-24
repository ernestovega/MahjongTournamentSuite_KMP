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
     * The teams that a removal leaves incomplete, with the players that stay in each: a team that loses some of its
     * players but not all. Teams that leave whole and teams untouched are not in it.
     */
    fun brokenTeams(slots: List<TournamentPlayer>, removedSlotIds: Collection<Int>): Map<Int, List<Int>> {
        val removed = removedSlotIds.toSet()
        return slots.filter { it.team > 0 }.groupBy { it.team }
            .mapValues { (_, members) -> members.filter { it.id !in removed }.map { it.id }.sorted() }
            .filter { (team, kept) -> kept.isNotEmpty() && kept.size < slots.count { it.team == team } }
            .toSortedMap().toMap()
    }

    /**
     * The slots of the new draw: the slots that stay keep their number and their team, and the new ones take the
     * numbers after the highest one (and with teams, the team numbers after the highest team), in groups of 4.
     * Nobody who stays changes number (Ernesto, 22-nov), so the numbers can have gaps after a removal.
     */
    fun targetSlots(current: List<TournamentPlayer>, removedSlotIds: Collection<Int>, target: Int, isTeams: Boolean): List<TournamentPlayer> {
        val removed = removedSlotIds.toSet()
        val kept = current.filter { it.id !in removed }.sortedBy { it.id }
        val added = (target - kept.size).coerceAtLeast(0)
        val firstId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val firstTeam = (current.maxOfOrNull { it.team } ?: 0) + 1
        val keptSlots = kept.map { TournamentPlayer(id = it.id, name = "Player ${it.id}", team = if (isTeams) it.team else 0) }
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
