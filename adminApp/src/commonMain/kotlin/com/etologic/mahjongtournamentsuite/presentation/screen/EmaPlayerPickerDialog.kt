package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.name_ema_id_or_country
import mahjongtournamentsuite.adminapp.generated.resources.no_player_found
import mahjongtournamentsuite.adminapp.generated.resources.select_ema_player
import mahjongtournamentsuite.adminapp.generated.resources.showing_first_of
import mahjongtournamentsuite.adminapp.generated.resources.used_by
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

private const val MAX_RESULTS = 100

/**
 * Searches the shared EMA player list by name, EMA ID or country.
 * A player whose EMA ID already belongs to another account is shown with that account and cannot be selected.
 *
 * @param owners the email of the account that owns each taken EMA ID
 */
@Composable
internal fun EmaPlayerPickerDialog(
    players: List<Player>,
    owners: Map<String, String>,
    selectedEmaId: String?,
    onSelect: (Player) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val compact = LocalCompactLayout.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val terms = query.trim().split(' ').filter { it.isNotEmpty() }
    val matches = players.filter { player ->
        val haystack = "${player.displayName} ${player.emaId} ${player.country}"
        terms.all { haystack.matchesSearch(it) }
    }
    TrackedDialog("SelectEmaPlayer") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.select_ema_player)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(Res.string.name_ema_id_or_country)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    if (matches.isEmpty()) {
                        Text(stringResource(Res.string.no_player_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val matchesState = rememberLazyListState()
                        LazyColumn(Modifier.heightIn(max = 360.dp).scrollIndicators(matchesState), state = matchesState) {
                            items(matches.take(MAX_RESULTS), key = { it.emaId }) { player ->
                                val owner = owners[player.emaId]
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable(enabled = owner == null) { onSelect(player) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Name, flag and EMA number on one line. "Used by" follows them on a wide window
                                    // and goes to a second line on a phone, where it does not fit.
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                player.displayName.ifBlank { player.emaId },
                                                Modifier.weight(1f, fill = false),
                                                fontWeight = if (player.emaId == selectedEmaId) FontWeight.Bold else null,
                                                color = if (owner == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            CountryFlag(code = player.country, width = 20.dp)
                                            Text(
                                                player.emaId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                            if (owner != null && !compact) {
                                                Spacer(Modifier.weight(1f))
                                                UsedBy(owner)
                                            }
                                        }
                                        if (owner != null && compact) UsedBy(owner)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                        if (matches.size > MAX_RESULTS) {
                            Text(
                                stringResource(Res.string.showing_first_of, MAX_RESULTS, matches.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    TextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun UsedBy(owner: String) = Text(
    stringResource(Res.string.used_by, owner),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.error,
    maxLines = 1,
)
