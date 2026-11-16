package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.LocalAdaptiveTopBar
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The buttons of the top bars of the Combinations and Penalties screens and the info buttons of the list of
 * tournaments: on a wide screen they show in the bar, and on a phone they are in the three-dots menu.
 */
@OptIn(ExperimentalTestApi::class)
class RulesTopBarUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun ComposeUiTest.show(screen: @Composable (NavHostController) -> Unit) = setContent {
        CompositionLocalProvider(LocalAdaptiveTopBar provides true) {
            MtsTheme(useDarkTheme = false) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "screen") {
                    composable("screen") { screen(nav) }
                    composable<SupportRoute> { Text("Support form") }
                }
            }
        }
    }

    private fun ComposeUiTest.button(label: String) = onNode(hasText(label) or hasContentDescription(label))

    private fun ComposeUiTest.openMenu() = onNodeWithContentDescription("Menu").performClick()

    @Test
    fun onAWideScreenTheCreditsButtonOfCombinationsOpensItsDialog() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { CombinationsScreen(it) }
        button("Credits").performClick()
        onNodeWithText("martinpersson.org").assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithText("martinpersson.org").assertDoesNotExist()
    }

    @Test
    fun onAPhoneTheCreditsOfCombinationsAreInTheMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { CombinationsScreen(it) }
        button("Credits").assertDoesNotExist()
        openMenu()
        button("Credits").performClick()
        // The menu closes, and the dialog stays open, because its state is out of the button.
        onNodeWithText("martinpersson.org").assertExists()
    }

    @Test
    fun theContactButtonOfCombinationsOpensTheForm() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { CombinationsScreen(it) }
        button("Contact").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun thePenaltiesShowTheirThreeSectionsAndTheContactButton() = runDesktopComposeUiTest(width = 1200, height = 1600) {
        show { PenaltiesScreen(it) }
        onNodeWithText("Warning and progressive penalty").assertExists()
        onNodeWithText("Dead hand").assertExists()
        onNodeWithText("Point penalties and severe penalties").assertExists()
        button("Contact").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun onAPhoneTheContactOfPenaltiesIsInTheMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { PenaltiesScreen(it) }
        button("Contact").assertDoesNotExist()
        openMenu()
        button("Contact").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun onAWideScreenTheInfoButtonsAreInTheBar() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { nav -> AppScaffold(title = "Tournaments", actions = { InfoTopBarButtons(nav) }) {} }
        listOf("Green Book", "Contact", "Privacy", "Version").forEach { button(it).assertExists() }
        onNodeWithContentDescription("Menu").assertDoesNotExist()
    }

    @Test
    fun onAPhoneTheInfoButtonsAreInTheMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { nav -> AppScaffold(title = "Tournaments", actions = { InfoTopBarButtons(nav) }) {} }
        button("Version").assertDoesNotExist()
        openMenu()
        listOf("Green Book", "Contact", "Privacy", "Version").forEach { button(it).assertExists() }
        button("Contact").performClick()
        onNodeWithText("Support form").assertExists()
    }
}
