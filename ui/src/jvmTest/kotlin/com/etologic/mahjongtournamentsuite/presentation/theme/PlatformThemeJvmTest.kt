package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test

/** On a Mac the theme follows the appearance of the system, which it reads again every second. */
@OptIn(ExperimentalTestApi::class)
class PlatformThemeJvmTest {
    private val system = System.getProperty("os.name")

    @AfterTest
    fun restore() {
        System.setProperty("os.name", system)
    }

    @Test
    fun aMacWhoseAppearanceCannotBeReadIsLight() = runComposeUiTest {
        // This machine has no macOS defaults tool, so the read fails and the theme stays light.
        System.setProperty("os.name", "Mac OS X")
        setContent { Text(if (isPlatformDarkTheme()) "dark" else "light") }
        onNodeWithText("light").assertExists()

        mainClock.advanceTimeBy(2_500)
        waitForIdle()
        onNodeWithText("light").assertExists()
    }

    @Test
    fun anotherSystemUsesTheThemeOfCompose() = runComposeUiTest {
        System.setProperty("os.name", "Linux")
        setContent { Text(if (isPlatformDarkTheme()) "dark" else "light") }
        onNodeWithText("light").assertExists()
    }
}
