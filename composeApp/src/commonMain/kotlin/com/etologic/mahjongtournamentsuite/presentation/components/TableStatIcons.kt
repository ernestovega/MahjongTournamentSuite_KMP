package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.icon_chicken
import mahjongtournamentsuite.composeapp.generated.resources.icon_trophy
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

/** Trophy image from the app resources (`drawable/icon_trophy.svg`, Twemoji). */
@Composable
fun TrophyIcon(
    contentDescription: String? = null,
    modifier: Modifier = Modifier.size(22.dp),
) {
    Image(
        painter = painterResource(Res.drawable.icon_trophy),
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
    bestHandScore: Int?,
) {
    if (chickenHandCount > 0) {
        val text = if (chickenHandCount == 1) "1 chicken hand" else "$chickenHandCount chicken hands"
        HintTooltip(text) { ChickenIcon(contentDescription = text) }
    }
    if (bestHandScore != null) {
        val text = "Best hand of the tournament: $bestHandScore"
        HintTooltip(text) { TrophyIcon(contentDescription = text) }
    }
}
