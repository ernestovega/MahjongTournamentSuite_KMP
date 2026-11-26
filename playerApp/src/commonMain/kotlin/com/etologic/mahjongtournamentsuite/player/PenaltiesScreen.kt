package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.allStringResources
import mahjongtournamentsuite.playerapp.generated.resources.penalties_progressive
import mahjongtournamentsuite.playerapp.generated.resources.penalties_section_dead_hand
import mahjongtournamentsuite.playerapp.generated.resources.penalties_section_points
import mahjongtournamentsuite.playerapp.generated.resources.penalties_section_warning
import mahjongtournamentsuite.playerapp.generated.resources.penalties_source
import mahjongtournamentsuite.playerapp.generated.resources.penalties_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The three kinds of penalty of the summary of the EMA, from the lightest to the most severe. */
internal enum class PenaltyKind(val prefix: String, val count: Int) {
    WARNING("w", 9),
    DEAD_HAND("d", 9),
    POINTS("p", 8),
    ;

    val items: List<StringResource> get() = (1..count).map { Res.allStringResources.getValue("penalty_$prefix$it") }
}

/** The usual penalties of the Mahjong Competition Rules, grouped by how severe they are. */
@Composable
fun PenaltiesScreen(navController: NavHostController) {
    AppScaffold(
        title = stringResource(Res.string.penalties_title),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = { ContactTopBarButton(navController) },
    ) {
        ScreenColumn(
            maxWidth = 720.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            scrollable = true,
        ) {
            PenaltySection(
                kind = PenaltyKind.WARNING,
                title = stringResource(Res.string.penalties_section_warning),
                icon = Icons.Default.WarningAmber,
                accent = penaltyColor(light = 0xFF9A5B00, dark = 0xFFFFC94D),
                note = stringResource(Res.string.penalties_progressive),
            )
            PenaltySection(
                kind = PenaltyKind.DEAD_HAND,
                title = stringResource(Res.string.penalties_section_dead_hand),
                icon = Icons.Default.Block,
                accent = penaltyColor(light = 0xFFC2410C, dark = 0xFFFF8A50),
            )
            PenaltySection(
                kind = PenaltyKind.POINTS,
                title = stringResource(Res.string.penalties_section_points),
                icon = Icons.Default.Gavel,
                accent = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(Res.string.penalties_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Amber for a warning, orange for a dead hand, and the red of errors for the point penalties. */
@Composable
private fun penaltyColor(light: Long, dark: Long): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(dark) else Color(light)

@Composable
private fun PenaltySection(kind: PenaltyKind, title: String, icon: ImageVector, accent: Color, note: String? = null) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            }
            if (note != null) {
                Surface(color = accent.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
                    Text(note, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            kind.items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(penaltyText(stringResource(item)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The text before the first colon is the name of the infraction, in bold. A text without a colon is all bold. */
internal fun penaltyText(text: String): AnnotatedString = buildAnnotatedString {
    val colon = text.indexOf(':')
    if (colon < 0) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
    } else {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(0, colon + 1)) }
        append(text.substring(colon + 1))
    }
}
