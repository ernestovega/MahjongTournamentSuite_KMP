package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.icon_chicken
import mahjongtournamentsuite.composeapp.generated.resources.icon_trophy
import mahjongtournamentsuite.composeapp.generated.resources.icon_trophy_bronze
import mahjongtournamentsuite.composeapp.generated.resources.icon_trophy_silver
import org.jetbrains.compose.resources.painterResource

/** Chicken image from the app resources (`drawable/icon_chicken.svg`, Twemoji). */
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

/** Medal color of a trophy. */
enum class TrophyMedal { Gold, Silver, Bronze }

/**
 * Trophy image from the app resources (`drawable/icon_trophy*.svg`, Twemoji with changed colors).
 * The [medal] sets the color: gold, silver or bronze.
 */
@Composable
fun TrophyIcon(
    contentDescription: String? = null,
    modifier: Modifier = Modifier.size(22.dp),
    medal: TrophyMedal = TrophyMedal.Gold,
) {
    Image(
        painter = painterResource(
            when (medal) {
                TrophyMedal.Gold -> Res.drawable.icon_trophy
                TrophyMedal.Silver -> Res.drawable.icon_trophy_silver
                TrophyMedal.Bronze -> Res.drawable.icon_trophy_bronze
            },
        ),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/**
 * Shows [text] in a tooltip on hover (desktop) and on click or long press.
 * The click is not consumed, so a clickable parent still receives it.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HintTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.pointerInput(tooltipState) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (waitForUpOrCancellation() != null) {
                        coroutineScope.launch { tooltipState.show() }
                    }
                }
            },
        ) { content() }
    }
}

/** Chicken and trophy images for list entries, each with a tooltip. They show only when they have data. */
@Composable
fun TableStatBadges(
    chickenHandCount: Int,
    bestHandScores: List<Int>,
) {
    val options = LocalHandStatOptions.current
    if (options.countChickenHands && chickenHandCount > 0) {
        val text = if (chickenHandCount == 1) "1 chicken hand" else "$chickenHandCount chicken hands"
        HintTooltip(text) { ChickenIcon(contentDescription = text) }
    }
    if (options.countBestHands && bestHandScores.isNotEmpty()) {
        val text = if (bestHandScores.size == 1) {
            "Best hand of the tournament: ${bestHandScores.first()}"
        } else {
            "${bestHandScores.size} best hands of the tournament: ${bestHandScores.joinToString(", ")}"
        }
        HintTooltip(text) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrophyIcon(contentDescription = text)
                if (bestHandScores.size > 1) {
                    Text("×${bestHandScores.size}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
