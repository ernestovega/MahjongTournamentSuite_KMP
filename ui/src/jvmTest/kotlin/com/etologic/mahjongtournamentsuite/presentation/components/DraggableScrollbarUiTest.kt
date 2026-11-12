package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DraggableScrollbarUiTest {
    @Test
    fun draggingTheThumbToTheBottomShowsTheEndOfTheList() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            Box(Modifier.height(400.dp).fillMaxWidth()) {
                LazyColumn(state = state) {
                    items((1..300).toList(), key = { it }) { Text("Row $it", Modifier.height(40.dp)) }
                }
                DraggableScrollbar(state, Modifier.align(Alignment.CenterEnd)) { item -> "Item ${item.key}" }
            }
        }
        onNodeWithTag(DRAGGABLE_SCROLLBAR_TAG).assertIsDisplayed()
        // A slow drag of the thumb from the top to the bottom.
        onNodeWithTag(DRAGGABLE_SCROLLBAR_TAG).performTouchInput {
            swipe(center, Offset(center.x, center.y + 2000f), durationMillis = 600)
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex > 280, "The list is at its end, not at ${state.firstVisibleItemIndex}.")
        onNodeWithText("Row 300").assertIsDisplayed()
    }

    @Test
    fun aListThatFitsHasNoThumb() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            Box(Modifier.height(400.dp).fillMaxWidth()) {
                LazyColumn(state = state) { items((1..3).toList()) { Text("Row $it", Modifier.height(40.dp)) } }
                DraggableScrollbar(state, Modifier.align(Alignment.CenterEnd))
            }
        }
        onNodeWithTag(DRAGGABLE_SCROLLBAR_TAG).assertDoesNotExist()
    }
}
