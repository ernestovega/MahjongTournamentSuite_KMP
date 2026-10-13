package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.key
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.topHandScores

internal const val MIN_HAND_SCORE = 8

internal const val MAX_CHICKEN_HAND_SCORE = 12

/**
 * Holds the editable values for one table.
 *
 * The editor keeps hands and manual scores at the same time. Switching modes only changes which
 * values apply. It never clears values from an inactive mode.
 *
 * - Hands mode applies calculated hand totals and calculated table points.
 * - Manual Scores applies manual scores and calculated table points.
 * Legacy point fields remain readable for migration compatibility. The application never exposes
 * them as an editing mode. Saving writes all changed hand and manual values, including values from
 * inactive modes. Loading the table again restores those values when the related mode becomes
 * active. Discard restores the values from the last saved table state.
 */
@Stable
internal class TableManagerEditorState private constructor(
    private val initialTable: TableState,
    initialHands: List<TableHand>,
) {
    val roundId: Int = initialTable.roundId
    val tableId: Int = initialTable.tableId
    val version: Long = initialTable.version
    val playerIds: List<Int> = initialTable.playerIds

    var isCompleted by mutableStateOf(initialTable.isCompleted)
    var useTotalsOnly by mutableStateOf(initialTable.useTotalsOnly)
    var usePointsCalculation by mutableStateOf(initialTable.usePointsCalculation)

    var playerEastId by mutableStateOf("")
    var playerSouthId by mutableStateOf("")
    var playerWestId by mutableStateOf("")
    var playerNorthId by mutableStateOf("")

    private val initialSeatAssignments: SeatAssignments

    var playerEastScore by mutableStateOf(initialManualScores().east)
    var playerSouthScore by mutableStateOf(initialManualScores().south)
    var playerWestScore by mutableStateOf(initialManualScores().west)
    var playerNorthScore by mutableStateOf(initialManualScores().north)

    var playerEastPoints by mutableStateOf(initialManualPoints().east)
    var playerSouthPoints by mutableStateOf(initialManualPoints().south)
    var playerWestPoints by mutableStateOf(initialManualPoints().west)
    var playerNorthPoints by mutableStateOf(initialManualPoints().north)

    /** Sum of the four manual scores, or null until all four are whole numbers. */
    val manualScoreSum: Long?
        get() {
            val scores = manualTableScores()
            return listOf(scores.east, scores.south, scores.west, scores.north)
                .map { it.toLongOrNull() ?: return null }
                .sum()
        }

    /**
     * Sum of the four table scores, or null while it cannot be known. The scores are the manual totals or
     * the ones calculated from the hands. A calculated sum needs at least one counted hand.
     */
    val scoreSum: Long?
        get() {
            if (useTotalsOnly) return manualScoreSum
            if (!isCompleted && hands.none { it.isDone }) return null
            val totals = calculatedHandScoreTotals
            return listOf(totals.east, totals.south, totals.west, totals.north)
                .map { it.toLongOrNull() ?: return null }
                .sum()
        }

    /**
     * The sum that the calculated scores must have. Penalties take points from a player and give them
     * to nobody, so the scores add up to the total of the penalties.
     */
    val expectedScoreSum: Long
        get() {
            val seats = currentSeatIds() ?: return 0L
            return hands
                .filter { (it.isDone || isCompleted) && calculateHandSeatScores(it, seats) != null }
                .sumOf { hand -> (0..3).sumOf { seat -> parsePenalty(hand.currentPenalty(seat)).toLong() } }
        }

    val hands = mutableStateListOf<HandDraftState>()

    val calculatedHandScoreTotals: SeatTextValues
        get() = calculateHandScoreTotals()

    val cumulativeHandScoreSubtotals: List<SeatTextValues>
        get() = calculateCumulativeHandScoreSubtotals()

    /** The result of each hand alone, with its penalties. A hand that does not count has no value. */
    val handResultScores: List<SeatTextValues>
        get() {
            val seats = currentSeatIds() ?: return List(hands.size) { SeatTextValues.EMPTY }
            return hands.map { hand ->
                if (hand.isDone || isCompleted) {
                    calculateHandSeatScores(hand, seats)?.asTextValues() ?: SeatTextValues.EMPTY
                } else {
                    SeatTextValues.EMPTY
                }
            }
        }

    val displayEastScore: String
        get() = displayedTableScores().east
    val displaySouthScore: String
        get() = displayedTableScores().south
    val displayWestScore: String
        get() = displayedTableScores().west
    val displayNorthScore: String
        get() = displayedTableScores().north

    val displayEastPoints: String
        get() = effectiveTablePoints().east
    val displaySouthPoints: String
        get() = effectiveTablePoints().south
    val displayWestPoints: String
        get() = effectiveTablePoints().west
    val displayNorthPoints: String
        get() = effectiveTablePoints().north

    val hasUnsavedChanges: Boolean
        get() = buildTablePatch().isNotEmpty() || buildHandPatches().isNotEmpty()

    /** Manual totals ignore the hands, except to count chicken hands and best hands. */
    val hasInvalidHands: Boolean
        get() = !useTotalsOnly && hands.any { it.isResultSelectionInvalid }

    val chickenHandCount: Int
        get() = hands.count { it.isChickenHand }

    /**
     * Done hands with a score that are among the best hands of this table, from highest to lowest.
     * Same rule as the server (`calculateBestHandScores`): the three highest scores and their ties.
     * Each entry has the score and the winner id, or null when the hand has no winner.
     */
    val bestCompletedHands: List<Pair<Int, String?>>
        get() {
            val scored = hands.filter { it.isDone }.mapNotNull { hand ->
                hand.handScore.trim().toIntOrNull()?.let { it to hand.playerWinnerId.trim().takeIf(String::isNotEmpty) }
            }
            val threshold = topHandScores(scored.map { it.first }).lastOrNull() ?: return emptyList()
            return scored.filter { it.first >= threshold }.sortedByDescending { it.first }
        }

    val hasCompleteSeatPositions: Boolean
        get() = currentSeatIds() != null

    /** Same rules as the server (`tableManagerService.ts`), so the card matches the tables list. */
    private val summaryHasProgress: Boolean
        get() = isCompleted || summaryHasData

    private val summaryHasData: Boolean
        get() {
            val scores = manualTableScores().let { listOf(it.east, it.south, it.west, it.north) }
            val points = manualTablePoints().let { listOf(it.east, it.south, it.west, it.north) }
            return listOf(playerEastId, playerSouthId, playerWestId, playerNorthId).any { it.isNotBlank() } ||
                scores.any { it.isNotBlank() } ||
                points.any { it.isNotBlank() } ||
                hands.any { hand ->
                    hand.isDone || hand.isChickenHand || listOf(
                        hand.playerWinnerId, hand.normalizedLoserId, hand.handScore,
                        hand.playerEastPenalty, hand.playerSouthPenalty,
                        hand.playerWestPenalty, hand.playerNorthPenalty,
                    ).any { it.isNotBlank() }
                }
        }

    private val summaryHasValidManualTotals: Boolean
        get() {
            val scores = manualTableScores().let { listOf(it.east, it.south, it.west, it.north) }
            val points = manualTablePoints().let { listOf(it.east, it.south, it.west, it.north) }
            val validScores = scores.all { it.matches(Regex("^-?\\d+$")) }
            val validPoints = points.all { it.matches(Regex("^-?\\d+(?:[,.]\\d+)?$")) } &&
                kotlin.math.abs(points.sumOf { it.replace(',', '.').toDouble() } - 7.0) < 0.011
            return if (useTotalsOnly) validScores else !usePointsCalculation && validPoints
        }

    /** Hands mode needs valid effective scores. It needs no hands, because scores fall back to the manual ones. */
    private val hasValidEffectiveScores: Boolean
        get() = effectiveTableScores().let { listOf(it.east, it.south, it.west, it.north) }
            .all { it.matches(Regex("^-?\\d+$")) }

    /** Same rules as `calculateTableCompletionStatus` on the server. */
    val completionStatus: CompletionStatus
        get() = when {
            !summaryHasData -> CompletionStatus.Empty
            !hasCompleteSeatPositions -> CompletionStatus.InProgress
            useTotalsOnly -> if (summaryHasValidManualTotals) CompletionStatus.Manual else CompletionStatus.InProgress
            hasValidEffectiveScores -> CompletionStatus.Completed
            else -> CompletionStatus.InProgress
        }

    /** Copies this saved table state into the summary shown in the rounds and tables columns. */
    fun applyTo(summary: TournamentTable): TournamentTable = summary.copy(
        isCompleted = isCompleted,
        useTotalsOnly = useTotalsOnly,
        usePointsCalculation = usePointsCalculation,
        hasProgress = summaryHasProgress,
        hasValidManualTotals = summaryHasValidManualTotals,
        completionStatus = when (completionStatus) {
            CompletionStatus.Empty -> "empty"
            CompletionStatus.InProgress -> "incomplete"
            CompletionStatus.Manual -> "partial"
            CompletionStatus.Completed -> "completed"
        },
        bestHandScores = bestCompletedHands.map { it.first },
        chickenHandCount = chickenHandCount,
        version = version,
    )

    val hasEastSeatChanged: Boolean get() = playerEastId.trim() != initialSeatAssignments.east
    val hasSouthSeatChanged: Boolean get() = playerSouthId.trim() != initialSeatAssignments.south
    val hasWestSeatChanged: Boolean get() = playerWestId.trim() != initialSeatAssignments.west
    val hasNorthSeatChanged: Boolean get() = playerNorthId.trim() != initialSeatAssignments.north

    val hasEastScoreChanged: Boolean get() = playerEastScore.trim() != initialManualScores().east
    val hasSouthScoreChanged: Boolean get() = playerSouthScore.trim() != initialManualScores().south
    val hasWestScoreChanged: Boolean get() = playerWestScore.trim() != initialManualScores().west
    val hasNorthScoreChanged: Boolean get() = playerNorthScore.trim() != initialManualScores().north


    init {
        val initialAssignments = sanitizeSeatAssignments(
            playerIds = playerIds,
            east = initialTable.playerEastId,
            south = initialTable.playerSouthId,
            west = initialTable.playerWestId,
            north = initialTable.playerNorthId,
        )
        initialSeatAssignments = initialAssignments
        playerEastId = initialAssignments.east
        playerSouthId = initialAssignments.south
        playerWestId = initialAssignments.west
        playerNorthId = initialAssignments.north

        hands.addAll(initialHands.map { HandDraftState.from(it) })
    }

    fun setSeatAssignment(seatIndex: Int, playerId: String) {
        val trimmed = playerId.trim()
        when (seatIndex) {
            0 -> {
                playerEastId = trimmed
                if (trimmed.isBlank()) return
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            1 -> {
                playerSouthId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            2 -> {
                playerWestId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            3 -> {
                playerNorthId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
            }

            else -> Unit
        }
    }

    fun enableManualTotals() {
        useTotalsOnly = true
        usePointsCalculation = true
        isCompleted = false
    }

    fun updateManualScore(seatIndex: Int, rawValue: String) {
        val value = rawValue.toSignedIntegerInput()
        if (value != manualTableScores().bySeat(seatIndex)) isCompleted = false
        when (seatIndex) {
            0 -> playerEastScore = value
            1 -> playerSouthScore = value
            2 -> playerWestScore = value
            3 -> playerNorthScore = value
        }
    }

    fun disableManualTotals() {
        useTotalsOnly = false
        usePointsCalculation = true
    }

    /** Returns the patch used by the application. Manual Points is not an application mode. */
    fun buildApplicationTablePatch(): Map<String, Any?> {
        val patch = buildTablePatch().toMutableMap()
        if (!usePointsCalculation) patch["usePointsCalculation"] = true
        return patch
    }

    fun updateCompletedState(value: Boolean) {
        if (!value) {
            isCompleted = false
            return
        }

        hands.forEach { it.markResultFieldsTouched() }
        if (hasInvalidHands) return
        isCompleted = true
    }

    fun buildTablePatch(): Map<String, Any?> {
        val patch = linkedMapOf<String, Any?>()
        val handPatches = buildHandPatches()

        fun putIfChanged(key: String, current: Any?, initial: Any?) {
            if (current != initial) patch[key] = current
        }

        putIfChanged("isCompleted", isCompleted, initialTable.isCompleted)
        putIfChanged("useTotalsOnly", useTotalsOnly, initialTable.useTotalsOnly)
        putIfChanged("usePointsCalculation", usePointsCalculation, initialTable.usePointsCalculation)

        putIfChanged("playerEastId", playerEastId.trim(), initialSeatAssignments.east)
        putIfChanged("playerSouthId", playerSouthId.trim(), initialSeatAssignments.south)
        putIfChanged("playerWestId", playerWestId.trim(), initialSeatAssignments.west)
        putIfChanged("playerNorthId", playerNorthId.trim(), initialSeatAssignments.north)

        val manualScores = manualTableScores()
        val initialManualScores = initialManualScores()
        putIfChanged("manualPlayerEastScore", manualScores.east, initialManualScores.east)
        putIfChanged("manualPlayerSouthScore", manualScores.south, initialManualScores.south)
        putIfChanged("manualPlayerWestScore", manualScores.west, initialManualScores.west)
        putIfChanged("manualPlayerNorthScore", manualScores.north, initialManualScores.north)

        val manualPoints = manualTablePoints()
        val initialManualPoints = initialManualPoints()
        putIfChanged("manualPlayerEastPoints", manualPoints.east, initialManualPoints.east)
        putIfChanged("manualPlayerSouthPoints", manualPoints.south, initialManualPoints.south)
        putIfChanged("manualPlayerWestPoints", manualPoints.west, initialManualPoints.west)
        putIfChanged("manualPlayerNorthPoints", manualPoints.north, initialManualPoints.north)

        if (shouldPersistEffectiveTotals(handPatches.isNotEmpty())) {
            val effectiveScores = effectiveTableScores()
            val effectivePoints = effectiveTablePoints()

            putIfChanged("playerEastScore", effectiveScores.east, initialTable.playerEastScore)
            putIfChanged("playerSouthScore", effectiveScores.south, initialTable.playerSouthScore)
            putIfChanged("playerWestScore", effectiveScores.west, initialTable.playerWestScore)
            putIfChanged("playerNorthScore", effectiveScores.north, initialTable.playerNorthScore)

            putIfChanged("playerEastPoints", effectivePoints.east, initialTable.playerEastPoints)
            putIfChanged("playerSouthPoints", effectivePoints.south, initialTable.playerSouthPoints)
            putIfChanged("playerWestPoints", effectivePoints.west, initialTable.playerWestPoints)
            putIfChanged("playerNorthPoints", effectivePoints.north, initialTable.playerNorthPoints)
        }

        return patch
    }

    fun buildHandPatches(): List<Pair<Int, Map<String, Any?>>> {
        return hands.mapNotNull { draft ->
            val patch = draft.buildPatch()
            if (patch.isEmpty()) null else draft.handId to patch
        }
    }

    fun discard() {
        isCompleted = initialTable.isCompleted
        useTotalsOnly = initialTable.useTotalsOnly
        usePointsCalculation = initialTable.usePointsCalculation
        playerEastId = initialSeatAssignments.east
        playerSouthId = initialSeatAssignments.south
        playerWestId = initialSeatAssignments.west
        playerNorthId = initialSeatAssignments.north
        val scores = initialManualScores()
        playerEastScore = scores.east
        playerSouthScore = scores.south
        playerWestScore = scores.west
        playerNorthScore = scores.north
        val points = initialManualPoints()
        playerEastPoints = points.east
        playerSouthPoints = points.south
        playerWestPoints = points.west
        playerNorthPoints = points.north
        hands.forEach { it.reset() }
    }

    companion object {
        private const val MIN_HAND_SCORE = 8
        private const val LOSER_COUNT = 3

        fun from(
            table: TableState,
            hands: List<TableHand>,
        ): TableManagerEditorState = TableManagerEditorState(table, hands)
    }

    private fun effectiveTableScores(): SeatTextValues {
        return if (useTotalsOnly) {
            manualTableScores()
        } else {
            calculatedHandScoreTotals.ifEmpty { manualTableScores() }
        }
    }

    private fun displayedTableScores(): SeatTextValues {
        return if (!usePointsCalculation) {
            manualTableScores()
        } else {
            effectiveTableScores()
        }
    }

    private fun effectiveTablePoints(): SeatTextValues {
        return when {
            !usePointsCalculation -> manualTablePoints()
            else -> calculatePointsFromScores(effectiveTableScores()).ifEmpty { manualTablePoints() }
        }
    }

    private fun manualTableScores(): SeatTextValues = SeatTextValues(
        east = playerEastScore.trim(),
        south = playerSouthScore.trim(),
        west = playerWestScore.trim(),
        north = playerNorthScore.trim(),
    )

    private fun manualTablePoints(): SeatTextValues = SeatTextValues(
        east = playerEastPoints.trim(),
        south = playerSouthPoints.trim(),
        west = playerWestPoints.trim(),
        north = playerNorthPoints.trim(),
    )

    private fun shouldPersistEffectiveTotals(hasHandChanges: Boolean): Boolean {
        val manualScoresChanged = manualTableScores() != initialManualScores()
        val manualPointsChanged = manualTablePoints() != initialManualPoints()
        if (initialTable.usePointsCalculation != usePointsCalculation) return true
        if (initialTable.useTotalsOnly != useTotalsOnly) return true
        if (useTotalsOnly && manualScoresChanged) return true
        if (!usePointsCalculation && manualPointsChanged) return true
        if (hasHandChanges) return true
        if (playerEastId.trim() != initialSeatAssignments.east) return true
        if (playerSouthId.trim() != initialSeatAssignments.south) return true
        if (playerWestId.trim() != initialSeatAssignments.west) return true
        if (playerNorthId.trim() != initialSeatAssignments.north) return true
        return false
    }

    private fun initialManualScores(): SeatTextValues = SeatTextValues(
        east = initialTable.manualPlayerEastScore.ifBlank { initialTable.playerEastScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        south = initialTable.manualPlayerSouthScore.ifBlank { initialTable.playerSouthScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        west = initialTable.manualPlayerWestScore.ifBlank { initialTable.playerWestScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        north = initialTable.manualPlayerNorthScore.ifBlank { initialTable.playerNorthScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
    )

    private fun initialManualPoints(): SeatTextValues = SeatTextValues(
        east = initialTable.manualPlayerEastPoints.ifBlank {
            initialTable.playerEastPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        south = initialTable.manualPlayerSouthPoints.ifBlank {
            initialTable.playerSouthPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        west = initialTable.manualPlayerWestPoints.ifBlank {
            initialTable.playerWestPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        north = initialTable.manualPlayerNorthPoints.ifBlank {
            initialTable.playerNorthPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
    )

    private fun calculateHandScoreTotals(): SeatTextValues {
        val seats = currentSeatIds() ?: return SeatTextValues.EMPTY
        var totals = SeatIntValues.ZERO
        var hasCalculatedHand = false

        for (hand in hands) {
            if (!hand.isDone && !isCompleted) continue
            val handTotals = calculateHandSeatScores(hand, seats) ?: continue
            totals += handTotals
            hasCalculatedHand = true
        }

        return if (hasCalculatedHand) totals.asTextValues() else SeatTextValues.EMPTY
    }

    private fun calculateCumulativeHandScoreSubtotals(): List<SeatTextValues> =
        cumulativeSubtotalsFor(hands, blankUncalculatedHands = false, allHandsDone = isCompleted)

    /**
     * Running totals for [source]. The hands dialog passes its draft hands, so the subtotals follow
     * unsaved edits. A hand counts only when it is done or [allHandsDone] is true. With
     * [blankUncalculatedHands], a hand that does not count shows no value.
     */
    fun cumulativeSubtotalsFor(
        source: List<HandDraftState>,
        blankUncalculatedHands: Boolean,
        allHandsDone: Boolean,
    ): List<SeatTextValues> {
        val seats = currentSeatIds() ?: return List(source.size) { SeatTextValues.EMPTY }
        var totals = SeatIntValues.ZERO
        var hasCalculatedHand = false

        return source.map { hand ->
            val handTotals = if (hand.isDone || allHandsDone) calculateHandSeatScores(hand, seats) else null
            if (handTotals != null) {
                totals += handTotals
                hasCalculatedHand = true
            }
            when {
                blankUncalculatedHands && handTotals == null -> SeatTextValues.EMPTY
                hasCalculatedHand -> totals.asTextValues()
                else -> SeatTextValues.EMPTY
            }
        }
    }

    private fun calculateHandSeatScores(
        hand: HandDraftState,
        seats: SeatIds,
    ): SeatIntValues? {
        val score = hand.handScore.trim()
        val winnerId = hand.playerWinnerId.trim()
        val loserId = hand.normalizedLoserId

        val base = when {
            hand.isIgnoredForCalculation -> return null
            score.isEmpty() -> return null
            winnerId.isEmpty() && loserId.isEmpty() && score == "0" -> SeatIntValues.ZERO
            winnerId.isEmpty() -> return null
            loserId.isEmpty() -> {
                val handScore = score.toIntOrNull() ?: return null
                val winnerPoints = (handScore + MIN_HAND_SCORE) * LOSER_COUNT
                val loserPoints = -(handScore + MIN_HAND_SCORE)
                seatValuesForWinnerOnly(
                    winnerId = winnerId,
                    seats = seats,
                    winnerValue = winnerPoints,
                    otherValue = loserPoints,
                ) ?: return null
            }
            else -> {
                val handScore = score.toIntOrNull() ?: return null
                val winnerPoints = handScore + (MIN_HAND_SCORE * LOSER_COUNT)
                val loserPoints = -(handScore + MIN_HAND_SCORE)
                seatValuesForWinnerAndLoser(
                    winnerId = winnerId,
                    loserId = loserId,
                    seats = seats,
                    winnerValue = winnerPoints,
                    loserValue = loserPoints,
                    otherValue = -MIN_HAND_SCORE,
                ) ?: return null
            }
        }

        return base.copy(
            east = base.east + parsePenalty(hand.currentPenalty(0)),
            south = base.south + parsePenalty(hand.currentPenalty(1)),
            west = base.west + parsePenalty(hand.currentPenalty(2)),
            north = base.north + parsePenalty(hand.currentPenalty(3)),
        )
    }

    private fun calculatePointsFromScores(scores: SeatTextValues): SeatTextValues {
        return try {
            val ranked = listOf(
                RankedSeat(Seat.EAST, scores.east.toIntOrNull()),
                RankedSeat(Seat.SOUTH, scores.south.toIntOrNull()),
                RankedSeat(Seat.WEST, scores.west.toIntOrNull()),
                RankedSeat(Seat.NORTH, scores.north.toIntOrNull()),
            )
            if (ranked.any { it.score == null }) return SeatTextValues.EMPTY

            val sorted = ranked.sortedByDescending { it.score ?: Int.MIN_VALUE }
            val assigned = when {
                sorted[0].score == sorted[1].score && sorted[1].score == sorted[2].score && sorted[2].score == sorted[3].score ->
                    listOf("1,75", "1,75", "1,75", "1,75")
                sorted[0].score == sorted[1].score && sorted[1].score == sorted[2].score ->
                    listOf("2,33", "2,33", "2,33", "0")
                sorted[1].score == sorted[2].score && sorted[2].score == sorted[3].score ->
                    listOf("4", "1", "1", "1")
                sorted[0].score == sorted[1].score && sorted[2].score == sorted[3].score ->
                    listOf("3", "3", "0,5", "0,5")
                sorted[0].score == sorted[1].score ->
                    listOf("3", "3", "1", "0")
                sorted[1].score == sorted[2].score ->
                    listOf("4", "1,5", "1,5", "0")
                sorted[2].score == sorted[3].score ->
                    listOf("4", "2", "0,5", "0,5")
                else ->
                    listOf("4", "2", "1", "0")
            }

            val bySeat = sorted.mapIndexed { index, rankedSeat -> rankedSeat.seat to assigned[index] }.toMap()
            SeatTextValues(
                east = bySeat[Seat.EAST].orEmpty(),
                south = bySeat[Seat.SOUTH].orEmpty(),
                west = bySeat[Seat.WEST].orEmpty(),
                north = bySeat[Seat.NORTH].orEmpty(),
            )
        } catch (_: Exception) {
            SeatTextValues.EMPTY
        }
    }

    private fun currentSeatIds(): SeatIds? {
        val east = playerEastId.trim()
        val south = playerSouthId.trim()
        val west = playerWestId.trim()
        val north = playerNorthId.trim()
        if (east.isEmpty() || south.isEmpty() || west.isEmpty() || north.isEmpty()) return null
        if (setOf(east, south, west, north).size != 4) return null
        return SeatIds(east = east, south = south, west = west, north = north)
    }

    private fun seatValuesForWinnerOnly(
        winnerId: String,
        seats: SeatIds,
        winnerValue: Int,
        otherValue: Int,
    ): SeatIntValues? {
        return when (winnerId) {
            seats.east -> SeatIntValues(winnerValue, otherValue, otherValue, otherValue)
            seats.south -> SeatIntValues(otherValue, winnerValue, otherValue, otherValue)
            seats.west -> SeatIntValues(otherValue, otherValue, winnerValue, otherValue)
            seats.north -> SeatIntValues(otherValue, otherValue, otherValue, winnerValue)
            else -> null
        }
    }

    private fun seatValuesForWinnerAndLoser(
        winnerId: String,
        loserId: String,
        seats: SeatIds,
        winnerValue: Int,
        loserValue: Int,
        otherValue: Int,
    ): SeatIntValues? {
        if (winnerId == loserId) return null
        if (winnerId !in seats.all || loserId !in seats.all) return null
        return SeatIntValues(
            east = when (seats.east) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            south = when (seats.south) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            west = when (seats.west) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            north = when (seats.north) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
        )
    }

    private fun parsePenalty(value: String): Int = value
        .split(';')
        .sumOf { it.trim().toIntOrNull() ?: 0 }
}
