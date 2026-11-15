package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier,
) {
    AutoHide(listState.isScrollInProgress, modifier) { autoHideModifier ->
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = autoHideModifier,
        )
    }
}

@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    AutoHide(scrollState.isScrollInProgress, modifier) { autoHideModifier ->
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = autoHideModifier,
        )
    }
}

@Composable
actual fun PlatformHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    AutoHide(scrollState.isScrollInProgress, modifier) { autoHideModifier ->
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = autoHideModifier,
        )
    }
}

/**
 * The scrollbar shows only while the list scrolls or the mouse is over it, so a list at rest has none.
 * It stays in the layout while hidden, so the mouse can find it and drag it.
 */
@Composable
private fun AutoHide(
    scrolling: Boolean,
    modifier: Modifier,
    scrollbar: @Composable (Modifier) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val alpha by scrollbarAlpha(rememberScrollbarShown(scrolling, hovered))
    scrollbar(modifier.hoverable(interactionSource).alpha(alpha))
}

actual val PlatformScrollbarThickness: Dp = 12.dp
