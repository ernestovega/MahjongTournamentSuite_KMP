package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.icon_chicken
import org.jetbrains.compose.resources.painterResource

/** Chicken image from the app resources (`drawable/icon_chicken.png`, Twemoji). */
@Composable
fun ChickenIcon(
    contentDescription: String? = null,
    modifier: Modifier = Modifier.size(22.dp),
) {
    Image(
        painter = painterResource(Res.drawable.icon_chicken),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/** Which hand statistics the tournament counts and shows. See `Tournament.countBestHands`. */
data class HandStatOptions(
    val countBestHands: Boolean = true,
    val countChickenHands: Boolean = true,
)

/** Hand statistic options of the open tournament. The tournament screen provides them. */
val LocalHandStatOptions = compositionLocalOf { HandStatOptions() }

/**
 * Shows [text] in a tooltip on hover (desktop), and while a finger touches [content].
 * The touch is not consumed, so a clickable parent still receives it.
 */
@Composable
fun HintTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppTooltipBox(text, modifier) { content() }
}
