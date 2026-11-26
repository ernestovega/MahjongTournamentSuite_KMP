package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "How does the draw work?": the button opens the explanation, which shows the three ways in order and closes. The flowcharts draw at any width. */
@OptIn(ExperimentalTestApi::class)
class DrawExplanationUiTest {
    @Test
    fun theButtonOpensTheExplanationAndCloseClosesIt() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                var open by remember { mutableStateOf(false) }
                Column {
                    DrawExplanationButton(onClick = { open = true })
                    DrawBackupRunningNote()
                }
                if (open) DrawExplanationDialog(onDismiss = { open = false })
            }
        }
        onNodeWithTag("drawBackupRunning").assertExists()
        onNodeWithTag("drawExplanationButton").performClick()
        waitForIdle()
        onNodeWithText("How the draw works").assertExists()
        onNodeWithText("First way: seat by seat").assertExists()
        onNodeWithTag("drawBackupTitle").performScrollTo()
        onNodeWithTag("drawBlocksTitle").performScrollTo()
        onNodeWithTag("drawExplanationEnd").performScrollTo()
        onNode(hasClickAction() and hasText("Close")).performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithText("How the draw works").fetchSemanticsNodes().size)
    }

    @Test
    fun theTopBarButtonOpensTheExplanation() = runComposeUiTest {
        var clicks = 0
        setContent { MtsTheme(useDarkTheme = false) { DrawExplanationTopBarButton(onClick = { clicks++ }) } }
        onNode(hasClickAction() and hasText("How does the draw work?")).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun theFlowchartsDrawOnAWideWindow() = runComposeUiTest { drawEveryDiagram(600.dp) }

    @Test
    fun theFlowchartsDrawOnAPhone() = runComposeUiTest { drawEveryDiagram(320.dp) }

    @Test
    fun theFlowchartsDrawInTheDarkTheme() = runComposeUiTest { drawEveryDiagram(600.dp, dark = true) }

    private fun ComposeUiTest.drawEveryDiagram(width: Dp, dark: Boolean = false) {
        val diagrams: List<@Composable (Modifier) -> Unit> = listOf(
            { DrawFlowDiagram(it) },
            { DrawSwapFlowDiagram(it) },
            { DrawBlocksFlowDiagram(it) },
        )
        var index by mutableStateOf(0)
        setContent { MtsTheme(useDarkTheme = dark) { diagrams[index](Modifier.width(width)) } }
        diagrams.indices.forEach { i ->
            index = i
            waitForIdle()
            // The flowchart draws its arrows and boxes only when the frame is drawn.
            val image = onNode(isRoot()).captureToImage()
            assertTrue(image.width > 0 && image.height > 0)
        }
    }
}
