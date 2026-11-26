package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TableHandChangesTest {
    private fun hand(id: Int, done: Boolean = true, score: String = "8", eastPenalty: String = "") = TableHand(
        handId = id,
        playerWinnerId = if (done) "1" else "",
        playerLooserId = if (done) "2" else "",
        handScore = if (done) score else "",
        isChickenHand = false,
        isDone = done,
        playerEastPenalty = eastPenalty,
        playerSouthPenalty = "",
        playerWestPenalty = "",
        playerNorthPenalty = "",
    )

    private val twoHands = listOf(hand(1), hand(2), hand(3, done = false))

    @Test
    fun aNewHandIsAdded() {
        val change = assertNotNull(handListChange(twoHands, listOf(hand(1), hand(2), hand(3)), serial = 1))
        assertEquals(setOf(3), change.added)
        assertEquals(emptySet(), change.edited)
        assertEquals(emptyList(), change.removed)
    }

    @Test
    fun aPenaltyOnAHandInPlayAddsIt() {
        val change = assertNotNull(handListChange(twoHands, listOf(hand(1), hand(2), hand(3, done = false, eastPenalty = "-10")), 1))
        assertEquals(setOf(3), change.added)
    }

    @Test
    fun aCorrectionIsEditedAndADeletionIsRemoved() {
        val change = assertNotNull(handListChange(twoHands, listOf(hand(1, score = "12"), hand(2, done = false), hand(3, done = false)), 1))
        assertEquals(setOf(1), change.edited)
        assertEquals(listOf(hand(2)), change.removed)
    }

    @Test
    fun theSameHandsAreNoChange() {
        assertNull(handListChange(twoHands, twoHands.toList(), 1))
    }

    @Test
    fun theFirstLoadDoesNotAnimate() {
        val tracker = TableChangeTracker()
        assertNull(tracker.update(twoHands, animate = true))
        assertEquals(setOf(3), tracker.update(listOf(hand(1), hand(2), hand(3)), animate = true)?.added)
    }

    @Test
    fun lessMotionAnimatesNothing() {
        val tracker = TableChangeTracker()
        tracker.update(twoHands, animate = false)
        assertNull(tracker.update(listOf(hand(1), hand(2), hand(3)), animate = false))
    }

    @Test
    fun lessMotionStillKeepsTheChangeForTheScreenReader() {
        val tracker = TableChangeTracker()
        tracker.update(twoHands, animate = false)
        assertNull(tracker.latest)
        tracker.update(listOf(hand(1), hand(2), hand(3)), animate = false)
        assertEquals(setOf(3), tracker.latest?.added)
    }
}
