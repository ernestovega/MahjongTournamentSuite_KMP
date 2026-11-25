package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.place_1st
import mahjongtournamentsuite.ui.generated.resources.place_2nd
import mahjongtournamentsuite.ui.generated.resources.place_3rd
import mahjongtournamentsuite.ui.generated.resources.stat_best_hand
import org.jetbrains.compose.resources.stringResource

/**
 * The best hand icon of both apps: a raised hand. It is gold, silver or bronze for the first three best hands, and it
 * has the color of the content for the other places or when [place] is null. The trophies are for the places of the
 * players, so a best hand never shows a trophy.
 */
@Composable
fun BestHandIcon(
    place: Int? = null,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = stringResource(Res.string.stat_best_hand),
) {
    val content = LocalContentColor.current
    Icon(
        Icons.Default.PanTool,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (place != null && place in 1..3) MtsTheme.colors.position(place, content) else content,
    )
}

/** [BestHandIcon] that says what it is on a touch, a long touch or a hover: "Best hand (1st)". */
@Composable
fun BestHandMark(place: Int, size: Dp = 22.dp) {
    val placeText = when (place) {
        1 -> stringResource(Res.string.place_1st)
        2 -> stringResource(Res.string.place_2nd)
        else -> stringResource(Res.string.place_3rd)
    }
    AppTooltipBox("${stringResource(Res.string.stat_best_hand)} ($placeText)", alignEnd = true) {
        BestHandIcon(place, size = size)
    }
}
