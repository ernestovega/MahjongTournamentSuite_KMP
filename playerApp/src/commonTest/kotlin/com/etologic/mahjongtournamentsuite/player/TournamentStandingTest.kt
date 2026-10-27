package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TournamentStandingTest {
    private val place = Standing(rank = 5, of = 40, points = 3.0, score = 10, teamRank = 4, chickenRank = 2, bestHandRank = 3)

    private fun standing(
        place: Standing = this.place,
        isTeams: Boolean = true,
        finished: Boolean = true,
        chicken: Boolean = true,
        best: Boolean = true,
    ) = tournamentStandingOf(place, isTeams, finished, chicken, best)

    @Test
    fun showsThePlaceTheTeamPlaceAndThePrizesOfAFinishedTournament() {
        assertEquals(TournamentStanding(position = 5, teamRank = 4, chickenRank = 2, bestHandRank = 3), standing())
    }

    @Test
    fun showsTheTeamPlaceOnlyInATournamentWithTeams() {
        assertNull(standing(isTeams = false).teamRank)
    }

    @Test
    fun showsTheTeamPlaceWhileTheTournamentIsInProgress() {
        assertEquals(4, standing(finished = false).teamRank)
    }

    @Test
    fun showsThePrizesOfTheHandsOnlyWhenTheTournamentIsOver() {
        val inProgress = standing(finished = false)
        assertNull(inProgress.chickenRank)
        assertNull(inProgress.bestHandRank)
    }

    @Test
    fun showsThePrizesOfTheHandsOnlyWhenTheTournamentCountsThem() {
        assertNull(standing(chicken = false).chickenRank)
        assertNull(standing(best = false).bestHandRank)
        assertEquals(3, standing(chicken = false).bestHandRank)
    }

    @Test
    fun showsThePrizesOfTheHandsOnlyInTheFirstThreePlaces() {
        val outside = standing(place.copy(chickenRank = 4, bestHandRank = 10))
        assertNull(outside.chickenRank)
        assertNull(outside.bestHandRank)
        assertEquals(1, standing(place.copy(chickenRank = 1)).chickenRank)
    }

    @Test
    fun keepsTheTeamPlaceOutsideTheFirstThree() {
        assertEquals(12, standing(place.copy(teamRank = 12)).teamRank)
    }

    @Test
    fun showsNoPrizeWhenTheServerHasNoRank() {
        val none = standing(place.copy(teamRank = null, chickenRank = null, bestHandRank = null))
        assertEquals(TournamentStanding(position = 5), none)
    }
}
