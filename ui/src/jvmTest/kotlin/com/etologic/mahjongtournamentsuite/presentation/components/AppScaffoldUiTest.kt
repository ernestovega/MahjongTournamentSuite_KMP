package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The top bar of [AppScaffold]: on a wide screen every button shows; on a phone the bar keeps at most one icon at each
 * side and puts the other buttons in a menu. Also the loading bar and the title that gets shorter when it does not fit.
 */
@OptIn(ExperimentalTestApi::class)
class AppScaffoldUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private val clicks = mutableListOf<String>()

    private fun button(label: String) = @Composable { AppTopBarButton(text = label, icon = Icons.Default.Home, onClick = { clicks += label }) }

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalAdaptiveTopBar provides true) {
            MtsTheme(useDarkTheme = false) { content() }
        }
    }

    private fun ComposeUiTest.node(label: String) = onNode(hasText(label) or hasContentDescription(label))

    private fun ComposeUiTest.menus() = onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().size

    @Test
    fun onAWideScreenEveryButtonShowsAndTheLoadingBarDisablesThem() = runDesktopComposeUiTest(width = 1200, height = 800) {
        show {
            AppScaffold(
                title = "Madrid Open",
                subtitleContent = { Text("Spain flag") },
                titleLeading = { Text("Photo") },
                titleAction = { AppTopBarButton(text = "Edit", icon = Icons.Default.Edit, onClick = {}) },
                isLoading = true,
                navigationIcon = button("Home"),
                rootLeadingActions = button("Players"),
                actions = button("Timer"),
            ) { Text("Content") }
        }
        listOf("Madrid Open", "Spain flag", "Photo", "Edit", "Home", "Players", "Timer", "Content").forEach { node(it).assertExists() }
        onNode(hasClickAction() and hasText("Timer")).assertIsNotEnabled()
        onNode(hasClickAction() and hasText("Edit")).assertIsNotEnabled()
        assertEquals(0, menus())
    }

    @Test
    fun onAPhoneTheButtonsLeftOfTheTitleShareOneMenuThatIsNotTheThreeDots() = runDesktopComposeUiTest(width = 400, height = 800) {
        show {
            AppScaffold(title = "Madrid Open", navigationIcon = button("Home"), leadingActions = button("Players")) {}
        }
        node("Players").assertDoesNotExist()
        assertEquals(1, menus())
        onNode(hasClickAction() and hasContentDescription("Menu")).performClick()
        waitForIdle()
        node("Home").assertExists()
        node("Players").performClick()
        waitForIdle()
        assertEquals(listOf("Players"), clicks)
    }

    @Test
    fun onAPhoneTheRootButtonsStayVisibleWhileTheyFit() = runDesktopComposeUiTest(width = 400, height = 800) {
        show {
            AppScaffold(title = "Madrid Open", rootLeadingActions = { button("Players")(); button("Ranking")() }) {}
        }
        node("Players").assertExists()
        node("Ranking").assertExists()
        assertEquals(0, menus())
    }

    @Test
    fun onAPhoneMoreRootButtonsThanFitGoIntoOneMenu() = runDesktopComposeUiTest(width = 400, height = 800) {
        show {
            AppScaffold(
                title = "Madrid Open",
                rootLeadingActions = { button("Players")(); button("Ranking")() },
                actions = button("Timer"),
            ) {}
        }
        // The icons are measured to count them, but only the menu is placed.
        onNode(hasContentDescription("Players")).assertIsNotDisplayed()
        assertEquals(2, menus(), "One menu at the left for the root buttons, the three dots at the right.")
        onAllNodesWithContentDescription("Menu")[0].performClick()
        waitForIdle()
        onNode(hasClickAction() and hasText("Players")).assertExists()
        onNode(hasClickAction() and hasText("Ranking")).performClick()
        waitForIdle()
        assertEquals(listOf("Ranking"), clicks)
    }

    @Test
    fun onAPhoneWithABackButtonTheOtherButtonsAndTheTitleButtonGoIntoTheThreeDots() = runDesktopComposeUiTest(width = 400, height = 800) {
        show {
            AppScaffold(
                title = "Madrid Open",
                onBack = { clicks += "Back" },
                leadingActions = button("Players"),
                titleAction = { AppTopBarButton(text = "Edit", icon = Icons.Default.Edit, onClick = { clicks += "Edit" }) },
                actions = { AppTopBarButton(text = "Settings", icon = Icons.Default.Settings, onClick = { clicks += "Settings" }) },
            ) {}
        }
        node("Edit").assertDoesNotExist()
        assertEquals(1, menus())
        onNode(hasClickAction() and hasContentDescription("Menu")).performClick()
        waitForIdle()
        listOf("Players", "Settings").forEach { node(it).assertExists() }
        node("Edit").performClick()
        waitForIdle()
        node("Back").performClick()
        waitForIdle()
        assertEquals(listOf("Edit", "Back"), clicks)
    }

    @Test
    fun aTitleThatDoesNotFitGivesItsPlaceToTheShortTitle() = runDesktopComposeUiTest(width = 360, height = 800) {
        val long = "International Riichi Mahjong Championship of the City of Madrid 2026"
        show {
            AppScaffold(
                title = long,
                shortTitle = "IRMC",
                subtitle = "A subtitle that is far too long to fit in the top bar of a phone, even at the smallest size",
                actions = { AppTopBarButton(text = "Timer", icon = Icons.Default.Timer, onClick = {}) },
            ) {}
        }
        onNodeWithText("IRMC").assertExists()
        onNodeWithText(long).assertDoesNotExist()
        onNodeWithText("A subtitle", substring = true).assertExists()
    }

    @Test
    fun aShortTitleThatDoesNotFitEitherIsCut() = runDesktopComposeUiTest(width = 360, height = 800) {
        show {
            AppScaffold(
                title = "International Riichi Mahjong Championship of the City of Madrid 2026",
                shortTitle = "Riichi Championship of Madrid, the short name that is still long",
                onBack = {},
                actions = { AppTopBarButton(text = "Players", icon = Icons.Default.People, onClick = {}) },
            ) {}
        }
        onNodeWithText("Riichi Championship of Madrid, the short name that is still long").assertExists()
    }

    @Test
    fun theScaffoldIsSkippedWhenItsParentDrawsAgainWithTheSameValues() = runDesktopComposeUiTest(width = 1200, height = 800) {
        var count by mutableStateOf(0)
        show {
            Text("Count $count")
            AppScaffold(title = "Madrid Open", autoFocusFirst = false) { Text("Content") }
        }
        count = 1
        waitForIdle()
        onNodeWithText("Count 1").assertExists()
        onNodeWithText("Madrid Open").assertExists()
    }
}
