package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsInputModality
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A control lights up in one place only. Material buttons draw their own hover and focus layer in their own shape (a
 * circle for an icon button, a pill for a button). The focus halo around them must not add a second lit area, such as
 * the square behind the circle of the edit pencil. The corners of the box around a round button stay as they were on
 * hover and on keyboard focus.
 *
 * The focus shows only while the keyboard is in use ([MtsInputModality]); with the mouse only the hover shows.
 *
 * With the environment variable `HIGHLIGHT_SHOTS` set to a folder, the test also saves what it sees there.
 */
@OptIn(ExperimentalTestApi::class)
class SingleHighlightUiTest {
    private companion object {
        const val MIN_HOVER_CHANGE = 40
    }

    private val tag = "control"

    private fun save(image: ImageBitmap, name: String) {
        val folder = System.getenv("HIGHLIGHT_SHOTS") ?: return
        File(folder).mkdirs()
        ImageIO.write(image.toAwtImage(), "png", File(folder, "$name.png"))
    }

    /** Draws [control] on the card color of the apps, hovers it and focuses it, and checks the corner pixels. */
    private fun ComposeUiTest.assertOneLitArea(name: String, dark: Boolean, control: @Composable (Modifier, FocusRequester) -> Unit) {
        lateinit var focus: FocusRequester
        setContent {
            focus = remember { FocusRequester() }
            MtsTheme(useDarkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.padding(24.dp)) { control(Modifier.testTag(tag), focus) }
                }
            }
        }
        val node = onNodeWithTag(tag)
        val idle = node.captureToImage()
        node.performMouseInput { moveTo(center) }
        waitForIdle()
        val hovered = node.captureToImage()
        node.performMouseInput { exit() }
        // With the mouse in use the focus shows nothing: only one mark at a time, the hover.
        MtsInputModality.keyboard = false
        runOnIdle { focus.requestFocus() }
        waitForIdle()
        val focusedWithMouse = node.captureToImage()
        assertEquals(idle.toPixelMap().buffer.toList(), focusedWithMouse.toPixelMap().buffer.toList(), "$name: the focus shows while the mouse is in use.")
        MtsInputModality.keyboard = true
        waitForIdle()
        val focused = node.captureToImage()
        MtsInputModality.keyboard = false
        val theme = if (dark) "dark" else "light"
        save(idle, "$name-$theme-idle")
        save(hovered, "$name-$theme-hover")
        save(focused, "$name-$theme-focus")

        val idlePixels = idle.toPixelMap()
        // Points near the corners of the box, inside the room of the old halo fill. A round or pill shaped control and
        // its own layer never reach them, and the focus ring passes outside them.
        // A Debounced button has no halo room: its box is the drawn control, so the points go closer to the corners.
        val c = if (idle.height < 48) 2 else 5
        val corners = listOf(c to c, idle.width - 1 - c to c, c to idle.height - 1 - c, idle.width - 1 - c to idle.height - 1 - c)
        for ((label, image) in listOf("hover" to hovered, "focus" to focused)) {
            val pixels = image.toPixelMap()
            corners.forEach { (x, y) ->
                assertEquals(idlePixels[x, y], pixels[x, y], "$name ($theme): the corner ($x, $y) lights up on $label, so two areas light up.")
            }
        }
        // The hover shows clearly on the control itself, also on a filled green button (Ernesto, 27-nov). The point is
        // inside the shape of every control, left of its text or icon.
        val probeX = if (idle.height < 48) 6 else 14
        val probeY = idle.height / 2
        val change = colorDistance(idlePixels[probeX, probeY], hovered.toPixelMap()[probeX, probeY])
        assertTrue(change >= MIN_HOVER_CHANGE, "$name ($theme): the hover changes the control by $change only, too little to see.")
        // Something does show the focus: the control does not look the same as at rest.
        assertNotEquals(idlePixels.buffer.toList(), focused.toPixelMap().buffer.toList(), "$name ($theme): the keyboard focus does not show.")
    }

    /** The sum of the RGB differences, from 0 to 765. */
    private fun colorDistance(a: Color, b: Color): Int =
        ((kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)) * 255).toInt()

    private val controls: List<Pair<String, @Composable (Modifier, FocusRequester) -> Unit>> = listOf(
        "icon-button" to { m, f -> FocusedIconButton({}, m, focusRequester = f) { Icon(Icons.Default.Edit, contentDescription = "Edit") } },
        "button" to { m, f -> FocusedButton({}, m, focusRequester = f) { Text("SAVE") } },
        "outlined-button" to { m, f -> FocusedOutlinedButton({}, m, focusRequester = f) { Text("COMPARE") } },
        "text-button" to { m, f -> FocusedTextButton({}, m, focusRequester = f) { Text("CANCEL") } },
        "debounced-icon-button" to { m, f -> DebouncedIconButton({}, m.focusRequester(f)) { Icon(Icons.Default.Edit, contentDescription = "Edit") } },
        "debounced-button" to { m, f -> DebouncedButton({}, m.focusRequester(f)) { Text("SAVE") } },
        "debounced-text-button" to { m, f -> DebouncedTextButton({}, m.focusRequester(f)) { Text("CANCEL") } },
    )

    @Test
    fun aFocusedButtonLightsUpInItsOwnShapeOnly() {
        for (dark in listOf(false, true)) {
            controls.forEach { (name, control) -> runComposeUiTest { assertOneLitArea(name, dark, control) } }
        }
    }

    /** A press of the mouse moves the focus to the button, so Tab and the arrows go on from there. */
    @Test
    fun aMousePressFocusesTheButton() {
        controls.forEach { (name, control) ->
            runComposeUiTest {
                setContent { MtsTheme(useDarkTheme = false) { Box(Modifier.padding(24.dp)) { control(Modifier.testTag(tag), remember { FocusRequester() }) } } }
                onNodeWithTag(tag).performMouseInput { click(center) }
                waitForIdle()
                // The focus is on the button itself or on the clickable node inside the tagged box.
                val focused = onAllNodes(isFocused()).fetchSemanticsNodes()
                assertEquals(1, focused.size, "$name: a mouse press does not move the focus to the button.")
            }
        }
    }

    /** A card that folds: the hover of its title reaches the sides of the card, with no inner box. */
    @Test
    fun theHoverOfAFoldingCardReachesItsSides() {
        runComposeUiTest {
            setContent {
                MtsTheme(useDarkTheme = true) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Box(Modifier.padding(24.dp).width(360.dp)) {
                            StatCard("GAME DAY", Modifier.testTag(tag), expanded = false, onToggle = {}, summary = { Text("Nov 19, 2026") }) {}
                        }
                    }
                }
            }
            val node = onNodeWithTag(tag)
            val idle = node.captureToImage()
            node.performMouseInput { moveTo(center) }
            waitForIdle()
            val hovered = node.captureToImage()
            save(idle, "stat-card-dark-idle")
            save(hovered, "stat-card-dark-hover")
            // A point 6 px inside the left side of the card, halfway down, is inside the lit area.
            val y = idle.height / 2
            assertNotEquals(idle.toPixelMap()[6, y], hovered.toPixelMap()[6, y], "The hover of the card title stops before the side of the card.")
        }
    }
}
