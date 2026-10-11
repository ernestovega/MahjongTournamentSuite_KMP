package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal data class NormalizedSeats(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
)

internal data class SeatIds(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
) {
    val all: Set<String> = setOf(east, south, west, north)
}

internal data class SeatIntValues(
    val east: Int,
    val south: Int,
    val west: Int,
    val north: Int,
) {
    operator fun plus(other: SeatIntValues): SeatIntValues = SeatIntValues(
        east = east + other.east,
        south = south + other.south,
        west = west + other.west,
        north = north + other.north,
    )

    fun asTextValues(): SeatTextValues = SeatTextValues(
        east = east.toString(),
        south = south.toString(),
        west = west.toString(),
        north = north.toString(),
    )

    companion object {
        val ZERO = SeatIntValues(0, 0, 0, 0)
    }
}

internal data class SeatTextValues(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
) {
    fun bySeat(seatIndex: Int): String = when (seatIndex) {
        0 -> east
        1 -> south
        2 -> west
        3 -> north
        else -> ""
    }

    inline fun ifEmpty(fallback: () -> SeatTextValues): SeatTextValues =
        if (this == EMPTY) fallback() else this

    companion object {
        val EMPTY = SeatTextValues("", "", "", "")
        val ZERO = SeatTextValues("0", "0", "0", "0")
    }
}

internal enum class Seat {
    EAST,
    SOUTH,
    WEST,
    NORTH,
}

internal data class RankedSeat(
    val seat: Seat,
    val score: Int?,
)

internal typealias SeatAssignments = NormalizedSeats

internal fun sanitizeSeatAssignments(
    playerIds: List<Int>,
    east: String,
    south: String,
    west: String,
    north: String,
): SeatAssignments {
    fun parseValid(value: String): Int? {
        val id = value.trim().toIntOrNull() ?: return null
        return id.takeIf { it in playerIds }
    }

    return SeatAssignments(
        east = parseValid(east)?.toString().orEmpty(),
        south = parseValid(south)?.toString().orEmpty(),
        west = parseValid(west)?.toString().orEmpty(),
        north = parseValid(north)?.toString().orEmpty(),
    )
}
