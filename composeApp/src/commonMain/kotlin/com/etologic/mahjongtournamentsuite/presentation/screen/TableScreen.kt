package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
                ExtendedFloatingActionButton(
                    onClick = { coroutineScope.launch { saveChanges() } },
                ) {
                    Text("Save")
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

    ResultsSection(
        editor = editor,
        enabled = enabled,
        onManualTotalsChange = onManualTotalsChange,
    )

    Spacer(modifier = Modifier.height(16.dp))

    PointsSection(
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
private fun ResultsSection(
    editor: TableManagerEditorState,
    enabled: Boolean,
    onManualTotalsChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = "Results",
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
private fun PointsSection(
    editor: TableManagerEditorState,
    enabled: Boolean,
    onManualPointsChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = "Points",
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
                label = { Text("East") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            CompactOutlinedTextField(
                value = southValue,
                onValueChange = onSouthChange,
                enabled = enabled,
                label = { Text("South") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            CompactOutlinedTextField(
                value = westValue,
                onValueChange = onWestChange,
                enabled = enabled,
                label = { Text("West") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            CompactOutlinedTextField(
                value = northValue,
                onValueChange = onNorthChange,
                enabled = enabled,
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
                    text = "The table cannot be marked completed while there are invalid hands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            editor.hands.forEachIndexed { index, hand ->
                HandRow(
                    index = index,
                    hand = hand,
                    playerIds = editor.playerIds,
                    playerNamesById = playerNamesById,
                    enabled = enabled,
                )
                if (index != editor.hands.lastIndex) {
                    handSubtotals.getOrNull(index)?.takeUnless { it == SeatTextValues.EMPTY }?.let { subtotal ->
                        HandSubtotalRow(subtotal)
                    }
                }
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
private fun HandRow(
    index: Int,
    hand: HandDraftState,
    playerIds: List<Int>,
    playerNamesById: Map<Int, String>,
    enabled: Boolean,
) {
    var penaltiesExpanded by remember(hand.handId) { mutableStateOf(false) }
    val fieldSpacing = 12.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(fieldSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(fieldSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerDropdown(
                        label = "Winner",
                        playerIds = playerIds,
                        value = hand.playerWinnerId,
                        enabled = enabled,
                        onChange = { hand.setWinnerPlayerId(it) },
                        onDismiss = { hand.markResultFieldsTouched() },
                        playerNamesById = playerNamesById,
                        excludedPlayerIds = setOfNotNull(hand.selectedLoserPlayerId),
                        emptyOptionLabel = "-",
                        modifier = Modifier.weight(1f),
                    )
                    PlayerDropdown(
                        label = "Loser",
                        playerIds = playerIds,
                        value = hand.playerLooserId,
                        enabled = enabled,
                        onChange = { hand.setLoserPlayerId(it) },
                        onDismiss = { hand.markResultFieldsTouched() },
                        playerNamesById = playerNamesById,
                        excludedPlayerIds = setOfNotNull(hand.selectedWinnerPlayerId),
                        emptyOptionLabel = "-",
                        modifier = Modifier.weight(1f),
                    )
                    CompactOutlinedTextField(
                        value = hand.handScore,
                        onValueChange = { hand.updateHandScore(it) },
                        enabled = enabled,
                        label = { Text("Score") },
                        isError = hand.showValidationError,
                        modifier = Modifier
                            .width(104.dp)
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    hand.markResultFieldsTouched()
                                }
                            },
                        singleLine = true,
                    )
                }

                if (hand.showValidationError) {
                    Text(
                        text = "This hand is invalid and is ignored in the calculation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                AnimatedVisibility(visible = penaltiesExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(fieldSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PenaltyField(
                            modifier = Modifier.weight(1f),
                            label = "East pty.",
                            value = hand.playerEastPenalty,
                            enabled = enabled,
                            onValueChange = { hand.playerEastPenalty = it },
                        )
                        PenaltyField(
                            modifier = Modifier.weight(1f),
                            label = "South pty.",
                            value = hand.playerSouthPenalty,
                            enabled = enabled,
                            onValueChange = { hand.playerSouthPenalty = it },
                        )
                        PenaltyField(
                            modifier = Modifier.weight(1f),
                            label = "West pty.",
                            value = hand.playerWestPenalty,
                            enabled = enabled,
                            onValueChange = { hand.playerWestPenalty = it },
                        )
                        PenaltyField(
                            modifier = Modifier.weight(1f),
                            label = "North pty.",
                            value = hand.playerNorthPenalty,
                            enabled = enabled,
                            onValueChange = { hand.playerNorthPenalty = it },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(fieldSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Chicken hand",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Checkbox(
                        checked = hand.isChickenHand,
                        enabled = enabled && hand.hasLoserSelected,
                        onCheckedChange = { hand.isChickenHand = it },
                    )
                }

                TextButton(onClick = { penaltiesExpanded = !penaltiesExpanded }) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Penalties")
                        Icon(
                            imageVector = if (penaltiesExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PenaltyField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    CompactOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.widthIn(min = 92.dp),
        singleLine = true,
    )
}

@Composable
private fun HandSubtotalRow(subtotal: SeatTextValues) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp, end = 12.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Subtotal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
        )
        ReadOnlySubtotalField(
            label = "East",
            value = subtotal.east,
            modifier = Modifier.weight(1f),
        )
        ReadOnlySubtotalField(
            label = "South",
            value = subtotal.south,
            modifier = Modifier.weight(1f),
        )
        ReadOnlySubtotalField(
            label = "West",
            value = subtotal.west,
            modifier = Modifier.weight(1f),
        )
        ReadOnlySubtotalField(
            label = "North",
            value = subtotal.north,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReadOnlySubtotalField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        label = label,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        colors = colors,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

private enum class TableManagerPendingUnsavedAction {
    Back,
    Refresh,
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
            if (parsedScore < 0) return true
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
    }

    fun updateHandScore(value: String) {
        handScore = value.filter { it.isDigit() }
    }

    fun markResultFieldsTouched() {
        resultFieldsTouched = true
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
    playerNamesById: Map<Int, String> = emptyMap(),
    optionSuffixById: Map<Int, String> = emptyMap(),
    excludedPlayerIds: Set<Int> = emptySet(),
    emptyOptionLabel: String? = null,
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val trimmed = value.trim()
    val selectedId = trimmed.toIntOrNull()
    val displayValue = when {
        trimmed.isBlank() || selectedId == null && trimmed == "-" -> emptyOptionLabel.orEmpty()
        selectedId == null -> trimmed
        else -> playerNamesById[selectedId] ?: "Player $selectedId"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand -> if (enabled) expanded = shouldExpand },
        modifier = modifier,
    ) {
        CompactOutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onDismiss()
            },
        ) {
            emptyOptionLabel?.let { emptyLabel ->
                DropdownMenuItem(
                    text = { Text(emptyLabel) },
                    onClick = {
                        expanded = false
                        onChange(emptyLabel)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            playerIds.forEach { id ->
                if (id in excludedPlayerIds) return@forEach
                val suffix = optionSuffixById[id]
                DropdownMenuItem(
                    text = {
                        val base = playerNamesById[id] ?: "Player $id"
                        Text(if (suffix.isNullOrBlank()) base else "$base ($suffix)")
                    },
                    onClick = {
                        expanded = false
                        onChange(id.toString())
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
