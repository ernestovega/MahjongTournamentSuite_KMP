package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsRadioRow
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
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
import mahjongtournamentsuite.adminapp.generated.resources.substitution
import mahjongtournamentsuite.adminapp.generated.resources.substitution_explanation
import mahjongtournamentsuite.adminapp.generated.resources.the_head_referee_excluded_the_player
import mahjongtournamentsuite.adminapp.generated.resources.the_player_leaves_after_the_session
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

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

    TrackedDialog("SubstitutePlayerQuestion") {
        AlertDialog(
            onDismissRequest = onDismiss,
            // A short title fits one line on a phone. The player goes under it.
            title = { MtsDialogTitle(stringResource(Res.string.substitution), subtitle = playerName) },
            // The same gap between the sections, and the same height for every radio and checkbox line.
            text = {
                Column(Modifier.verticalScrollWithIndicators(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg)) {
                    Text(
                        stringResource(Res.string.substitution_explanation),
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    SubstitutionSection(stringResource(Res.string.reason)) {
                        SubstitutionReason.entries.forEach { option ->
                            MtsRadioRow(
                                selected = reason == option,
                                onClick = {
                                    reason = option
                                    if (option == SubstitutionReason.LATE) duringSession = false
                                    if (option != SubstitutionReason.ILLNESS) excluded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                gap = 12.dp,
                                contentPadding = ChoiceLinePadding,
                                controlPadding = 0.dp,
                            ) {
                                ChoiceText(
                                    when (option) {
                                        SubstitutionReason.LATE -> stringResource(Res.string.reason_late)
                                        SubstitutionReason.ILLNESS -> stringResource(Res.string.reason_ill)
                                        SubstitutionReason.DISQUALIFIED -> stringResource(Res.string.reason_disqualified)
                                    },
                                )
                            }
                        }
                    }
                    SubstitutionSection(stringResource(Res.string.first_round_of_the_substitute)) {
                        Row(
                            Modifier.align(Alignment.CenterHorizontally).horizontalScrollWithIndicators(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rounds.forEach { round ->
                                FilterChip(selected = fromRound == round, onClick = { fromRound = round }, label = { Text("$round") })
                            }
                        }
                    }
                    if (reason != SubstitutionReason.DISQUALIFIED) {
                        Column {
                            if (reason != SubstitutionReason.LATE) {
                                MtsCheckboxRow(
                                    checked = duringSession,
                                    onCheckedChange = { duringSession = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    gap = 12.dp,
                                    contentPadding = ChoiceLinePadding,
                                    controlPadding = 0.dp,
                                ) { ChoiceText(stringResource(Res.string.the_player_leaves_after_the_session)) }
                            }
                            if (reason == SubstitutionReason.ILLNESS) {
                                MtsCheckboxRow(
                                    checked = excluded,
                                    onCheckedChange = { excluded = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    gap = 12.dp,
                                    contentPadding = ChoiceLinePadding,
                                    controlPadding = 0.dp,
                                ) { ChoiceText(stringResource(Res.string.the_head_referee_excluded_the_player)) }
                            }
                        }
                    } else {
                        Text(
                            stringResource(Res.string.a_disqualified_player_gets_0_table),
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(
                        enabled = !busy,
                        onClick = { onSave(PlayerSubstitution(reason, fromRound, duringSession, excluded)) },
                    ) { Text(stringResource(if (current == null) Res.string.substitute else Res.string.save)) }
                    if (current != null) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = onRemove,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(!busy),
                        ) { Text(stringResource(Res.string.remove_substitution)) }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** A labelled part of the substitution dialog: a small centered title and its content close under it. */
@Composable
private fun SubstitutionSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        content()
    }
}

/** The text of a radio or checkbox line. It takes the rest of the line and wraps when it is long. */
@Composable
private fun RowScope.ChoiceText(text: String) {
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
}

/** The padding inside a line of a choice, so the whole line is the touch target. */
private val ChoiceLinePadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
