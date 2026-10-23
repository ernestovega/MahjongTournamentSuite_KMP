package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.export_bullet
import mahjongtournamentsuite.adminapp.generated.resources.export_error_message
import mahjongtournamentsuite.adminapp.generated.resources.export_no_tables
import mahjongtournamentsuite.adminapp.generated.resources.export_problem_invalid_scores
import mahjongtournamentsuite.adminapp.generated.resources.export_problem_invalid_seats
import mahjongtournamentsuite.adminapp.generated.resources.export_problem_missing_scores
import mahjongtournamentsuite.adminapp.generated.resources.export_problem_not_completed
import mahjongtournamentsuite.adminapp.generated.resources.export_problem_repeated_seats
import mahjongtournamentsuite.adminapp.generated.resources.export_table_problems
import mahjongtournamentsuite.adminapp.generated.resources.list_and_many
import mahjongtournamentsuite.adminapp.generated.resources.list_and_two
import mahjongtournamentsuite.adminapp.generated.resources.wind_east
import mahjongtournamentsuite.adminapp.generated.resources.wind_north
import mahjongtournamentsuite.adminapp.generated.resources.wind_south
import mahjongtournamentsuite.adminapp.generated.resources.wind_west
import org.jetbrains.compose.resources.StringResource

internal fun officialResultsExportError(tables: List<RankingTable>): UiText? {
    if (tables.isEmpty()) {
        return UiText.of(Res.string.export_no_tables)
    }

    val tableProblems = tables
        .sortedWith(compareBy({ it.table.roundId }, { it.table.tableId }))
        .mapNotNull(::tableExportProblem)

    if (tableProblems.isEmpty()) return null

    val list = UiText.Joined(tableProblems.map { UiText.of(Res.string.export_bullet, it) }, separator = "\n")
    return UiText.of(Res.string.export_error_message, list)
}

private fun tableExportProblem(rankingTable: RankingTable): UiText? {
    val table = rankingTable.table
    val problems = buildList {
        addSeatProblems(table)
        addScoreProblems(table)

        val usesHands = !table.useTotalsOnly
        if (usesHands && !table.isCompleted) {
            add(UiText.of(Res.string.export_problem_not_completed))
        }
    }

    if (problems.isEmpty()) return null
    return UiText.of(Res.string.export_table_problems, table.roundId, table.tableId, UiText.Joined(problems, separator = "; "))
}

private fun MutableList<UiText>.addSeatProblems(table: TableState) {
    val seats = table.seatValues()
    val invalidSeats = seats.filter { (_, value) ->
        val playerId = value.toIntOrNull()
        playerId == null || playerId !in table.playerIds
    }
    if (invalidSeats.isNotEmpty()) {
        add(UiText.of(Res.string.export_problem_invalid_seats, formatLabels(invalidSeats.map { it.label })))
    }

    val assignedIds = seats.mapNotNull { it.value.toIntOrNull() }
    if (invalidSeats.isEmpty() && assignedIds.distinct().size != seats.size) {
        add(UiText.of(Res.string.export_problem_repeated_seats))
    }
}

private fun MutableList<UiText>.addScoreProblems(table: TableState) {
    val scores = table.scoreValues()
    val missing = scores.filter { it.value.isBlank() }
    val invalid = scores.filter { it.value.isNotBlank() && it.value.toIntOrNull() == null }

    if (missing.isNotEmpty()) {
        add(UiText.of(Res.string.export_problem_missing_scores, formatLabels(missing.map { it.label })))
    }
    if (invalid.isNotEmpty()) {
        add(UiText.of(Res.string.export_problem_invalid_scores, formatLabels(invalid.map { it.label })))
    }
}

private data class SeatValue(
    val label: UiText,
    val value: String,
)

private fun wind(resource: StringResource) = UiText.of(resource)

private fun TableState.seatValues() = listOf(
    SeatValue(wind(Res.string.wind_east), playerEastId.trim()),
    SeatValue(wind(Res.string.wind_south), playerSouthId.trim()),
    SeatValue(wind(Res.string.wind_west), playerWestId.trim()),
    SeatValue(wind(Res.string.wind_north), playerNorthId.trim()),
)

private fun TableState.scoreValues() = listOf(
    SeatValue(wind(Res.string.wind_east), playerEastScore.trim()),
    SeatValue(wind(Res.string.wind_south), playerSouthScore.trim()),
    SeatValue(wind(Res.string.wind_west), playerWestScore.trim()),
    SeatValue(wind(Res.string.wind_north), playerNorthScore.trim()),
)

private fun formatLabels(labels: List<UiText>): UiText = when (labels.size) {
    0 -> UiText.Plain("")
    1 -> labels.single()
    2 -> UiText.of(Res.string.list_and_two, labels[0], labels[1])
    else -> UiText.of(
        Res.string.list_and_many,
        UiText.Joined(labels.dropLast(1), separator = ", "),
        labels.last(),
    )
}
