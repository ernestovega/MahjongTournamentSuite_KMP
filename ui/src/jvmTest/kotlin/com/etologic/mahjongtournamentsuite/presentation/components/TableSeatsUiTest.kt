package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TableSeatsUiTest {
    private val seats = listOf(
        SeatDisplay("Ana García", "32", "4.0", slotId = 1),
        SeatDisplay("Bruno Díaz", "-8", "1.0", penalty = -10, isMine = true, slotId = 2),
        SeatDisplay("Carla Ruiz", "12", "2.0", slotId = 3),
        SeatDisplay("David Moreno", "-36", "0.0", slotId = 4),
    )

    private fun snapshot(name: String, image: androidx.compose.ui.graphics.ImageBitmap) {
        val dir = System.getenv("MTS_SCREENSHOT_DIR") ?: return
        ImageIO.write(image.toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test
    fun playersChangeChairsEveryFourHandsAndWindsTurnEveryHand() {
        // Hand 1: everyone sits at the chair of the seat of hand 1.
        assertEquals(listOf(0, 1, 2, 3), (0..3).map { chairOf(it, 1) })
        assertEquals(listOf(0, 1, 2, 3), (0..3).map { chairOf(it, 4) })
        // Hand 5: East and South swap, West and North swap.
        assertEquals(listOf(1, 0, 3, 2), (0..3).map { chairOf(it, 5) })
        assertEquals(listOf(2, 3, 0, 1), (0..3).map { chairOf(it, 9) })
        assertEquals(listOf(3, 2, 1, 0), (0..3).map { chairOf(it, 13) })
        // The winds turn after every hand.
        assertEquals(listOf(SeatWind.EAST, SeatWind.SOUTH, SeatWind.WEST, SeatWind.NORTH), (0..3).map { windOfChair(it, 1) })
        assertEquals(listOf(SeatWind.NORTH, SeatWind.EAST, SeatWind.SOUTH, SeatWind.WEST), (0..3).map { windOfChair(it, 2) })
        assertEquals(listOf(SeatWind.SOUTH, SeatWind.WEST, SeatWind.NORTH, SeatWind.EAST), (0..3).map { windOfChair(it, 4) })
        assertEquals(listOf(SeatWind.EAST, SeatWind.SOUTH, SeatWind.WEST, SeatWind.NORTH), (0..3).map { windOfChair(it, 5) })
        assertEquals(SeatWind.SOUTH, roundWindOf(5))
        assertEquals(SeatWind.NORTH, roundWindOf(16))
    }

    @Test
    fun showsSeatsAndDifferences() = runComposeUiTest {
        var opened: Int? = null
        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    TableSeatsView(
                        seats = seats,
                        handNumber = 5,
                        centerLabel = "Hand\n5/16",
                        showPoints = false,
                        onPlayerClick = { opened = it },
                    )
                }
            }
        }
        onNodeWithText("Bruno Díaz").assertIsDisplayed()
        onNodeWithText("David Moreno").assertIsDisplayed()
        onNodeWithText("Penalty -10").assertIsDisplayed()
        // The points of the table are hidden while the table is not complete.
        assertEquals(0, onAllNodesWithTextCount("1.0 pts"))
        snapshot("seats", onRoot().captureToImage())

        onNodeWithText("David Moreno").performClick()
        assertEquals(4, opened)

        onNodeWithText("Diffs").performClick()
        snapshot("seats_diffs", onRoot().captureToImage())
    }

    @Test
    fun showsTheTablePointsBelowTheScoreWhenTheTableIsComplete() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = true) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    TableSeatsView(
                        seats = seats,
                        handNumber = 16,
                        centerLabel = "Done",
                        showPoints = true,
                    )
                }
            }
        }
        // Whole points show with no decimal part.
        assertTrue(onAllNodesWithText("4.0 pts").fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("4 pts").fetchSemanticsNodes().isNotEmpty())
        snapshot("seats_complete_dark", onRoot().captureToImage())
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}
