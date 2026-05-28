package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TableManagerScreen(
    navController: NavHostController,
    tournamentId: String,
    roundId: Int,
    tableId: Int,
) {
    val presenter = koinInject<TableManagerPresenter>()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tableState by remember { mutableStateOf<TableState?>(null) }
    var hands by remember { mutableStateOf<List<TableHand>>(emptyList()) }
    var pendingUnsavedAction by remember { mutableStateOf<TableManagerPendingUnsavedAction?>(null) }
    fun refresh() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            when (val result = presenter.loadTableWithHands(tournamentId, roundId, tableId)) {
                is AppResult.Success -> {
                    tableState = result.value.first
                    hands = result.value.second
                }

                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
            isLoading = false
        }
    }

    LaunchedEffect(tournamentId, roundId, tableId) {
        refresh()
    }

    val table = tableState
    val editorState = remember(table, hands) { table?.let { TableManagerEditorState.from(it, hands) } }
    val hasUnsavedChanges = editorState?.hasUnsavedChanges == true

    suspend fun saveChanges(): Boolean {
        val editor = editorState ?: return true
        val tablePatch = editor.buildTablePatch()
        val handPatches = editor.buildHandPatches()
        if (tablePatch.isEmpty() && handPatches.isEmpty()) return true

        isLoading = true
        errorMessage = null
        try {
            if (tablePatch.isNotEmpty()) {
                when (val result = presenter.patchTable(tournamentId, roundId, tableId, tablePatch)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> {
                        errorMessage = result.error.toUiMessage()
                        return false
                    }
                }
            }

            for ((handId, patch) in handPatches) {
                when (val result = presenter.patchHand(tournamentId, roundId, tableId, handId, patch)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> {
                        errorMessage = result.error.toUiMessage()
                        return false
                    }
                }
            }

            refresh()
            return true
        } finally {
            isLoading = false
        }
    }

    fun performUnsavedAction(action: TableManagerPendingUnsavedAction) {
        when (action) {
            TableManagerPendingUnsavedAction.Back -> navController.popBackStack()
            TableManagerPendingUnsavedAction.Refresh -> refresh()
        }
    }

    fun requestUnsavedAction(action: TableManagerPendingUnsavedAction) {
        if (isLoading) return
        if (hasUnsavedChanges) {
            pendingUnsavedAction = action
            return
        }
        performUnsavedAction(action)
    }

    pendingUnsavedAction?.let { action ->
        UnsavedChangesDialog(
            isSaving = isLoading,
            onSave = {
                coroutineScope.launch {
                    if (saveChanges()) {
                        pendingUnsavedAction = null
                        performUnsavedAction(action)
                    }
                }
            },
            onDiscard = {
                pendingUnsavedAction = null
                performUnsavedAction(action)
            },
            onCancel = { pendingUnsavedAction = null },
        )
    }

    AppScaffold(
        title = "Round $roundId • Table $tableId",
        subtitle = tournamentId,
        isLoading = isLoading,
        onBack = { requestUnsavedAction(TableManagerPendingUnsavedAction.Back) },
        actions = {
            AppTopBarActions(
                onRefresh = { requestUnsavedAction(TableManagerPendingUnsavedAction.Refresh) },
            )
        },
        floatingActionButton = {
            if (editorState != null && editorState.hasUnsavedChanges) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { editorState.discard() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text("Discard")
                    }
                    ExtendedFloatingActionButton(
                        onClick = { coroutineScope.launch { saveChanges() } },
                    ) {
                        Text("Save")
                    }
                }
            }
        },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            scrollable = true,
        ) {
            errorMessage?.let { AppErrorMessage(message = it) }

            if (isLoading && table == null) {
                Text("Loading…")
                return@ScreenColumn
            }
            val editor = editorState ?: return@ScreenColumn

            TableManagerContent(
                editor = editor,
                enabled = !isLoading,
                onManualTotalsChange = { checked ->
                    if (checked) {
                        editor.enableManualTotals()
                    } else {
                        editor.disableManualTotals()
                    }
                },
            )
        }
    }
}

@Composable
internal fun TableManagerContent(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String> = emptyMap(),
    onManualTotalsChange: (Boolean) -> Unit = { editor.useTotalsOnly = it },
    onManualPointsChange: (Boolean) -> Unit = { editor.usePointsCalculation = !it },
) {
    SeatPositionsSection(
        editor = editor,
        enabled = enabled,
        playerNamesById = playerNamesById,
    )

    Spacer(modifier = Modifier.height(16.dp))

    TotalScoreSection(
        editor = editor,
        enabled = enabled,
        onManualTotalsChange = onManualTotalsChange,
    )

    Spacer(modifier = Modifier.height(16.dp))

    TablePointsSection(
        editor = editor,
        enabled = enabled,
        onManualPointsChange = onManualPointsChange,
    )

    Spacer(modifier = Modifier.height(16.dp))

    HandsSection(
        editor = editor,
        enabled = enabled,
        playerNamesById = playerNamesById,
    )
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun TableManagerScreenPreview() {
    MtsTheme(useDarkTheme = false) {
        AppScaffold(
            title = "Round 2 • Table 1",
            subtitle = "preview-tournament",
        ) {
            ScreenColumn(
                maxWidth = 1200.dp,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                scrollable = true,
            ) {
                TableManagerContent(
                    editor = remember { previewTableManagerEditorState() },
                    enabled = true,
                    playerNamesById = previewTablePlayerNamesById(),
                )
            }
        }
    }
}

private fun previewTableManagerEditorState(): TableManagerEditorState {
    return TableManagerEditorState.from(
        table = TableState(
            roundId = 2,
            tableId = 1,
            playerIds = listOf(101, 102, 103, 104),
            playerEastId = "101",
            playerSouthId = "102",
            playerWestId = "103",
            playerNorthId = "104",
            playerEastScore = "48",
            playerSouthScore = "32",
            playerWestScore = "-16",
            playerNorthScore = "-64",
            playerEastPoints = "4",
            playerSouthPoints = "2",
            playerWestPoints = "1",
            playerNorthPoints = "0",
            isCompleted = false,
            useTotalsOnly = false,
            usePointsCalculation = true,
        ),
        hands = listOf(
            TableHand(
                handId = 1,
                playerWinnerId = "101",
                playerLooserId = "",
                handScore = "16",
                isChickenHand = false,
                isDone = false,
                playerEastPenalty = "",
                playerSouthPenalty = "",
                playerWestPenalty = "",
                playerNorthPenalty = "",
            ),
            TableHand(
                handId = 2,
                playerWinnerId = "102",
                playerLooserId = "104",
                handScore = "24",
                isChickenHand = true,
                isDone = true,
                playerEastPenalty = "",
                playerSouthPenalty = "",
                playerWestPenalty = "-8",
                playerNorthPenalty = "",
            ),
            TableHand(
                handId = 3,
                playerWinnerId = "103",
                playerLooserId = "",
                handScore = "12",
                isChickenHand = false,
                isDone = false,
                playerEastPenalty = "",
                playerSouthPenalty = "-4",
                playerWestPenalty = "",
                playerNorthPenalty = "",
            ),
        ),
    )
}

private fun previewTablePlayerNamesById(): Map<Int, String> = mapOf(
    101 to "Aiko Tan",
    102 to "Bruno Lee",
    103 to "Carla Ruiz",
    104 to "Diego Mora",
)

@Composable
private fun SeatPositionsSection(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
) {
    SectionCard(
        title = "Seat positions",
        content = {
            SeatPositionsRow(
                playerIds = editor.playerIds,
                east = editor.playerEastId,
                south = editor.playerSouthId,
                west = editor.playerWestId,
                north = editor.playerNorthId,
                enabled = enabled,
                eastChanged = editor.hasEastSeatChanged,
                southChanged = editor.hasSouthSeatChanged,
                westChanged = editor.hasWestSeatChanged,
                northChanged = editor.hasNorthSeatChanged,
                onEastChange = { editor.setSeatAssignment(0, it) },
                onSouthChange = { editor.setSeatAssignment(1, it) },
                onWestChange = { editor.setSeatAssignment(2, it) },
                onNorthChange = { editor.setSeatAssignment(3, it) },
                playerNamesById = playerNamesById,
            )
        },
    )
}

@Composable
private fun TotalScoreSection(
    editor: TableManagerEditorState,
    enabled: Boolean,
    onManualTotalsChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = "Total Score",
        actions = {
            LabeledSwitch(
                label = "Manual Scores",
                checked = editor.useTotalsOnly,
                enabled = enabled,
                onCheckedChange = { onManualTotalsChange(it) },
            )
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SeatFieldRow(
                    enabled = enabled && editor.useTotalsOnly,
                    eastValue = editor.displayEastScore,
                    southValue = editor.displaySouthScore,
                    westValue = editor.displayWestScore,
                    northValue = editor.displayNorthScore,
                    eastChanged = editor.hasEastScoreChanged,
                    southChanged = editor.hasSouthScoreChanged,
                    westChanged = editor.hasWestScoreChanged,
                    northChanged = editor.hasNorthScoreChanged,
                    onEastChange = { editor.playerEastScore = it },
                    onSouthChange = { editor.playerSouthScore = it },
                    onWestChange = { editor.playerWestScore = it },
                    onNorthChange = { editor.playerNorthScore = it },
                )
            }
        },
    )
}

@Composable
private fun TablePointsSection(
    editor: TableManagerEditorState,
    enabled: Boolean,
    onManualPointsChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = "Table Points",
        actions = {
            LabeledSwitch(
                label = "Manual Points",
                checked = !editor.usePointsCalculation,
                enabled = enabled,
                onCheckedChange = { onManualPointsChange(it) },
            )
        },
        content = {
            SeatFieldRow(
                enabled = enabled && !editor.usePointsCalculation,
                eastValue = editor.displayEastPoints,
                southValue = editor.displaySouthPoints,
                westValue = editor.displayWestPoints,
                northValue = editor.displayNorthPoints,
                eastChanged = editor.hasEastPointsChanged,
                southChanged = editor.hasSouthPointsChanged,
                westChanged = editor.hasWestPointsChanged,
                northChanged = editor.hasNorthPointsChanged,
                onEastChange = { editor.playerEastPoints = it },
                onSouthChange = { editor.playerSouthPoints = it },
                onWestChange = { editor.playerWestPoints = it },
                onNorthChange = { editor.playerNorthPoints = it },
            )
        },
    )
}

@Composable
private fun SeatFieldRow(
    enabled: Boolean,
    eastValue: String,
    southValue: String,
    westValue: String,
    northValue: String,
    eastChanged: Boolean = false,
    southChanged: Boolean = false,
    westChanged: Boolean = false,
    northChanged: Boolean = false,
    onEastChange: (String) -> Unit,
    onSouthChange: (String) -> Unit,
    onWestChange: (String) -> Unit,
    onNorthChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactOutlinedTextField(
                value = eastValue,
                onValueChange = onEastChange,
                enabled = enabled,
                isChanged = eastChanged,
                label = { Text("East") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            CompactOutlinedTextField(
                value = southValue,
                onValueChange = onSouthChange,
                enabled = enabled,
                isChanged = southChanged,
                label = { Text("South") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            CompactOutlinedTextField(
                value = westValue,
                onValueChange = onWestChange,
                enabled = enabled,
                isChanged = westChanged,
                label = { Text("West") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            CompactOutlinedTextField(
                value = northValue,
                onValueChange = onNorthChange,
                enabled = enabled,
                isChanged = northChanged,
                label = { Text("North") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun HandsSection(
    editor: TableManagerEditorState,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
) {
    val handSubtotals = editor.cumulativeHandScoreSubtotals
    val rowNavigators = remember(editor.hands.map { it.handId }) {
        editor.hands.map { HandRowKeyboardNavigator() }
    }
    val firstEditableHandIndex = editor.hands.indexOfFirst { !editor.isCompleted && !it.isDone }

    LaunchedEffect(enabled, editor.isCompleted, firstEditableHandIndex) {
        if (!enabled) return@LaunchedEffect
        if (firstEditableHandIndex < 0) return@LaunchedEffect
        rowNavigators.getOrNull(firstEditableHandIndex)?.winner?.requestFocus()
    }

    SectionCard(
        title = "Hands",
        subtitle = "${editor.hands.size} hands",
        actions = {
            LabeledSwitch(
                label = "Completed",
                checked = editor.isCompleted,
                enabled = enabled,
                onCheckedChange = { editor.updateCompletedState(it) },
            )
        },
        content = {
            if (editor.hands.isEmpty()) {
                Text(
                    text = "No hands found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@SectionCard
            }

            if (editor.hasInvalidHands) {
                Text(
                    text = "This hand is invalid and cannot be marked as done or completed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            editor.hands.forEachIndexed { index, hand ->
                if (index > 0) {
                    HorizontalDivider()
                }

                HandRow(
                    index = index,
                    hand = hand,
                    playerIds = editor.playerIds,
                    playerNamesById = playerNamesById,
                    enabled = enabled,
                    forceDoneChecked = editor.isCompleted,
                    subtotal = handSubtotals.getOrNull(index).orZero(),
                    navigation = rowNavigators.getOrNull(index) ?: HandRowKeyboardNavigator(),
                    previousNavigation = rowNavigators.getOrNull(index - 1),
                    nextNavigation = rowNavigators.getOrNull(index + 1),
                )
            }
        },
    )
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun HandRow(
    index: Int,
    hand: HandDraftState,
    playerIds: List<Int>,
    playerNamesById: Map<Int, String>,
    enabled: Boolean,
    forceDoneChecked: Boolean,
    subtotal: SeatTextValues,
    navigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator? = null,
    nextNavigation: HandRowKeyboardNavigator? = null,
) {
    val rowLocked = forceDoneChecked || hand.isDone
    val rowEnabled = enabled && !rowLocked

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HandRowHorizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(HandRowFieldSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (rowEnabled) 1f else 0.38f),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(HandRowIndexWidth),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HandRowBlockSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(HandRowFieldSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerDropdown(
                            label = "Winner",
                            playerIds = playerIds,
                            value = hand.playerWinnerId,
                            enabled = rowEnabled,
                            isChanged = hand.hasWinnerChanged,
                            onChange = { hand.setWinnerPlayerId(it) },
                            onDismiss = { hand.markResultFieldsTouched() },
                            onFocusLost = { hand.markResultFieldsTouched() },
                            playerNamesById = playerNamesById,
                            excludedPlayerIds = setOfNotNull(hand.selectedLoserPlayerId),
                            emptyOptionLabel = "-",
                            onNavigateLeft = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Winner,
                                    direction = HandArrowDirection.Left,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateRight = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Winner,
                                    direction = HandArrowDirection.Right,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateUp = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Winner,
                                    direction = HandArrowDirection.Up,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateDown = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Winner,
                                    direction = HandArrowDirection.Down,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            modifier = Modifier.weight(0.93f),
                            focusRequester = navigation.winner,
                        )
                        PlayerDropdown(
                            label = "Loser",
                            playerIds = playerIds,
                            value = hand.playerLooserId,
                            enabled = rowEnabled,
                            isChanged = hand.hasLoserChanged,
                            onChange = { hand.setLoserPlayerId(it) },
                            onDismiss = { hand.markResultFieldsTouched() },
                            onFocusLost = { hand.markResultFieldsTouched() },
                            playerNamesById = playerNamesById,
                            excludedPlayerIds = setOfNotNull(hand.selectedWinnerPlayerId),
                            emptyOptionLabel = "-",
                            onNavigateLeft = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Loser,
                                    direction = HandArrowDirection.Left,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateRight = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Loser,
                                    direction = HandArrowDirection.Right,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateUp = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Loser,
                                    direction = HandArrowDirection.Up,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateDown = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Loser,
                                    direction = HandArrowDirection.Down,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            modifier = Modifier.weight(0.93f),
                            focusRequester = navigation.loser,
                        )
                        KeyboardAwareOutlinedTextField(
                            value = hand.handScore,
                            onValueChange = { hand.updateHandScore(it) },
                            enabled = rowEnabled,
                            isChanged = hand.hasScoreChanged,
                            label = { Text("Score") },
                            isError = hand.showValidationError,
                            currentSlot = HandFieldSlot.Score,
                            rowEnabled = rowEnabled,
                            rowNavigation = navigation,
                            previousNavigation = previousNavigation,
                            nextNavigation = nextNavigation,
                            focusRequester = navigation.score,
                            modifier = Modifier
                                .width(92.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        hand.markResultFieldsTouched()
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                        )
                        HandToggleControl(
                            width = HandChickenControlWidth,
                            label = "Chicken\nHand",
                            checked = hand.isChickenHand,
                            enabled = rowEnabled,
                            isChanged = hand.hasChickenHandChanged,
                            onCheckedChange = { hand.updateChickenHand(it) },
                            focusRequester = navigation.chicken,
                            onNavigateLeft = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Chicken,
                                    direction = HandArrowDirection.Left,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateRight = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Chicken,
                                    direction = HandArrowDirection.Right,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateUp = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Chicken,
                                    direction = HandArrowDirection.Up,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                            onNavigateDown = {
                                moveHandFieldFocus(
                                    current = HandFieldSlot.Chicken,
                                    direction = HandArrowDirection.Down,
                                    rowEnabled = rowEnabled,
                                    rowNavigation = navigation,
                                    previousNavigation = previousNavigation,
                                    nextNavigation = nextNavigation,
                                )
                            },
                        )
                    }

                    Column(
                        modifier = Modifier.width(HandRightColumnWidth),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HandSeatColumnHeader(enabled = rowEnabled)

                        HandFourFieldRow(title = "Subtotals", enabled = rowEnabled) {
                            ReadOnlySubtotalField(
                                value = subtotal.east,
                                modifier = Modifier.weight(1f),
                            )
                            ReadOnlySubtotalField(
                                value = subtotal.south,
                                modifier = Modifier.weight(1f),
                            )
                            ReadOnlySubtotalField(
                                value = subtotal.west,
                                modifier = Modifier.weight(1f),
                            )
                            ReadOnlySubtotalField(
                                value = subtotal.north,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        HandFourFieldRow(title = "Penalties", enabled = rowEnabled) {
                            PenaltyField(
                                value = hand.playerEastPenalty,
                                enabled = rowEnabled,
                                isChanged = hand.hasEastPenaltyChanged,
                                onValueChange = { hand.playerEastPenalty = it },
                                modifier = Modifier.weight(1f),
                                focusRequester = navigation.penaltyEast,
                                onNavigateLeft = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyEast,
                                        direction = HandArrowDirection.Left,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateRight = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyEast,
                                        direction = HandArrowDirection.Right,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateUp = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyEast,
                                        direction = HandArrowDirection.Up,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateDown = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyEast,
                                        direction = HandArrowDirection.Down,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                            )
                            PenaltyField(
                                value = hand.playerSouthPenalty,
                                enabled = rowEnabled,
                                isChanged = hand.hasSouthPenaltyChanged,
                                onValueChange = { hand.playerSouthPenalty = it },
                                modifier = Modifier.weight(1f),
                                focusRequester = navigation.penaltySouth,
                                onNavigateLeft = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltySouth,
                                        direction = HandArrowDirection.Left,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateRight = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltySouth,
                                        direction = HandArrowDirection.Right,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateUp = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltySouth,
                                        direction = HandArrowDirection.Up,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateDown = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltySouth,
                                        direction = HandArrowDirection.Down,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                            )
                            PenaltyField(
                                value = hand.playerWestPenalty,
                                enabled = rowEnabled,
                                isChanged = hand.hasWestPenaltyChanged,
                                onValueChange = { hand.playerWestPenalty = it },
                                modifier = Modifier.weight(1f),
                                focusRequester = navigation.penaltyWest,
                                onNavigateLeft = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyWest,
                                        direction = HandArrowDirection.Left,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateRight = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyWest,
                                        direction = HandArrowDirection.Right,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateUp = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyWest,
                                        direction = HandArrowDirection.Up,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateDown = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyWest,
                                        direction = HandArrowDirection.Down,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                            )
                            PenaltyField(
                                value = hand.playerNorthPenalty,
                                enabled = rowEnabled,
                                isChanged = hand.hasNorthPenaltyChanged,
                                onValueChange = { hand.playerNorthPenalty = it },
                                modifier = Modifier.weight(1f),
                                focusRequester = navigation.penaltyNorth,
                                onNavigateLeft = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyNorth,
                                        direction = HandArrowDirection.Left,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateRight = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyNorth,
                                        direction = HandArrowDirection.Right,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateUp = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyNorth,
                                        direction = HandArrowDirection.Up,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                                onNavigateDown = {
                                    moveHandFieldFocus(
                                        current = HandFieldSlot.PenaltyNorth,
                                        direction = HandArrowDirection.Down,
                                        rowEnabled = rowEnabled,
                                        rowNavigation = navigation,
                                        previousNavigation = previousNavigation,
                                        nextNavigation = nextNavigation,
                                    )
                                },
                            )
                        }
                    }
                }

                if (hand.showValidationError) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = hand.validationErrorMessage ?: "This hand is invalid and cannot be marked as done or completed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        hand.validationDetailMessages.forEach { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(HandDoneBlockSpacing))

            Column(
                modifier = Modifier.width(HandDoneControlWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                HandToggleControl(
                    width = HandDoneControlWidth,
                    label = "Done",
                    checked = hand.isDone,
                    enabled = !forceDoneChecked,
                    isChanged = hand.hasDoneChanged,
                    onCheckedChange = { hand.updateDoneState(it) },
                    focusRequester = navigation.done,
                    onNavigateLeft = {
                        moveHandFieldFocus(
                            current = HandFieldSlot.Done,
                            direction = HandArrowDirection.Left,
                            rowEnabled = rowEnabled,
                            rowNavigation = navigation,
                            previousNavigation = previousNavigation,
                            nextNavigation = nextNavigation,
                        )
                    },
                    onNavigateRight = {
                        moveHandFieldFocus(
                            current = HandFieldSlot.Done,
                            direction = HandArrowDirection.Right,
                            rowEnabled = rowEnabled,
                            rowNavigation = navigation,
                            previousNavigation = previousNavigation,
                            nextNavigation = nextNavigation,
                        )
                    },
                    onNavigateUp = {
                        moveHandFieldFocus(
                            current = HandFieldSlot.Done,
                            direction = HandArrowDirection.Up,
                            rowEnabled = rowEnabled,
                            rowNavigation = navigation,
                            previousNavigation = previousNavigation,
                            nextNavigation = nextNavigation,
                        )
                    },
                    onNavigateDown = {
                        moveHandFieldFocus(
                            current = HandFieldSlot.Done,
                            direction = HandArrowDirection.Down,
                            rowEnabled = rowEnabled,
                            rowNavigation = navigation,
                            previousNavigation = previousNavigation,
                            nextNavigation = nextNavigation,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HandSeatColumnHeader(enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HandSubtotalFieldSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(HandRightRowTitleWidth))
        Text(
            text = "East",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "South",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "West",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "North",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HandToggleControl(
    width: Dp,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    isChanged: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)?,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    val shape = MaterialTheme.shapes.extraSmall
    var focused by remember { mutableStateOf(false) }
    val borderModifier = if (focused && enabled) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }
    val checkboxColors = when {
        !enabled -> CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            checkmarkColor = MaterialTheme.colorScheme.surface,
            disabledCheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            disabledIndeterminateColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
        isChanged -> CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.tertiary,
            uncheckedColor = MaterialTheme.colorScheme.tertiary,
            checkmarkColor = MaterialTheme.colorScheme.onTertiary,
        )
        else -> CheckboxDefaults.colors()
    }

    Column(
        modifier = Modifier
            .width(width)
            .then(borderModifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                isChanged -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = checkboxColors,
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> onNavigateLeft?.invoke() ?: false
                        Key.DirectionRight -> onNavigateRight?.invoke() ?: false
                        Key.DirectionUp -> onNavigateUp?.invoke() ?: false
                        Key.DirectionDown -> onNavigateDown?.invoke() ?: false
                        else -> false
                    }
                },
        )
    }
}

@Composable
private fun HandFourFieldRow(
    title: String,
    enabled: Boolean,
    fields: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HandSubtotalFieldSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.width(HandRightRowTitleWidth),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(HandSubtotalFieldSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) { fields() }
    }
}

@Composable
private fun PenaltyField(
    modifier: Modifier = Modifier,
    value: String,
    enabled: Boolean,
    isChanged: Boolean = false,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    SimpleHandCellField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        isChanged = isChanged,
        placeholder = "-",
        focusRequester = focusRequester,
        onNavigateLeft = onNavigateLeft,
        onNavigateRight = onNavigateRight,
        onNavigateUp = onNavigateUp,
        onNavigateDown = onNavigateDown,
        modifier = modifier
            .widthIn(min = 72.dp)
            .alpha(if (value.isBlank()) 0.58f else 1f),
    )
}

@Composable
private fun ReadOnlySubtotalField(
    modifier: Modifier = Modifier,
    value: String,
) {
    SimpleHandCellDisplay(
        value = value,
        modifier = modifier.alpha(0.72f),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        ),
    )
}

private fun SeatTextValues?.orZero(): SeatTextValues = this ?: SeatTextValues.ZERO

@Composable
private fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    isChanged: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    compactHeight: Dp? = null,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
) {
    val resolvedColors = if (enabled && isChanged && !isError) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        colors
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (compactHeight != null) modifier.height(compactHeight) else modifier,
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        colors = resolvedColors,
    )
}

@Composable
private fun KeyboardAwareOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    currentSlot: HandFieldSlot,
    rowEnabled: Boolean,
    rowNavigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator? = null,
    nextNavigation: HandRowKeyboardNavigator? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    isChanged: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    compactHeight: Dp? = null,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
) {
    val resolvedColors = if (enabled && isChanged && !isError) {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        colors
    }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            onValueChange(newValue.text)
        },
        modifier = (if (compactHeight != null) modifier.height(compactHeight) else modifier)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val selection = fieldValue.selection
                val textLength = fieldValue.text.length
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (selection.collapsed && selection.start == 0) {
                            moveHandFieldFocus(
                                current = currentSlot,
                                direction = HandArrowDirection.Left,
                                rowEnabled = rowEnabled,
                                rowNavigation = rowNavigation,
                                previousNavigation = previousNavigation,
                                nextNavigation = nextNavigation,
                            )
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (selection.collapsed && selection.end == textLength) {
                            moveHandFieldFocus(
                                current = currentSlot,
                                direction = HandArrowDirection.Right,
                                rowEnabled = rowEnabled,
                                rowNavigation = rowNavigation,
                                previousNavigation = previousNavigation,
                                nextNavigation = nextNavigation,
                            )
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp -> moveHandFieldFocus(
                        current = currentSlot,
                        direction = HandArrowDirection.Up,
                        rowEnabled = rowEnabled,
                        rowNavigation = rowNavigation,
                        previousNavigation = previousNavigation,
                        nextNavigation = nextNavigation,
                    )

                    Key.DirectionDown -> moveHandFieldFocus(
                        current = currentSlot,
                        direction = HandArrowDirection.Down,
                        rowEnabled = rowEnabled,
                        rowNavigation = rowNavigation,
                        previousNavigation = previousNavigation,
                        nextNavigation = nextNavigation,
                    )

                    else -> false
                }
            },
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        colors = resolvedColors,
    )
}

@Composable
private fun SimpleHandCellField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isChanged: Boolean = false,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> MaterialTheme.colorScheme.primary
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isChanged -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val shape = MaterialTheme.shapes.extraSmall
    val borderWidth = if (focused) 2.dp else 1.dp
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    Box(
        modifier = modifier
            .height(HandMiniFieldHeight)
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue.text)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val selection = textFieldValue.selection
                    val textLength = textFieldValue.text.length
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (selection.collapsed && selection.start == 0) {
                                onNavigateLeft?.invoke() ?: false
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (selection.collapsed && selection.end == textLength) {
                                onNavigateRight?.invoke() ?: false
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp -> onNavigateUp?.invoke() ?: false
                        Key.DirectionDown -> onNavigateDown?.invoke() ?: false
                        else -> false
                    }
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isBlank() && !focused) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SimpleHandCellDisplay(
    value: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
) {
    val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val shape = MaterialTheme.shapes.extraSmall

    Box(
        modifier = modifier
            .height(HandMiniFieldHeight)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class TableManagerPendingUnsavedAction {
    Back,
    Refresh,
}

private val HandRowHorizontalPadding = 10.dp
private val HandRowIndexWidth = 40.dp
private val HandRowFieldSpacing = 8.dp
private val HandRowBlockSpacing = 40.dp
private val HandRightColumnWidth = 364.dp
private val HandRightRowTitleWidth = 84.dp
private val HandMiniFieldHeight = 38.dp
private val HandSubtotalFieldSpacing = 6.dp
private val HandChickenControlWidth = 70.dp
private val HandDoneControlWidth = HandChickenControlWidth
private val HandDoneBlockSpacing = 6.dp
private const val MIN_HAND_SCORE = 8
private const val MAX_CHICKEN_HAND_SCORE = 12

private enum class HandArrowDirection {
    Left,
    Right,
    Up,
    Down,
}

private enum class HandFieldSlot {
    Winner,
    Loser,
    Score,
    Chicken,
    SubtotalEast,
    SubtotalSouth,
    SubtotalWest,
    SubtotalNorth,
    PenaltyEast,
    PenaltySouth,
    PenaltyWest,
    PenaltyNorth,
    Done,
}

private class HandRowKeyboardNavigator {
    val winner = FocusRequester()
    val loser = FocusRequester()
    val score = FocusRequester()
    val chicken = FocusRequester()
    val subtotalEast = FocusRequester()
    val subtotalSouth = FocusRequester()
    val subtotalWest = FocusRequester()
    val subtotalNorth = FocusRequester()
    val penaltyEast = FocusRequester()
    val penaltySouth = FocusRequester()
    val penaltyWest = FocusRequester()
    val penaltyNorth = FocusRequester()
    val done = FocusRequester()
}

private fun handFieldSlotAtOffset(slot: HandFieldSlot, direction: HandArrowDirection): HandFieldSlot? {
    val order = listOf(
        HandFieldSlot.Winner,
        HandFieldSlot.Loser,
        HandFieldSlot.Score,
        HandFieldSlot.Chicken,
        HandFieldSlot.PenaltyEast,
        HandFieldSlot.PenaltySouth,
        HandFieldSlot.PenaltyWest,
        HandFieldSlot.PenaltyNorth,
        HandFieldSlot.Done,
    )
    val index = order.indexOf(slot)
    return when (direction) {
        HandArrowDirection.Left -> order.getOrNull(index - 1)
        HandArrowDirection.Right -> order.getOrNull(index + 1)
        else -> slot
    }
}

private fun HandRowKeyboardNavigator.focusRequesterFor(slot: HandFieldSlot): FocusRequester = when (slot) {
    HandFieldSlot.Winner -> winner
    HandFieldSlot.Loser -> loser
    HandFieldSlot.Score -> score
    HandFieldSlot.Chicken -> chicken
    HandFieldSlot.SubtotalEast -> subtotalEast
    HandFieldSlot.SubtotalSouth -> subtotalSouth
    HandFieldSlot.SubtotalWest -> subtotalWest
    HandFieldSlot.SubtotalNorth -> subtotalNorth
    HandFieldSlot.PenaltyEast -> penaltyEast
    HandFieldSlot.PenaltySouth -> penaltySouth
    HandFieldSlot.PenaltyWest -> penaltyWest
    HandFieldSlot.PenaltyNorth -> penaltyNorth
    HandFieldSlot.Done -> done
}

private fun moveHandFieldFocus(
    current: HandFieldSlot,
    direction: HandArrowDirection,
    rowEnabled: Boolean,
    rowNavigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator?,
    nextNavigation: HandRowKeyboardNavigator?,
): Boolean {
    if (!rowEnabled) return false
    val target = when (direction) {
        HandArrowDirection.Left, HandArrowDirection.Right -> {
            val slot = handFieldSlotAtOffset(current, direction) ?: return false
            rowNavigation.focusRequesterFor(slot)
        }

        HandArrowDirection.Up -> previousNavigation?.focusRequesterFor(current) ?: return false
        HandArrowDirection.Down -> nextNavigation?.focusRequesterFor(current) ?: return false
    }

    return runCatching {
        target.requestFocus()
        true
    }.getOrDefault(false)
}

@Stable
internal class TableManagerEditorState private constructor(
    private val initialTable: TableState,
    initialHands: List<TableHand>,
) {
    val roundId: Int = initialTable.roundId
    val tableId: Int = initialTable.tableId
    val playerIds: List<Int> = initialTable.playerIds

    var isCompleted by mutableStateOf(initialTable.isCompleted)
    var useTotalsOnly by mutableStateOf(initialTable.useTotalsOnly)
    var usePointsCalculation by mutableStateOf(initialTable.usePointsCalculation)

    var playerEastId by mutableStateOf("")
    var playerSouthId by mutableStateOf("")
    var playerWestId by mutableStateOf("")
    var playerNorthId by mutableStateOf("")

    private val initialSeatAssignments: SeatAssignments

    var playerEastScore by mutableStateOf(initialManualScores().east)
    var playerSouthScore by mutableStateOf(initialManualScores().south)
    var playerWestScore by mutableStateOf(initialManualScores().west)
    var playerNorthScore by mutableStateOf(initialManualScores().north)

    var playerEastPoints by mutableStateOf(initialManualPoints().east)
    var playerSouthPoints by mutableStateOf(initialManualPoints().south)
    var playerWestPoints by mutableStateOf(initialManualPoints().west)
    var playerNorthPoints by mutableStateOf(initialManualPoints().north)

    val hands = mutableStateListOf<HandDraftState>()

    val calculatedHandScoreTotals: SeatTextValues
        get() = calculateHandScoreTotals()

    val cumulativeHandScoreSubtotals: List<SeatTextValues>
        get() = calculateCumulativeHandScoreSubtotals()

    val calculatedHandPointTotals: SeatTextValues
        get() = calculatePointsFromScores(calculatedHandScoreTotals)

    val displayEastScore: String
        get() = effectiveTableScores().east
    val displaySouthScore: String
        get() = effectiveTableScores().south
    val displayWestScore: String
        get() = effectiveTableScores().west
    val displayNorthScore: String
        get() = effectiveTableScores().north

    val displayEastPoints: String
        get() = effectiveTablePoints().east
    val displaySouthPoints: String
        get() = effectiveTablePoints().south
    val displayWestPoints: String
        get() = effectiveTablePoints().west
    val displayNorthPoints: String
        get() = effectiveTablePoints().north

    val hasUnsavedChanges: Boolean
        get() = buildTablePatch().isNotEmpty() || buildHandPatches().isNotEmpty()

    val hasInvalidHands: Boolean
        get() = hands.any { it.isResultSelectionInvalid }

    val hasEastSeatChanged: Boolean get() = playerEastId.trim() != initialSeatAssignments.east
    val hasSouthSeatChanged: Boolean get() = playerSouthId.trim() != initialSeatAssignments.south
    val hasWestSeatChanged: Boolean get() = playerWestId.trim() != initialSeatAssignments.west
    val hasNorthSeatChanged: Boolean get() = playerNorthId.trim() != initialSeatAssignments.north

    val hasEastScoreChanged: Boolean get() = playerEastScore.trim() != initialManualScores().east
    val hasSouthScoreChanged: Boolean get() = playerSouthScore.trim() != initialManualScores().south
    val hasWestScoreChanged: Boolean get() = playerWestScore.trim() != initialManualScores().west
    val hasNorthScoreChanged: Boolean get() = playerNorthScore.trim() != initialManualScores().north

    val hasEastPointsChanged: Boolean get() = playerEastPoints.trim() != initialManualPoints().east
    val hasSouthPointsChanged: Boolean get() = playerSouthPoints.trim() != initialManualPoints().south
    val hasWestPointsChanged: Boolean get() = playerWestPoints.trim() != initialManualPoints().west
    val hasNorthPointsChanged: Boolean get() = playerNorthPoints.trim() != initialManualPoints().north

    init {
        val initialAssignments = sanitizeSeatAssignments(
            playerIds = playerIds,
            east = initialTable.playerEastId,
            south = initialTable.playerSouthId,
            west = initialTable.playerWestId,
            north = initialTable.playerNorthId,
        )
        initialSeatAssignments = initialAssignments
        playerEastId = initialAssignments.east
        playerSouthId = initialAssignments.south
        playerWestId = initialAssignments.west
        playerNorthId = initialAssignments.north

        hands.addAll(initialHands.map { HandDraftState.from(it) })
    }

    fun setSeatAssignment(seatIndex: Int, playerId: String) {
        val trimmed = playerId.trim()
        when (seatIndex) {
            0 -> {
                playerEastId = trimmed
                if (trimmed.isBlank()) return
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            1 -> {
                playerSouthId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            2 -> {
                playerWestId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerNorthId.trim() == trimmed) playerNorthId = ""
            }

            3 -> {
                playerNorthId = trimmed
                if (trimmed.isBlank()) return
                if (playerEastId.trim() == trimmed) playerEastId = ""
                if (playerSouthId.trim() == trimmed) playerSouthId = ""
                if (playerWestId.trim() == trimmed) playerWestId = ""
            }

            else -> Unit
        }
    }

    fun enableManualTotals() {
        useTotalsOnly = true
    }

    fun disableManualTotals() {
        useTotalsOnly = false
    }

    fun updateCompletedState(value: Boolean) {
        if (!value) {
            isCompleted = false
            return
        }

        hands.forEach { it.markResultFieldsTouched() }
        if (hasInvalidHands) return
        isCompleted = true
    }

    fun buildTablePatch(): Map<String, Any?> {
        val patch = linkedMapOf<String, Any?>()
        val handPatches = buildHandPatches()

        fun putIfChanged(key: String, current: Any?, initial: Any?) {
            if (current != initial) patch[key] = current
        }

        putIfChanged("isCompleted", isCompleted, initialTable.isCompleted)
        putIfChanged("useTotalsOnly", useTotalsOnly, initialTable.useTotalsOnly)
        putIfChanged("usePointsCalculation", usePointsCalculation, initialTable.usePointsCalculation)

        putIfChanged("playerEastId", playerEastId.trim(), initialSeatAssignments.east)
        putIfChanged("playerSouthId", playerSouthId.trim(), initialSeatAssignments.south)
        putIfChanged("playerWestId", playerWestId.trim(), initialSeatAssignments.west)
        putIfChanged("playerNorthId", playerNorthId.trim(), initialSeatAssignments.north)

        val manualScores = manualTableScores()
        val initialManualScores = initialManualScores()
        putIfChanged("manualPlayerEastScore", manualScores.east, initialManualScores.east)
        putIfChanged("manualPlayerSouthScore", manualScores.south, initialManualScores.south)
        putIfChanged("manualPlayerWestScore", manualScores.west, initialManualScores.west)
        putIfChanged("manualPlayerNorthScore", manualScores.north, initialManualScores.north)

        val manualPoints = manualTablePoints()
        val initialManualPoints = initialManualPoints()
        putIfChanged("manualPlayerEastPoints", manualPoints.east, initialManualPoints.east)
        putIfChanged("manualPlayerSouthPoints", manualPoints.south, initialManualPoints.south)
        putIfChanged("manualPlayerWestPoints", manualPoints.west, initialManualPoints.west)
        putIfChanged("manualPlayerNorthPoints", manualPoints.north, initialManualPoints.north)

        if (shouldPersistEffectiveTotals(handPatches.isNotEmpty())) {
            val effectiveScores = effectiveTableScores()
            val effectivePoints = effectiveTablePoints()

            putIfChanged("playerEastScore", effectiveScores.east, initialTable.playerEastScore)
            putIfChanged("playerSouthScore", effectiveScores.south, initialTable.playerSouthScore)
            putIfChanged("playerWestScore", effectiveScores.west, initialTable.playerWestScore)
            putIfChanged("playerNorthScore", effectiveScores.north, initialTable.playerNorthScore)

            putIfChanged("playerEastPoints", effectivePoints.east, initialTable.playerEastPoints)
            putIfChanged("playerSouthPoints", effectivePoints.south, initialTable.playerSouthPoints)
            putIfChanged("playerWestPoints", effectivePoints.west, initialTable.playerWestPoints)
            putIfChanged("playerNorthPoints", effectivePoints.north, initialTable.playerNorthPoints)
        }

        return patch
    }

    fun buildHandPatches(): List<Pair<Int, Map<String, Any?>>> {
        return hands.mapNotNull { draft ->
            val patch = draft.buildPatch()
            if (patch.isEmpty()) null else draft.handId to patch
        }
    }

    fun discard() {
        isCompleted = initialTable.isCompleted
        useTotalsOnly = initialTable.useTotalsOnly
        usePointsCalculation = initialTable.usePointsCalculation
        playerEastId = initialSeatAssignments.east
        playerSouthId = initialSeatAssignments.south
        playerWestId = initialSeatAssignments.west
        playerNorthId = initialSeatAssignments.north
        val scores = initialManualScores()
        playerEastScore = scores.east
        playerSouthScore = scores.south
        playerWestScore = scores.west
        playerNorthScore = scores.north
        val points = initialManualPoints()
        playerEastPoints = points.east
        playerSouthPoints = points.south
        playerWestPoints = points.west
        playerNorthPoints = points.north
        hands.forEach { it.reset() }
    }

    companion object {
        private const val MIN_HAND_SCORE = 8
        private const val LOSER_COUNT = 3

        fun from(
            table: TableState,
            hands: List<TableHand>,
        ): TableManagerEditorState = TableManagerEditorState(table, hands)
    }

    private fun effectiveTableScores(): SeatTextValues {
        return if (useTotalsOnly) {
            manualTableScores()
        } else {
            calculatedHandScoreTotals
        }
    }

    private fun effectiveTablePoints(): SeatTextValues {
        return when {
            !usePointsCalculation -> manualTablePoints()
            else -> calculatePointsFromScores(effectiveTableScores())
        }
    }

    private fun manualTableScores(): SeatTextValues = SeatTextValues(
        east = playerEastScore.trim(),
        south = playerSouthScore.trim(),
        west = playerWestScore.trim(),
        north = playerNorthScore.trim(),
    )

    private fun manualTablePoints(): SeatTextValues = SeatTextValues(
        east = playerEastPoints.trim(),
        south = playerSouthPoints.trim(),
        west = playerWestPoints.trim(),
        north = playerNorthPoints.trim(),
    )

    private fun shouldPersistEffectiveTotals(hasHandChanges: Boolean): Boolean {
        if (initialTable.usePointsCalculation != usePointsCalculation) return true
        if (!usePointsCalculation) return true
        if (useTotalsOnly) return true
        if (initialTable.useTotalsOnly != useTotalsOnly) return true
        if (hasHandChanges) return true
        if (playerEastId.trim() != initialSeatAssignments.east) return true
        if (playerSouthId.trim() != initialSeatAssignments.south) return true
        if (playerWestId.trim() != initialSeatAssignments.west) return true
        if (playerNorthId.trim() != initialSeatAssignments.north) return true
        return false
    }

    private fun initialManualScores(): SeatTextValues = SeatTextValues(
        east = initialTable.manualPlayerEastScore.ifBlank { initialTable.playerEastScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        south = initialTable.manualPlayerSouthScore.ifBlank { initialTable.playerSouthScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        west = initialTable.manualPlayerWestScore.ifBlank { initialTable.playerWestScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
        north = initialTable.manualPlayerNorthScore.ifBlank { initialTable.playerNorthScore.takeIf { initialTable.useTotalsOnly }.orEmpty() },
    )

    private fun initialManualPoints(): SeatTextValues = SeatTextValues(
        east = initialTable.manualPlayerEastPoints.ifBlank {
            initialTable.playerEastPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        south = initialTable.manualPlayerSouthPoints.ifBlank {
            initialTable.playerSouthPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        west = initialTable.manualPlayerWestPoints.ifBlank {
            initialTable.playerWestPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
        north = initialTable.manualPlayerNorthPoints.ifBlank {
            initialTable.playerNorthPoints.takeIf { !initialTable.usePointsCalculation }.orEmpty()
        },
    )

    private fun calculateHandScoreTotals(): SeatTextValues {
        val seats = currentSeatIds() ?: return SeatTextValues.EMPTY
        var totals = SeatIntValues.ZERO
        var hasCalculatedHand = false

        for (hand in hands) {
            val handTotals = calculateHandSeatScores(hand, seats) ?: continue
            totals += handTotals
            hasCalculatedHand = true
        }

        return if (hasCalculatedHand) totals.asTextValues() else SeatTextValues.EMPTY
    }

    private fun calculateCumulativeHandScoreSubtotals(): List<SeatTextValues> {
        val seats = currentSeatIds() ?: return List(hands.size) { SeatTextValues.EMPTY }
        var totals = SeatIntValues.ZERO
        var hasCalculatedHand = false

        return hands.map { hand ->
            val handTotals = calculateHandSeatScores(hand, seats)
            if (handTotals != null) {
                totals += handTotals
                hasCalculatedHand = true
            }
            if (hasCalculatedHand) totals.asTextValues() else SeatTextValues.EMPTY
        }
    }

    private fun calculateHandSeatScores(
        hand: HandDraftState,
        seats: SeatIds,
    ): SeatIntValues? {
        val score = hand.handScore.trim()
        val winnerId = hand.playerWinnerId.trim()
        val loserId = hand.normalizedLoserId

        val base = when {
            hand.isIgnoredForCalculation -> return null
            score.isEmpty() -> return null
            winnerId.isEmpty() && loserId.isEmpty() && score == "0" -> SeatIntValues.ZERO
            winnerId.isEmpty() -> return null
            loserId.isEmpty() -> {
                val handScore = score.toIntOrNull() ?: return null
                val winnerPoints = (handScore + MIN_HAND_SCORE) * LOSER_COUNT
                val loserPoints = -(handScore + MIN_HAND_SCORE)
                seatValuesForWinnerOnly(
                    winnerId = winnerId,
                    seats = seats,
                    winnerValue = winnerPoints,
                    otherValue = loserPoints,
                ) ?: return null
            }
            else -> {
                val handScore = score.toIntOrNull() ?: return null
                val winnerPoints = handScore + (MIN_HAND_SCORE * LOSER_COUNT)
                val loserPoints = -(handScore + MIN_HAND_SCORE)
                seatValuesForWinnerAndLoser(
                    winnerId = winnerId,
                    loserId = loserId,
                    seats = seats,
                    winnerValue = winnerPoints,
                    loserValue = loserPoints,
                    otherValue = -MIN_HAND_SCORE,
                ) ?: return null
            }
        }

        return base.copy(
            east = base.east + parsePenalty(hand.playerEastPenalty),
            south = base.south + parsePenalty(hand.playerSouthPenalty),
            west = base.west + parsePenalty(hand.playerWestPenalty),
            north = base.north + parsePenalty(hand.playerNorthPenalty),
        )
    }

    private fun calculatePointsFromScores(scores: SeatTextValues): SeatTextValues {
        val ranked = listOf(
            RankedSeat(Seat.EAST, scores.east.toIntOrNull()),
            RankedSeat(Seat.SOUTH, scores.south.toIntOrNull()),
            RankedSeat(Seat.WEST, scores.west.toIntOrNull()),
            RankedSeat(Seat.NORTH, scores.north.toIntOrNull()),
        )
        if (ranked.any { it.score == null }) return SeatTextValues.EMPTY

        val sorted = ranked.sortedByDescending { it.score ?: Int.MIN_VALUE }
        val assigned = when {
            sorted[0].score == sorted[1].score && sorted[1].score == sorted[2].score && sorted[2].score == sorted[3].score ->
                listOf("1,75", "1,75", "1,75", "1,75")
            sorted[0].score == sorted[1].score && sorted[1].score == sorted[2].score ->
                listOf("2,33", "2,33", "2,33", "0")
            sorted[1].score == sorted[2].score && sorted[2].score == sorted[3].score ->
                listOf("4", "1", "1", "1")
            sorted[0].score == sorted[1].score && sorted[2].score == sorted[3].score ->
                listOf("3", "3", "0,5", "0,5")
            sorted[0].score == sorted[1].score ->
                listOf("3", "3", "1", "0")
            sorted[1].score == sorted[2].score ->
                listOf("4", "1,5", "1,5", "0")
            sorted[2].score == sorted[3].score ->
                listOf("4", "2", "0,5", "0,5")
            else ->
                listOf("4", "2", "1", "0")
        }

        val bySeat = sorted.mapIndexed { index, rankedSeat -> rankedSeat.seat to assigned[index] }.toMap()
        return SeatTextValues(
            east = bySeat[Seat.EAST].orEmpty(),
            south = bySeat[Seat.SOUTH].orEmpty(),
            west = bySeat[Seat.WEST].orEmpty(),
            north = bySeat[Seat.NORTH].orEmpty(),
        )
    }

    private fun currentSeatIds(): SeatIds? {
        val east = playerEastId.trim()
        val south = playerSouthId.trim()
        val west = playerWestId.trim()
        val north = playerNorthId.trim()
        if (east.isEmpty() || south.isEmpty() || west.isEmpty() || north.isEmpty()) return null
        if (setOf(east, south, west, north).size != 4) return null
        return SeatIds(east = east, south = south, west = west, north = north)
    }

    private fun seatValuesForWinnerOnly(
        winnerId: String,
        seats: SeatIds,
        winnerValue: Int,
        otherValue: Int,
    ): SeatIntValues? {
        return when (winnerId) {
            seats.east -> SeatIntValues(winnerValue, otherValue, otherValue, otherValue)
            seats.south -> SeatIntValues(otherValue, winnerValue, otherValue, otherValue)
            seats.west -> SeatIntValues(otherValue, otherValue, winnerValue, otherValue)
            seats.north -> SeatIntValues(otherValue, otherValue, otherValue, winnerValue)
            else -> null
        }
    }

    private fun seatValuesForWinnerAndLoser(
        winnerId: String,
        loserId: String,
        seats: SeatIds,
        winnerValue: Int,
        loserValue: Int,
        otherValue: Int,
    ): SeatIntValues? {
        if (winnerId == loserId) return null
        if (winnerId !in seats.all || loserId !in seats.all) return null
        return SeatIntValues(
            east = when (seats.east) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            south = when (seats.south) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            west = when (seats.west) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
            north = when (seats.north) {
                winnerId -> winnerValue
                loserId -> loserValue
                else -> otherValue
            },
        )
    }

    private fun parsePenalty(value: String): Int = value.trim().toIntOrNull() ?: 0
}

@Stable
internal class HandDraftState private constructor(
    private val initial: TableHand,
) {
    val handId: Int = initial.handId

    var playerWinnerId by mutableStateOf(initial.playerWinnerId)
    var playerLooserId by mutableStateOf(initial.playerLooserId)
    var handScore by mutableStateOf(initial.handScore)
    var isChickenHand by mutableStateOf(initial.isChickenHand)
    var isDone by mutableStateOf(initial.isDone)
    var playerEastPenalty by mutableStateOf(initial.playerEastPenalty)
    var playerSouthPenalty by mutableStateOf(initial.playerSouthPenalty)
    var playerWestPenalty by mutableStateOf(initial.playerWestPenalty)
    var playerNorthPenalty by mutableStateOf(initial.playerNorthPenalty)
    private var resultFieldsTouched by mutableStateOf(false)

    val hasLoserSelected: Boolean
        get() = normalizedLoserId.isNotEmpty()

    val selectedWinnerPlayerId: Int?
        get() = playerWinnerId.trim().toIntOrNull()

    val selectedLoserPlayerId: Int?
        get() = normalizedLoserId.toIntOrNull()

    val normalizedLoserId: String
        get() = playerLooserId.trim().takeUnless { it == "-" }.orEmpty()

    val showValidationError: Boolean
        get() = resultFieldsTouched && isResultSelectionInvalid

    val validationErrorMessage: String?
        get() = when {
            !isResultSelectionInvalid -> null
            isCompletelyEmpty -> null
            else -> "This hand is invalid and cannot be marked as done or completed."
        }

    val validationDetailMessages: List<String>
        get() {
            if (!isResultSelectionInvalid || isCompletelyEmpty) return emptyList()

            val winnerId = playerWinnerId.trim()
            val loserId = normalizedLoserId
            val score = handScore.trim()
            val parsedScore = score.toIntOrNull()
            val messages = mutableListOf<String>()

            if (winnerId.isEmpty() || score.isEmpty()) {
                messages += "There are missing fields."
            }

            if (winnerId.isEmpty() && loserId.isNotEmpty()) {
                messages += "A loser cannot be selected without a winner."
            }

            if (score.isNotEmpty() && parsedScore == null) {
                messages += "The score must be a whole number."
            }

            if (parsedScore != null && parsedScore < MIN_HAND_SCORE) {
                messages += "The minimum score to win a hand is 8."
            }

            if (parsedScore != null && isChickenHand && parsedScore > MAX_CHICKEN_HAND_SCORE) {
                messages += "A chicken hand cannot score more than 12 points."
            }

            return messages.distinct()
        }

    val hasWinnerChanged: Boolean get() = playerWinnerId.trim() != initial.playerWinnerId
    val hasLoserChanged: Boolean get() = normalizedLoserId != initial.playerLooserId
    val hasScoreChanged: Boolean get() = handScore.trim() != initial.handScore
    val hasChickenHandChanged: Boolean get() = isChickenHand != initial.isChickenHand
    val hasDoneChanged: Boolean get() = isDone != initial.isDone
    val hasEastPenaltyChanged: Boolean get() = playerEastPenalty.trim() != initial.playerEastPenalty
    val hasSouthPenaltyChanged: Boolean get() = playerSouthPenalty.trim() != initial.playerSouthPenalty
    val hasWestPenaltyChanged: Boolean get() = playerWestPenalty.trim() != initial.playerWestPenalty
    val hasNorthPenaltyChanged: Boolean get() = playerNorthPenalty.trim() != initial.playerNorthPenalty

    val isIgnoredForCalculation: Boolean
        get() = isResultSelectionInvalid || isCompletelyEmpty

    private val isCompletelyEmpty: Boolean
        get() = playerWinnerId.trim().isEmpty() && normalizedLoserId.isEmpty() && handScore.trim().isEmpty()

    val isResultSelectionInvalid: Boolean
        get() {
            val winnerId = playerWinnerId.trim()
            val loserId = normalizedLoserId
            val score = handScore.trim()

            if (winnerId.isEmpty() && loserId.isEmpty() && score.isEmpty()) return false
            if (winnerId.isEmpty() && loserId.isEmpty() && score == "0") return false
            if (winnerId.isEmpty() && loserId.isNotEmpty()) return true
            if (winnerId.isEmpty() || score.isEmpty()) return true

            val parsedScore = score.toIntOrNull() ?: return true
            if (parsedScore < MIN_HAND_SCORE) return true
            if (isChickenHand && parsedScore > MAX_CHICKEN_HAND_SCORE) return true
            return false
        }

    fun setWinnerPlayerId(value: String) {
        val trimmed = value.trim().takeUnless { it == "-" }.orEmpty()
        playerWinnerId = trimmed
        if (trimmed.isNotEmpty() && normalizedLoserId == trimmed) {
            playerLooserId = "-"
            isChickenHand = false
        }
    }

    fun setLoserPlayerId(value: String) {
        playerLooserId = value.trim().ifBlank { "-" }
        if (!hasLoserSelected) {
            isChickenHand = false
        }
        markResultFieldsTouched()
    }

    fun updateHandScore(value: String) {
        handScore = value.filter { it.isDigit() }
        markResultFieldsTouched()
    }

    fun updateChickenHand(value: Boolean) {
        isChickenHand = value
        markResultFieldsTouched()
    }

    fun updateDoneState(value: Boolean) {
        if (!value) {
            isDone = false
            markResultFieldsTouched()
            return
        }
        if (isResultSelectionInvalid) {
            isDone = false
            markResultFieldsTouched()
            return
        }
        isDone = true
        markResultFieldsTouched()
    }

    fun markResultFieldsTouched() {
        resultFieldsTouched = true
    }

    fun reset() {
        playerWinnerId = initial.playerWinnerId
        playerLooserId = initial.playerLooserId.ifBlank { "-" }
        handScore = initial.handScore
        isChickenHand = initial.isChickenHand
        isDone = initial.isDone
        playerEastPenalty = initial.playerEastPenalty
        playerSouthPenalty = initial.playerSouthPenalty
        playerWestPenalty = initial.playerWestPenalty
        playerNorthPenalty = initial.playerNorthPenalty
        resultFieldsTouched = false
    }

    fun buildPatch(): Map<String, Any?> {
        val patch = linkedMapOf<String, Any?>()

        fun putIfChanged(key: String, current: Any?, initialValue: Any?) {
            if (current != initialValue) patch[key] = current
        }

        putIfChanged("playerWinnerId", playerWinnerId.trim(), initial.playerWinnerId)
        putIfChanged("playerLooserId", normalizedLoserId, initial.playerLooserId)
        putIfChanged("handScore", handScore.trim(), initial.handScore)
        putIfChanged("isChickenHand", isChickenHand, initial.isChickenHand)
        putIfChanged("isDone", isDone, initial.isDone)
        putIfChanged("playerEastPenalty", playerEastPenalty.trim(), initial.playerEastPenalty)
        putIfChanged("playerSouthPenalty", playerSouthPenalty.trim(), initial.playerSouthPenalty)
        putIfChanged("playerWestPenalty", playerWestPenalty.trim(), initial.playerWestPenalty)
        putIfChanged("playerNorthPenalty", playerNorthPenalty.trim(), initial.playerNorthPenalty)

        return patch
    }

    companion object {
        fun from(hand: TableHand): HandDraftState = HandDraftState(hand).also { draft ->
            if (draft.playerLooserId.trim().isEmpty()) {
                draft.playerLooserId = "-"
            }
        }
    }
}

private data class NormalizedSeats(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
)

private data class SeatIds(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
) {
    val all: Set<String> = setOf(east, south, west, north)
}

private data class SeatIntValues(
    val east: Int,
    val south: Int,
    val west: Int,
    val north: Int,
) {
    operator fun plus(other: SeatIntValues): SeatIntValues = SeatIntValues(
        east = east + other.east,
        south = south + other.south,
        west = west + other.west,
        north = north + other.north,
    )

    fun asTextValues(): SeatTextValues = SeatTextValues(
        east = east.toString(),
        south = south.toString(),
        west = west.toString(),
        north = north.toString(),
    )

    companion object {
        val ZERO = SeatIntValues(0, 0, 0, 0)
    }
}

internal data class SeatTextValues(
    val east: String,
    val south: String,
    val west: String,
    val north: String,
) {
    companion object {
        val EMPTY = SeatTextValues("", "", "", "")
        val ZERO = SeatTextValues("0", "0", "0", "0")
    }
}

private enum class Seat {
    EAST,
    SOUTH,
    WEST,
    NORTH,
}

private data class RankedSeat(
    val seat: Seat,
    val score: Int?,
)

private typealias SeatAssignments = NormalizedSeats

private fun sanitizeSeatAssignments(
    playerIds: List<Int>,
    east: String,
    south: String,
    west: String,
    north: String,
): SeatAssignments {
    fun parseValid(value: String): Int? {
        val id = value.trim().toIntOrNull() ?: return null
        return id.takeIf { it in playerIds }
    }

    return SeatAssignments(
        east = parseValid(east)?.toString().orEmpty(),
        south = parseValid(south)?.toString().orEmpty(),
        west = parseValid(west)?.toString().orEmpty(),
        north = parseValid(north)?.toString().orEmpty(),
    )
}

@Composable
private fun SeatPositionsRow(
    playerIds: List<Int>,
    east: String,
    south: String,
    west: String,
    north: String,
    enabled: Boolean,
    eastChanged: Boolean = false,
    southChanged: Boolean = false,
    westChanged: Boolean = false,
    northChanged: Boolean = false,
    onEastChange: (String) -> Unit,
    onSouthChange: (String) -> Unit,
    onWestChange: (String) -> Unit,
    onNorthChange: (String) -> Unit,
    playerNamesById: Map<Int, String>,
) {
    fun seatMarksByPlayerId(): Map<Int, String> {
        fun parseId(value: String): Int? = value.trim().toIntOrNull()

        val marks = linkedMapOf<Int, MutableList<String>>()
        fun put(value: String, seat: String) {
            val id = parseId(value) ?: return
            marks.getOrPut(id) { mutableListOf() }.add(seat)
        }
        put(east, "E")
        put(south, "S")
        put(west, "W")
        put(north, "N")
        return marks.mapValues { (_, seats) -> seats.distinct().joinToString(separator = ",") }
    }

    val optionSuffixById = remember(east, south, west, north) { seatMarksByPlayerId() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerDropdown(
            modifier = Modifier.weight(1f),
            label = "East",
            playerIds = playerIds,
            value = east,
            enabled = enabled,
            isChanged = eastChanged,
            onChange = onEastChange,
            playerNamesById = playerNamesById,
            optionSuffixById = optionSuffixById,
        )
        PlayerDropdown(
            modifier = Modifier.weight(1f),
            label = "South",
            playerIds = playerIds,
            value = south,
            enabled = enabled,
            isChanged = southChanged,
            onChange = onSouthChange,
            playerNamesById = playerNamesById,
            optionSuffixById = optionSuffixById,
        )
        PlayerDropdown(
            modifier = Modifier.weight(1f),
            label = "West",
            playerIds = playerIds,
            value = west,
            enabled = enabled,
            isChanged = westChanged,
            onChange = onWestChange,
            playerNamesById = playerNamesById,
            optionSuffixById = optionSuffixById,
        )
        PlayerDropdown(
            modifier = Modifier.weight(1f),
            label = "North",
            playerIds = playerIds,
            value = north,
            enabled = enabled,
            isChanged = northChanged,
            onChange = onNorthChange,
            playerNamesById = playerNamesById,
            optionSuffixById = optionSuffixById,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerDropdown(
    modifier: Modifier = Modifier,
    label: String,
    playerIds: List<Int>,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    playerNamesById: Map<Int, String> = emptyMap(),
    optionSuffixById: Map<Int, String> = emptyMap(),
    excludedPlayerIds: Set<Int> = emptySet(),
    emptyOptionLabel: String? = null,
    isChanged: Boolean = false,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    onNavigateRight: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchorFocusRequester = remember { FocusRequester() }
    val menuFocusRequester = remember { FocusRequester() }
    data class DropdownOption(
        val value: String,
        val label: String,
        val onSelect: () -> Unit,
    )

    val trimmed = value.trim()
    val selectedId = trimmed.toIntOrNull()
    val displayValue = when {
        trimmed.isBlank() || selectedId == null && trimmed == "-" -> emptyOptionLabel.orEmpty()
        selectedId == null -> trimmed
        else -> playerNamesById[selectedId] ?: "Player $selectedId"
    }
    val options = buildList {
        emptyOptionLabel?.let { emptyLabel ->
            add(
                DropdownOption(
                    value = emptyLabel,
                    label = emptyLabel,
                    onSelect = {
                        expanded = false
                        onChange(emptyLabel)
                        onDismiss()
                    },
                ),
            )
        }
        playerIds.forEach { id ->
            if (id in excludedPlayerIds) return@forEach
            val suffix = optionSuffixById[id]
            val base = playerNamesById[id] ?: "Player $id"
            add(
                DropdownOption(
                    value = id.toString(),
                    label = if (suffix.isNullOrBlank()) base else "$base ($suffix)",
                    onSelect = {
                        expanded = false
                        onChange(id.toString())
                        onDismiss()
                    },
                ),
            )
        }
    }
    val selectedOptionIndex = options.indexOfFirst { it.value == trimmed }.takeIf { it >= 0 } ?: 0
    var activeOptionIndex by remember { mutableStateOf(selectedOptionIndex) }

    fun openMenu() {
        activeOptionIndex = selectedOptionIndex
        expanded = true
        anchorFocusRequester.requestFocus()
    }

    fun moveActive(delta: Int) {
        if (options.isEmpty()) return
        val nextIndex = (activeOptionIndex + delta).coerceIn(0, options.lastIndex)
        activeOptionIndex = nextIndex
    }

    fun moveAway(key: Key): Boolean {
        if (expanded) expanded = false
        return when (key) {
            Key.DirectionLeft -> onNavigateLeft?.invoke() ?: false
            Key.DirectionRight -> onNavigateRight?.invoke() ?: false
            Key.DirectionUp -> onNavigateUp?.invoke() ?: false
            Key.DirectionDown -> onNavigateDown?.invoke() ?: false
            else -> false
        }
    }

    LaunchedEffect(expanded, selectedOptionIndex, options.size) {
        if (!expanded) {
            activeOptionIndex = selectedOptionIndex
        } else {
            activeOptionIndex = activeOptionIndex.coerceIn(0, options.lastIndex.coerceAtLeast(0))
            menuFocusRequester.requestFocus()
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand -> if (enabled) expanded = shouldExpand },
        modifier = modifier,
    ) {
        CompactOutlinedTextField(
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled,
                )
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusRequester(anchorFocusRequester)
                .onFocusChanged { if (!it.isFocused) onFocusLost() }
                .onPreviewKeyEvent { e: KeyEvent ->
                    if (!enabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> if (expanded) {
                            moveActive(1)
                            true
                        } else {
                            openMenu()
                            true
                        }

                        Key.DirectionUp -> if (expanded) {
                            moveActive(-1)
                            true
                        } else {
                            openMenu()
                            true
                        }

                        Key.DirectionLeft, Key.DirectionRight -> moveAway(e.key)

                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            if (expanded) {
                                options.getOrNull(activeOptionIndex)?.onSelect()
                            } else {
                                openMenu()
                            }
                            true
                        }
                        Key.Escape -> {
                            if (expanded) {
                                expanded = false
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                .fillMaxWidth(),
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        colors = if (enabled && isChanged) {
            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onDismiss()
            },
        ) {
            Column(
                modifier = Modifier
                    .focusRequester(menuFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (!enabled || !expanded || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> {
                                if (activeOptionIndex < options.lastIndex) {
                                    moveActive(1)
                                    true
                                } else {
                                    moveAway(Key.DirectionDown)
                                }
                            }

                            Key.DirectionUp -> {
                                if (activeOptionIndex > 0) {
                                    moveActive(-1)
                                    true
                                } else {
                                    moveAway(Key.DirectionUp)
                                }
                            }

                            Key.DirectionLeft, Key.DirectionRight -> moveAway(e.key)
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                options.getOrNull(activeOptionIndex)?.onSelect()
                                true
                            }

                            Key.Escape -> {
                                expanded = false
                                true
                            }

                            else -> false
                        }
                    },
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        modifier = Modifier.background(
                            if (index == activeOptionIndex) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                        text = { Text(option.label) },
                        onClick = {
                            activeOptionIndex = index
                            option.onSelect()
                            anchorFocusRequester.requestFocus()
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}
