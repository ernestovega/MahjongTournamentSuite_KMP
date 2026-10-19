package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableManagerEditorStateTest {

    @Test
    fun tableWithoutAnyDataIsEmpty() {
        val editor = TableManagerEditorState.from(blankTableState(), (1..16).map { sampleHand(handId = it) })

        assertEquals(CompletionStatus.Empty, editor.completionStatus)
    }

    @Test
    fun markingEmptyHandAsDoneSetsScoreToZero() {
        val hand = HandDraftState.from(sampleHand(handId = 1))

        hand.updateDoneState(true)

        assertTrue(hand.isDone)
        assertEquals("0", hand.handScore)
    }

    @Test
    fun handWithOnlyChickenCheckedCannotBeDone() {
        val hand = HandDraftState.from(sampleHand(handId = 1))

        hand.updateChickenHand(true)
        hand.updateDoneState(true)

        assertFalse(hand.isDone)
        assertTrue(hand.showValidationError)
    }

    @Test
    fun savedPenaltiesSplitIntoRows() {
        val hand = HandDraftState.from(sampleHand(handId = 1).copy(playerEastPenalty = "-8;-4"))

        assertEquals(2, hand.penaltyRows.size)
        assertEquals("-4", hand.penaltyRows[1].east)
        assertEquals("-8;-4", hand.currentPenalty(0))
    }

    @Test
    fun freshEditorHasNoUnsavedChanges() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = emptyList(),
        )

        assertFalse(editor.hasUnsavedChanges)
        assertTrue(editor.buildTablePatch().isEmpty())
    }

    @Test
    fun manualScoreSumNeedsFourWholeNumbers() {
        assertEquals(0L, TableManagerEditorState.from(sampleTableState(playerEastScore = "32000", playerSouthScore = "-12000", playerWestScore = "-8000", playerNorthScore = "-12000"), emptyList()).manualScoreSum)
        assertNull(TableManagerEditorState.from(sampleTableState(playerNorthScore = ""), emptyList()).manualScoreSum)
    }

    @Test
    fun bestCompletedHandsKeepThreeHighestScoresAndTies() {
        val scores = listOf("8", "40", "30", "30", "30", "12")
        val editor = TableManagerEditorState.from(
            sampleTableState(),
            scores.mapIndexed { index, score -> sampleHand(handId = index + 1, score = score).copy(isDone = true) },
        )

        assertEquals(listOf(40, 30, 30, 30), editor.bestCompletedHands.map { it.first })
    }

    @Test
    fun handSummaryCountsChickenHandsAndFindsBestCompletedHand() {
        val editor = TableManagerEditorState.from(
            sampleTableState(),
            listOf(
                sampleHand(handId = 1, score = "8").copy(isChickenHand = true, isDone = true),
                sampleHand(handId = 2, score = "16").copy(isDone = true),
            ),
        )

        assertEquals(1, editor.chickenHandCount)
        assertEquals(listOf(16, 8), editor.bestCompletedHands.map { it.first })
    }

    @Test
    fun switchingModesKeepsManualScoresAndHands() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = listOf(sampleHand(handId = 1)),
        )
        val hand = editor.hands.single()
        editor.playerEastScore = "33000"
        hand.setWinnerPlayerId("1")
        hand.setLoserPlayerId("2")
        hand.updateHandScore("16")
        hand.updateDoneState(true)

        editor.enableManualTotals()
        assertTrue(editor.useTotalsOnly)
        assertEquals("33000", editor.displayEastScore)

        editor.disableManualTotals()
        assertFalse(editor.useTotalsOnly)
        assertEquals("40", editor.displayEastScore)
        assertEquals("1", hand.playerWinnerId)
        assertEquals("2", hand.normalizedLoserId)
        assertEquals("16", hand.handScore)

        editor.enableManualTotals()
        assertEquals("33000", editor.displayEastScore)
        assertEquals("33000", editor.playerEastScore)
    }

    @Test
    fun tablePointsAlwaysComeFromTheAppliedScores() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = true),
            hands = emptyList(),
        )

        editor.playerEastScore = "33000"
        editor.playerSouthScore = "27000"
        editor.playerWestScore = "21000"
        editor.playerNorthScore = "19000"

        assertEquals("4", editor.displayEastPoints)
        assertEquals("2", editor.displaySouthPoints)
        assertEquals("1", editor.displayWestPoints)
        assertEquals("0", editor.displayNorthPoints)
        assertFalse(editor.buildTablePatch().keys.any { it.endsWith("Points") })
    }

    @Test
    fun aSubstituteWhoLeftDuringTheSessionGetsZeroTablePoints() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = true).copy(forcedZeroPlayerId = "1"),
            hands = emptyList(),
        )

        editor.playerEastScore = "33000"
        editor.playerSouthScore = "27000"
        editor.playerWestScore = "21000"
        editor.playerNorthScore = "19000"

        assertEquals("0", editor.displayEastPoints)
        assertEquals("4", editor.displaySouthPoints)
        assertEquals("2", editor.displayWestPoints)
        assertEquals("1", editor.displayNorthPoints)
    }

    @Test
    fun tablePointsAreEmptyWhileAScoreIsMissing() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(
                useTotalsOnly = true,
                playerEastScore = "",
                playerSouthScore = "",
                playerWestScore = "",
                playerNorthScore = "",
            ),
            hands = emptyList(),
        )

        editor.playerEastScore = "33000"

        assertEquals("", editor.displayEastPoints)
        assertEquals("", editor.displayNorthPoints)
    }

    @Test
    fun savePatchIncludesValuesFromInactiveModesAndHands() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = listOf(sampleHand(handId = 1)),
        )
        val hand = editor.hands.single()
        editor.playerEastScore = "33000"
        hand.setWinnerPlayerId("1")
        hand.setLoserPlayerId("2")
        hand.updateHandScore("16")

        editor.useTotalsOnly = false

        val tablePatch = editor.buildTablePatch()
        val handPatch = editor.buildHandPatches().single().second

        assertEquals("33000", tablePatch["manualPlayerEastScore"])
        assertEquals(false, tablePatch["useTotalsOnly"])
        assertEquals("1", handPatch["playerWinnerId"])
        assertEquals("2", handPatch["playerLooserId"])
        assertEquals("16", handPatch["handScore"])
    }

    @Test
    fun disablingManualScoresKeepsValuesVisibleWhenCalculationIsUnavailable() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = true),
            hands = emptyList(),
        )

        editor.playerEastScore = "33000"
        editor.playerSouthScore = "27000"
        editor.playerWestScore = "21000"
        editor.playerNorthScore = "19000"

        editor.disableManualTotals()

        assertFalse(editor.useTotalsOnly)
        assertEquals("33000", editor.displayEastScore)
        assertEquals("33000", editor.playerEastScore)

        val patch = editor.buildTablePatch()

        assertEquals(false, patch["useTotalsOnly"])
        assertEquals("33000", patch["manualPlayerEastScore"])
        assertEquals("33000", patch["playerEastScore"])
    }

    @Test
    fun loadsManualValuesFromDedicatedFieldsAfterBeingDisabled() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(
                useTotalsOnly = false,
                playerEastScore = "100",
                playerSouthScore = "50",
                playerWestScore = "-50",
                playerNorthScore = "-100",
                manualPlayerEastScore = "32000",
                manualPlayerSouthScore = "28000",
                manualPlayerWestScore = "22000",
                manualPlayerNorthScore = "18000",
            ),
            hands = emptyList(),
        )

        editor.enableManualTotals()

        assertEquals("32000", editor.displayEastScore)
        assertEquals("28000", editor.displaySouthScore)
        assertEquals("4", editor.displayEastPoints)
        assertEquals("2", editor.displaySouthPoints)
    }

    @Test
    fun invalidHandIsIgnoredWithoutBlockingLaterHands() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false),
            hands = listOf(
                sampleHand(handId = 1, winner = "1", loser = "", score = ""),
                sampleHand(handId = 2, winner = "2", loser = "-", score = "8").copy(isDone = true),
            ),
        )

        editor.hands[0].markResultFieldsTouched()

        assertTrue(editor.hands[0].showValidationError)
        assertEquals("-16", editor.displayEastScore)
        assertEquals("48", editor.displaySouthScore)
        assertEquals("-16", editor.displayWestScore)
        assertEquals("-16", editor.displayNorthScore)
    }

    @Test
    fun loserHyphenBehavesAsEmptyTsumoValue() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false),
            hands = listOf(sampleHand(handId = 1, winner = "1", loser = "", score = "8").copy(isDone = true)),
        )

        val hand = editor.hands.single()

        assertEquals("-", hand.playerLooserId)
        assertFalse(hand.hasLoserSelected)

        val patch = hand.buildPatch()

        assertNull(patch["playerLooserId"])
        assertEquals("48", editor.displayEastScore)
    }

    @Test
    fun selectingWinnerClearsMatchingLoser() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "8"))

        hand.setWinnerPlayerId("2")

        assertEquals("2", hand.playerWinnerId)
        assertEquals("-", hand.playerLooserId)
        assertFalse(hand.hasLoserSelected)
    }

    @Test
    fun statusIsEmptyWithoutData() {
        val editor = TableManagerEditorState.from(blankTableState(), emptyList())

        assertEquals(CompletionStatus.Empty, editor.completionStatus)
    }

    @Test
    fun statusIsIncompleteWhenSeatsAreMissing() {
        val editor = TableManagerEditorState.from(
            blankTableState().copy(playerEastId = "1", playerSouthId = "2", manualPlayerEastScore = "10"),
            emptyList(),
        )

        assertEquals(CompletionStatus.InProgress, editor.completionStatus)
    }

    @Test
    fun statusIsIncompleteWhenManualScoresAreNotValid() {
        val editor = TableManagerEditorState.from(
            sampleTableState(useTotalsOnly = true, playerEastScore = "32000", playerSouthScore = "", playerWestScore = "", playerNorthScore = ""),
            emptyList(),
        )

        assertEquals(CompletionStatus.InProgress, editor.completionStatus)
    }

    @Test
    fun statusIsPartiallyCompleteWithSeatsAndValidManualScores() {
        val editor = TableManagerEditorState.from(sampleTableState(useTotalsOnly = true), emptyList())

        assertEquals(CompletionStatus.Manual, editor.completionStatus)
    }

    @Test
    fun statusIsCompletedInHandsModeWithoutHandsWhenScoresAreValid() {
        val editor = TableManagerEditorState.from(
            sampleTableState(
                useTotalsOnly = false,
                manualPlayerEastScore = "32000",
                manualPlayerSouthScore = "28000",
                manualPlayerWestScore = "22000",
                manualPlayerNorthScore = "18000",
            ),
            emptyList(),
        )

        assertEquals(CompletionStatus.Completed, editor.completionStatus)
    }

    @Test
    fun statusIsCompletedInHandsModeWithCalculatedHandScores() {
        val editor = TableManagerEditorState.from(
            blankTableState().copy(
                playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
                useTotalsOnly = false,
            ),
            listOf(sampleHand(handId = 1, winner = "1", loser = "2", score = "8").copy(isDone = true)),
        )

        assertEquals(CompletionStatus.Completed, editor.completionStatus)
    }

    @Test
    fun statusIsIncompleteInHandsModeWhenNoScoresCanBeCalculated() {
        val editor = TableManagerEditorState.from(
            blankTableState().copy(
                playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
                useTotalsOnly = false,
            ),
            emptyList(),
        )

        assertEquals(CompletionStatus.InProgress, editor.completionStatus)
    }

    @Test
    fun statusChangesWhenTheSwitchIsToggled() {
        val editor = TableManagerEditorState.from(sampleTableState(useTotalsOnly = true), emptyList())

        editor.disableManualTotals()
        assertEquals(CompletionStatus.Completed, editor.completionStatus)

        editor.enableManualTotals()
        assertEquals(CompletionStatus.Manual, editor.completionStatus)
    }

    @Test
    fun applyToCopiesTheStatusToTheTableSummary() {
        val editor = TableManagerEditorState.from(sampleTableState(useTotalsOnly = true), emptyList())
        val summary = TournamentTable(
            roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4), isCompleted = false,
            useTotalsOnly = true, hasProgress = false,
        )

        assertEquals("partial", editor.applyTo(summary).completionStatus)

        editor.disableManualTotals()
        assertEquals("completed", editor.applyTo(summary).completionStatus)
    }

    @Test
    fun invalidHandsPreventMarkingTableAsCompleted() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(useTotalsOnly = false),
            hands = listOf(sampleHand(handId = 1, winner = "1", loser = "", score = "")),
        )

        editor.updateCompletedState(true)

        assertFalse(editor.isCompleted)
        assertTrue(editor.hasInvalidHands)
        assertTrue(editor.hands[0].showValidationError)

        editor.hands[0].handScore = "8"
        editor.updateCompletedState(true)

        assertTrue(editor.isCompleted)
    }

    @Test
    fun scoreBelowMinimumMarksHandInvalid() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "8"))

        hand.updateHandScore("7")

        assertTrue(hand.showValidationError)
        assertTrue(hand.isResultSelectionInvalid)
        assertEquals("This hand is invalid and cannot be marked as done or completed.", hand.validationErrorMessage)
        assertEquals(listOf("The minimum score to win a hand is 8."), hand.validationDetailMessages)
    }

    @Test
    fun chickenHandCannotExceedTwelvePointsWhenSelected() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "13"))

        hand.updateChickenHand(true)

        assertTrue(hand.showValidationError)
        assertTrue(hand.isResultSelectionInvalid)
        assertEquals("This hand is invalid and cannot be marked as done or completed.", hand.validationErrorMessage)
        assertEquals(listOf("A chicken hand cannot score more than 12 points."), hand.validationDetailMessages)
    }

    @Test
    fun raisingScoreAboveTwelveInvalidatesAlreadySelectedChickenHand() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "8"))

        hand.updateChickenHand(true)
        hand.updateHandScore("13")

        assertTrue(hand.showValidationError)
        assertTrue(hand.isResultSelectionInvalid)
        assertEquals("This hand is invalid and cannot be marked as done or completed.", hand.validationErrorMessage)
        assertEquals(listOf("A chicken hand cannot score more than 12 points."), hand.validationDetailMessages)
    }

    @Test
    fun missingFieldsAreReportedAsSpecificValidationMessages() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "", loser = "2", score = "8"))

        assertTrue(hand.isResultSelectionInvalid)
        assertEquals(
            listOf(
                "There are missing fields.",
                "A loser cannot be selected without a winner.",
            ),
            hand.validationDetailMessages,
        )
    }

    @Test
    fun incompleteSeatPositionsAreDetected() {
        val editor = TableManagerEditorState.from(
            table = sampleTableState(),
            hands = emptyList(),
        )

        assertTrue(editor.hasCompleteSeatPositions)

        editor.setSeatAssignment(0, "")

        assertFalse(editor.hasCompleteSeatPositions)

        editor.setSeatAssignment(0, "1")

        assertTrue(editor.hasCompleteSeatPositions)
    }

    @Test
    fun invalidHandCannotBeMarkedDone() {
        val hand = HandDraftState.from(sampleHand(handId = 1, winner = "1", loser = "2", score = "7"))

        hand.updateDoneState(true)

        assertFalse(hand.isDone)
        assertTrue(hand.isResultSelectionInvalid)
    }

    @Test
    fun doneStateIsIncludedInHandPatch() {
        val hand = sampleHand(handId = 1, winner = "1", loser = "2", score = "8")

        val editor = HandDraftState.from(hand)
        editor.isDone = true

        val patch = editor.buildPatch()

        assertEquals(true, patch["isDone"])
    }
}

private fun sampleHand(
    handId: Int,
    winner: String = "",
    loser: String = "",
    score: String = "",
) = TableHand(
    handId = handId,
    playerWinnerId = winner,
    playerLooserId = loser,
    handScore = score,
    isChickenHand = false,
    isDone = false,
    playerEastPenalty = "",
    playerSouthPenalty = "",
    playerWestPenalty = "",
    playerNorthPenalty = "",
)

private fun sampleTableState(
    useTotalsOnly: Boolean = true,
    playerEastScore: String = "32000",
    playerSouthScore: String = "28000",
    playerWestScore: String = "22000",
    playerNorthScore: String = "18000",
    manualPlayerEastScore: String = "",
    manualPlayerSouthScore: String = "",
    manualPlayerWestScore: String = "",
    manualPlayerNorthScore: String = "",
) = TableState(
    roundId = 1,
    tableId = 1,
    playerIds = listOf(1, 2, 3, 4),
    playerEastId = "1",
    playerSouthId = "2",
    playerWestId = "3",
    playerNorthId = "4",
    playerEastScore = playerEastScore,
    playerSouthScore = playerSouthScore,
    playerWestScore = playerWestScore,
    playerNorthScore = playerNorthScore,
    manualPlayerEastScore = manualPlayerEastScore,
    manualPlayerSouthScore = manualPlayerSouthScore,
    manualPlayerWestScore = manualPlayerWestScore,
    manualPlayerNorthScore = manualPlayerNorthScore,
    isCompleted = false,
    useTotalsOnly = useTotalsOnly,
)

private fun blankTableState() = sampleTableState(
    playerEastScore = "",
    playerSouthScore = "",
    playerWestScore = "",
    playerNorthScore = "",
).copy(playerEastId = "", playerSouthId = "", playerWestId = "", playerNorthId = "")
