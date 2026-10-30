package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.toDigitsInput
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
import mahjongtournamentsuite.adminapp.generated.resources.timer_pause
import mahjongtournamentsuite.adminapp.generated.resources.timer_start
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimerStandaloneScreen(
    initialRound: Int = 1,
) {
    val themeController = rememberScreenThemeController()

    MtsTheme(useDarkTheme = themeController.isDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize().appFocusGroup()) {
            Box(modifier = Modifier.fillMaxSize()) {
                TimerContent(
                    initialRound = initialRound,
                    themePreference = themeController.preference,
                    onToggleTheme = themeController.onTogglePreference,
                )
            }
        }
    }
}

@Composable
private fun TimerContent(
    initialRound: Int,
    themePreference: ThemePreference,
    onToggleTheme: () -> Unit,
) {
    val roundDownFocusRequester = remember { FocusRequester() }
    val roundUpFocusRequester = remember { FocusRequester() }
    val startFocusRequester = remember { FocusRequester() }
    val resetFocusRequester = remember { FocusRequester() }
    val setTimeFocusRequester = remember { FocusRequester() }
    val themeFocusRequester = remember { FocusRequester() }
    var isRunning by remember { mutableStateOf(false) }
    var roundNum by remember { mutableStateOf(initialRound) }

    LaunchedEffect(initialRound) {
        roundNum = initialRound
    }

    var maxTimeSeconds by remember { mutableStateOf(DEFAULT_MAX_TIME_SECONDS) }
    var timeLeftSeconds by remember { mutableStateOf(DEFAULT_MAX_TIME_SECONDS) }

    var isSetTimeDialogOpen by remember { mutableStateOf(false) }
    var minutesValue by remember {
        mutableStateOf(TextFieldValue((maxTimeSeconds / 60).toString()))
    }

    val hasFinished = timeLeftSeconds <= 0L

    fun applyRemainingTime() {
        val minutes = minutesValue.text.trim().toLongOrNull()
        if (minutes != null && minutes > 0) {
            maxTimeSeconds = minutes * 60L
            timeLeftSeconds = maxTimeSeconds
            isRunning = false
            isSetTimeDialogOpen = false
        }
    }

    LaunchedEffect(Unit) {
        startFocusRequester.requestFocus()
    }

    LaunchedEffect(isRunning, maxTimeSeconds) {
        while (isRunning) {
            delay(1_000)
            timeLeftSeconds = (timeLeftSeconds - 1L).coerceAtLeast(0L)
            if (timeLeftSeconds <= 0L) isRunning = false
        }
    }

    val progress = if (maxTimeSeconds <= 0L) 0f else {
        ((maxTimeSeconds - timeLeftSeconds).toFloat() / maxTimeSeconds.toFloat()).coerceIn(0f, 1f)
    }

    val isWarning = maxTimeSeconds >= 3600L && timeLeftSeconds == 900L
    val isRed = (maxTimeSeconds >= 3600L && timeLeftSeconds <= 900L) || isWarning
    val timeColor = if (isRed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    val gangFontFamily = GangOfThreeFontFamily()
    val timeText = formatHms(timeLeftSeconds)
    val sampleText = remember(maxTimeSeconds) {
        val hours = (maxTimeSeconds / 3600L).coerceAtLeast(0L).toString()
        val digits = hours.length.coerceAtLeast(1)
        "${"8".repeat(digits)}:88:88"
    }

    if (isSetTimeDialogOpen) {
        val timeFieldFocusRequester = remember { FocusRequester() }
        val setFocusRequester = remember { FocusRequester() }
        val cancelFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            minutesValue = minutesValue.copy(
                selection = TextRange(0, minutesValue.text.length),
            )
            timeFieldFocusRequester.requestFocus()
        }

        TrackedDialog("SetRemainingTime") {
            AlertDialog(
                modifier = Modifier.appFocusGroup(),
                onDismissRequest = { isSetTimeDialogOpen = false },
                confirmButton = {
                    Button(
                        focusRequester = setFocusRequester,
                        buttonModifier = Modifier.focusLoop(
                            previous = timeFieldFocusRequester,
                            next = cancelFocusRequester,
                        ),
                        onClick = { applyRemainingTime() },
                    ) {
                        Text(stringResource(Res.string.set))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { isSetTimeDialogOpen = false },
                        focusRequester = cancelFocusRequester,
                        buttonModifier = Modifier.focusLoop(
                            previous = setFocusRequester,
                            next = timeFieldFocusRequester,
                        ),
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
                title = { Text(stringResource(Res.string.remaining_time)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(Res.string.enter_remaining_time_in_minutes))
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
                                onDone = { applyRemainingTime() },
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
                                            applyRemainingTime()
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

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isRunning) {
                FocusedIconButton(
                    enabled = roundNum > 0,
                    onClick = { roundNum -= 1 },
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
            if (roundNum > 0) {
                Text(
                    text = stringResource(Res.string.round_n, roundNum),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (!isRunning) {
                FocusedIconButton(
                    onClick = { roundNum += 1 },
                    focusRequester = roundUpFocusRequester,
                    buttonModifier = Modifier
                        .size(32.dp)
                        .focusLoop(
                            previous = if (roundNum > 0) roundDownFocusRequester else themeFocusRequester,
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
            if (hasFinished) {
                Text(
                    text = stringResource(Res.string.time_is_up),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                FocusedIconButton(
                    focusRequester = startFocusRequester,
                    showFocusHighlight = !isRunning,
                    onClick = {
                        if (hasFinished) {
                            timeLeftSeconds = maxTimeSeconds
                        }
                        isRunning = !isRunning
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(if (isRunning) 0.5f else 1f),
                    buttonModifier = Modifier
                        .size(56.dp)
                        .focusLoop(
                            previous = if (isRunning) startFocusRequester else roundUpFocusRequester,
                            next = if (isRunning) startFocusRequester else resetFocusRequester,
                        ),
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isRunning) Res.string.timer_pause else Res.string.timer_start),
                        modifier = Modifier.size(34.dp),
                    )
                }

                if (!isRunning) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusedIconButton(
                            focusRequester = resetFocusRequester,
                            buttonModifier = Modifier.focusLoop(
                                previous = startFocusRequester,
                                next = setTimeFocusRequester,
                            ),
                            onClick = {
                                isRunning = false
                                timeLeftSeconds = maxTimeSeconds
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.reset_timer),
                            )
                        }
                        FocusedIconButton(
                            focusRequester = setTimeFocusRequester,
                            buttonModifier = Modifier.focusLoop(
                                previous = resetFocusRequester,
                                next = themeFocusRequester,
                            ),
                            onClick = {
                                val minutesText = (maxTimeSeconds / 60).toString()
                                minutesValue = TextFieldValue(
                                    text = minutesText,
                                    selection = TextRange(0, minutesText.length),
                                )
                                isSetTimeDialogOpen = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = stringResource(Res.string.set_time),
                            )
                        }
                        FocusedIconButton(
                            onClick = onToggleTheme,
                            focusRequester = themeFocusRequester,
                            buttonModifier = Modifier.focusLoop(
                                previous = setTimeFocusRequester,
                                next = if (roundNum > 0) roundDownFocusRequester else roundUpFocusRequester,
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

private const val DEFAULT_MAX_TIME_SECONDS: Long = 6900L // 1h 55m

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
    return "${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun TimerStandaloneScreenPreview() {
    MtsTheme(useDarkTheme = false) {
        TimerStandaloneScreen()
    }
}
