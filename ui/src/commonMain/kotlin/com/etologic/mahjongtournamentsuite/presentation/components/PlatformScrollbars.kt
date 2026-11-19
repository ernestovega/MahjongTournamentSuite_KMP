package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/**
 * A vertical lazy list with the fading edges and an always visible scrollbar beside it, outside the rows
 * ([scrollIndicators]).
 */
@Composable
fun LazyColumnWithScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().scrollIndicators(state, scrollbar = scrollbar),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** A scrollable column with the fading edges and an always visible scrollbar beside it ([scrollIndicators]). */
@Composable
fun ScrollableColumnWithScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A fixed height from the caller reaches the column through the min constraints. A height that is only a maximum
    // lets the box wrap short content, so a dialog with one line of text does not grow to that maximum.
    Box(modifier = modifier, propagateMinConstraints = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scrollIndicators(state, scrollbar = scrollbar)
                .verticalScroll(state),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun PlatformScrollbarsPreview() {
    MtsTheme(useDarkTheme = false) {
        val listState = rememberLazyListState()
        Column {
            LazyColumnWithScrollbar(state = listState, modifier = Modifier.height(220.dp)) {
                items((1..40).toList()) { index ->
                    Text(text = "Row $index", modifier = Modifier.fillMaxWidth().height(28.dp))
                }
            }
            ScrollableColumnWithScrollbar(state = rememberScrollState(), modifier = Modifier.height(96.dp)) {
                repeat(10) { Text("Line $it") }
            }
        }
    }
}
