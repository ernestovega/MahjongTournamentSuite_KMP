package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
expect fun PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
)

@Composable
expect fun PlatformHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
)

expect val PlatformScrollbarThickness: Dp
