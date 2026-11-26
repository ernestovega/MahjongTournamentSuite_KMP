package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/**
 * A vertical lazy list with the fading edges and a scrollbar that shows while it scrolls, outside the rows
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
    /**
     * True for the list of an AlertDialog that takes its whole width: it reaches both sides of the dialog, so its
     * fading edges take the whole width, and the margins of the dialog go inside it ([dialogFullBleed]).
     */
    inDialog: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = if (inDialog) modifier.dialogFullBleed() else modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().scrollIndicators(state, scrollbar = if (inDialog) MtsScrollbar.Overlay else scrollbar),
            contentPadding = if (inDialog) contentPadding.plusDialogMargins() else contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** A scrollable column with the fading edges and a scrollbar that shows while it scrolls ([scrollIndicators]). */
@Composable
fun ScrollableColumnWithScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    scrollbar: MtsScrollbar = MtsScrollbar.Outside,
    /**
     * True for the content of an AlertDialog: it reaches both sides of the dialog, so its fading edges take the whole
     * width, and the margins of the dialog go inside the scroll ([dialogVerticalScroll]).
     */
    inDialog: Boolean = false,
    /** Space inside the scroll, around the content: the margins of a column that reaches the edges ([screenFullBleed]). */
    contentPadding: PaddingValues = PaddingValues.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A fixed height from the caller reaches the column through the min constraints. A height that is only a maximum
    // lets the box wrap short content, so a dialog with one line of text does not grow to that maximum.
    Box(modifier = if (inDialog) modifier.dialogFullBleed() else modifier, propagateMinConstraints = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scrollIndicators(state, scrollbar = if (inDialog) MtsScrollbar.Overlay else scrollbar)
                .verticalScroll(state)
                .padding(if (inDialog) contentPadding.plusDialogMargins() else contentPadding),
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

/** [this] padding with the side margins of an AlertDialog added, for a list that reaches the sides of the dialog. */
@Composable
private fun PaddingValues.plusDialogMargins(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + MtsDialogPadding,
        top = calculateTopPadding(),
        end = calculateEndPadding(direction) + MtsDialogPadding,
        bottom = calculateBottomPadding(),
    )
}
