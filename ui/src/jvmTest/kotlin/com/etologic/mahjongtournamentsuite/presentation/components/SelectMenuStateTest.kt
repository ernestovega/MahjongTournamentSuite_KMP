package com.etologic.mahjongtournamentsuite.presentation.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The state of a select menu ignores the click that follows a tap outside of the menu. */
class SelectMenuStateTest {
    private val pastGuard = 450L

    @Test
    fun theMenuStartsClosed() {
        assertFalse(SelectMenuState().expanded)
    }

    @Test
    fun openOpensTheMenu() {
        val state = SelectMenuState()
        state.open()
        assertTrue(state.expanded)
    }

    @Test
    fun closeClosesTheMenu() {
        val state = SelectMenuState().apply { open() }
        state.close()
        assertFalse(state.expanded)
    }

    @Test
    fun aClickRightAfterAClosedMenuDoesNotOpenItAgain() {
        val state = SelectMenuState().apply { open() }
        state.close()
        state.open()
        assertFalse(state.expanded)
    }

    @Test
    fun theMenuOpensAgainAfterTheGuardTime() {
        val state = SelectMenuState().apply { open() }
        state.close()
        Thread.sleep(pastGuard)
        state.open()
        assertTrue(state.expanded)
    }

    @Test
    fun theMenuOpensAgainAtOnceAfterAChoice() {
        val state = SelectMenuState().apply { open() }
        state.choose()
        assertFalse(state.expanded)
        state.open()
        assertTrue(state.expanded)
    }

    @Test
    fun aChoiceClearsNothingOfAnEarlierDismissGuardButNeverBlocksTheNextOpening() {
        val state = SelectMenuState().apply { open() }
        state.close()
        Thread.sleep(pastGuard)
        state.open()
        state.choose()
        state.open()
        assertTrue(state.expanded)
    }

    @Test
    fun requestTrueOpensAndRequestFalseCloses() {
        val state = SelectMenuState()
        state.request(true)
        assertTrue(state.expanded)
        state.request(false)
        assertFalse(state.expanded)
    }

    @Test
    fun requestTrueRightAfterRequestFalseStaysClosed() {
        val state = SelectMenuState().apply { request(true) }
        state.request(false)
        state.request(true)
        assertFalse(state.expanded)
    }
}
