package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.foundation.shape.RoundedCornerShape
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.CreateTournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.koin.compose.koinInject

@Composable
fun CreateTournamentScreen(
    navController: NavHostController,
) {
    val presenter = koinInject<CreateTournamentPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val nameFocusRequester = remember { FocusRequester() }
    val playersFocusRequester = remember { FocusRequester() }
    val roundsFocusRequester = remember { FocusRequester() }

    var name by remember { mutableStateOf("") }
    var numPlayersText by remember { mutableStateOf("60") }
    var numRoundsText by remember { mutableStateOf("7") }
    var isTeams by remember { mutableStateOf(true) }
    var computeMode by remember { mutableStateOf(CreateTournamentPresenter.ComputeMode.LIGHT) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<CreateTournamentPresenter.Progress?>(null) }
    var createJob by remember { mutableStateOf<Job?>(null) }
    var calcStartMark by remember { mutableStateOf<TimeMark?>(null) }
    val teamsToggleInteractionSource = remember { MutableInteractionSource() }
    val computeToggleInteractionSource = remember { MutableInteractionSource() }

    fun setTeamsChecked(checked: Boolean) {
        if (isLoading) return
        isTeams = checked
    }

    fun setHeavyCompute(checked: Boolean) {
        if (isLoading) return
        computeMode = if (checked) {
            CreateTournamentPresenter.ComputeMode.HEAVY
        } else {
            CreateTournamentPresenter.ComputeMode.LIGHT
        }
    }

    fun cancelCreation() {
        createJob?.cancel()
        createJob = null
        isLoading = false
        progress = null
        calcStartMark = null
    }

    DisposableEffect(Unit) {
        onDispose { createJob?.cancel() }
    }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    fun startCreate() {
        if (isLoading) return

        val trimmedName = name.trim()
        val numPlayers = numPlayersText.trim().toIntOrNull()
        val numRounds = numRoundsText.trim().toIntOrNull()

        if (trimmedName.isBlank()) {
            errorMessage = "* Name is required."
            nameFocusRequester.requestFocus()
            return
        }
        if (numPlayers == null || numPlayers <= 0 || numPlayers % 4 != 0) {
            errorMessage = "* Must be a positive multiple of 4."
            playersFocusRequester.requestFocus()
            return
        }
        if (numRounds == null || numRounds <= 0) {
            errorMessage = "* Must be a positive integer."
            roundsFocusRequester.requestFocus()
            return
        }

        errorMessage = null
        isLoading = true
        progress = null
        calcStartMark = TimeSource.Monotonic.markNow()

        val progressSink: (CreateTournamentPresenter.Progress) -> Unit = { p ->
            coroutineScope.launch { progress = p }
        }

        createJob = coroutineScope.launch {
            try {
                when (val result = presenter.createTournament(
                    name = trimmedName,
                    isTeams = isTeams,
                    numPlayers = numPlayers,
                    numRounds = numRounds,
                    computeMode = computeMode,
                    onProgress = progressSink,
                )) {
                    is AppResult.Success -> {
                        store.addTournament(result.value)
                        navController.navigate(
                            TournamentRoute(
                                tournamentId = result.value.id,
                                tournamentName = result.value.name,
                            ),
                        ) {
                            popUpTo(CreateTournamentRoute) { inclusive = true }
                        }
                    }

                    is AppResult.Failure -> {
                        errorMessage = result.error.toUiMessage()
                    }
                }
            } catch (_: CancellationException) {
                // User cancelled or navigated away.
            } finally {
                isLoading = false
                createJob = null
                progress = null
                calcStartMark = null
            }
        }
    }

    fun handleKeys(event: KeyEvent): Boolean {
        if (isLoading) return false
        if (event.type != KeyEventType.KeyDown) return false

        return when (event.key) {
            Key.Tab -> {
                focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                true
            }

            Key.Enter, Key.NumPadEnter -> {
                startCreate()
                true
            }

            else -> false
        }
    }

    AppScaffold(
        title = "Create tournament",
        isLoading = isLoading,
        onBack = {
            cancelCreation()
            navController.popBackStack()
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScreenColumn(
                modifier = Modifier.onPreviewKeyEvent(::handleKeys),
                maxWidth = 640.dp,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                scrollable = true,
            ) {
                SectionCard(
                    title = "",
                    subtitle = "Creates rounds, tables and players automatically by an endless try and error random assignment process, satisfying the teams restriction and avoiding 2 players playing together twice; if it takes too long, constraints may be unsatisfiable.",
                    verticalSpacing = 0.dp,
                    content = {
                        Spacer(Modifier.size(16.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name") },
                            singleLine = true,
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { playersFocusRequester.requestFocus() },
                                onDone = { startCreate() },
                            ),
                        )

                        Spacer(Modifier.size(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = numPlayersText,
                                onValueChange = { numPlayersText = it },
                                label = { Text("Players") },
                                supportingText = { Text("* Only multiples of 4") },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(playersFocusRequester),
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { roundsFocusRequester.requestFocus() },
                                    onDone = { startCreate() },
                                ),
                            )

                            OutlinedTextField(
                                value = numRoundsText,
                                onValueChange = { numRoundsText = it },
                                label = { Text("Rounds") },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(roundsFocusRequester),
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { startCreate() },
                                    onNext = { startCreate() },
                                ),
                            )
                        }

                        Spacer(Modifier.size(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(
                                checked = isTeams,
                                onCheckedChange = ::setTeamsChecked,
                                enabled = !isLoading,
                            )
                            Text(
                                text = "Teams",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .toggleable(
                                        value = isTeams,
                                        enabled = !isLoading,
                                        role = Role.Switch,
                                        interactionSource = teamsToggleInteractionSource,
                                        indication = null,
                                        onValueChange = ::setTeamsChecked,
                                    ),
                            )
                        }

                        Text(
                            text = if (isTeams) {
                                "\t * Group Players by teams of 4 people that won't play together."
                            } else {
                                "\t * Won't group players."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.size(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isHeavy = computeMode == CreateTournamentPresenter.ComputeMode.HEAVY
                            Switch(
                                checked = isHeavy,
                                onCheckedChange = ::setHeavyCompute,
                                enabled = !isLoading,
                            )
                            Text(
                                text = "Heavy computing",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .toggleable(
                                        value = isHeavy,
                                        enabled = !isLoading,
                                        role = Role.Switch,
                                        interactionSource = computeToggleInteractionSource,
                                        indication = null,
                                        onValueChange = ::setHeavyCompute,
                                    ),
                            )
                        }

                        Text(
                            text = if (computeMode == CreateTournamentPresenter.ComputeMode.HEAVY) {
                                "\t * Uses more CPU to finish faster. Best if you can leave the computer working."
                            } else {
                                "\t * Uses fewer parallel tries so you can keep using the computer while it runs."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.size(8.dp))
                    },
                )

                errorMessage?.let { message ->
                    AppErrorMessage(message = message)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        enabled = !isLoading,
                        onClick = ::startCreate,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Create")
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { },
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .wrapContentWidth()
                        .widthIn(max = 440.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = when (progress?.phase) {
                                    CreateTournamentPresenter.Phase.CREATING -> "Saving tournament…"
                                    else -> "Calculating schedule…"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }

                        progress?.let { p ->
                            if (p.phase == CreateTournamentPresenter.Phase.CALCULATING) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "Parallel running tries (${p.maxConcurrency} max):",
                                        textAlign = TextAlign.Center,
                                    )
                                    RunningTriesSlots(
                                        runningTries = p.runningTries,
                                        maxConcurrency = p.maxConcurrency,
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Tried:",
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        text = buildString {
                                            append(formatWithDots(p.tried))
                                            formatAvgPerTry(
                                                tried = p.tried,
                                                startMark = calcStartMark,
                                            )?.let { avg ->
                                                append("  ")
                                                append(avg)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                Text(
                                    buildAnnotatedString {
                                        append("Calculated schedule in ")
                                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                            append(formatWithDots(p.tried))
                                        }
                                        append(" tries")
                                    },
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Saving to server…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } ?: Text("Starting…")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Button(
                                onClick = ::cancelCreation,
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatWithDots(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val reversed = s.reversed().chunked(3).joinToString(".")
    return reversed.reversed()
}

private fun formatAvgPerTry(
    tried: Long,
    startMark: TimeMark?,
): String? {
    if (startMark == null) return null
    if (tried <= 0) return null

    val elapsedNs = startMark.elapsedNow().inWholeNanoseconds
    if (elapsedNs <= 0) return null

    val nsPerTry = elapsedNs.toDouble() / tried.toDouble()

    return when {
        nsPerTry >= 1_000_000_000.0 -> {
            val s = nsPerTry / 1_000_000_000.0
            "(${formatDecimal(s, 2)} s/try)"
        }

        nsPerTry >= 1_000_000.0 -> {
            val ms = nsPerTry / 1_000_000.0
            "(${formatDecimal(ms, if (ms >= 10) 1 else 2)} ms/try)"
        }

        nsPerTry >= 1_000.0 -> {
            val us = nsPerTry / 1_000.0
            "(${formatDecimal(us, if (us >= 10) 1 else 2)} µs/try)"
        }

        else -> "(${formatDecimal(nsPerTry, 0)} ns/try)"
    }
}

private fun formatDecimal(value: Double, decimals: Int): String {
    val safeDecimals = decimals.coerceIn(0, 6)
    val factor = (0 until safeDecimals).fold(1.0) { acc, _ -> acc * 10.0 }
    val rounded = (value * factor).roundToLong().toDouble() / factor
    // Avoid "-0.0"
    val safe = if (abs(rounded) < 0.000_000_1) 0.0 else rounded
    return if (decimals == 0) safe.toLong().toString() else safe.toString()
}

@Composable
private fun RunningTriesSlots(
    runningTries: Int,
    maxConcurrency: Int,
) {
    val safeMax = maxConcurrency.coerceAtLeast(1)
    val filled = runningTries.coerceIn(0, safeMax)
    val shape = RoundedCornerShape(3.dp)
    val columns = minOf(25, safeMax)
    val slotSize = 10.dp
    val spacing = 2.dp
    val rows = (safeMax + columns - 1) / columns
    val gridWidth =
        (slotSize * columns.toFloat()) + (spacing * (columns - 1).coerceAtLeast(0).toFloat())
    val gridHeight = (slotSize * rows.toFloat()) + (spacing * (rows - 1).coerceAtLeast(0).toFloat())
    val maxGridHeight = 420.dp

    @OptIn(ExperimentalFoundationApi::class)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier
            .widthIn(min = gridWidth, max = gridWidth)
            .heightIn(max = gridHeight.coerceAtMost(maxGridHeight)),
    ) {
        items(count = safeMax) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(slotSize)
                    .clip(shape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .background(
                        color = if (isFilled) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = shape,
                    ),
            )
        }
    }
}
