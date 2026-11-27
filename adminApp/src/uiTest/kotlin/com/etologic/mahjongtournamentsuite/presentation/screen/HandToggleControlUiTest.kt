package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The check boxes of the hands of a table: a tap on the name checks them too, and the arrows move to the neighbours. */
@OptIn(ExperimentalTestApi::class)
class HandToggleControlUiTest {
    private fun androidx.compose.ui.test.ComposeUiTest.box(tag: String) = onNode(isToggleable() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun aTapOnTheNameChecksTheBoxAndTheArrowsMoveOn() = runComposeUiTest {
        val moves = mutableListOf<String>()
        var chicken by mutableStateOf(false)
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    Column(Modifier.testTag("changed")) {
                        HandToggleControl(
                            width = 80.dp,
                            label = "Chicken",
                            checked = chicken,
                            enabled = true,
                            isChanged = true,
                            onCheckedChange = { chicken = it },
                            onNavigateLeft = { moves += "left"; true },
                            onNavigateRight = { moves += "right"; true },
                            onNavigateUp = { moves += "up"; true },
                            onNavigateDown = { moves += "down"; true },
                        )
                    }
                    Column(Modifier.testTag("plain")) {
                        HandToggleControl(width = 80.dp, label = "Done", checked = true, enabled = true, onCheckedChange = {})
                    }
                    Column(Modifier.testTag("off")) {
                        HandToggleControl(width = 80.dp, label = "Locked", checked = false, enabled = false, onCheckedChange = { chicken = true })
                    }
                }
            }
        }
        onNodeWithText("Chicken").performClick()
        waitForIdle()
        assertEquals(true, chicken)
        box("changed").assertIsOn()

        box("changed").requestFocus()
        box("changed").performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.A)
        }
        assertEquals(listOf("left", "right", "up", "down"), moves)

        box("plain").assertIsOn()
        // A disabled box ignores the tap on its name.
        onNodeWithText("Locked").performClick()
        waitForIdle()
        box("off").assertIsOff()
        assertEquals(4, moves.size)
    }
}
