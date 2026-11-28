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
 * The Rules button opens its own menu on a wide screen. On a phone its options are in the three-dots menu, not in a second menu.
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
                    composable<CombinationsRoute> { Text("Combinations screen") }
                    composable<PenaltiesRoute> { Text("Penalties screen") }
                }
            }
        }
    }

    /** A screen with the info buttons, as the list of tournaments: the draw explanation is a dialog of the screen. */
    @Composable
    private fun InfoScreen(nav: NavHostController) {
        val drawExplanation = rememberDrawExplanationState()
        drawExplanation.Dialog()
        AppScaffold(title = "Tournaments", actions = { InfoTopBarButtons(nav, drawExplanation) }) {}
    }

    private fun ComposeUiTest.button(label: String) = onNode(hasText(label) or hasContentDescription(label))

    private fun ComposeUiTest.openMenu() = onNodeWithContentDescription("Menu").performClick()

    @Test
    fun onAWideScreenTheCreditsButtonOfCombinationsOpensItsDialog() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { CombinationsScreen(it) }
        button("Credits").performClick()
        onNodeWithText("www.martinpersson.org").assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithText("www.martinpersson.org").assertDoesNotExist()
    }

    @Test
    fun onAPhoneTheCreditsOfCombinationsAreInTheMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { CombinationsScreen(it) }
        button("Credits").assertDoesNotExist()
        openMenu()
        button("Credits").performClick()
        // The menu closes, and the dialog stays open, because its state is out of the button.
        onNodeWithText("www.martinpersson.org").assertExists()
    }

    @Test
    fun theContactButtonOfCombinationsOpensTheForm() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { CombinationsScreen(it) }
        button("Support").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun thePenaltiesShowTheirThreeSectionsAndTheContactButton() = runDesktopComposeUiTest(width = 1200, height = 1600) {
        show { PenaltiesScreen(it) }
        onNodeWithText("Warning and progressive penalty").assertExists()
        onNodeWithText("Dead hand").assertExists()
        onNodeWithText("Point penalties and severe penalties").assertExists()
        button("Support").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun onAPhoneTheContactOfPenaltiesIsInTheMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { PenaltiesScreen(it) }
        button("Support").assertDoesNotExist()
        openMenu()
        button("Support").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun onAWideScreenTheBarShowsRulesAndAThreeDotsMenuWithContactAndPrivacy() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { nav -> InfoScreen(nav) }
        button("Rules").assertExists()
        listOf("Green Book", "Support", "Privacy", "Version").forEach { button(it).assertDoesNotExist() }
        onNodeWithContentDescription("Menu").assertDoesNotExist()
        button("More").performClick()
        listOf("Support", "Privacy").forEach { button(it).assertExists() }
        button("Version").assertDoesNotExist()
        button("Support").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun onAWideScreenRulesOpensAMenuWithItsThreeOptions() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { nav -> InfoScreen(nav) }
        button("Rules").performClick()
        listOf("Green Book", "Combinations", "Penalties overview").forEach { button(it).assertExists() }
        button("Penalties overview").performClick()
        onNodeWithText("Penalties screen").assertExists()
        button("Green Book").assertDoesNotExist()
    }

    @Test
    fun onAPhoneTheRulesOptionsAreInTheMenuUnderAHeadingWithoutASecondMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { nav -> InfoScreen(nav) }
        button("Support").assertDoesNotExist()
        openMenu()
        // The heading Rules and its options, one level only, then Support and Privacy, without a second three-dots menu.
        listOf("Rules", "Green Book", "Combinations", "Penalties overview", "How does the draw work?", "Support", "Privacy").forEach {
            button(it).assertExists()
        }
        listOf("Version", "More").forEach { button(it).assertDoesNotExist() }
        button("Combinations").performClick()
        onNodeWithText("Combinations screen").assertExists()
    }

    @Test
    fun onAPhoneTheContactOfTheInfoButtonsOpensTheForm() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { nav -> InfoScreen(nav) }
        openMenu()
        button("Support").performClick()
        onNodeWithText("Support form").assertExists()
    }

    /** The explanation of the draw, with its flowchart, is public: any player opens it from the three-dots menu. */
    @Test
    fun onAWideScreenTheMenuOpensHowTheDrawWorks() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { nav -> InfoScreen(nav) }
        button("More").performClick()
        button("How does the draw work?").performClick()
        waitForIdle()
        onNodeWithText("How the draw works").assertExists()
        button("How does the draw work?").assertDoesNotExist()
    }

    @Test
    fun onAPhoneTheMenuOpensHowTheDrawWorks() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { nav -> InfoScreen(nav) }
        openMenu()
        button("How does the draw work?").performClick()
        waitForIdle()
        onNodeWithText("How the draw works").assertExists()
    }

    @Test
    fun aPlayerOfTheTournamentOpensTheirResultsFromTheMenu() = runDesktopComposeUiTest(width = 1200, height = 800) {
        var opened = 0
        show { nav ->
            val drawExplanation = rememberDrawExplanationState()
            AppScaffold(title = "Madrid Open", actions = { InfoTopBarButtons(nav, drawExplanation, onMyResults = { opened++ }) }) {}
        }
        button("More").performClick()
        button("My results").performClick()
        waitForIdle()
        kotlin.test.assertEquals(1, opened)
    }

    /** The top bar of the table screen: the rules, and the contact with support as in the other screens. */
    @Composable
    private fun TableBar(nav: NavHostController) = AppScaffold(title = "Round 1 - Table 1", onBack = {}, actions = { TableTopBarActions(nav) }) {}

    @Test
    fun onAWideScreenTheTableShowsRulesAndSupport() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show { nav -> TableBar(nav) }
        button("Rules").assertExists()
        button("Support").performClick()
        onNodeWithText("Support form").assertExists()
    }

    @Test
    fun onAPhoneTheSupportOfTheTableIsInTheMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show { nav -> TableBar(nav) }
        button("Support").assertDoesNotExist()
        openMenu()
        listOf("Rules", "Green Book", "Combinations", "Penalties overview").forEach { button(it).assertExists() }
        button("Support").performClick()
        onNodeWithText("Support form").assertExists()
    }
}
