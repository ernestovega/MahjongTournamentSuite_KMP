package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

/** A tap on a day or on a minute confirms the date or the time at once, without the OK button. */
@OptIn(ExperimentalTestApi::class)
class TournamentPickerDialogsUiTest {
    private fun ComposeUiTest.themed(content: @androidx.compose.runtime.Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
            MtsTheme(useDarkTheme = false) { content() }
        }
    }

    @Test
    fun aTapOnADayChoosesIt() = runComposeUiTest {
        val chosen = mutableListOf<String>()
        themed { TournamentDatePickerDialog(selectedDisplayDate = "22/10/2026", onDismiss = {}, onDateSelected = { chosen += it }) }
        waitForIdle()

        onAllNodes(hasText("15 October 2026", substring = true) and hasClickAction()).onFirst().performClick()
        waitForIdle()

        assertEquals(listOf("15/10/2026"), chosen)
    }

    @Test
    fun aTapOnAMinuteChoosesTheTime() = runComposeUiTest {
        val chosen = mutableListOf<String>()
        themed { TournamentTimePickerDialog(selectedTime = "10:00", onDismiss = {}, onTimeSelected = { chosen += it }) }
        waitForIdle()

        // The dial shows the hours first. The second toggle shows the minutes, where the seventh mark is :30.
        val dial = onAllNodes(isSelectable(), useUnmergedTree = true)
        dial[1].performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onAllNodes(isSelectable(), useUnmergedTree = true).filter(hasClickAction())[8].performSemanticsAction(SemanticsActions.OnClick)
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(1, chosen.size, "chosen: $chosen")
    }
}
