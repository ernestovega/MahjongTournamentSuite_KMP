package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class LazyColumnWithScrollbarTest {

    @Test
    fun verticalListScrollsAndScrollbarDoesNotInterceptRowPointerInput() = runComposeUiTest {
        var hoveredRow: Int? by mutableStateOf(null)
        var clickedRow: Int? by mutableStateOf(null)
        lateinit var listState: LazyListState

        setContent {
            MtsTheme(useDarkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    listState = rememberLazyListState()
                    LazyColumnWithScrollbar(
                        state = listState,
                        modifier = Modifier.height(120.dp),
                    ) {
                        items(10) { index ->
                            DataTableRow(
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("row-$index")
                                    .onPointerEvent(PointerEventType.Enter) {
                                        hoveredRow = index
                                    },
                                onClick = { clickedRow = index },
                            ) {
                                Text("Player $index")
                            }
                        }
                    }
                }
            }
        }

        onNodeWithTag("row-0").performMouseInput {
            moveTo(center)
            click()
        }
        waitForIdle()
        assertEquals(0, hoveredRow)
        assertEquals(0, clickedRow)

        onNodeWithTag("row-2").performMouseInput {
            moveTo(center)
            click()
        }
        waitForIdle()
        assertEquals(2, hoveredRow)
        assertEquals(2, clickedRow)

        onNodeWithTag("row-2").performMouseInput {
            scroll(120f)
        }
        waitForIdle()
        check(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            "The mouse wheel did not scroll the vertical list"
        }
    }
}
