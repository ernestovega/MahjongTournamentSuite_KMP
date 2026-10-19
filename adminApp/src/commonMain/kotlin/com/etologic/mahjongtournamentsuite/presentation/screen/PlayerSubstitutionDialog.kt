package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason

/**
 * The editor sets here that a substitute plays for a player from a round on.
 * The EMA rules decide the table points. The substitute is never recorded.
 */
@Composable
fun PlayerSubstitutionDialog(
    playerName: String,
    current: PlayerSubstitution?,
    rounds: List<Int>,
    defaultRound: Int,
    busy: Boolean,
    onSave: (PlayerSubstitution) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(current?.reason ?: SubstitutionReason.ILLNESS) }
    var fromRound by remember { mutableStateOf(current?.fromRound ?: defaultRound) }
    var duringSession by remember { mutableStateOf(current?.duringSession ?: false) }
    var excluded by remember { mutableStateOf(current?.excludedFromEma ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Substitute $playerName?" else "Substitution of $playerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A substitute plays for this player from the chosen round. The substitute is not recorded " +
                        "and gets no result in the ranking. The player gets 0 table points for the rounds that the substitute plays.",
                )
                Text("Reason", style = MaterialTheme.typography.labelLarge)
                SubstitutionReason.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = reason == option,
                            onClick = {
                                reason = option
                                if (option == SubstitutionReason.LATE) duringSession = false
                                if (option != SubstitutionReason.ILLNESS) excluded = false
                            },
                        )
                        Text(
                            when (option) {
                                SubstitutionReason.LATE -> "The player came late."
                                SubstitutionReason.ILLNESS -> "The player is ill."
                                SubstitutionReason.DISQUALIFIED -> "The player is disqualified."
                            },
                        )
                    }
                }
                Text("First round of the substitute", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rounds.forEach { round ->
                        FilterChip(selected = fromRound == round, onClick = { fromRound = round }, label = { Text("$round") })
                    }
                }
                if (reason != SubstitutionReason.LATE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = duringSession, onCheckedChange = { duringSession = it })
                        Text("The player leaves after the session has started. The substitute gets 0 table points in that round.")
                    }
                }
                if (reason == SubstitutionReason.ILLNESS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = excluded, onCheckedChange = { excluded = it })
                        Text("The head referee excluded the player from the final ranking. The EMA report has no row for the player.")
                    }
                }
                if (reason == SubstitutionReason.DISQUALIFIED) {
                    Text("A disqualified player gets 0 table points for the whole tournament. The result still counts for the EMA ranking.")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = { onSave(PlayerSubstitution(reason, fromRound, duringSession, excluded)) },
            ) { Text(if (current == null) "Substitute" else "Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null) TextButton(enabled = !busy, onClick = onRemove) { Text("Remove substitution") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
