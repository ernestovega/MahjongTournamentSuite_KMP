package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.DrawAssignment
import com.etologic.mahjongtournamentsuite.domain.model.REGISTRATION_TEAM_SIZE
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlin.random.Random

/**
 * Says which confirmed person sits in each slot of a new draw. The draw already mixes the slots, so the people take
 * the slots in a random order too: nobody can choose a slot by registering early.
 * - Without teams, any person takes any slot.
 * - With teams, the 4 people of one registration team take the 4 slots of one team of the draw. [teamOf] gives the
 *   registration team of each EMA number. Returns null when a person has no full team.
 */
fun assignRegistrationsToDraw(
    slots: List<TournamentPlayer>,
    emaIds: List<String>,
    isTeams: Boolean,
    teamOf: (String) -> String?,
    random: Random = Random.Default,
): List<DrawAssignment>? {
    if (slots.size != emaIds.size) return null
    if (!isTeams) {
        return slots.map { it.id }.shuffled(random).zip(emaIds.shuffled(random)) { slot, emaId -> DrawAssignment(slot, emaId) }
    }
    val people = emaIds.groupBy { teamOf(it) }
    if (people.keys.any { it == null } || people.values.any { it.size != REGISTRATION_TEAM_SIZE }) return null
    val drawTeams = slots.groupBy { it.team }.values.toList()
    if (drawTeams.size != people.size || drawTeams.any { it.size != REGISTRATION_TEAM_SIZE }) return null
    return drawTeams.shuffled(random).zip(people.values.shuffled(random)).flatMap { (drawTeam, members) ->
        drawTeam.map { it.id }.shuffled(random).zip(members.shuffled(random)) { slot, emaId -> DrawAssignment(slot, emaId) }
    }
}
