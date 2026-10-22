package com.etologic.mahjongtournamentsuite.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TournamentPlayerCountValidatorTest {
    @Test
    fun rejectsFewerThanSixteenPlayers() {
        listOf(-4, 0, 4, 8, 12).forEach { assertFalse(TournamentPlayerCountValidator.isValid(it), "$it players") }
    }

    @Test
    fun rejectsCountsThatAreNotMultiplesOfFour() {
        listOf(17, 18, 19, 22).forEach { assertFalse(TournamentPlayerCountValidator.isValid(it), "$it players") }
    }

    @Test
    fun acceptsSixteenOrMorePlayersInGroupsOfFour() {
        listOf(16, 20, 60, 128).forEach { assertTrue(TournamentPlayerCountValidator.isValid(it), "$it players") }
    }
}
