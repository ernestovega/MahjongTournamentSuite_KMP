package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.assign_ema_player_to_player
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment_2
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment_message
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.country_n
import mahjongtournamentsuite.adminapp.generated.resources.ema_number
import mahjongtournamentsuite.adminapp.generated.resources.name
import mahjongtournamentsuite.adminapp.generated.resources.no_ema_players_match
import mahjongtournamentsuite.adminapp.generated.resources.no_matches
import mahjongtournamentsuite.adminapp.generated.resources.photo
import mahjongtournamentsuite.adminapp.generated.resources.search
import mahjongtournamentsuite.adminapp.generated.resources.this_assignment
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton

/** The dialog that assigns an EMA player to the place [slotId] of the tournament. [players] are the ones the search finds. */
@Composable
internal fun AssignEmaPlayerDialog(
    slotId: Int,
    players: List<Player>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAssign: (Player) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember(slotId) { FocusRequester() }
    val clearSearchFocusRequester = remember(slotId) { FocusRequester() }
    val cancelFocusRequester = remember(slotId) { FocusRequester() }
    val playerFocusRequesters = remember(slotId, players.map(Player::emaId)) {
        players.map { FocusRequester() }
    }
    val playerListState = rememberPersistentLazyListState("players")
    var focusedPlayerIndex by remember(slotId) { mutableStateOf(0) }
    var searchFieldFocused by remember(slotId) { mutableStateOf(false) }

    fun selectedPlayerIndex(): Int = focusedPlayerIndex
        .coerceAtMost(playerFocusRequesters.lastIndex)
        .coerceAtLeast(0)

    fun requestPlayerFocus(index: Int) {
        if (index !in playerFocusRequesters.indices) return
        focusedPlayerIndex = index
        scope.launch {
            playerListState.scrollToItem(index)
            playerFocusRequesters[index].requestFocus()
        }
    }

    LaunchedEffect(slotId) {
        searchFocusRequester.requestFocus()
    }

    TrackedDialog("AssignEmaPlayerToPlayer") {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.assign_ema_player_to_player, slotId)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text(stringResource(Res.string.search)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { searchFieldFocused = it.isFocused }
                            .focusProperties {
                                next = if (searchQuery.isNotEmpty()) {
                                    clearSearchFocusRequester
                                } else {
                                    playerFocusRequesters.firstOrNull() ?: cancelFocusRequester
                                }
                                previous = cancelFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (!searchFieldFocused) return@onPreviewKeyEvent false
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Tab -> {
                                        if (event.isShiftPressed) {
                                            cancelFocusRequester.requestFocus()
                                        } else if (searchQuery.isNotEmpty()) {
                                            clearSearchFocusRequester.requestFocus()
                                        } else if (playerFocusRequesters.isEmpty()) {
                                            cancelFocusRequester.requestFocus()
                                        } else {
                                            requestPlayerFocus(selectedPlayerIndex())
                                        }
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter, Key.DirectionDown -> {
                                        if (playerFocusRequesters.isEmpty()) {
                                            cancelFocusRequester.requestFocus()
                                        } else {
                                            requestPlayerFocus(0)
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        cancelFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (searchQuery.isEmpty()) {
                                            cancelFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        if (searchQuery.isEmpty()) {
                                            if (playerFocusRequesters.isEmpty()) {
                                                cancelFocusRequester.requestFocus()
                                            } else {
                                                requestPlayerFocus(selectedPlayerIndex())
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        onSearchQueryChange("")
                                        searchFocusRequester.requestFocus()
                                    },
                                    buttonModifier = Modifier
                                        .focusRequester(clearSearchFocusRequester)
                                        .focusProperties {
                                            previous = searchFocusRequester
                                            next = playerFocusRequesters.firstOrNull() ?: cancelFocusRequester
                                        }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) {
                                                return@onPreviewKeyEvent false
                                            }
                                            when (event.key) {
                                                Key.Tab -> {
                                                    if (event.isShiftPressed) {
                                                        searchFocusRequester.requestFocus()
                                                    } else if (playerFocusRequesters.isEmpty()) {
                                                        cancelFocusRequester.requestFocus()
                                                    } else {
                                                        requestPlayerFocus(selectedPlayerIndex())
                                                    }
                                                    true
                                                }
                                                Key.DirectionLeft -> {
                                                    searchFocusRequester.requestFocus()
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    if (playerFocusRequesters.isEmpty()) {
                                                        cancelFocusRequester.requestFocus()
                                                    } else {
                                                        requestPlayerFocus(selectedPlayerIndex())
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search))
                                }
                            }
                        } else {
                            null
                        },
                    )
                    Column(Modifier.fillMaxWidth()) {
                        DataTableHeaderRow {
                            TournamentPlayerHeader(stringResource(Res.string.photo), Modifier.width(AssignmentPhotoColumnWidth))
                            TournamentPlayerHeader(stringResource(Res.string.country), Modifier.width(AssignmentCountryColumnWidth))
                            TournamentPlayerHeader(stringResource(Res.string.ema_number), Modifier.width(AssignmentEmaColumnWidth))
                            TournamentPlayerHeader(stringResource(Res.string.name), Modifier.weight(1f))
                        }
                        DataTableDivider()
                        if (players.isEmpty()) {
                            AppEmptyState(Icons.Default.Search, stringResource(Res.string.no_matches), stringResource(Res.string.no_ema_players_match), compact = true)
                        } else {
                            LazyColumnWithScrollbar(
                                state = playerListState,
                                modifier = Modifier.heightIn(max = 410.dp),
                                inDialog = true,
                            ) {
                                items(players.size, key = { players[it].emaId }) { index ->
                                    val player = players[index]
                                    DataTableRow(
                                        modifier = Modifier
                                            .focusRequester(playerFocusRequesters[index])
                                            .onFocusChanged {
                                                if (it.isFocused) focusedPlayerIndex = index
                                            }
                                            .focusProperties {
                                                previous = searchFocusRequester
                                                next = cancelFocusRequester
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown) {
                                                    return@onPreviewKeyEvent false
                                                }
                                                when (event.key) {
                                                    Key.Tab -> {
                                                        if (event.isShiftPressed) {
                                                            if (searchQuery.isNotEmpty()) {
                                                                clearSearchFocusRequester.requestFocus()
                                                            } else {
                                                                searchFocusRequester.requestFocus()
                                                            }
                                                        } else {
                                                            cancelFocusRequester.requestFocus()
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionUp -> {
                                                        if (index == 0) {
                                                            searchFocusRequester.requestFocus()
                                                        } else {
                                                            requestPlayerFocus(index - 1)
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionDown -> {
                                                        if (index == players.lastIndex) {
                                                            cancelFocusRequester.requestFocus()
                                                        } else {
                                                            requestPlayerFocus(index + 1)
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionLeft -> {
                                                        searchFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    Key.DirectionRight -> {
                                                        cancelFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            },
                                        onClick = { onAssign(player) },
                                    ) {
                                        TournamentPlayerPhoto(
                                            photoUrl = player.photoUrl,
                                            playerName = player.displayName,
                                            columnWidth = AssignmentPhotoColumnWidth,
                                        )
                                        Box(
                                            modifier = Modifier.width(AssignmentCountryColumnWidth),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CountryFlag(
                                                code = player.country,
                                                contentDescription = stringResource(Res.string.country_n, player.country),
                                            )
                                        }
                                        Text(
                                            player.emaId,
                                            modifier = Modifier.width(AssignmentEmaColumnWidth),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(player.displayName, modifier = Modifier.weight(1f))
                                    }
                                    DataTableDivider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { onDismiss() },
                        interactionSource = cancelInteractionSource,
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .focusProperties {
                                previous = playerFocusRequesters.getOrNull(
                                    selectedPlayerIndex(),
                                ) ?: searchFocusRequester
                                next = searchFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Tab -> {
                                        if (event.isShiftPressed && playerFocusRequesters.isNotEmpty()) {
                                            requestPlayerFocus(selectedPlayerIndex())
                                        } else {
                                            searchFocusRequester.requestFocus()
                                        }
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (playerFocusRequesters.isEmpty()) {
                                            searchFocusRequester.requestFocus()
                                        } else {
                                            requestPlayerFocus(selectedPlayerIndex())
                                        }
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        searchFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (playerFocusRequesters.isEmpty()) {
                                            searchFocusRequester.requestFocus()
                                        } else {
                                            requestPlayerFocus(selectedPlayerIndex())
                                        }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        searchFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    ) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** Asks before the place [slotId] loses its EMA player, named [assignedName]. */
@Composable
internal fun ClearAssignmentDialog(
    slotId: Int,
    assignedName: String?,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clearConfirmFocusRequester = remember(slotId) { FocusRequester() }
    val clearCancelFocusRequester = remember(slotId) { FocusRequester() }
    val clearConfirmInteractionSource = remember(slotId) { MutableInteractionSource() }
    val clearCancelInteractionSource = remember(slotId) { MutableInteractionSource() }

    LaunchedEffect(slotId) {
        clearConfirmFocusRequester.requestFocus()
    }

    TrackedDialog("ClearAssignment") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { onDismiss() },
            title = { MtsDialogTitle(stringResource(Res.string.clear_assignment)) },
            text = {
                MtsDialogText(
                    stringResource(
                        Res.string.clear_assignment_message,
                        assignedName ?: stringResource(Res.string.this_assignment),
                        slotId,
                    ),
                )
            },
            confirmButton = {
                MtsDialogButtons {
                    Box {
                        Button(
                            enabled = !saving,
                            onClick = onConfirm,
                            interactionSource = clearConfirmInteractionSource,
                            modifier = Modifier
                                .focusRequester(clearConfirmFocusRequester)
                                .focusProperties {
                                    previous = clearCancelFocusRequester
                                    next = clearCancelFocusRequester
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }
                                    when (event.key) {
                                        Key.Tab,
                                        Key.DirectionLeft,
                                        Key.DirectionRight,
                                        Key.DirectionUp,
                                        Key.DirectionDown,
                                        -> {
                                            clearCancelFocusRequester.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                },
                        ) { ButtonText(stringResource(Res.string.clear_assignment_2)) }
                    }

                    Box {
                        TextButton(
                            onClick = { onDismiss() },
                            interactionSource = clearCancelInteractionSource,
                            modifier = Modifier
                                .focusRequester(clearCancelFocusRequester)
                                .focusProperties {
                                    previous = clearConfirmFocusRequester
                                    next = clearConfirmFocusRequester
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }
                                    when (event.key) {
                                        Key.Tab,
                                        Key.DirectionLeft,
                                        Key.DirectionRight,
                                        Key.DirectionUp,
                                        Key.DirectionDown,
                                        -> {
                                            clearConfirmFocusRequester.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                },
                        ) { ButtonText(stringResource(Res.string.cancel)) }
                    }
                }
            },
        )
    }
}
