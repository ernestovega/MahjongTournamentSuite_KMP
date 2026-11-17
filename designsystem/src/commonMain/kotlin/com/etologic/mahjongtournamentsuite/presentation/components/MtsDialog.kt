package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsWindowWidth

/*
 * The dialogs of the suite (Ernesto, 2026-11-17):
 * - no icon above the title;
 * - the title, the text and the buttons are centered;
 * - on a phone the buttons go one under the other, the main one on top; on a bigger window they share a line, the
 *   main one on the right;
 * - a destructive action is red: filled when it is the main button, outlined when it is a secondary one.
 */

/** The colors of the buttons of the suite that are not in the Material defaults. */
object MtsButtons {
    /** A destructive action that is the main button of its place, like "Delete" in a delete confirmation. */
    @Composable
    fun destructiveColors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )

    /** A destructive action that is a secondary button, like "Discard" next to "Save". Use it with [destructiveBorder]. */
    @Composable
    fun destructiveOutlinedColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)

    /** The border of a secondary destructive button. A disabled one keeps the usual soft border. */
    @Composable
    fun destructiveBorder(enabled: Boolean = true): BorderStroke = if (enabled) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.outlinedButtonBorder(enabled = false)
    }
}

/** The title of a dialog, centered. [subtitle] is a short detail under it, like the round of a table. */
@Composable
fun MtsDialogTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, textAlign = TextAlign.Center)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A paragraph of a dialog, centered. */
@Composable
fun MtsDialogText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

/**
 * The buttons of a dialog. Put them in the confirm slot of the AlertDialog, the main one first, and leave the dismiss
 * slot empty. On a phone, or when they do not fit on one line, they go one under the other and centered, the main one
 * on top. On a bigger window they share a centered line, the main one on the right.
 */
@Composable
fun MtsDialogButtons(modifier: Modifier = Modifier, stacked: Boolean? = null, content: @Composable () -> Unit) {
    val compact = stacked ?: (MtsTheme.windowWidth == MtsWindowWidth.Compact)
    val gap = MtsTheme.spacing.sm
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val rowWidth = placeables.sumOf { it.width } + gapPx * (placeables.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else rowWidth
        if (!compact && rowWidth <= width) {
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                // The main button is the first one and goes on the right.
                var x = (width - rowWidth) / 2
                placeables.asReversed().forEach {
                    it.place(x, (height - it.height) / 2)
                    x += it.width + gapPx
                }
            }
        } else {
            // The text buttons of Material have little height, so a smaller gap keeps the stack together.
            val stackGap = 4.dp.roundToPx()
            val height = placeables.sumOf { it.height } + stackGap * (placeables.size - 1).coerceAtLeast(0)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.place((width - it.width) / 2, y)
                    y += it.height + stackGap
                }
            }
        }
    }
}
