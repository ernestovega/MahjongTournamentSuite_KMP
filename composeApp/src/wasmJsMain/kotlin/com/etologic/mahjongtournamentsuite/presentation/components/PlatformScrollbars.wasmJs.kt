package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier,
) = Unit

@Composable
actual fun PlatformHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
) = Unit

actual val PlatformScrollbarThickness: Dp = 0.dp
