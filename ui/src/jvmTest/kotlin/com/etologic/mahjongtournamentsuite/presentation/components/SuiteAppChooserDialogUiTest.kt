package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The step after the sign-in of an admin or an editor: the player app or the admin app. */
@OptIn(ExperimentalTestApi::class)
class SuiteAppChooserDialogUiTest {
    private val chosen = mutableListOf<Pair<SuiteApp, Boolean>>()

    private fun androidx.compose.ui.test.ComposeUiTest.show() = setContent {
        MtsTheme(useDarkTheme = false) {
            SuiteAppChooserDialog { app, remember -> chosen += app to remember }
        }
    }

    @Test
    fun offersBothAppsWithoutTheRememberOptionOnTheDesktop() = runComposeUiTest {
        show()
        onNodeWithText("What do you want to open?").assertExists()
        onNodeWithText("Tournaments").assertExists()
        onNodeWithText("Management").assertExists()
        // Only the web remembers the choice.
        onNodeWithText("Remember my choice on this device").assertDoesNotExist()
    }

    @Test
    fun choosingManagementOpensTheAdminApp() = runComposeUiTest {
        show()
        onNodeWithText("Management").performClick()
        waitForIdle()
        assertEquals(listOf(SuiteApp.ADMIN to false), chosen)
    }

    @Test
    fun choosingTournamentsStaysInThePlayerApp() = runComposeUiTest {
        show()
        onNodeWithText("Tournaments").performClick()
        waitForIdle()
        assertEquals(listOf(SuiteApp.PLAYER to false), chosen)
    }
}
