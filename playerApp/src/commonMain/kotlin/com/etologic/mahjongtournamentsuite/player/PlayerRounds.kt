package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase

/** The rounds of a tournament, from 1 to the last one. */
internal fun Tournament.roundIds(): List<Int> = (1..numRounds).toList()

/**
 * The rankings at the end of each round that has a completed table. A round that nobody finished yet has no ranking,
 * so its place shows as a gap.
 */
internal fun PlayerTournamentView.rankingsByRound(): Map<Int, TournamentRankings> {
    val played = tables.filter { it.isComplete }.map { it.roundId }.toSet()
    return CalculateTournamentRankingsUseCase().afterEachRound(
        players = players.values.map { TournamentPlayer(id = it.slotId, name = it.name, team = it.team, substitution = it.substitution) },
        tables = tables.map { RankingTable(it.table, it.hands) },
        includeTeams = tournament.isTeams,
        rounds = tournament.roundIds().filter { it in played },
    )
}

/** The place of the player in [slotId] at the end of each round. */
internal fun Map<Int, TournamentRankings>.placesOfPlayer(slotId: Int): Map<Int, Int> = mapNotNull { (round, rankings) -> rankings.players.firstOrNull { it.playerId == slotId }?.let { round to it.position } }.toMap()

/** The place of the team [teamId] at the end of each round. */
internal fun Map<Int, TournamentRankings>.placesOfTeam(teamId: Int): Map<Int, Int> = mapNotNull { (round, rankings) -> rankings.teams.firstOrNull { it.teamId == teamId }?.let { round to it.position } }.toMap()
