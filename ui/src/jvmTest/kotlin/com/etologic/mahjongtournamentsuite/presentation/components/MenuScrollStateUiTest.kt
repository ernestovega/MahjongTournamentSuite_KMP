package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A dropdown menu opens scrolled to the selected item, or to the start when no item is selected. */
@OptIn(ExperimentalTestApi::class)
class MenuScrollStateUiTest {
    private fun scrollAfterOpening(selectedIndex: Int?, defaultIndex: Int = 0): Int {
        var scroll: ScrollState? = null
        var expanded by mutableStateOf(false)
        runComposeUiTest {
            setContent {
                val state = rememberMenuScrollState(expanded, selectedIndex, defaultIndex)
                scroll = state
                DropdownMenu(expanded = expanded, onDismissRequest = {}, scrollState = state) {
                    repeat(100) { DropdownMenuItem(text = { Text("Item $it") }, onClick = {}) }
                }
            }
            expanded = true
            waitForIdle()
            waitUntil(timeoutMillis = 5_000) { scroll!!.maxValue > 0 }
            waitForIdle()
        }
        return scroll!!.value
    }

    @Test
    fun theMenuScrollsToTheSelectedItem() {
        assertTrue(scrollAfterOpening(selectedIndex = 60) > 0)
    }

    @Test
    fun withoutASelectedItemTheMenuStaysAtTheStart() {
        assertEquals(0, scrollAfterOpening(selectedIndex = null))
    }

    @Test
    fun withoutASelectedItemTheMenuScrollsToTheDefaultItem() {
        assertTrue(scrollAfterOpening(selectedIndex = null, defaultIndex = 60) > 0)
    }
}
