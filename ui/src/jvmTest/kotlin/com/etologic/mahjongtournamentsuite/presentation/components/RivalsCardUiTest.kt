package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Best Rivals card: one button per rival with an EMA number, which compares the player with that rival. */
@OptIn(ExperimentalTestApi::class)
class RivalsCardUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun useEnglish() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    /** Two tables with two rivals with an EMA number and one rival without one (a key with a hash). */
    private val statistics = PlayerStatistics(
        1,
        List(2) { index ->
            TableResult(
                roundId = index + 1,
                tableId = 1,
                seat = 0,
                position = 3,
                score = 0,
                points = 1.0,
                hasHands = true,
                rivals = listOf(
                    RivalTally("1001", 1, 1, 1, 3),
                    RivalTally("1002", 1, 0, 1, 2),
                    RivalTally("7#3", 0, 1, 0, 1),
                ),
            )
        },
    )
    private val names = mapOf("1001" to "DANIELA NATALI", "1002" to "TIMUR HAHN")

    @Test
    fun eachRivalWithAnEmaNumberHasACompareButtonThatGivesItsNumber() = runComposeUiTest {
        val compared = mutableListOf<String>()
        setContent { MtsTheme(useDarkTheme = false) { RivalsCard(statistics, names, Modifier, onCompareRival = { compared += it }) } }

        onNodeWithText("Player 3").assertExists()
        val buttons = onAllNodesWithText("Compare")
        buttons.assertCountEquals(2)
        buttons[0].performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        buttons[1].performClick()
        waitForIdle()

        assertEquals(listOf("1001", "1002"), compared)
    }

    @Test
    fun withoutACompareActionTheRivalsHaveNoButton() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { RivalsCard(statistics, names, Modifier, onCompareRival = null) } }

        onNodeWithText("DANIELA NATALI").assertExists()
        onAllNodesWithText("Compare").assertCountEquals(0)
    }
}
