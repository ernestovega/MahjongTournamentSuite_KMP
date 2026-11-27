package com.etologic.mahjongtournamentsuite.presentation.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The back layers on the web: a tab other than the first one of the start screen is an entry of the browser history,
 * so the browser back opens the first tab and does not leave the app (Ernesto 27-nov).
 */
@OptIn(ExperimentalTestApi::class)
class BackLayersUiTest {
    private fun ComposeUiTest.setApp(history: FakeBrowserHistory, layers: BackLayers) {
        setContent {
            val navController = rememberNavController()
            // A notice over every screen, outside the NavHost, like the reminder of the table of the player.
            var notice by rememberSaveable { mutableIntStateOf(0) }
            NavHost(navController, startDestination = "splash") {
                composable("splash") {
                    LaunchedEffect(Unit) { navController.navigate("home") { popUpTo("splash") { inclusive = true } } }
                }
                composable("home") {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    var dialog by rememberSaveable { mutableIntStateOf(0) }
                    BackLayerEffect(enabled = tab != 0, onBack = { tab = 0 }, layers = layers)
                    // Like DialogBackHandler on the web, next to the dialog.
                    if (dialog == 1) BackLayerEffect(enabled = true, onBack = { dialog = 0 }, layers = layers)
                    Column {
                        Text("Tab $tab")
                        Text("Tab one", Modifier.clickable { tab = 1 })
                        Text("Tab two", Modifier.clickable { tab = 2 })
                        Text("First tab", Modifier.clickable { tab = 0 })
                        Text("Open detail", Modifier.clickable { navController.navigate("detail") })
                        Text("Open dialog", Modifier.clickable { dialog = 1 })
                        if (dialog == 1) Text("Dialog open")
                    }
                }
                composable("detail") {
                    var working by rememberSaveable { mutableIntStateOf(0) }
                    // Like a screen that does not let the user leave while it works.
                    BackLayerEffect(enabled = working == 1, onBack = {}, layers = layers)
                    Column {
                        Text("Detail")
                        Text("Work", Modifier.clickable { working = 1 })
                        Text("Show notice", Modifier.clickable { notice = 1 })
                    }
                }
            }
            if (notice == 1) {
                BackLayerEffect(enabled = true, onBack = { notice = 0 }, layers = layers)
                Text("Notice")
            }
            BrowserHistory(navController, history, layers)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.browser(action: () -> Unit) {
        runOnIdle(action)
        waitForIdle()
    }

    @Test
    fun anotherTabAddsAnEntryAndTheBrowserBackOpensTheFirstTab() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history, BackLayers())

        onNodeWithText("Tab two").performClick()
        waitForIdle()
        assertEquals(listOf(listOf("home"), listOf("home", "~back1")), history.screens())

        browser { history.back() }
        onNodeWithText("Tab 0").assertIsDisplayed()
        assertEquals(0, history.index, "The first tab is the first entry of the app: only its back leaves the app.")
    }

    @Test
    fun movingBetweenOtherTabsKeepsOneEntry() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history, BackLayers())

        onNodeWithText("Tab one").performClick()
        onNodeWithText("Tab two").performClick()
        waitForIdle()
        assertEquals(2, history.entries.size)

        browser { history.back() }
        onNodeWithText("Tab 0").assertIsDisplayed()
    }

    @Test
    fun choosingTheFirstTabInTheAppGoesBackInTheHistory() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history, BackLayers())
        onNodeWithText("Tab two").performClick()
        waitForIdle()

        onNodeWithText("First tab").performClick()
        browser { history.settle() }
        assertEquals(0, history.index, "No entry is left behind for a tab that the person closed.")
        onNodeWithText("Tab 0").assertIsDisplayed()

        // The forward cannot open the closed tab again: the history shows the first tab.
        browser { history.forward() }
        browser { history.settle() }
        onNodeWithText("Tab 0").assertIsDisplayed()
        assertEquals(0, history.index)
    }

    @Test
    fun aScreenOverTheTabReturnsToItAndThenToTheFirstTab() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history, BackLayers())
        onNodeWithText("Tab two").performClick()
        onNodeWithText("Open detail").performClick()
        waitForIdle()
        onNodeWithText("Detail").assertIsDisplayed()
        assertEquals(listOf("home", "~back1", "detail"), history.screens().last())

        browser { history.back() }
        onNodeWithText("Tab 2").assertIsDisplayed()
        assertEquals(1, history.index)

        browser { history.back() }
        onNodeWithText("Tab 0").assertIsDisplayed()
        assertEquals(0, history.index)
    }

    @Test
    fun aLayerThatStaysGetsItsEntryBack() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history, BackLayers())
        onNodeWithText("Open detail").performClick()
        onNodeWithText("Work").performClick()
        waitForIdle()
        assertEquals(listOf("home", "detail", "~back1"), history.screens().last())

        browser { history.back() }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        onNodeWithText("Detail").assertIsDisplayed()
        assertEquals(listOf("home", "detail", "~back1"), history.screens()[history.index])
    }

    @Test
    fun theBrowserBackClosesOnlyTheDialog() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history, BackLayers())
        onNodeWithText("Tab two").performClick()
        onNodeWithText("Open dialog").performClick()
        waitForIdle()
        assertEquals(listOf("home", "~back1", "~back2"), history.screens().last())

        browser { history.back() }
        onNodeWithText("Dialog open").assertDoesNotExist()
        onNodeWithText("Tab 2").assertIsDisplayed()

        browser { history.back() }
        onNodeWithText("Tab 0").assertIsDisplayed()
        assertEquals(0, history.index)
    }

    @Test
    fun aDialogOutsideTheScreensGoesOverTheTopScreen() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history, BackLayers())
        onNodeWithText("Open detail").performClick()
        onNodeWithText("Show notice").performClick()
        waitForIdle()
        assertEquals(listOf("home", "detail", "~back1"), history.screens().last())

        browser { history.back() }
        onNodeWithText("Notice").assertDoesNotExist()
        onNodeWithText("Detail").assertIsDisplayed()
        assertEquals(1, history.index)
    }
}
