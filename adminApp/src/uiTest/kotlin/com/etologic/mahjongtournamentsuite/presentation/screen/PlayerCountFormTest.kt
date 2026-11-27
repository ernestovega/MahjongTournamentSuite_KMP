package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The choice of the screen of the number of players: its limits, and how it survives a recreation of the screen. */
class PlayerCountFormTest {
    @Test
    fun theChoiceComesBackWholeAfterARecreation() {
        val form = PlayerCountForm(chosen = setOf(3, 9), addedTables = 1, waiting = listOf("10990001"), query = "ana", teamMoves = mapOf(4 to 2, 8 to 1))

        val saved = assertNotNull(with(PlayerCountForm.Saver) { SaverScope { true }.save(form) })

        assertEquals(form, PlayerCountForm.Saver.restore(saved))
    }

    @Test
    fun nobodyMoreIsMarkedThanTheTablesAllow() {
        val full = PlayerCountForm(chosen = setOf(1, 2))
        assertEquals(full, full.toggleChosen(3, max = 2), "the limit is reached")
        assertEquals(full, PlayerCountForm(chosen = setOf(1, 2), addedTables = 1).toggleChosen(3, max = 4).copy(addedTables = 0), "adding tables excludes removing")

        val waiting = PlayerCountForm(waiting = listOf("a"))
        assertEquals(waiting, waiting.toggleWaiting("b", max = 1))
        assertEquals(listOf("a", "b"), waiting.toggleWaiting("b", max = 2).waiting)
        assertEquals(emptyList(), waiting.toggleWaiting("a", max = 1).waiting)
    }
}
