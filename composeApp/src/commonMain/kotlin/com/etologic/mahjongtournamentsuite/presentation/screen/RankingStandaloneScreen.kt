package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard

@Composable
fun RankingStandaloneScreen(
    tournamentId: String,
    tournamentName: String?,
    onClose: () -> Unit,
) {
    AppScaffold(
        title = tournamentName?.ifBlank { null } ?: "Rankings",
        subtitle = tournamentId,
        onBack = onClose,
    ) {
        ScreenColumn(
            maxWidth = 980.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(
                title = "Rankings",
                subtitle = "Standalone screen (desktop window)",
                content = {
                    Text(
                        text = "Rankings are not implemented yet in KMP.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Next step: compute rankings from saved table totals/hands and export to HTML like the original app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            SectionCard(
                title = "Planned outputs",
                subtitle = "What this screen will show",
                content = {
                    Text("• Current standings (rank, player, points, penalties).")
                    Text("• Filters by round / stage.")
                    Text("• Export to HTML / CSV like the original app.")
                },
            )
        }
    }
}
