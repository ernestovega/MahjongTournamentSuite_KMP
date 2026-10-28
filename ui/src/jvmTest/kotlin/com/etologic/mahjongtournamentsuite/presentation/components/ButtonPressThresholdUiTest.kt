package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every kind of button ignores a second press within 500 ms, so a double click cannot run an action twice,
 * and takes a press again after that time.
 */
@OptIn(ExperimentalTestApi::class)
class ButtonPressThresholdUiTest {
    private val tag = "button"

    private fun ComposeUiTest.assertThreshold(button: @Composable (onClick: () -> Unit) -> Unit) {
        var presses = 0
        setContent { MtsTheme(useDarkTheme = false) { button { presses += 1 } } }

        onNodeWithTag(tag).performClick()
        onNodeWithTag(tag).performClick()
        onNodeWithTag(tag).performClick()
        assertEquals(1, presses, "A fast double or triple press counts once.")

        Thread.sleep(600)
        onNodeWithTag(tag).performClick()
        assertEquals(2, presses, "A press after the threshold counts.")
    }

    @Test
    fun theDebouncedButtonsIgnoreAFastSecondPress() {
        listOf<@Composable (() -> Unit) -> Unit>(
            { onClick -> DebouncedButton(onClick, Modifier.testTag(tag)) { Text("Go") } },
            { onClick -> DebouncedOutlinedButton(onClick, Modifier.testTag(tag)) { Text("Go") } },
            { onClick -> DebouncedTextButton(onClick, Modifier.testTag(tag)) { Text("Go") } },
            { onClick -> DebouncedIconButton(onClick, Modifier.testTag(tag)) { Icon(Icons.Default.Add, contentDescription = "Add") } },
            { onClick -> DebouncedExtendedFloatingActionButton(onClick, Modifier.testTag(tag)) { Text("Go") } },
        ).forEach { button -> runComposeUiTest { assertThreshold(button) } }
    }

    @Test
    fun theFocusedButtonsIgnoreAFastSecondPress() {
        listOf<@Composable (() -> Unit) -> Unit>(
            { onClick -> FocusedButton(onClick, buttonModifier = Modifier.testTag(tag)) { Text("Go") } },
            { onClick -> FocusedOutlinedButton(onClick, buttonModifier = Modifier.testTag(tag)) { Text("Go") } },
            { onClick -> FocusedTextButton(onClick, buttonModifier = Modifier.testTag(tag)) { Text("Go") } },
            { onClick -> FocusedIconButton(onClick, buttonModifier = Modifier.testTag(tag)) { Icon(Icons.Default.Add, contentDescription = "Add") } },
            { onClick -> FocusedAssistChip(onClick, label = { Text("Go") }, chipModifier = Modifier.testTag(tag)) },
            { onClick -> AppTextButton(onClick, Modifier.testTag(tag)) { Text("Go") } },
        ).forEach { button -> runComposeUiTest { assertThreshold(button) } }
    }

    @Test
    fun theTopBarButtonsIgnoreAFastSecondPress() {
        listOf<@Composable (() -> Unit) -> Unit>(
            { onClick -> AppTopBarButton("Go", Icons.Default.Add, onClick, showLabel = true) },
            { onClick -> AppTopBarButton("Go", Icons.Default.Add, onClick, showLabel = false) },
        ).forEachIndexed { index, button ->
            runComposeUiTest {
                var presses = 0
                setContent { MtsTheme(useDarkTheme = false) { button { presses += 1 } } }
                val node = if (index == 0) onNodeWithText("Go") else onNodeWithContentDescription("Go")

                node.performClick()
                node.performClick()
                assertEquals(1, presses)
                Thread.sleep(600)
                node.performClick()
                assertEquals(2, presses)
            }
        }
    }

    @Test
    fun aDisabledButtonTakesNoPressAtAll() = runComposeUiTest {
        var presses = 0
        setContent { MtsTheme(useDarkTheme = false) { DebouncedButton({ presses += 1 }, Modifier.testTag(tag), enabled = false) { Text("Go") } } }

        onNodeWithTag(tag).performClick()

        assertEquals(0, presses)
    }
}
