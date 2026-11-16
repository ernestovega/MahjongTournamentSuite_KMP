package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.allDrawableResources
import org.jetbrains.compose.resources.imageResource

/**
 * A short way to write the tiles of a hand, as Mahjong Scoring 2 drew them.
 * Numbers take the suit that follows them: m characters, p dots, s bamboos, f flowers, g seasons. "123m" is 1, 2 and 3
 * of characters. The honors are one letter each: E S W N for the winds, C (red), F (green) and P (white) for the dragons.
 * X is a concealed tile, shown from the back. A space starts a new group.
 */
internal object TileNotation {
    /** The drawable of every tile, by the name of the drawable. "tile_back" is the back of a tile. */
    fun parse(notation: String): List<List<String>> = notation.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::parseGroup)

    private fun parseGroup(group: String): List<String> {
        val tiles = mutableListOf<String>()
        val numbers = StringBuilder()
        for (char in group) {
            when {
                char.isDigit() -> numbers.append(char)
                char in "mpsfg" -> {
                    require(numbers.isNotEmpty()) { "A suit without numbers in $group" }
                    numbers.forEach { tiles += "tile_$char$it" }
                    numbers.clear()
                }
                else -> {
                    require(numbers.isEmpty()) { "Numbers without a suit in $group" }
                    tiles += HONORS[char] ?: error("Unknown tile $char in $group")
                }
            }
        }
        require(numbers.isEmpty()) { "Numbers without a suit in $group" }
        return tiles
    }

    private val HONORS = mapOf(
        'E' to "tile_we",
        'S' to "tile_ws",
        'W' to "tile_ww",
        'N' to "tile_wn",
        'C' to "tile_dr",
        'F' to "tile_dg",
        'P' to "tile_dw",
        'X' to "tile_back",
    )
}

/**
 * The width of a tile over its height, as in the pictures of the tiles. The pictures are the set of Martin Persson
 * (martinpersson.org, free with a link to his page), which reads clearly even small.
 */
private const val TileAspect = 84f / 128f

/** The largest a tile grows in the list, so that a short hand on a wide screen does not get huge. */
private val MaxTileWidth = 52.dp

private val TileGap = 1.dp
private val GroupGap = 6.dp

/**
 * The groups of tiles of a hand in one row. The tiles grow with the width, up to [maxTileWidth].
 * The pictures load only when the row shows, so a closed combination costs nothing.
 */
@Composable
internal fun TileGroupsRow(
    groups: List<List<String>>,
    modifier: Modifier = Modifier,
    maxTileWidth: Dp = MaxTileWidth,
    alignment: Alignment = Alignment.TopStart,
) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val count = groups.sumOf { it.size }.coerceAtLeast(1)
        val gaps = TileGap * (count - groups.size).coerceAtLeast(0) + GroupGap * (groups.size - 1).coerceAtLeast(0)
        val width: Dp = min(maxTileWidth, (maxWidth - gaps) / count)
        val height = width / TileAspect
        Row(horizontalArrangement = Arrangement.spacedBy(GroupGap)) {
            groups.forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    group.forEach { name ->
                        val tile = Res.allDrawableResources.getValue(name)
                        // The high filter keeps the pictures smooth when they draw much smaller than they are.
                        Image(
                            imageResource(tile),
                            contentDescription = null,
                            modifier = Modifier.size(width, height),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
        }
    }
}
