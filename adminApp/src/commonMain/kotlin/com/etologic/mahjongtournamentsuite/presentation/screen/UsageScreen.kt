package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsage
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.LineChart
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private fun Double?.percent(): String = this?.let { "${(it * 100).roundToInt()}%" } ?: "–"
private fun Double?.decimal(): String = this?.let { ((it * 10).roundToInt() / 10.0).toString() } ?: "–"

/** Usage analytics for administrators. It is separate from the game statistics. */
@Composable
fun UsageScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    var report by remember { mutableStateOf<UsageReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        when (val result = presenter.loadUsageReport()) {
            is AppResult.Success -> {
                report = result.value
                message = null
            }
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
        isLoading = false
    }

    AppScaffold(
        title = "App usage",
        subtitle = report?.generatedAt?.let { "Generated $it" },
        isLoading = isLoading,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        ScreenColumn(
            maxWidth = 1100.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            scrollable = true,
        ) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val data = report ?: return@ScreenColumn
            StatSection("Summary") {
                StatRow("Tournaments", data.totals.tournaments.toString())
                StatRow("Saves", data.totals.saves.toString())
                StatRow("Saves from players", data.totals.playerSaves.toString())
                StatRow("Saves from editors", data.totals.editorSaves.toString())
                StatRow("Share from players", data.totals.playerShare.percent())
                StatRow("Refused saves (conflicts)", data.totals.conflicts.toString())
                StatRow("Conflict rate", data.totals.conflictRate.percent())
                StatRow("Table resets", data.totals.resets.toString())
            }
            StatSection("Saves by hour of the day (UTC)") {
                Spacer(Modifier.height(12.dp))
                LineChart(listOf("Saves" to data.savesByHour))
            }
            StatSection("Users by saves") {
                if (data.users.isEmpty()) Text("No saves yet.", Modifier.padding(vertical = 10.dp))
                data.users.take(20).forEach { user ->
                    StatRow("${user.name ?: user.uid} (${user.source})", "${user.saves} saves")
                }
            }
            data.tournaments.forEach { TournamentUsageSection(it) }
        }
    }
}

@Composable
private fun TournamentUsageSection(item: TournamentUsage) {
    StatSection(item.name.ifBlank { item.tournamentId }) {
        StatRow("Tables completed", "${item.completedTables} of ${item.tables}")
        StatRow("Tables with hands", item.handsTables.toString())
        StatRow("Tables with totals only", item.totalsOnlyTables.toString())
        StatRow("Saves by players", item.playerSaves.toString())
        StatRow("Saves by editors", item.editorSaves.toString())
        StatRow("Saves per table", item.savesPerTouchedTable.decimal())
        StatRow("Conflicts", item.conflicts.toString())
        StatRow("Players assigned", "${item.assignedSlots} of ${item.slots}")
        StatRow("Players with account", item.slotsWithAccount.toString())
        StatRow("Invitations accepted", item.invitationsAccepted.toString())
    }
}
