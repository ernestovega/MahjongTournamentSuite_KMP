package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MatchTimeFieldsUiTest {
    @Test
    fun extraTimeFieldShowsOnlyWhenTheHandInPlayIsFinished() = runComposeUiTest {
        var finishHand by mutableStateOf(false)
        setContent {
            MtsTheme(useDarkTheme = false) {
                MatchTimeFields(
                    durationText = "120",
                    onDurationChange = {},
                    timeZoneText = "Europe/Madrid",
                    onTimeZoneChange = {},
                    finishHand = finishHand,
                    onFinishHandChange = { finishHand = it },
                    extraMinutesText = "15",
                    onExtraMinutesChange = {},
                    enabled = true,
                )
            }
        }

        onNodeWithText("Match duration (minutes)").assertIsDisplayed()
        onNodeWithText("Time zone").assertIsDisplayed()
        onNodeWithText("Extra time (minutes)").assertDoesNotExist()

        onNode(isToggleable()).performClick()
        waitForIdle()
        assertEquals(true, finishHand)
        onNodeWithText("Extra time (minutes)").assertIsDisplayed()
    }
}
