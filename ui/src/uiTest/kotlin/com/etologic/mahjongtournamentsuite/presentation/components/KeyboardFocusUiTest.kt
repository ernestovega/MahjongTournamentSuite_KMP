package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The keyboard moves the focus inside a group, around a closed loop and out of a text field at its ends. */
@OptIn(ExperimentalTestApi::class)
class KeyboardFocusUiTest {

    private fun ComposeUiTest.press(tag: String, key: Key) {
        onNodeWithTag(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.focus(tag: String) {
        onNodeWithTag(tag).requestFocus()
        waitForIdle()
    }

    @Test
    fun theArrowsMoveTheFocusInsideAGroup() = runComposeUiTest {
        setContent {
            Row(Modifier.appFocusGroup()) {
                listOf("a", "b").forEach { Row(Modifier.size(40.dp).testTag(it).focusable()) {} }
            }
        }
        focus("a")
        press("a", Key.DirectionRight)
        onNodeWithTag("b").assertIsFocused()
        press("b", Key.DirectionLeft)
        onNodeWithTag("a").assertIsFocused()
        press("a", Key.DirectionDown)
        press("a", Key.DirectionUp)
        press("a", Key.A)
    }

    @Test
    fun aLoopGoesForwardAndBackWithTabAndTheArrows() = runComposeUiTest {
        val first = FocusRequester()
        val second = FocusRequester()
        setContent {
            Row {
                Row(Modifier.size(40.dp).testTag("first").focusRequester(first).focusLoop(previous = second, next = second).focusable()) {}
                Row(Modifier.size(40.dp).testTag("second").focusRequester(second).focusLoop(previous = first, next = first).focusable()) {}
            }
        }
        focus("first")
        press("first", Key.Tab)
        onNodeWithTag("second").assertIsFocused()
        onNodeWithTag("second").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        waitForIdle()
        onNodeWithTag("first").assertIsFocused()
        press("first", Key.DirectionRight)
        onNodeWithTag("second").assertIsFocused()
        press("second", Key.DirectionUp)
        onNodeWithTag("first").assertIsFocused()
        press("first", Key.DirectionDown)
        press("second", Key.DirectionLeft)
        onNodeWithTag("first").assertIsFocused()
        press("first", Key.A)
        onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun aTextFieldLetsTheFocusGoOnlyAtItsEnds() = runComposeUiTest {
        val before = FocusRequester()
        val field = FocusRequester()
        val after = FocusRequester()
        var value by mutableStateOf(TextFieldValue("abc", selection = TextRange(1)))
        setContent {
            Row {
                Row(Modifier.size(40.dp).testTag("before").focusRequester(before).focusable()) {}
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.testTag("field").focusRequester(field).textFieldFocusLoop(before, after) { value },
                )
                Row(Modifier.size(40.dp).testTag("after").focusRequester(after).focusable()) {}
            }
        }
        focus("field")
        // In the middle of the text the arrows move the caret.
        press("field", Key.DirectionLeft)
        press("field", Key.DirectionRight)
        onNodeWithTag("field").assertIsFocused()

        value = value.copy(selection = TextRange(0))
        waitForIdle()
        press("field", Key.DirectionLeft)
        onNodeWithTag("before").assertIsFocused()

        focus("field")
        value = value.copy(selection = TextRange(3))
        waitForIdle()
        press("field", Key.DirectionRight)
        onNodeWithTag("after").assertIsFocused()

        focus("field")
        press("field", Key.DirectionUp)
        onNodeWithTag("before").assertIsFocused()
        focus("field")
        press("field", Key.DirectionDown)
        onNodeWithTag("after").assertIsFocused()
        focus("field")
        press("field", Key.Tab)
        onNodeWithTag("after").assertIsFocused()
        focus("field")
        onNodeWithTag("field").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        waitForIdle()
        onNodeWithTag("before").assertIsFocused()
        focus("field")
        press("field", Key.A)
        onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun enterActivatesOnlyWhenEnabled() = runComposeUiTest {
        var clicks = 0
        var enabled by mutableStateOf(true)
        setContent {
            Row(Modifier.size(40.dp).testTag("control").activateOnEnter(enabled = enabled) { clicks++ }.focusable()) {}
        }
        focus("control")
        press("control", Key.Enter)
        press("control", Key.A)
        enabled = false
        waitForIdle()
        press("control", Key.Enter)
        assertEquals(1, clicks)
    }
}
