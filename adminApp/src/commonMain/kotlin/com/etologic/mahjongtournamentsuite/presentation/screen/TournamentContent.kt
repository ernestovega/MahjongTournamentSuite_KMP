package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTooltipBox
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.SelectMenu
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberSelectMenuState
import com.etologic.mahjongtournamentsuite.presentation.components.screenLayout
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.next_round
import mahjongtournamentsuite.adminapp.generated.resources.preview_tournament
import mahjongtournamentsuite.adminapp.generated.resources.previous_round
import mahjongtournamentsuite.adminapp.generated.resources.reset_table
import mahjongtournamentsuite.adminapp.generated.resources.round_n
import mahjongtournamentsuite.adminapp.generated.resources.round_n_text
import mahjongtournamentsuite.adminapp.generated.resources.round_table_header
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.save_and_next_table
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedExtendedFloatingActionButton as ExtendedFloatingActionButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton

/** The stateless body of the tournament screen: the round and table strip, and the editor of the selected table. */
@Composable
internal fun TournamentContent(
    rounds: List<TournamentRound>,
    allTables: List<TournamentTable>,
    tableEditors: Map<Int, TableManagerEditorState>,
    selectedRoundId: Int?,
    selectedTableId: Int?,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
    roundFocusRequester: FocusRequester,
    scoreFocusRequester: FocusRequester,
    saveFocusRequester: FocusRequester,
    onSelectRound: (Int) -> Unit,
    onSelectTable: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onSaveAndNext: (Int) -> Unit,
    /** Admins can empty a table. Editors do not see the button. */
    canResetTable: Boolean = false,
    onResetTable: (Int) -> Unit = {},
) {
    val selectedEditor = selectedTableId?.let { tableEditors[it] }
    val roundTableCount = allTables.count { it.roundId == selectedRoundId }
    val compact = LocalCompactLayout.current
    Column(
        modifier = Modifier.fillMaxSize().padding(
            horizontal = screenLayout.screenGutter,
            vertical = screenLayout.sectionGap,
        ),
        verticalArrangement = Arrangement.spacedBy(screenLayout.sectionGap),
    ) {
        RoundTableStrip(
            rounds = rounds,
            allTables = allTables,
            tableEditors = tableEditors,
            selectedRoundId = selectedRoundId,
            selectedTableId = selectedTableId,
            enabled = enabled,
            roundFocusRequester = roundFocusRequester,
            onSelectRound = onSelectRound,
            onSelectTable = onSelectTable,
        )
        if (selectedEditor != null && compact) {
            // A phone has no room for the labelled button: the title goes to the start and the button keeps its icon.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.round_table_header, selectedEditor.roundId, selectedEditor.tableId),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (canResetTable) {
                    val resetLabel = stringResource(Res.string.reset_table)
                    AppTooltipBox(resetLabel) {
                        FocusedIconButton(
                            onClick = { onResetTable(selectedEditor.tableId) },
                            enabled = enabled,
                            modifier = Modifier.testTag("resetTableButton"),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = resetLabel, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else if (selectedEditor != null) {
            // The same space on both sides keeps the title centred when the admin button shows.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(Res.string.round_table_header, selectedEditor.roundId, selectedEditor.tableId),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (canResetTable) {
                        OutlinedButton(
                            onClick = { onResetTable(selectedEditor.tableId) },
                            enabled = enabled,
                            modifier = Modifier.testTag("resetTableButton"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, Modifier.size(18.dp))
                            Text(stringResource(Res.string.reset_table), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedEditor != null) {
                val tableId = selectedEditor.tableId
                val accent = MaterialTheme.colorScheme.tertiary
                val scrollState = rememberPersistentScrollState()
                val stickyHost = remember(scrollState) { StickyHeaderHost(scrollState) }
                Column(
                    modifier = Modifier
                        .widthIn(max = 1600.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onGloballyPositioned { stickyHost.viewportTop = it.positionInRoot().y }
                        .verticalScroll(scrollState)
                        .padding(bottom = 96.dp),
                ) {
                    CompositionLocalProvider(LocalStickyHeaderHost provides stickyHost) {
                        TableEditorPanel(
                            editor = selectedEditor,
                            enabled = enabled,
                            playerNamesById = playerNamesById,
                            scoreFocusRequester = scoreFocusRequester,
                            otherTablesBestHandScores = allTables
                                .filterNot { it.roundId == selectedEditor.roundId && it.tableId == tableId }
                                .flatMap { it.bestHandScores },
                        )
                    }
                }
                if (selectedEditor.hasUnsavedChanges) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { onSave(tableId) },
                            modifier = Modifier.focusRequester(saveFocusRequester),
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ) { Text(stringResource(Res.string.save)) }
                        if (roundTableCount > 1) {
                            ExtendedFloatingActionButton(
                                onClick = { onSaveAndNext(tableId) },
                                containerColor = accent,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ) { Text(stringResource(Res.string.save_and_next_table)) }
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun TournamentContentPreview() {
    MtsTheme(useDarkTheme = false) {
        val editor = remember { previewTableManagerEditorState() }
        val tables = listOf(1, 2, 3).map { id ->
            TournamentTable(
                roundId = editor.roundId,
                tableId = id,
                playerIds = listOf(101, 102, 103, 104),
                isCompleted = false,
                useTotalsOnly = false,
                hasProgress = id == editor.tableId,
            )
        }
        AppScaffold(title = stringResource(Res.string.preview_tournament)) {
            TournamentContent(
                rounds = listOf(TournamentRound(1), TournamentRound(editor.roundId)),
                allTables = tables,
                tableEditors = mapOf(editor.tableId to editor),
                selectedRoundId = editor.roundId,
                selectedTableId = editor.tableId,
                enabled = true,
                playerNamesById = previewTablePlayerNamesById(),
                roundFocusRequester = remember { FocusRequester() },
                scoreFocusRequester = remember { FocusRequester() },
                saveFocusRequester = remember { FocusRequester() },
                onSelectRound = {},
                onSelectTable = {},
                onSave = {},
                onSaveAndNext = {},
            )
        }
    }
}

/**
 * Returns the table to open after [currentId]. It prefers the next table that needs data and wraps around.
 * When every table has data, it returns the table after [currentId], or null for the last table.
 */
internal fun nextTableToOpen(tableIds: List<Int>, currentId: Int, needsData: (Int) -> Boolean): Int? {
    val index = tableIds.indexOf(currentId)
    if (index < 0) return tableIds.firstOrNull()
    val following = tableIds.drop(index + 1) + tableIds.take(index)
    return following.firstOrNull(needsData) ?: tableIds.getOrNull(index + 1)
}

/** One slim row: round picker, one chip per table, and a ranking shortcut once the round has all data. */
@Composable
private fun RoundTableStrip(
    rounds: List<TournamentRound>,
    allTables: List<TournamentTable>,
    tableEditors: Map<Int, TableManagerEditorState>,
    selectedRoundId: Int?,
    selectedTableId: Int?,
    enabled: Boolean,
    roundFocusRequester: FocusRequester,
    onSelectRound: (Int) -> Unit,
    onSelectTable: (Int) -> Unit,
) {
    val tables = allTables.filter { it.roundId == selectedRoundId }.sortedBy { it.tableId }
    val roundIndex = rounds.indexOfFirst { it.roundId == selectedRoundId }
    val chipsState = rememberLazyListState()
    val selectedChipIndex = tables.indexOfFirst { it.tableId == selectedTableId }
    // Bring the selected table into view, e.g. when entering a tournament with a table saved from before.
    LaunchedEffect(selectedRoundId, selectedChipIndex) {
        if (selectedChipIndex < 0) return@LaunchedEffect
        val layout = chipsState.layoutInfo
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == selectedChipIndex }
        val fullyVisible = visible != null &&
            visible.offset >= layout.viewportStartOffset &&
            visible.offset + visible.size <= layout.viewportEndOffset
        when {
            fullyVisible -> Unit
            // First layout (just entered the tournament): jump straight there.
            layout.visibleItemsInfo.isEmpty() -> chipsState.scrollToItem(selectedChipIndex)
            else -> chipsState.animateScrollToItem(selectedChipIndex)
        }
    }
    val roundMenu = rememberSelectMenuState()
    val roundStatus = roundCompletionStatus(tables)
    val roundPicker: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            FocusedIconButton(
                onClick = { rounds.getOrNull(roundIndex - 1)?.let { onSelectRound(it.roundId) } },
                enabled = enabled && roundIndex > 0,
            ) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(Res.string.previous_round)) }
            Box {
                FocusedOutlinedButton(
                    onClick = roundMenu::open,
                    enabled = enabled,
                    focusRequester = roundFocusRequester,
                ) {
                    Text(stringResource(Res.string.round_n_text, selectedRoundId?.toString() ?: "-"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    StatusIconWithTooltip(roundStatus)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                val roundOptions = rounds.map { round ->
                    SelectOption(
                        key = round.roundId,
                        label = stringResource(Res.string.round_n, round.roundId),
                        trailingIcon = { StatusIconWithTooltip(roundCompletionStatus(allTables.filter { it.roundId == round.roundId })) },
                    )
                }
                SelectMenu(roundMenu, roundOptions, selectedRoundId, onSelectRound)
            }
            FocusedIconButton(
                onClick = { rounds.getOrNull(roundIndex + 1)?.let { onSelectRound(it.roundId) } },
                enabled = enabled && roundIndex in 0 until rounds.lastIndex,
            ) { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(Res.string.next_round)) }
        }
    }
    val tableChips: @Composable (Modifier) -> Unit = { modifier ->
        LazyRow(
            state = chipsState,
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tables, key = { _, table -> table.tableId }) { index, table ->
                val editor = tableEditors[table.tableId]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) {
                        Box(Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    TableChip(
                        label = "${table.tableId}",
                        status = editor?.completionStatus ?: tableCompletionStatus(table),
                        hasUnsavedChanges = editor?.hasUnsavedChanges == true,
                        selected = table.tableId == selectedTableId,
                        // A table that is not loaded yet stays disabled until its data arrives.
                        enabled = enabled && editor != null,
                        onClick = { onSelectTable(table.tableId) },
                        loaded = editor != null,
                        modifier = Modifier.testTag("table-chip-${table.tableId}"),
                    )
                }
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth().appFocusGroup(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        if (LocalCompactLayout.current) {
            // A phone shows the round picker over the tables, so both have the full width.
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { roundPicker() }
                HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                tableChips(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                roundPicker()
                Box(Modifier.height(32.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                tableChips(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableChip(
    label: String,
    status: CompletionStatus,
    hasUnsavedChanges: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    loaded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(modifier = Modifier, interactionSource = interactionSource) {
        Surface(
            modifier = modifier
                .alpha(if (loaded) 1f else 0.38f)
                .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
                .activateOnEnter(enabled = enabled, onClick = onClick),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MtsTheme.spacing.sm, vertical = MtsTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                StatusIconWithTooltip(status = status)
                if (hasUnsavedChanges) {
                    // Marks a table with changes that are not saved yet.
                    Box(
                        Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape),
                    )
                }
            }
        }
    }
}
