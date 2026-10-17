package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/** The scroll state of a [ScreenColumn] and the top of its viewport in the window. A section uses them to stick or to scroll. */
class ScreenScroll(val state: ScrollState) {
    var viewportTop by mutableFloatStateOf(0f)
}

/** The [ScreenScroll] of the closest scrolling [ScreenColumn]. Null when the screen does not scroll. */
val LocalScreenScroll = compositionLocalOf<ScreenScroll?> { null }

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 900.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val scrollState = rememberPersistentScrollState("screen")
        val screenScroll = remember(scrollState) { ScreenScroll(scrollState) }
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalScreenScroll provides if (scrollable) screenScroll else null) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { screenScroll.viewportTop = it.positionInRoot().y }
                    .padding(contentPadding)
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
            }
            if (scrollable) {
                PlatformVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(PlatformScrollbarThickness),
                )
            }
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun ScreenColumnPreview() {
    MtsTheme(useDarkTheme = false) {
        ScreenColumn(
            maxWidth = 700.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "One") { Text("First item") }
            SectionCard(title = "Two") { Text("Second item") }
        }
    }
}
