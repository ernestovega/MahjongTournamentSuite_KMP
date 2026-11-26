package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The dialog that asks for minutes: Set time on a started round, and its keys. */
@OptIn(ExperimentalTestApi::class)
class TimerMinutesDialogUiTest {
    private val now = 1_000_000_000L
    private val min = 60_000L

    private fun ComposeUiTest.field() = onNode(hasSetTextAction())

    private fun ComposeUiTest.press(key: Key, shift: Boolean = false) {
        field().performKeyInput {
            if (shift) withKeyDown(Key.ShiftLeft) { pressKey(key) } else pressKey(key)
        }
        waitForIdle()
    }

    @Test
    fun setTimeChangesTheRemainingTimeOfAStartedRound() = runComposeUiTest {
        val server = FakeTournamentRepository().apply {
            tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 1, matchDurationMinutes = 120))
            roundClocks = listOf(RoundClock(roundId = 1, startedAtMs = now - 40 * min, pausedAtMs = now - 10 * min))
            serverNowMs = now
        }
        val controller = RoundTimerController(server, "t1", 1, deviceNowMs = { now })
        setContent {
            MtsTheme(useDarkTheme = false) {
                TimerContent(controller = controller, themePreference = ThemePreference.Light, onToggleTheme = {}, deviceNowMs = { now })
            }
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()

        onNodeWithContentDescription("Set time").performClick()
        mainClock.advanceTimeBy(300)
        waitForIdle()
        onNodeWithText("Remaining time").assertExists()
        field().performTextReplacement("45")
        press(Key.Enter)
        mainClock.advanceTimeBy(500)
        waitForIdle()

        onNodeWithText("0:45:00").assertExists()
    }

    @Test
    fun theKeysMoveTheFocusAndEnterAppliesOnlyAValidNumber() = runComposeUiTest {
        val applied = mutableListOf<Long>()
        var dismissed = false
        setContent {
            MtsTheme(useDarkTheme = false) {
                MinutesDialog("Minutes", "Minutes", "How many?", initialMinutes = 5, allowZero = false, onApply = { applied += it }, onDismiss = { dismissed = true })
            }
        }
        waitForIdle()

        press(Key.Tab)
        press(Key.Tab, shift = true)
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        press(Key.A)
        field().performTextReplacement("0")
        press(Key.Enter)
        field().performTextReplacement("x")
        press(Key.NumPadEnter)
        assertEquals(emptyList(), applied, "zero and a word are not minutes here")

        field().performTextReplacement("7")
        press(Key.NumPadEnter)
        assertEquals(listOf(7L), applied)

        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertTrue(dismissed)
    }
}
