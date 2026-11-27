package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The logo of the Timer and the standalone ranking, with its "Show while running" switch (27-nov). */
@OptIn(ExperimentalTestApi::class)
class ProjectionLogoUiTest {

    private var running by mutableStateOf(false)
    private var showWhileRunning by mutableStateOf(true)

    private fun androidx.compose.ui.test.ComposeUiTest.show(changes: MutableList<Boolean>? = null) = setContent {
        ProjectionLogo(
            running = running,
            showWhileRunning = showWhileRunning,
            onShowWhileRunningChange = {
                changes?.add(it)
                showWhileRunning = it
            },
        ) { Box(Modifier.size(48.dp).testTag(LOGO)) }
    }

    @Test
    fun whilePausedTheLogoAndItsSwitchShow() = runComposeUiTest {
        show()
        onNodeWithTag(LOGO).assertExists()
        onNodeWithText(SWITCH_TEXT).assertExists().assertIsOn()
    }

    @Test
    fun aTapOnTheSwitchTextTurnsItOffAndOnAgain() = runComposeUiTest {
        val changes = mutableListOf<Boolean>()
        show(changes)
        onNodeWithText(SWITCH_TEXT).performClick()
        onNodeWithText(SWITCH_TEXT).assertIsOff()
        onNodeWithText(SWITCH_TEXT).performClick()
        onNodeWithText(SWITCH_TEXT).assertIsOn()
        assertEquals(listOf(false, true), changes)
    }

    @Test
    fun whileRunningTheSwitchHidesAndTheLogoStaysWhenTheSwitchIsOn() = runComposeUiTest {
        running = true
        show()
        onNodeWithTag(LOGO).assertExists()
        onNodeWithText(SWITCH_TEXT).assertDoesNotExist()
    }

    @Test
    fun whileRunningTheLogoHidesWhenTheSwitchIsOff() = runComposeUiTest {
        show()
        onNodeWithText(SWITCH_TEXT).performClick()
        running = true
        waitForIdle()
        onNodeWithTag(LOGO).assertDoesNotExist()
        onNodeWithText(SWITCH_TEXT).assertDoesNotExist()
        // Paused again: the logo comes back with the switch off, so it can be turned on.
        running = false
        waitForIdle()
        onNodeWithTag(LOGO).assertExists()
        onNodeWithText(SWITCH_TEXT).assertIsOff()
    }

    private companion object {
        const val LOGO = "logo"
        const val SWITCH_TEXT = "Show while running"
    }
}
