package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlin.test.Test
import kotlin.test.assertEquals

class TournamentOrderingTest {
    @Test
    fun sortsRecentTournamentsFirst() {
        val tournaments = listOf(
            tournament(id = "old", createdAt = "2025-01-01T10:00:00Z"),
            tournament(
                id = "updated",
                createdAt = "2024-01-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:00Z",
            ),
            tournament(id = "new", createdAt = "2026-02-01T10:00:00Z"),
            tournament(id = "unknown"),
        )

        assertEquals(
            listOf("new", "old", "updated", "unknown"),
            tournaments.sortedRecentFirst().map(Tournament::id),
        )
    }

    private fun tournament(
        id: String,
        createdAt: String? = null,
        updatedAt: String? = null,
    ) = Tournament(
        id = id,
        name = id,
        isTeams = false,
        numPlayers = 4,
        numRounds = 1,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
