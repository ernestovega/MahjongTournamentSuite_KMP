package com.etologic.mahjongtournamentsuite.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class GenerateTournamentScheduleScenarioTest {
    @Test
    fun rejectsInvalidTournamentSizesBeforeStartingWorkers() = runTest {
        val generateSchedule = GenerateTournamentScheduleBruteForceParallelUseCase()
        assertNull(generateSchedule(15, 3, isTeams = true, maxConcurrency = 1) {})
        assertNull(generateSchedule(16, 0, isTeams = true, maxConcurrency = 1) {})
    }

}
