package com.etologic.mahjongtournamentsuite.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class GenerateTournamentScheduleBruteForceParallelUseCaseTest {
    private val generate = GenerateTournamentScheduleBruteForceParallelUseCase()

    @Test
    fun rejectsInvalidPlayerAndRoundCounts() = runTest {
        assertNull(generate(3, 1, false, 1) {})
        assertNull(generate(4, 0, false, 1) {})
    }

    @Test
    fun createsTheSmallestValidTournament() = runTest {
        val result = generate(
            numPlayers = 4,
            numRounds = 1,
            isTeams = false,
            maxConcurrency = 1,
            onProgress = {},
        )

        val schedule = assertNotNull(result)
        assertEquals(4, schedule.players.size)
        assertEquals(1, schedule.tables.size)
        assertEquals(4, schedule.tables.single().playerIds.size)
    }
}
