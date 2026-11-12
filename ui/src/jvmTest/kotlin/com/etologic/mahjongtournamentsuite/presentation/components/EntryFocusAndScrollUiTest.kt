package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.ScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the focus and the scroll are when a screen or a dialog opens. */
@OptIn(ExperimentalTestApi::class)
class EntryFocusAndScrollUiTest {
    // ---------------------------------------------------------------- focus

    @Test
    fun theSupportFormWithoutAnEmailFocusesTheEmail() = runComposeUiTest {
        setContent { TestTheme { SupportForm(app = "test", initialEmail = "", onSend = { AppResult.Success(Unit) }, onBack = {}) } }
        waitForIdle()
        field("Email").assertIsFocused()
        field("Message").assertIsNotFocused()
    }

    @Test
    fun theSupportFormWithAKnownEmailFocusesTheMessage() = runComposeUiTest {
        setContent { TestTheme { SupportForm(app = "test", initialEmail = "ada@example.test", onSend = { AppResult.Success(Unit) }, onBack = {}) } }
        waitForIdle()

        field("Message").assertIsFocused()
        field("Email").assertIsNotFocused()
    }

    @Test
    fun theProfileDialogFocusesTheAlias() = runComposeUiTest {
        setContent {
            TestTheme {
                ProfileDialog(
                    profile = UserProfile(uid = "u1", email = "ada@example.test", alias = "Ada", emaId = null),
                    onSavePhoto = { _, _ -> error("unused") },
                    onRemovePhoto = { error("unused") },
                    onSaveAlias = { error("unused") },
                    onSaved = {},
                    onDismiss = {},
                )
            }
        }
        waitForIdle()

        onNode(hasSetTextAction() and hasText("Ada")).assertIsFocused()
    }

    // ---------------------------------------------------------------- scroll

    @Test
    fun aListWithoutASavedPositionStartsAtTheTop() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent { PersistentList(store = null) { state = it } }
        waitForIdle()

        assertEquals(0, state.firstVisibleItemIndex)
        onNodeWithTag("row-0").assertIsDisplayed()
    }

    @Test
    fun aListWithASavedPositionOpensThere() = runComposeUiTest {
        lateinit var state: LazyListState
        val store = ScrollPositionStore(MemoryStorage("scrollPositions" to "60,0,screen/list"))
        setContent { PersistentList(store) { state = it } }
        waitForIdle()

        assertEquals(60, state.firstVisibleItemIndex)
        onNodeWithTag("row-60").assertIsDisplayed()
    }

    @Test
    fun aSavedPositionIsUsedOnlyOnce() = runComposeUiTest {
        // A later visit to the same screen starts at the top, as after a normal navigation.
        val store = ScrollPositionStore(MemoryStorage("scrollPositions" to "60,0,screen/list"))
        assertEquals(60, store.takeRestored("screen/list")?.index)
        lateinit var state: LazyListState
        setContent { PersistentList(store) { state = it } }
        waitForIdle()

        assertEquals(0, state.firstVisibleItemIndex)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
}

@Composable
private fun PersistentList(store: ScrollPositionStore?, onState: (LazyListState) -> Unit) {
    CompositionLocalProvider(LocalScrollPositionStore provides store) {
        ScrollScope("screen") {
            val state = rememberPersistentLazyListState("list")
            onState(state)
            LazyColumn(state = state, modifier = Modifier.size(300.dp)) {
                items(100) { Text("Row $it", Modifier.height(40.dp).testTag("row-$it")) }
            }
        }
    }
}

private class MemoryStorage(vararg initial: Pair<String, String>) : SessionStateStorage {
    private val saved = mutableMapOf(*initial)
    override fun read(name: String) = saved[name]
    override fun write(name: String, value: String) {
        saved[name] = value
    }
}

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
        MtsTheme(useDarkTheme = false) { content() }
    }
}
