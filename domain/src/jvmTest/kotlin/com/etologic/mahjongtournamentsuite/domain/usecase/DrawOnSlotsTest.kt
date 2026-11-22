package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountRules
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * The numbers of the people who stay do not change when tables leave or come (docs/player-count-change.md): the draw
 * of 1..N goes onto the slots of the tournament, and keeps every rule.
 */
class DrawOnSlotsTest {
    private fun slots(count: Int, isTeams: Boolean) = (1..count).map { TournamentPlayer(it, "Player $it", if (isTeams) (it - 1) / 4 + 1 else 0) }

    private fun check(draw: TournamentScheduleCalcResult, slots: List<TournamentPlayer>, rounds: Int, isTeams: Boolean) {
        val problems = DrawInvariants.tableProblems(
            tables = draw.tables,
            rounds = (1..rounds).toList(),
            tablesPerRound = slots.size / 4,
            playing = slots.map { it.id }.toSet(),
            teamOf = if (isTeams) slots.associate { it.id to it.team } else emptyMap(),
        )
        if (problems.isNotEmpty()) fail(problems.take(10).joinToString("\n"))
        assertEquals(slots.map { it.id }.sorted(), draw.players.map { it.id })
        draw.players.forEach { assertEquals("Player ${it.id}", it.name) }
    }

    @Test
    fun theSlotsThatStayKeepTheirNumbersAndTheNewOnesComeAfterTheHighest() {
        val removed = PlayerCountRules.targetSlots(slots(32, false), listOf(5, 12, 20, 31), 28, isTeams = false)
        assertEquals((1..32).filter { it !in setOf(5, 12, 20, 31) }, removed.map { it.id })
        val added = PlayerCountRules.targetSlots(slots(16, false), emptyList(), 24, isTeams = false)
        assertEquals((1..24).toList(), added.map { it.id })
        // After a removal and later an addition, the new numbers come after the highest one, not in the gaps.
        val again = PlayerCountRules.targetSlots(removed, emptyList(), 32, isTeams = false)
        assertEquals(removed.map { it.id } + (33..36), again.map { it.id })
        val teams = PlayerCountRules.targetSlots(slots(24, true), (5..8).toList(), 20, isTeams = true)
        assertEquals(listOf(1, 3, 4, 5, 6), teams.map { it.team }.distinct())
        val moreTeams = PlayerCountRules.targetSlots(teams, emptyList(), 28, isTeams = true)
        assertEquals(listOf(7, 7, 7, 7, 8, 8, 8, 8), moreTeams.drop(20).map { it.team })
        assertEquals((25..32).toList(), moreTeams.drop(20).map { it.id })
    }

    @Test
    fun aDrawOnOtherSlotsKeepsEveryRule() {
        val draw = DrawTournamentScheduleUseCase()
        listOf(false, true).forEach { isTeams ->
            val target = PlayerCountRules.targetSlots(slots(32, isTeams), if (isTeams) (9..16).toList() else listOf(2, 7, 19, 30, 31, 32, 11, 12), 24, isTeams)
            val result = assertNotNull(runBlocking { draw(24, 4, isTeams, 2) {} })
            check(assertNotNull(result.onSlots(target, isTeams)), target, 4, isTeams)
            // The second way too.
            val second = assertNotNull(runBlocking { DrawTournamentScheduleUseCase(firstLimit = { _, _ -> kotlin.time.Duration.ZERO })(24, 5, isTeams, 2) {} })
            check(assertNotNull(second.onSlots(target, isTeams)), target, 5, isTeams)
        }
    }

    @Test
    fun slotsThatDoNotFitTheDrawGiveNothing() {
        val result = assertNotNull(runBlocking { DrawTournamentScheduleUseCase()(16, 2, true, 2) {} })
        assertNull(result.onSlots(slots(20, true), true))
        assertNull(result.onSlots(slots(16, true).map { if (it.id == 2) it.copy(id = 1) else it }, true))
        assertNull(result.onSlots(slots(16, true).map { if (it.id == 5) it.copy(team = 1) else it }, true))
    }
}
