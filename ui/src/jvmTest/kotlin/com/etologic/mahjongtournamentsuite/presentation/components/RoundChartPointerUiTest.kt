package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** The round charts follow the mouse and the finger: the round under the pointer gets selected and shows its bubble. */
@OptIn(ExperimentalTestApi::class)
class RoundChartPointerUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    @Test
    fun theMouseAndTheFingerChooseTheRoundAndShowItsBubble() = runComposeUiTest {
        val chosen = mutableListOf<Int>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(400.dp)) {
                    RoundBarChart(
                        names = listOf("R1", "R2", "R3", "R4"),
                        bars = listOf(
                            listOf(BarPart(10.0, Color.Green)),
                            listOf(BarPart(-5.0, Color.Red), BarPart(3.0, Color.Blue)),
                            emptyList(),
                            listOf(BarPart(8.0, Color.Green)),
                        ),
                        labels = listOf("10", "-2", null, "8"),
                        selected = 1,
                        onSelect = { chosen += it },
                        modifier = Modifier.testTag("bars"),
                        tooltip = "Round 2 · 3 points · 1 penalty",
                    )
                    // A short chart: the tall bubble does not fit over the point and goes to its side.
                    RoundLineChart(
                        names = listOf("R1", "R2", "R3"),
                        series = listOf(LineSeries(listOf(1.0, null, 3.0), Color.Blue), LineSeries(listOf(2.0, 2.0, 2.0), Color.Gray, dotsOnly = true)),
                        minValue = 0.0,
                        maxValue = 4.0,
                        selected = 2,
                        onSelect = { chosen += 10 + it },
                        modifier = Modifier.testTag("line"),
                        height = 60.dp,
                        tooltip = "Round 3\nFirst\nSecond\nThird\nFourth",
                        inverted = true,
                        allValues = true,
                        axisValues = true,
                    )
                    Text("end")
                }
            }
        }
        onNodeWithTag("bars").performMouseInput {
            enter(Offset(10f, 50f))
            moveTo(Offset(150f, 50f))
            press()
            release()
            moveTo(Offset(390f, 50f))
            exit()
        }
        onNodeWithTag("bars").performTouchInput {
            down(Offset(10f, 50f))
            moveTo(Offset(250f, 50f))
            up()
        }
        onNodeWithTag("line").performMouseInput {
            enter(Offset(380f, 30f))
            moveTo(Offset(390f, 30f))
        }
        onNodeWithTag("line").performTouchInput {
            down(Offset(5f, 30f))
            up()
        }
        waitForIdle()
        onNodeWithText("end").assertExists()
        assertTrue(0 in chosen && 3 in chosen, "The bars choose their rounds: $chosen")
        assertTrue(12 in chosen && 10 in chosen, "The line chooses its rounds: $chosen")
    }

    @Test
    fun eachScopeOfTheStatisticsHasItsTitle() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    StatisticsScope.entries.forEach { Text(it.title()) }
                    PlayerCountryAndEma(country = "ES", emaId = "10990001")
                    PlayerCountryAndEma(country = "", emaId = "")
                }
            }
        }
        onNodeWithText("EMA 10990001", substring = true).assertExists()
    }
}
