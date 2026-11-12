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
    private val chosen = mutableListOf<SuiteApp>()

    private fun androidx.compose.ui.test.ComposeUiTest.show() = setContent {
        MtsTheme(useDarkTheme = false) {
            SuiteAppChooserDialog { app -> chosen += app }
        }
    }

    @Test
    fun offersBothAppsAndSaysTheTopBarSwitchesLater() = runComposeUiTest {
        show()
        onNodeWithText("What do you want to open?").assertExists()
        onNodeWithText("Player view").assertExists()
        onNodeWithText("Management").assertExists()
        onNodeWithText("You can switch between the apps at any time with the button in the top bar.").assertExists()
    }

    @Test
    fun choosingManagementOpensTheAdminApp() = runComposeUiTest {
        show()
        onNodeWithText("Management").performClick()
        waitForIdle()
        assertEquals(listOf(SuiteApp.ADMIN), chosen)
    }

    @Test
    fun choosingPlayerViewStaysInThePlayerApp() = runComposeUiTest {
        show()
        onNodeWithText("Player view").performClick()
        waitForIdle()
        assertEquals(listOf(SuiteApp.PLAYER), chosen)
    }
}
