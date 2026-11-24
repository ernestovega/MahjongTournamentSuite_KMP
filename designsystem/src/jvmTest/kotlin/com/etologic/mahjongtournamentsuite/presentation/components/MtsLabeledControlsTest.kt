package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/** A tap on the text of a checkbox, a switch or a radio button changes it, like a tap on the box. */
@OptIn(ExperimentalTestApi::class)
class MtsLabeledControlsTest {
    @Test
    fun aTapOnTheTextOfACheckboxChecksIt() = runComposeUiTest {
        setContent {
            MtsTheme {
                var checked by remember { mutableStateOf(false) }
                MtsCheckboxRow(text = "Paid", checked = checked, onCheckedChange = { checked = it })
            }
        }
        onNodeWithText("Paid").assertIsOff().performClick()
        onNodeWithText("Paid").assertIsOn().performClick()
        onNodeWithText("Paid").assertIsOff()
    }

    @Test
    fun aTapOnTheTextOfASwitchTurnsItOn() = runComposeUiTest {
        setContent {
            MtsTheme {
                var checked by remember { mutableStateOf(false) }
                MtsSwitchRow(checked = checked, onCheckedChange = { checked = it }) { Text("Details") }
            }
        }
        onNodeWithText("Details").assertIsOff().performClick()
        onNodeWithText("Details").assertIsOn()
    }

    @Test
    fun aTapOnTheTextOfARadioButtonSelectsIt() = runComposeUiTest {
        setContent {
            MtsTheme {
                var selected by remember { mutableStateOf("A") }
                listOf("A", "B").forEach { option ->
                    MtsRadioRow(selected = selected == option, onClick = { selected = option }) { Text(option) }
                }
            }
        }
        onNodeWithText("B").performClick()
        onNodeWithText("B").assertIsSelected()
    }

    @Test
    fun aDisabledLineDoesNotChange() = runComposeUiTest {
        setContent {
            MtsTheme {
                var checked by remember { mutableStateOf(false) }
                MtsCheckboxRow(text = "Notify", checked = checked, onCheckedChange = { checked = it }, enabled = false)
            }
        }
        onNodeWithText("Notify").assertIsNotEnabled().performClick()
        onNodeWithText("Notify").assertIsOff()
    }

    @Test
    fun theLineIsOneControl() = runComposeUiTest {
        setContent { MtsTheme { MtsCheckboxRow(text = "Paid", checked = false, onCheckedChange = {}) } }
        // The text and the box are one node: TalkBack reads "Paid, checkbox, not checked".
        onNode(isToggleable()).assertIsOff()
        onNodeWithText("Paid").assertIsOff()
    }
}
