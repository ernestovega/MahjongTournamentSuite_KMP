package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

/** The width of the column with the Σ sign, left of the running totals of the hands. The headers leave it free. */
internal val SigmaColumnWidth = 18.dp

/**
 * The value of each seat in four columns. [subtotals], when given, show small under the values, with one Σ sign on the
 * left of the line. [showWinds] puts the wind of each seat before its value; the hands do not need it, the header of
 * the hands shows the seats. [muted] greys the values of a hand that does not count yet.
 */
@Composable
internal fun SeatValuesRow(
    values: SeatTextValues,
    subtotals: SeatTextValues? = null,
    names: List<String>? = null,
    showWinds: Boolean = true,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (subtotals != null) SigmaLabel()
        (0..3).forEach { seat ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showWinds) SeatWindIcon(seat, Modifier.size(16.dp))
                    Text(
                        text = values.bySeat(seat).ifBlank { "-" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
                names?.getOrNull(seat)?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                subtotals?.let {
                    Text(
                        text = it.bySeat(seat).ifBlank { "-" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The Σ sign of a line of running totals, in the [SigmaColumnWidth] column. */
@Composable
internal fun SigmaLabel(modifier: Modifier = Modifier) {
    Text(
        text = "Σ",
        modifier = modifier.width(SigmaColumnWidth),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun SeatWindIcon(seat: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(SeatWindIcons[seat]),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(LocalContentColor.current),
    )
}
