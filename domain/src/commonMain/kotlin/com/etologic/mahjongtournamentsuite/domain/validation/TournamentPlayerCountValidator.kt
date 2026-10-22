package com.etologic.mahjongtournamentsuite.domain.validation

/** A tournament has at least 16 players, always in groups of 4 (one table). */
object TournamentPlayerCountValidator {
    const val MIN_PLAYERS = 16

    fun isValid(numPlayers: Int): Boolean = numPlayers >= MIN_PLAYERS && numPlayers % 4 == 0
}
