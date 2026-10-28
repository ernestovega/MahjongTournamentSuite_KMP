package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton
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
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.a_disqualified_player_gets_0_table
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.first_round_of_the_substitute
import mahjongtournamentsuite.adminapp.generated.resources.reason
import mahjongtournamentsuite.adminapp.generated.resources.reason_disqualified
import mahjongtournamentsuite.adminapp.generated.resources.reason_ill
import mahjongtournamentsuite.adminapp.generated.resources.reason_late
import mahjongtournamentsuite.adminapp.generated.resources.remove_substitution
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.substitute
import mahjongtournamentsuite.adminapp.generated.resources.substitute_player_question
import mahjongtournamentsuite.adminapp.generated.resources.substitution_explanation
import mahjongtournamentsuite.adminapp.generated.resources.substitution_of_player
import mahjongtournamentsuite.adminapp.generated.resources.the_head_referee_excluded_the_player
import mahjongtournamentsuite.adminapp.generated.resources.the_player_leaves_after_the_session
import org.jetbrains.compose.resources.stringResource

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
        title = { Text(if (current == null) stringResource(Res.string.substitute_player_question, playerName) else stringResource(Res.string.substitution_of_player, playerName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.substitution_explanation))
                Text(stringResource(Res.string.reason), style = MaterialTheme.typography.labelLarge)
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
                                SubstitutionReason.LATE -> stringResource(Res.string.reason_late)
                                SubstitutionReason.ILLNESS -> stringResource(Res.string.reason_ill)
                                SubstitutionReason.DISQUALIFIED -> stringResource(Res.string.reason_disqualified)
                            },
                        )
                    }
                }
                Text(stringResource(Res.string.first_round_of_the_substitute), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rounds.forEach { round ->
                        FilterChip(selected = fromRound == round, onClick = { fromRound = round }, label = { Text("$round") })
                    }
                }
                if (reason != SubstitutionReason.LATE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = duringSession, onCheckedChange = { duringSession = it })
                        Text(stringResource(Res.string.the_player_leaves_after_the_session))
                    }
                }
                if (reason == SubstitutionReason.ILLNESS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = excluded, onCheckedChange = { excluded = it })
                        Text(stringResource(Res.string.the_head_referee_excluded_the_player))
                    }
                }
                if (reason == SubstitutionReason.DISQUALIFIED) {
                    Text(stringResource(Res.string.a_disqualified_player_gets_0_table))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = { onSave(PlayerSubstitution(reason, fromRound, duringSession, excluded)) },
            ) { Text(stringResource(if (current == null) Res.string.substitute else Res.string.save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null) TextButton(enabled = !busy, onClick = onRemove) { Text(stringResource(Res.string.remove_substitution)) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}
