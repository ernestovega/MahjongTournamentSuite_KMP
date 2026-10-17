package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState

internal fun officialResultsExportError(tables: List<RankingTable>): String? {
    if (tables.isEmpty()) {
        return "Official results cannot be exported because the tournament has no tables."
    }

    val tableProblems = tables
        .sortedWith(compareBy({ it.table.roundId }, { it.table.tableId }))
        .mapNotNull(::tableExportProblem)

    if (tableProblems.isEmpty()) return null

    return buildString {
        appendLine("Official results cannot be exported.")
        appendLine()
        appendLine(
            "Each table needs four seat assignments and four final scores. " +
                "Final scores must be whole numbers. Table points come from the final scores.",
        )
        appendLine()
        appendLine("Fix these tables:")
        tableProblems.forEach { appendLine("• $it") }
    }.trimEnd()
}

private fun tableExportProblem(rankingTable: RankingTable): String? {
    val table = rankingTable.table
    val problems = buildList {
        addSeatProblems(table)
        addScoreProblems(table)

        val usesHands = !table.useTotalsOnly
        if (usesHands && !table.isCompleted) {
            add("the table is not marked Completed")
        }
    }

    if (problems.isEmpty()) return null
    return "Round ${table.roundId}, Table ${table.tableId}: ${problems.joinToString("; ")}."
}

private fun MutableList<String>.addSeatProblems(table: TableState) {
    val seats = table.seatValues()
    val invalidSeats = seats.filter { (_, value) ->
        val playerId = value.toIntOrNull()
        playerId == null || playerId !in table.playerIds
    }
    if (invalidSeats.isNotEmpty()) {
        add("missing or invalid seat assignments for ${formatLabels(invalidSeats.map { it.label })}")
    }

    val assignedIds = seats.mapNotNull { it.value.toIntOrNull() }
    if (invalidSeats.isEmpty() && assignedIds.distinct().size != seats.size) {
        add("seat assignments must use each scheduled player once")
    }
}

private fun MutableList<String>.addScoreProblems(table: TableState) {
    val scores = table.scoreValues()
    val missing = scores.filter { it.value.isBlank() }
    val invalid = scores.filter { it.value.isNotBlank() && it.value.toIntOrNull() == null }

    if (missing.isNotEmpty()) {
        add("missing final scores for ${formatLabels(missing.map { it.label })}")
    }
    if (invalid.isNotEmpty()) {
        add("final scores must be whole numbers for ${formatLabels(invalid.map { it.label })}")
    }
}

private data class SeatValue(
    val label: String,
    val value: String,
)

private fun TableState.seatValues() = listOf(
    SeatValue("East", playerEastId.trim()),
    SeatValue("South", playerSouthId.trim()),
    SeatValue("West", playerWestId.trim()),
    SeatValue("North", playerNorthId.trim()),
)

private fun TableState.scoreValues() = listOf(
    SeatValue("East", playerEastScore.trim()),
    SeatValue("South", playerSouthScore.trim()),
    SeatValue("West", playerWestScore.trim()),
    SeatValue("North", playerNorthScore.trim()),
)

private fun formatLabels(labels: List<String>): String = when (labels.size) {
    0 -> ""
    1 -> labels.single()
    2 -> "${labels[0]} and ${labels[1]}"
    else -> labels.dropLast(1).joinToString(", ") + ", and ${labels.last()}"
}
