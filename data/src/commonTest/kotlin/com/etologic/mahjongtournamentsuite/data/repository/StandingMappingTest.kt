package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.StandingDto
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import kotlin.test.Test
import kotlin.test.assertEquals

class StandingMappingTest {
    @Test
    fun keepsThePlaceTheTeamPlaceAndThePlacesOfTheHands() {
        val standing = StandingDto(
            tournamentId = "t1", rank = 3, of = 20, points = 4.5, score = 60,
            teamRank = 2, teamOf = 5, chickenRank = 1, bestHandRank = 3,
        ).toStanding()

        assertEquals(Standing(3, 20, 4.5, 60, teamRank = 2, chickenRank = 1, bestHandRank = 3), standing)
    }

    @Test
    fun leavesThePlacesOfTheHandsEmptyWhenTheServerSendsNone() {
        val standing = StandingDto(tournamentId = "t1", rank = 1, of = 4).toStanding()

        assertEquals(Standing(1, 4, 0.0, 0), standing)
    }
}
