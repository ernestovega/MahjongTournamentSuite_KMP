package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import mahjongtournamentsuite.adminapp.generated.resources.timer_load_failed
import mahjongtournamentsuite.adminapp.generated.resources.timer_offline
import mahjongtournamentsuite.adminapp.generated.resources.timer_paused
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago_title
import mahjongtournamentsuite.adminapp.generated.resources.timer_waiting_gong
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.GongIcon
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.toDigitsInput
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalTelemetry
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.GangOfThreeFontFamily
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.presentation.theme.contentDescription
import com.etologic.mahjongtournamentsuite.presentation.theme.icon
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberScreenThemeController
import kotlinx.coroutines.delay
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.decrease_round
import mahjongtournamentsuite.adminapp.generated.resources.enter_remaining_time_in_minutes
import mahjongtournamentsuite.adminapp.generated.resources.increase_round
import mahjongtournamentsuite.adminapp.generated.resources.remaining_time
import mahjongtournamentsuite.adminapp.generated.resources.reset_timer
import mahjongtournamentsuite.adminapp.generated.resources.round_n
import mahjongtournamentsuite.adminapp.generated.resources.set
import mahjongtournamentsuite.adminapp.generated.resources.set_time
import mahjongtournamentsuite.adminapp.generated.resources.time_is_up
import mahjongtournamentsuite.adminapp.generated.resources.timer_gong_resume
import mahjongtournamentsuite.adminapp.generated.resources.timer_gong_start
import mahjongtournamentsuite.adminapp.generated.resources.timer_pause
import mahjongtournamentsuite.adminapp.generated.resources.timer_start
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button

@Composable
fun TimerStandaloneScreen(
    /** Null opens a Timer without a tournament, which keeps its clock on this device. */
    tournamentId: String?,
    initialRound: Int = 1,
) {
    val themeController = rememberScreenThemeController()
    val repository = koinInject<TournamentRepository>()
    val telemetry = LocalTelemetry.current
    val controller = remember(tournamentId) { RoundTimerController(repository, tournamentId, initialRound, telemetry = telemetry) }

    MtsTheme(useDarkTheme = themeController.isDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize().appFocusGroup()) {
            Box(modifier = Modifier.fillMaxSize()) {
                TimerContent(
                    controller = controller,
                    themePreference = themeController.preference,
                    onToggleTheme = themeController.onTogglePreference,
                )
            }
        }
    }
}

/** How often the Timer reads the clocks of the server, to follow a Play or a Pause on another Timer. */
private const val CLOCK_POLL_MS = 5_000L

@Composable
internal fun TimerContent(
    controller: RoundTimerController,
    themePreference: ThemePreference,
    onToggleTheme: () -> Unit,
    deviceNowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsState()
    val roundDownFocusRequester = remember { FocusRequester() }
    val roundUpFocusRequester = remember { FocusRequester() }
    val startFocusRequester = remember { FocusRequester() }
    val resetFocusRequester = remember { FocusRequester() }
    val setTimeFocusRequester = remember { FocusRequester() }
    val themeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(controller) {
        while (true) {
            controller.refresh()
            delay(CLOCK_POLL_MS)
        }
    }
    val now by produceState(deviceNowMs()) {
        while (true) {
            delay(200)
            value = deviceNowMs()
        }
    }

    val isRunning = state.isRunning
    val isStarted = state.isStarted
    val roundNum = state.roundId
    val maxTimeSeconds = state.totalSeconds()
    val timeLeftSeconds = state.remainingSeconds(now)
    val hasFinished = isStarted && timeLeftSeconds <= 0L
    // Only a Timer without a tournament picks its round. The Timer of a tournament follows the tournament.
    val showRoundButtons = state.independent && !isRunning

    var dialog by remember { mutableStateOf<TimerDialog?>(null) }

    LaunchedEffect(Unit) {
        startFocusRequester.requestFocus()
    }

    val progress = if (maxTimeSeconds <= 0L) {
        0f
    } else {
        ((maxTimeSeconds - timeLeftSeconds).toFloat() / maxTimeSeconds.toFloat()).coerceIn(0f, 1f)
    }

    val isRed = maxTimeSeconds >= 3600L && isStarted && timeLeftSeconds <= 900L
    val timeColor = if (isRed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    val gangFontFamily = GangOfThreeFontFamily()
    val timeText = formatHms(timeLeftSeconds)
    val sampleText = remember(maxTimeSeconds) {
        val hours = (maxTimeSeconds / 3600L).coerceAtLeast(0L).toString()
        val digits = hours.length.coerceAtLeast(1)
        "${"8".repeat(digits)}:88:88"
    }

    when (val open = dialog) {
        TimerDialog.SetTime -> MinutesDialog(
            name = "SetRemainingTime",
            title = stringResource(Res.string.remaining_time),
            message = stringResource(Res.string.enter_remaining_time_in_minutes),
            initialMinutes = ((timeLeftSeconds + 59) / 60).coerceAtLeast(1),
            allowZero = false,
            onApply = { minutes ->
                dialog = null
                scope.launch { controller.setRemainingMinutes(minutes) }
            },
            onDismiss = { dialog = null },
        )
        TimerDialog.StartedAgo -> MinutesDialog(
            name = "RoundStartedAgo",
            title = stringResource(Res.string.timer_started_ago_title),
            message = stringResource(Res.string.timer_started_ago_message),
            initialMinutes = 0,
            allowZero = true,
            onApply = { minutes ->
                dialog = null
                scope.launch { controller.startedMinutesAgo(minutes.toInt()) }
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    if (state.confirmReset) {
        TrackedDialog("ConfirmRoundReset") {
            AlertDialog(
                modifier = Modifier.appFocusGroup(),
                onDismissRequest = { controller.dismissResetConfirmation() },
                title = { Text(stringResource(Res.string.timer_reset_confirm_title)) },
                text = { Text(stringResource(Res.string.timer_reset_confirm_message)) },
                confirmButton = {
                    Button(onClick = { scope.launch { controller.reset(force = true) } }) {
                        Text(stringResource(Res.string.timer_reset_confirm))
                    }
                },
                dismissButton = {
                    Button(onClick = { controller.dismissResetConfirmation() }) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MtsTheme.spacing.lg, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRoundButtons) {
                FocusedIconButton(
                    enabled = roundNum > 1,
                    onClick = { controller.selectRound(roundNum - 1) },
                    focusRequester = roundDownFocusRequester,
                    buttonModifier = Modifier
                        .size(32.dp)
                        .focusLoop(
                            previous = themeFocusRequester,
                            next = roundUpFocusRequester,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(Res.string.decrease_round),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = stringResource(Res.string.round_n, roundNum),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (showRoundButtons) {
                FocusedIconButton(
                    enabled = roundNum < state.numRounds,
                    onClick = { controller.selectRound(roundNum + 1) },
                    focusRequester = roundUpFocusRequester,
                    buttonModifier = Modifier
                        .size(32.dp)
                        .focusLoop(
                            previous = roundDownFocusRequester,
                            next = startFocusRequester,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.increase_round),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(0)
            val maxHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(0)
            val baseStyle = MaterialTheme.typography.displayLarge.copy(fontFamily = gangFontFamily)

            val fittedFontSize = remember(sampleText, maxWidthPx, maxHeightPx, gangFontFamily) {
                findLargestFittingFontSize(
                    textMeasurer = textMeasurer,
                    text = sampleText,
                    style = baseStyle,
                    maxWidthPx = maxWidthPx,
                    maxHeightPx = maxHeightPx,
                )
            }

            Text(
                text = timeText,
                style = baseStyle.copy(fontSize = fittedFontSize),
                color = timeColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = if (isRed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val status = when {
                state.loadFailed && state.tournament == null -> stringResource(Res.string.timer_load_failed)
                state.isOffline -> stringResource(Res.string.timer_offline)
                hasFinished -> stringResource(Res.string.time_is_up)
                isStarted && !isRunning -> stringResource(Res.string.timer_paused)
                !isStarted && !state.independent -> stringResource(Res.string.timer_waiting_gong)
                else -> null
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasFinished || state.isOffline || state.loadFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                val startPrevious = when {
                    isRunning -> startFocusRequester
                    showRoundButtons -> roundUpFocusRequester
                    else -> themeFocusRequester
                }
                val startNext = if (isRunning) startFocusRequester else resetFocusRequester
                if (isRunning) {
                    FocusedIconButton(
                        focusRequester = startFocusRequester,
                        showFocusHighlight = false,
                        enabled = state.canControl,
                        onClick = { scope.launch { controller.pause() } },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .alpha(0.5f),
                        buttonModifier = Modifier
                            .size(56.dp)
                            .focusLoop(previous = startPrevious, next = startNext),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(Res.string.timer_pause),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                } else {
                    // The gong starts the round, or resumes it after a pause.
                    FocusedTextButton(
                        focusRequester = startFocusRequester,
                        enabled = state.canControl,
                        onClick = { scope.launch { controller.play() } },
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.align(Alignment.Center),
                        buttonModifier = Modifier.focusLoop(previous = startPrevious, next = startNext),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = GongIcon,
                                contentDescription = stringResource(Res.string.timer_start),
                                modifier = Modifier.size(72.dp),
                            )
                            Text(
                                text = stringResource(if (isStarted) Res.string.timer_gong_resume else Res.string.timer_gong_start),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }

                if (!isRunning) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusedIconButton(
                            focusRequester = resetFocusRequester,
                            enabled = isStarted,
                            buttonModifier = Modifier.focusLoop(
                                previous = startFocusRequester,
                                next = setTimeFocusRequester,
                            ),
                            onClick = { scope.launch { controller.reset() } },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.reset_timer),
                            )
                        }
                        // A started round can change its remaining time. A round that is not started can start earlier.
                        FocusedIconButton(
                            focusRequester = setTimeFocusRequester,
                            enabled = state.canControl,
                            buttonModifier = Modifier.focusLoop(
                                previous = resetFocusRequester,
                                next = themeFocusRequester,
                            ),
                            onClick = { dialog = if (isStarted) TimerDialog.SetTime else TimerDialog.StartedAgo },
                        ) {
                            Icon(
                                imageVector = if (isStarted) Icons.Default.AccessTime else Icons.Default.History,
                                contentDescription = stringResource(if (isStarted) Res.string.set_time else Res.string.timer_started_ago),
                            )
                        }
                        FocusedIconButton(
                            onClick = onToggleTheme,
                            focusRequester = themeFocusRequester,
                            buttonModifier = Modifier.focusLoop(
                                previous = setTimeFocusRequester,
                                next = if (showRoundButtons) roundDownFocusRequester else startFocusRequester,
                            ),
                        ) {
                            Icon(
                                imageVector = themePreference.icon,
                                contentDescription = themePreference.contentDescription,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class TimerDialog { SetTime, StartedAgo }

/** Asks for a whole number of minutes. Enter, Numpad Enter and the Done action apply it. */
@Composable
private fun MinutesDialog(
    name: String,
    title: String,
    message: String,
    initialMinutes: Long,
    allowZero: Boolean,
    onApply: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeFieldFocusRequester = remember { FocusRequester() }
    val setFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    var minutesValue by remember {
        val text = initialMinutes.toString()
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }

    fun apply() {
        val minutes = minutesValue.text.trim().toLongOrNull() ?: return
        if (minutes > 0 || (allowZero && minutes == 0L)) onApply(minutes)
    }

    LaunchedEffect(Unit) {
        timeFieldFocusRequester.requestFocus()
    }

    TrackedDialog(name) {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            confirmButton = {
                Button(
                    focusRequester = setFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = timeFieldFocusRequester,
                        next = cancelFocusRequester,
                    ),
                    onClick = { apply() },
                ) {
                    Text(stringResource(Res.string.set))
                }
            },
            dismissButton = {
                Button(
                    onClick = onDismiss,
                    focusRequester = cancelFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = setFocusRequester,
                        next = timeFieldFocusRequester,
                    ),
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message)
                    OutlinedTextField(
                        value = minutesValue,
                        onValueChange = { typed ->
                            val text = typed.text.toDigitsInput()
                            minutesValue = typed.copy(
                                text = text,
                                selection = TextRange(
                                    typed.selection.start.coerceAtMost(text.length),
                                    typed.selection.end.coerceAtMost(text.length),
                                ),
                            )
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { apply() },
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .focusRequester(timeFieldFocusRequester)
                            .focusProperties {
                                previous = cancelFocusRequester
                                next = setFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.Tab -> {
                                        if (event.isShiftPressed) {
                                            cancelFocusRequester.requestFocus()
                                        } else {
                                            setFocusRequester.requestFocus()
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        cancelFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        setFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        apply()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
            },
        )
    }
}

private fun findLargestFittingFontSize(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    maxHeightPx: Int,
    minSp: Float = 24f,
    maxSp: Float = 900f,
): TextUnit {
    if (maxWidthPx <= 0 || maxHeightPx <= 0) return minSp.sp

    val annotated = AnnotatedString(text)
    var low = minSp
    var high = maxSp

    repeat(18) {
        val mid = (low + high) / 2f
        val result = textMeasurer.measure(
            text = annotated,
            style = style.copy(fontSize = mid.sp),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )

        val fits = result.size.width <= maxWidthPx && result.size.height <= maxHeightPx
        if (fits) low = mid else high = mid
    }

    return low.sp
}

private fun formatHms(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
