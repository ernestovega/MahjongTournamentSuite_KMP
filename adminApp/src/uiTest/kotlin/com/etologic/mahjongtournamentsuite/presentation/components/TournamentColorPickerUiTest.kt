package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** The color of a tournament: the wheel by arrows, click and drag, and the #RRGGBB text. */
@OptIn(ExperimentalTestApi::class)
class TournamentColorPickerUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    @Test
    fun theWheelAndTheTextChooseTheColor() = runComposeUiTest {
        val chosen = mutableListOf<String>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                TournamentColorPickerDialog(initialColor = "#02B16B", onDismiss = {}, onColorSelected = { chosen += it })
            }
        }
        val wheel = onNodeWithContentDescription("Color wheel", substring = true)
        wheel.requestFocus()
        wheel.performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.A)
        }
        wheel.performTouchInput { swipe(Offset(120f, 120f), Offset(200f, 60f)) }
        wheel.performMouseInput { click(Offset(40f, 120f)) }
        waitForIdle()
        onNode(hasText("OK") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertTrue(chosen.single().matches(Regex("#[0-9A-F]{6}")), "$chosen")
        // A typed color moves the wheel to it, and the text keeps only a color.
        onNode(hasSetTextAction()).performTextReplacement("#ff00zz11")
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("#3366CC")
        waitForIdle()
        onNode(hasSetTextAction()).assertTextContains("#3366CC")
        // Different hues give the six parts of the color wheel.
        listOf("#FF0000", "#FFFF00", "#00FF00", "#00FFFF", "#0000FF", "#FF00FF", "#808080").forEach {
            onNode(hasSetTextAction()).performTextReplacement(it)
            waitForIdle()
        }
        wheel.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
    }
}
