package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableReconcilerTest {
    private fun editor(version: Long = 3, tableId: Int = 1, roundId: Int = 1) =
        TableManagerEditorState.from(syncTable(version = version, tableId = tableId, roundId = roundId), syncHands())

    // ---- known versions

    @Test
    fun knownVersionsUseSummaryRowsOfEveryRound() {
        val known = TableReconciler.knownVersions(
            summaries = listOf(syncSummary(1, 1, 4), syncSummary(2, 3, 9)),
            editors = emptyList(),
        )

        assertEquals(mapOf(TableKey(1, 1) to 4L, TableKey(2, 3) to 9L), known)
    }

    @Test
    fun anOpenEditorBeatsAnOlderSummaryRow() {
        val known = TableReconciler.knownVersions(listOf(syncSummary(1, 1, 2)), listOf(editor(version = 7)))

        assertEquals(7L, known.getValue(TableKey(1, 1)))
    }

    @Test
    fun aNewerSummaryRowBeatsAnOlderEditor() {
        val known = TableReconciler.knownVersions(listOf(syncSummary(1, 1, 9)), listOf(editor(version = 7)))

        assertEquals(9L, known.getValue(TableKey(1, 1)))
    }

    @Test
    fun anEditorWithoutASummaryRowIsStillKnown() {
        val known = TableReconciler.knownVersions(emptyList(), listOf(editor(version = 5, tableId = 4)))

        assertEquals(mapOf(TableKey(1, 4) to 5L), known)
    }

    // ---- reconcile

    @Test
    fun olderServerDataIsIgnored() {
        assertNull(TableReconciler.reconcile(editor(version = 5), syncTable(version = 4), syncHands()))
    }

    @Test
    fun sameVersionIsIgnoredBecauseItIsTheUsersOwnSave() {
        assertNull(TableReconciler.reconcile(editor(version = 5), syncTable(version = 5), syncHands()))
    }

    @Test
    fun newerServerDataReplacesTheEditorWithoutLostChangesWhenNothingWasTyped() {
        val update = assertNotNull(TableReconciler.reconcile(editor(version = 3), syncTable(version = 4, isCompleted = true), syncHands()))

        assertEquals(4L, update.editor.version)
        assertTrue(update.editor.isCompleted)
        assertTrue(update.notice.lostChanges.isEmpty())
        assertFalse(update.notice.saveRejected)
        assertEquals(TableKey(1, 1), update.notice.key)
    }

    @Test
    fun typedValuesAreListedAsLost() {
        val current = editor(version = 3)
        current.hands.first { it.handId == 3 }.handScore = "24"
        current.hands.first { it.handId == 3 }.playerWinnerId = "2"

        val update = assertNotNull(TableReconciler.reconcile(current, syncTable(version = 4), syncHands()))

        val lost = update.notice.lostChanges.associate { it.label to it.value }
        assertEquals("24", lost["Hand 3 · Hand score"])
        assertEquals("2", lost["Hand 3 · Player winner id"])
    }

    @Test
    fun theNewEditorHasNoUnsavedChanges() {
        val current = editor(version = 3)
        current.hands.first().handScore = "16"

        val update = assertNotNull(TableReconciler.reconcile(current, syncTable(version = 4), syncHands()))

        assertFalse(update.editor.hasUnsavedChanges)
    }

    @Test
    fun aTableThatWasNotLoadedBeforeIsAccepted() {
        val update = assertNotNull(TableReconciler.reconcile(null, syncTable(version = 1), syncHands()))

        assertEquals(1L, update.editor.version)
        assertTrue(update.notice.lostChanges.isEmpty())
    }

    @Test
    fun aRejectedSaveReplacesTheEditorEvenWhenTheVersionLooksOlder() {
        val current = editor(version = 5)
        current.hands.first().handScore = "16"

        val update = assertNotNull(TableReconciler.reconcile(current, syncTable(version = 2), syncHands(), saveRejected = true))

        assertEquals(2L, update.editor.version)
        assertTrue(update.notice.saveRejected)
        assertEquals(listOf("Hand 1 · Hand score"), update.notice.lostChanges.map { it.label })
    }

    // ---- lost changes

    @Test
    fun derivedTotalsAreNotListedAsLost() {
        val lost = TableReconciler.lostChangesOf(
            tablePatch = mapOf(
                "playerEastScore" to "32", "playerSouthPoints" to "4", "playerEastId" to "9",
                "manualPlayerEastScore" to "10", "isCompleted" to true,
            ),
            handPatches = emptyMap(),
        )

        assertEquals(
            listOf("Player east id" to "9", "Manual player east score" to "10", "Is completed" to "Yes"),
            lost.map { it.label to it.value },
        )
    }

    @Test
    fun handChangesAreListedInHandOrder() {
        val lost = TableReconciler.lostChangesOf(
            tablePatch = emptyMap(),
            handPatches = mapOf(5 to mapOf("handScore" to "8"), 2 to mapOf("isDone" to false, "handScore" to "")),
        )

        assertEquals(
            listOf(
                "Hand 2 · Is done" to "No",
                "Hand 2 · Hand score" to "empty",
                "Hand 5 · Hand score" to "8",
            ),
            lost.map { it.label to it.value },
        )
    }

    @Test
    fun nothingToLoseGivesAnEmptyList() {
        assertTrue(TableReconciler.lostChangesOf(emptyMap(), emptyMap()).isEmpty())
    }

    @Test
    fun valuesAreShownInPlainWords() {
        assertEquals("empty", null.toDisplayValue())
        assertEquals("empty", "  ".toDisplayValue())
        assertEquals("Yes", true.toDisplayValue())
        assertEquals("No", false.toDisplayValue())
        assertEquals("12", 12.toDisplayValue())
        assertEquals("8", "8".toDisplayValue())
    }

    @Test
    fun fieldNamesBecomeLabels() {
        assertEquals("Player east id", "playerEastId".toFieldLabel())
        assertEquals("Hand score", "handScore".toFieldLabel())
        assertEquals("Is done", "isDone".toFieldLabel())
    }

    // ---- notice wording

    @Test
    fun noticeWordingTellsWhatHappenedToTheTypedValues() {
        val key = TableKey(2, 5)

        assertEquals(
            "Round 2 · Table 5: another user changed this table. It now shows the latest data.",
            TableUpdateNotice(key, emptyList()).headline(),
        )
        assertEquals(
            "Round 2 · Table 5: another user changed this table. Your unsaved changes were replaced.",
            TableUpdateNotice(key, listOf(LostChange("Hand 1 · Hand score", "8"))).headline(),
        )
        assertEquals(
            "Round 2 · Table 5: someone else saved this table first. Your changes were not saved.",
            TableUpdateNotice(key, listOf(LostChange("Hand 1 · Hand score", "8")), saveRejected = true).headline(),
        )
    }
}
