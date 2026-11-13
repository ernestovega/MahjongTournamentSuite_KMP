package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/** A tag with a thin border of [color] and almost square corners, so it does not look like a button. Like the status of a tournament. */
@Composable
fun MtsOutlinedTag(color: Color, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .border(BorderStroke(1.dp, color), MtsTheme.shapes.tag)
            .padding(horizontal = MtsTheme.spacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
        content = content,
    )
}

/** A pill tinted with [accent], with an icon and a text in [accent], like the way of winning a hand or a penalty. */
@Composable
fun MtsTintedChip(icon: ImageVector, text: String, accent: Color, modifier: Modifier = Modifier, small: Boolean = false) {
    Surface(modifier, shape = MtsTheme.shapes.pill, color = accent.copy(alpha = 0.18f)) {
        Row(
            Modifier.padding(
                horizontal = if (small) MtsTheme.spacing.sm else MtsTheme.spacing.sm + MtsTheme.spacing.xxs,
                vertical = if (small) MtsTheme.spacing.xxs else MtsTheme.spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(if (small) 13.dp else 16.dp), tint = accent)
            Text(
                text,
                style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}
