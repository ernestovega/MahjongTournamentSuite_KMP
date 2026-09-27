package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.export.EmaResultsRow

@Composable
internal fun EmaReportDialog(
    report: EmaReportPreview,
    onExport: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("EMA report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(report.tournamentName, style = MaterialTheme.typography.titleMedium)
                Text("Tournament dates: ${report.startDate} to ${report.endDate}")
                Text("${report.rows.size} players")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Column {
                        ReportHeader()
                        LazyColumn(modifier = Modifier.width(720.dp)) {
                            items(report.rows, key = { it.place }) { row -> ReportRow(row) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onExport) { Text("Export CSV") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun ReportHeader() {
    ReportLine(
        values = listOf("Place", "First name", "Last name", "EMA number", "Table points", "Score", "EMA member", "Country"),
        bold = true,
    )
}

@Composable
private fun ReportRow(row: EmaResultsRow) {
    ReportLine(
        values = listOf(
            row.place.toString(),
            row.firstName,
            row.lastName,
            row.emaNumber,
            row.tablePoints,
            row.score.toString(),
            row.emaMember,
            row.country,
        ),
    )
}

@Composable
private fun ReportLine(values: List<String>, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            Text(
                text = value,
                modifier = Modifier.width(90.dp),
                style = if (bold) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}
