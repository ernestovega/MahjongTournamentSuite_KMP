package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.components.SELECT_SEARCH_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The player menu in a table cell: its options, its keys when closed, and its disabled state. */
@OptIn(ExperimentalTestApi::class)
class PlayerDropdownUiTest {
    private val names = mapOf(1 to "Ana", 2 to "Beto", 3 to "Carla", 4 to "Dani")

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun ComposeUiTest.item(text: String) = onNode(hasText(text) and !hasTestTag(FIELD))
    private fun ComposeUiTest.openField(probe: String) {
        onNodeWithTag(FIELD).performClick()
        waitUntil(timeoutMillis = 5_000) { count(probe) > 0 }
        waitForIdle()
    }

    private fun ComposeUiTest.show(
        value: String = "",
        enabled: Boolean = true,
        excluded: Set<Int> = emptySet(),
        emptyLabel: String? = "-",
        suffixes: Map<Int, String> = emptyMap(),
        onChange: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
        onKey: (Key) -> Boolean = { false },
    ) = setContent {
        val focus = FocusRequester()
        PlayerDropdown(
            modifier = Modifier.testTag(FIELD),
            label = null,
            playerIds = names.keys.toList(),
            value = value,
            enabled = enabled,
            onChange = onChange,
            onDismiss = onDismiss,
            playerNamesById = names,
            showPlayerId = true,
            optionSuffixById = suffixes,
            excludedPlayerIds = excluded,
            emptyOptionLabel = emptyLabel,
            focusRequester = focus,
            onNavigateLeft = { onKey(Key.DirectionLeft) },
            onNavigateRight = { onKey(Key.DirectionRight) },
            onNavigateUp = { onKey(Key.DirectionUp) },
            onNavigateDown = { onKey(Key.DirectionDown) },
        )
    }

    @Test
    fun theMenuListsTheEmptyOptionAndEveryPlayerWithItsNumber() = runComposeUiTest {
        show()
        openField("1 - Ana")
        listOf("2 - Beto", "3 - Carla", "4 - Dani").forEach { assertEquals(1, count(it), it) }
        assertTrue(count("-") >= 1)
    }

    @Test
    fun aPlayerInTheExcludedSetIsNotInTheMenu() = runComposeUiTest {
        show(excluded = setOf(2))
        openField("1 - Ana")
        assertEquals(0, count("2 - Beto"))
    }

    @Test
    fun aSuffixShowsAfterThePlayerName() = runComposeUiTest {
        show(suffixes = mapOf(3 to "seat 3"))
        openField("1 - Ana")
        assertEquals(1, count("3 - Carla (seat 3)"))
    }

    @Test
    fun theFieldShowsTheSelectedPlayerAndTheMenuStillListsEveryPlayer() = runComposeUiTest {
        show(value = "2")
        assertEquals(1, count("2 - Beto"))
        openField("1 - Ana")
        assertEquals(2, count("2 - Beto"))
    }

    @Test
    fun choosingAPlayerGivesItsNumberAndCallsOnDismiss() = runComposeUiTest {
        var value by mutableStateOf("")
        var dismissed = 0
        show(onChange = { value = it }, onDismiss = { dismissed++ })
        openField("3 - Carla")
        item("3 - Carla").performClick()
        waitForIdle()
        assertEquals("3", value)
        assertEquals(1, dismissed)
    }

    @Test
    fun choosingTheEmptyOptionGivesItsLabel() = runComposeUiTest {
        var value: String? = null
        show(value = "2", onChange = { value = it })
        openField("1 - Ana")
        onAllNodesWithText("-")[0].performClick()
        waitForIdle()
        assertEquals("-", value)
    }

    @Test
    fun aTapOutsideCallsOnDismissWithoutAChange() = runComposeUiTest {
        var value: String? = null
        var dismissed = 0
        show(onChange = { value = it }, onDismiss = { dismissed++ })
        openField("1 - Ana")
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        assertEquals(null, value)
        assertEquals(1, dismissed)
    }

    @Test
    fun withTheMenuClosedEnterOpensTheMenu() = runComposeUiTest {
        show()
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        onNodeWithTag(FIELD).performClick()
        // The first tap opened the menu and the second closed it. Enter opens it again after the guard time.
        waitForIdle()
        Thread.sleep(450)
        onNodeWithTag(FIELD).performKeyInput { pressKey(Key.Enter) }
        waitUntil(timeoutMillis = 5_000) { count("1 - Ana") > 0 }
    }

    @Test
    fun withTheMenuClosedTheArrowKeysMoveToTheNeighborCells() = runComposeUiTest {
        val keys = mutableListOf<Key>()
        show(onKey = { keys += it; true })
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        onNodeWithTag(FIELD).performClick()
        waitForIdle()
        Thread.sleep(450)
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown).forEach { key ->
            onNodeWithTag(FIELD).performKeyInput { pressKey(key) }
        }
        waitForIdle()
        assertEquals(listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown), keys)
        assertEquals(0, count("1 - Ana"))
    }

    @Test
    fun aDisabledFieldCanOpenToReadButChoosesNothing() = runComposeUiTest {
        var value: String? = null
        show(enabled = false, onChange = { value = it })
        openField("1 - Ana")
        item("1 - Ana").performClick()
        waitForIdle()
        assertEquals(null, value)
    }

    @Test
    fun withoutAnEmptyLabelTheMenuHasOnlyPlayers() = runComposeUiTest {
        show(emptyLabel = null)
        openField("1 - Ana")
        assertEquals(0, count("-"))
    }

    private companion object {
        const val FIELD = "field"
    }
}
