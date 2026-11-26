package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TmpRivalsShots {
    private val stats = PlayerStatistics(
        1,
        listOf(
            listOf(RivalTally("1001", 1, 0, 2, 3), RivalTally("1002", 1, 1, 1, 2), RivalTally("1003", 0, 1, 0, 1)),
            listOf(RivalTally("1001", 1, 0, 1, 3), RivalTally("1002", 0, 1, 1, 2), RivalTally("1003", 1, 0, 1, 1)),
        ).mapIndexed { i, r -> TableResult(roundId = i + 1, tableId = 1, seat = 0, position = 3, score = 0, points = 1.0, hasHands = true, rivals = r) },
    )
    private val names = mapOf("1001" to "DANIELA NATALI", "1002" to "TIMUR HAHN", "1003" to "MARÍA JOSÉ FERNÁNDEZ")

    @Test
    fun shots() {
        val dir = File("build/rivals-shots").apply { mkdirs() }
        for (design in 1..2) for (dark in listOf(false, true)) for ((label, w) in listOf("movil" to 360, "escritorio" to 520)) {
            runComposeUiTest {
                setContent {
                    MtsTheme(useDarkTheme = dark) {
                        Box(Modifier.testTag("shot").background(MaterialTheme.colorScheme.background).width(w.dp).padding(16.dp)) {
                            Column { RivalsCard(stats, names, Modifier, onCompareRival = {}, design = design) }
                        }
                    }
                }
                val img = onNodeWithTag("shot").captureToImage()
                ImageIO.write(img.toAwtImage(), "png", File(dir, "propuesta-$design-$label-${if (dark) "oscuro" else "claro"}.png"))
            }
        }
    }
}
