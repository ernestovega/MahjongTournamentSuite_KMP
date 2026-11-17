package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TournamentStandingTest {
    private val place = Standing(
        rank = 5, of = 40, points = 3.0, score = 10, teamRank = 4, chickenRank = 2, bestHandRank = 3,
        teamOf = 10, teamPoints = 7.5, chickenHands = 3, chickenOf = 12, bestHand = 64, bestHandOf = 150,
    )

    private fun standing(
        place: Standing = this.place,
        isTeams: Boolean = true,
        finished: Boolean = true,
        chicken: Boolean = true,
        best: Boolean = true,
    ) = tournamentStandingOf(place, isTeams, finished, chicken, best)

    @Test
    fun showsThePlaceTheTeamPlaceAndTheHandPlacesOfAFinishedTournamentWithTheirDetails() {
        assertEquals(
            TournamentStanding(
                position = 5, of = 40, points = 3.0, score = 10,
                teamRank = 4, teamOf = 10, teamPoints = 7.5,
                chickenRank = 2, chickenHands = 3, chickenOf = 12,
                bestHandRank = 3, bestHand = 64, bestHandOf = 150,
            ),
            standing(),
        )
    }

    @Test
    fun showsTheTeamPlaceOnlyInATournamentWithTeams() {
        val plain = standing(isTeams = false)
        assertNull(plain.teamRank)
        assertNull(plain.teamOf)
        assertNull(plain.teamPoints)
    }

    @Test
    fun showsTheTeamPlaceWhileTheTournamentIsInProgress() {
        assertEquals(4, standing(finished = false).teamRank)
    }

    @Test
    fun showsTheHandPlacesOnlyWhenTheTournamentIsOver() {
        val inProgress = standing(finished = false)
        assertNull(inProgress.chickenRank)
        assertNull(inProgress.bestHandRank)
        assertEquals(0, inProgress.chickenHands)
        assertEquals(0, inProgress.bestHand)
    }

    @Test
    fun showsTheHandPlacesOnlyWhenTheTournamentCountsThem() {
        assertNull(standing(chicken = false).chickenRank)
        assertNull(standing(best = false).bestHandRank)
        assertEquals(3, standing(chicken = false).bestHandRank)
    }

    @Test
    fun showsTheHandPlacesOutsideTheFirstThreeToo() {
        val outside = standing(place.copy(chickenRank = 4, bestHandRank = 10))
        assertEquals(4, outside.chickenRank)
        assertEquals(10, outside.bestHandRank)
    }

    @Test
    fun keepsTheTeamPlaceOutsideTheFirstThree() {
        assertEquals(12, standing(place.copy(teamRank = 12)).teamRank)
    }

    @Test
    fun showsOnlyTheRankingWhenTheServerHasNoOtherRank() {
        val none = standing(place.copy(teamRank = null, chickenRank = null, bestHandRank = null))
        assertEquals(TournamentStanding(position = 5, of = 40, points = 3.0, score = 10, teamOf = 10, teamPoints = 7.5), none)
    }
}
