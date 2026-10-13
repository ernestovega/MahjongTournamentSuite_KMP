package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.Arrangement
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_east
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_north
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_south
import mahjongtournamentsuite.adminapp.generated.resources.icon_wind_west
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

internal val GridHeaderHeight = 52.dp

internal val GridRowHeight = 64.dp

internal val HandSubtotalRowHeight = 36.dp

internal val GridPlayerRowHeight = 76.dp

internal val GridLabelColumnWidth = 128.dp

internal val HandsIndexColumnWidth = 48.dp

internal val HandsScoreColumnWidth = 88.dp

internal val HandsChickenColumnWidth = 84.dp

internal val HandsPenaltyColumnWidth = 92.dp

internal val HandsDoneColumnWidth = 76.dp

internal const val HandsPlayerColumnWeight = 1.6f

internal const val HandsSeatColumnWeight = 1f

internal val SeatLabels = listOf("East", "South", "West", "North")

/** Wind characters drawn as SVG paths, so they do not depend on a font. Same order as [SeatLabels]. */
internal val SeatWindIcons = listOf(
    Res.drawable.icon_wind_east,
    Res.drawable.icon_wind_south,
    Res.drawable.icon_wind_west,
    Res.drawable.icon_wind_north,
)

/**
 * Lets a table header stay visible while the page scrolls. The screen that owns the scroll provides it.
 * Without it, headers scroll with the page.
 */
@Stable
internal class StickyHeaderHost(val scrollState: ScrollState) {
    /** Top edge of the scroll viewport in root coordinates. */
    var viewportTop by mutableStateOf(0f)
}

internal val LocalStickyHeaderHost = compositionLocalOf<StickyHeaderHost?> { null }

/** A table frame with a thin border and round corners. */
@Composable
internal fun GridTable(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content,
    )
}

@Composable
internal fun GridRow(
    height: Dp,
    background: Color = Color.Transparent,
    topDivider: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (topDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = modifier.fillMaxWidth().height(height).background(background),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One cell. It draws a divider on its right side, unless it is the last cell of the row. */
@Composable
internal fun RowScope.GridCell(
    modifier: Modifier,
    last: Boolean = false,
    background: Color = Color.Transparent,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(background)
            .drawBehind {
                if (!last) {
                    val width = 1.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width - width / 2, 0f),
                        end = Offset(size.width - width / 2, size.height),
                        strokeWidth = width,
                    )
                }
            }
            .padding(horizontal = 8.dp),
        contentAlignment = alignment,
        content = content,
    )
}

@Composable
internal fun RowScope.GridHeaderCell(
    text: String,
    modifier: Modifier,
    subtext: String? = null,
    icon: DrawableResource? = null,
    last: Boolean = false,
) {
    GridCell(
        modifier = modifier,
        last = last,
        background = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtext != null) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun RowScope.GridLabelCell(text: String, last: Boolean = false) {
    GridCell(
        modifier = Modifier.width(GridLabelColumnWidth),
        last = last,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
    }
}

internal fun playerNameOrNull(id: String, playerNamesById: Map<Int, String>): String? {
    val number = id.trim().toIntOrNull() ?: return null
    return playerNamesById[number] ?: "Player $number"
}

/** Seat marks (E, S, W, N) shown next to a player in the seat selectors. */
internal fun seatMarksByPlayerId(east: String, south: String, west: String, north: String): Map<Int, String> {
    val marks = linkedMapOf<Int, MutableList<String>>()
    listOf(east to "E", south to "S", west to "W", north to "N").forEach { (value, seat) ->
        val id = value.trim().toIntOrNull() ?: return@forEach
        marks.getOrPut(id) { mutableListOf() }.add(seat)
    }
    return marks.mapValues { (_, seats) -> seats.joinToString(separator = ",") }
}
