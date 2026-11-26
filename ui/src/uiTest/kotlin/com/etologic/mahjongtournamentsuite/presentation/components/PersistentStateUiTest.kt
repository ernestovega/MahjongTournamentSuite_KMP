package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.ScrollPositionStore
import com.etologic.mahjongtournamentsuite.presentation.platform.SessionStateStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The scroll of a page and the small values that a page refresh keeps: a number, an optional number and a text. */
@OptIn(ExperimentalTestApi::class)
class PersistentStateUiTest {

    private class MemoryStorage(vararg initial: Pair<String, String>) : SessionStateStorage {
        val saved = mutableMapOf(*initial)
        override fun read(name: String) = saved[name]
        override fun write(name: String, value: String) {
            saved[name] = value
        }
    }

    @Composable
    private fun Page(store: ScrollPositionStore?, onState: (ScrollState) -> Unit) {
        CompositionLocalProvider(LocalScrollPositionStore provides store) {
            ScrollScope("screen") {
                val state = rememberPersistentScrollState("page")
                onState(state)
                Column(Modifier.size(300.dp).verticalScroll(state)) {
                    repeat(50) { Text("Line $it", Modifier.height(40.dp)) }
                }
            }
        }
    }

    @Test
    fun aPageWithASavedPositionOpensThereAndKeepsIt() = runComposeUiTest {
        val storage = MemoryStorage("scrollPositions" to "400,0,screen/page")
        lateinit var state: ScrollState
        setContent { Page(ScrollPositionStore(storage)) { state = it } }
        waitForIdle()
        assertEquals(400, state.value)

        // Once the page stands still, the store holds its position.
        assertTrue(storage.saved["scrollPositions"].orEmpty().contains("400,0,screen/page"), storage.saved.toString())
    }

    @Test
    fun withoutAStoreThePageStartsAtTheTop() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent { Page(null) { state = it } }
        waitForIdle()
        assertEquals(0, state.value)
    }

    @Test
    fun theValuesComeBackAfterARefreshAndAreSavedWhenTheyChange() = runComposeUiTest {
        val storage = MemoryStorage(
            "scrollPositions" to "3,0,screen/value:tab\n7,0,screen/value:table",
            "texts" to "screen/text:search\t+ana\nscreen/text:filter\t-",
        )
        lateinit var tab: MutableState<Int>
        lateinit var table: MutableState<Int?>
        lateinit var search: MutableState<String?>
        lateinit var filter: MutableState<String?>
        setContent {
            CompositionLocalProvider(LocalScrollPositionStore provides ScrollPositionStore(storage)) {
                ScrollScope("screen") {
                    tab = rememberPersistentInt("tab", default = 0)
                    table = rememberPersistentOptionalInt("table")
                    search = rememberPersistentText("search", default = "x")
                    filter = rememberPersistentText("filter", default = "all")
                }
            }
        }
        waitForIdle()
        assertEquals(3, tab.value)
        assertEquals(7, table.value)
        assertEquals("ana", search.value)
        assertNull(filter.value)

        tab.value = 1
        table.value = null
        search.value = "bea"
        waitForIdle()
        val positions = storage.saved["scrollPositions"].orEmpty()
        assertTrue("1,0,screen/value:tab" in positions, positions)
        assertTrue("-1,0,screen/value:table" in positions, positions)
        assertTrue("screen/text:search\t+bea" in storage.saved["texts"].orEmpty(), storage.saved.toString())
    }

    @Test
    fun withoutAStoreTheValuesStartFromTheirDefaults() = runComposeUiTest {
        lateinit var tab: MutableState<Int>
        lateinit var table: MutableState<Int?>
        lateinit var search: MutableState<String?>
        setContent {
            tab = rememberPersistentInt("tab", default = 2)
            table = rememberPersistentOptionalInt("table")
            search = rememberPersistentText("search", default = "x")
        }
        waitForIdle()
        assertEquals(2, tab.value)
        assertNull(table.value)
        assertEquals("x", search.value)
    }
}
