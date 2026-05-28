package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

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

@Preview(device = Devices.DESKTOP)
@Composable
private fun PlatformScrollbarsPreview() {
    MtsTheme(useDarkTheme = false) {
        val listState = rememberLazyListState()
        val scrollState = rememberScrollState()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = PlatformScrollbarThickness, bottom = PlatformScrollbarThickness),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.height(220.dp),
                ) {
                    items((1..40).toList()) { index ->
                        Text(
                            text = "Row $index",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .horizontalScroll(scrollState),
                ) {
                    Text(
                        text = "This is a horizontally scrollable preview area that is intentionally wider than the viewport.",
                    )
                }
            }

            PlatformVerticalScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize()
                    .width(PlatformScrollbarThickness),
            )
            PlatformHorizontalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(PlatformScrollbarThickness),
            )
        }
    }
}
