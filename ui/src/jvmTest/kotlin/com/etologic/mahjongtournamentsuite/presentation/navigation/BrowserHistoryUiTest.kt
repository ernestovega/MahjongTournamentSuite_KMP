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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlin.test.Test
import kotlin.test.assertEquals

/** A browser history in memory: entries, the current one, and the pop states that a `go` sends later. */
private class FakeBrowserHistory : BrowserHistoryApi {
    val entries = mutableListOf<String?>(null)
    var index = 0
    private val listeners = mutableListOf<(String?) -> Unit>()
    private var pendingGo = 0

    override val state: String? get() = entries[index]

    override fun push(state: String) {
        while (entries.size > index + 1) entries.removeAt(entries.lastIndex)
        entries += state
        index++
    }

    override fun replace(state: String) {
        entries[index] = state
    }

    override fun go(delta: Int) {
        pendingGo += delta
    }

    override fun addPopStateListener(listener: (String?) -> Unit) {
        listeners += listener
    }

    override fun removePopStateListener(listener: (String?) -> Unit) {
        listeners -= listener
    }

    /** The browser answers the `go` calls of the app, like it does after the current task. */
    fun settle() {
        if (pendingGo == 0) return
        move(pendingGo)
        pendingGo = 0
    }

    fun back() = move(-1)

    fun forward() = move(1)

    private fun move(delta: Int) {
        index = (index + delta).coerceIn(0, entries.lastIndex)
        listeners.toList().forEach { it(state) }
    }

    /** The screens of each entry, from the first to the last. */
    fun screens(): List<List<String>> = entries.map { HistoryEntry.decode(it)?.routes.orEmpty() }
}

@OptIn(ExperimentalTestApi::class)
class BrowserHistoryUiTest {
    private fun ComposeUiTest.setApp(history: FakeBrowserHistory) {
        setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = "splash") {
                composable("splash") {
                    LaunchedEffect(Unit) { navController.navigate("home") { popUpTo("splash") { inclusive = true } } }
                }
                composable("home") {
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Column {
                        Text("Count $count", Modifier.clickable { count++ })
                        Text("Open 7", Modifier.clickable { navController.navigate("detail/7") })
                    }
                }
                composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.read { getStringOrNull("id") }
                    Column {
                        Text("Detail $id")
                        Text("App back", Modifier.clickable { navController.popBackStack() })
                        Text("Open 8", Modifier.clickable { navController.navigate("detail/8") })
                    }
                }
            }
            BrowserHistory(navController, history)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.browser(action: () -> Unit) {
        runOnIdle(action)
        waitForIdle()
    }

    @Test
    fun theSplashLeavesNoEntryBehind() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history)

        onNodeWithText("Count 0").assertIsDisplayed()
        assertEquals(listOf(listOf("home")), history.screens())
    }

    @Test
    fun theBrowserBackReturnsToTheScreenWithItsStateAndTheForwardOpensItAgain() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history)
        onNodeWithText("Count 0").performClick()
        onNodeWithText("Count 1").performClick()
        onNodeWithText("Open 7").performClick()
        waitForIdle()
        onNodeWithText("Detail 7").assertIsDisplayed()
        assertEquals(listOf(listOf("home"), listOf("home", "detail/7")), history.screens())

        browser { history.back() }
        onNodeWithText("Count 2").assertIsDisplayed()

        browser { history.forward() }
        onNodeWithText("Detail 7").assertIsDisplayed()
    }

    @Test
    fun theAppBackGoesBackInTheBrowserHistory() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history)
        onNodeWithText("Open 7").performClick()
        onNodeWithText("Open 8").performClick()
        waitForIdle()
        assertEquals(2, history.index)

        onNodeWithText("App back").performClick()
        browser { history.settle() }
        onNodeWithText("Detail 7").assertIsDisplayed()
        assertEquals(1, history.index, "The app back moved the browser back, not forward with a new entry.")
        assertEquals(3, history.entries.size)

        browser { history.forward() }
        onNodeWithText("Detail 8").assertIsDisplayed()
    }

    @Test
    fun theBrowserBackGoesSeveralScreensBackAtOnceWhenTheUserJumps() = runComposeUiTest {
        val history = FakeBrowserHistory()
        setApp(history)
        onNodeWithText("Open 7").performClick()
        onNodeWithText("Open 8").performClick()
        waitForIdle()

        browser { history.back() }
        browser { history.back() }
        onNodeWithText("Count 0").assertIsDisplayed()

        browser { history.forward() }
        browser { history.forward() }
        onNodeWithText("Detail 8").assertIsDisplayed()
        assertEquals(listOf("home", "detail/7", "detail/8"), history.screens().last())
    }
}
