package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BackgroundExportsUiTest {
    private fun snapshot(name: String, image: ImageBitmap) {
        val dir = System.getenv("MTS_SCREENSHOT_DIR") ?: return
        ImageIO.write(image.toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test
    fun theCardsShowEachExportUntilTheUserClosesIt() = runComposeUiTest {
        lateinit var exports: BackgroundExports
        val idCards = CompletableDeferred<AppResult<ExportFile>>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                val scope = rememberCoroutineScope()
                exports = androidx.compose.runtime.remember(scope) { BackgroundExports(scope) }
                BackgroundExportsHost(exports) {
                    Surface(Modifier.size(900.dp, 500.dp)) { Box(Modifier.fillMaxSize()) { Text("The players of the tournament") } }
                }
            }
        }

        runOnIdle {
            exports.start("id-cards:t1", UiText.Plain("ID cards")) { idCards.await() }
            exports.start("id-list:t1", UiText.Plain("ID list")) {
                AppResult.Success(ExportFile("list.xlsx", byteArrayOf(1), "application/octet-stream", "Save"))
            }
            exports.start("ema-report:t1", UiText.Plain("EMA report")) { AppResult.Failure(AppError.Network) }
        }
        waitForIdle()

        onNodeWithText("ID cards: generating…").assertIsDisplayed()
        onNodeWithText("You can keep working. We'll let you know when it's ready.").assertIsDisplayed()
        onNodeWithText("ID list: ready").assertIsDisplayed()
        onNodeWithText("EMA report: could not be generated").assertIsDisplayed()
        onNodeWithText("The players of the tournament").assertIsDisplayed()
        snapshot("background_exports", onRoot().captureToImage())

        onAllNodesWithContentDescription("Close")[0].performClick()
        waitForIdle()
        runOnIdle { assertEquals(2, exports.tasks.size) }
    }
}
