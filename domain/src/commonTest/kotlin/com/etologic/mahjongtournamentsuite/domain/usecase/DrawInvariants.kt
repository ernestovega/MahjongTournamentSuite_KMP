package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable

/**
 * The rules that every draw of the app must keep (docs/player-count-change.md, "The draw is not touched"). Each
 * returns the list of what breaks it, empty when the draw is right, so a failed test says exactly what is wrong.
 */
object DrawInvariants {
    /** The slots of a new draw: ids 1..N, the default names, and teams of 4 by number only in a team tournament. */
    fun playerProblems(players: List<TournamentPlayer>, numPlayers: Int, isTeams: Boolean): List<String> = buildList {
        if (players.map { it.id } != (1..numPlayers).toList()) add("The slots are not 1..$numPlayers in order")
        players.forEach { player ->
            if (player.name != "Player ${player.id}") add("Slot ${player.id} is named ${player.name}")
            val team = if (isTeams) (player.id - 1) / 4 + 1 else 0
            if (player.team != team) add("Slot ${player.id} is in team ${player.team}, not $team")
        }
    }

    /**
     * The tables: [tablesPerRound] tables of 4 in each of [rounds], numbered 1..T, every [playing] slot once in each
     * round, nobody meets the same rival twice (counting [history], the tables already played), and with
     * [teamOf], two players of one team never share a table.
     */
    fun tableProblems(
        tables: List<TournamentTable>,
        rounds: List<Int>,
        tablesPerRound: Int,
        playing: Set<Int>,
        teamOf: Map<Int, Int> = emptyMap(),
        history: List<TournamentTable> = emptyList(),
    ): List<String> = buildList {
        val byRound = tables.groupBy { it.roundId }
        if (byRound.keys != rounds.toSet()) add("The rounds are ${byRound.keys.sorted()}, not $rounds")
        byRound.forEach { (round, list) ->
            if (list.map { it.tableId }.sorted() != (1..tablesPerRound).toList()) add("Round $round has tables ${list.map { it.tableId }.sorted()}")
            val seated = list.flatMap { it.playerIds }
            if (seated.sorted() != playing.sorted()) add("Round $round seats ${seated.sorted()} instead of each player once")
        }
        tables.forEach { table ->
            if (table.playerIds.size != 4 || table.playerIds.toSet().size != 4) add("Table ${table.roundId}_${table.tableId} has ${table.playerIds}")
            if (teamOf.isNotEmpty()) {
                val teams = table.playerIds.map { teamOf[it] }
                if (teams.toSet().size != teams.size) add("Table ${table.roundId}_${table.tableId} has two players of one team: ${table.playerIds}")
            }
        }
        val met = mutableMapOf<Pair<Int, Int>, String>()
        (history + tables).forEach { table ->
            val ids = table.playerIds.sorted()
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val pair = ids[i] to ids[j]
                    val where = "${table.roundId}_${table.tableId}"
                    met.put(pair, where)?.let { before -> add("Players ${pair.first} and ${pair.second} meet at $before and at $where") }
                }
            }
        }
    }
}
