package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlin.math.abs

internal fun officialResultsExportError(tables: List<RankingTable>): String? {
    if (tables.isEmpty()) {
        return "Official results cannot be exported because the tournament has no tables."
    }

    val tableProblems = tables
        .sortedWith(compareBy({ it.table.roundId }, { it.table.tableId }))
        .mapNotNull(::tableExportProblem)

    if (tableProblems.isEmpty()) return null

    val usesManualPointsWithoutScores = tables.any { rankingTable ->
        val table = rankingTable.table
        !table.usePointsCalculation && table.scoreValues().any { it.value.toIntOrNull() == null }
    }

    return buildString {
        appendLine("Official results cannot be exported.")
        appendLine()
        appendLine(
            "Each table needs four seat assignments, four final scores, and four table-point values. " +
                "Final scores must be whole numbers. Table points must be numbers that total 7.",
        )
        appendLine()
        appendLine("Fix these tables:")
        tableProblems.forEach { appendLine("• $it") }

        if (usesManualPointsWithoutScores) {
            appendLine()
            append(
                "Manual Points does not provide the final scores required for official results. " +
                    "Open each listed table, enable Manual Scores, and enter all four final scores.",
            )
        }
    }.trimEnd()
}

private fun tableExportProblem(rankingTable: RankingTable): String? {
    val table = rankingTable.table
    val problems = buildList {
        addSeatProblems(table)
        addScoreProblems(table)
        addPointProblems(table)

        val usesHands = !table.useTotalsOnly && table.usePointsCalculation
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

private fun MutableList<String>.addPointProblems(table: TableState) {
    val points = table.pointValues()
    val missing = points.filter { it.value.isBlank() }
    val invalid = points.filter {
        it.value.isNotBlank() && !TABLE_POINT_PATTERN.matches(it.value)
    }

    if (missing.isNotEmpty()) {
        add("missing table points for ${formatLabels(missing.map { it.label })}")
    }
    if (invalid.isNotEmpty()) {
        add("table points must be numbers for ${formatLabels(invalid.map { it.label })}")
    }
    if (missing.isEmpty() && invalid.isEmpty()) {
        val total = points.sumOf { it.value.replace(',', '.').toDouble() }
        if (abs(total - EXPECTED_TABLE_POINTS_TOTAL) >= TABLE_POINTS_TOLERANCE) {
            add("table points total ${formatNumber(total)} instead of 7")
        }
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

private fun TableState.pointValues() = listOf(
    SeatValue("East", playerEastPoints.trim()),
    SeatValue("South", playerSouthPoints.trim()),
    SeatValue("West", playerWestPoints.trim()),
    SeatValue("North", playerNorthPoints.trim()),
)

private fun formatLabels(labels: List<String>): String = when (labels.size) {
    0 -> ""
    1 -> labels.single()
    2 -> "${labels[0]} and ${labels[1]}"
    else -> labels.dropLast(1).joinToString(", ") + ", and ${labels.last()}"
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private const val EXPECTED_TABLE_POINTS_TOTAL = 7.0
private const val TABLE_POINTS_TOLERANCE = 0.011
private val TABLE_POINT_PATTERN = Regex("^-?\\d+(?:[,.]\\d+)?$")
