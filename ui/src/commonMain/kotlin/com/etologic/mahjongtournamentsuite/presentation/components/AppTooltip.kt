package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TooltipScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/** The widest tooltip. A narrower window makes it narrower. */
val TooltipMaxWidth = 320.dp

/** The free space between a tooltip and the edge of the window. */
val TooltipWindowMargin = 16.dp

/**
 * The tooltip of both apps. The standard tooltip text is small (12 sp), so the text here uses the body size (14 sp).
 * The tooltip is never wider or taller than the window.
 * Write a long text as short lines: one sentence per line, and a bullet "• " for each case of a list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipScope.AppPlainTooltip(modifier: Modifier = Modifier, maxWidth: Dp = TooltipMaxWidth, content: @Composable () -> Unit) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidth = with(density) { window.width.toDp() }
    val windowHeight = with(density) { window.height.toDp() }
    val width = if (window.width > 0) minOf(maxWidth, windowWidth - TooltipWindowMargin * 2) else maxWidth
    val height = if (window.height > 0) windowHeight - TooltipWindowMargin * 2 else Dp.Unspecified
    PlainTooltip(modifier.heightIn(max = height), maxWidth = width) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
    }
}

/**
 * Puts the tooltip above the anchor, or under it when there is no room above. The tooltip never leaves the window.
 * It is centered on the anchor, or ends at the end edge of the anchor when [alignEnd] is true.
 * When it fits on neither side, it moves over the anchor, on the side with more room.
 */
class AppTooltipPositionProvider(
    private val spacing: Int,
    private val margin: Int,
    private val alignEnd: Boolean = false,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val wantedX = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.center.x - popupContentSize.width / 2
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val above = anchorBounds.top - popupContentSize.height - spacing
        val below = anchorBounds.bottom + spacing
        val wantedY = when {
            above >= margin -> above
            below <= maxY -> below
            anchorBounds.top >= windowSize.height - anchorBounds.bottom -> above
            else -> below
        }
        return IntOffset(wantedX.coerceIn(margin, maxX), wantedY.coerceIn(margin, maxY))
    }
}

@Composable
fun rememberAppTooltipPositionProvider(alignEnd: Boolean = false): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density, alignEnd) {
        with(density) { AppTooltipPositionProvider(spacing = 4.dp.roundToPx(), margin = TooltipWindowMargin.roundToPx(), alignEnd = alignEnd) }
    }
}
