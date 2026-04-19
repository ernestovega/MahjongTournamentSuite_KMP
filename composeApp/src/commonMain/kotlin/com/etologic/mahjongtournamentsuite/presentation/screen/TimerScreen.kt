package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.theme.GangOfThreeFontFamily
import kotlinx.coroutines.delay

@Composable
fun TimerStandaloneScreen(
) {
    AppScaffold(title = "Timer") {
        TimerContent()
    }
}

@Composable
private fun TimerContent() {
    var isRunning by remember { mutableStateOf(false) }
    var roundNum by remember { mutableStateOf(1) }

    var maxTimeSeconds by remember { mutableStateOf(DEFAULT_MAX_TIME_SECONDS) }
    var timeLeftSeconds by remember { mutableStateOf(DEFAULT_MAX_TIME_SECONDS) }

    var isSetTimeDialogOpen by remember { mutableStateOf(false) }
    var minutesText by remember { mutableStateOf((maxTimeSeconds / 60).toString()) }

    val hasFinished = timeLeftSeconds <= 0L

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
        AlertDialog(
            onDismissRequest = { isSetTimeDialogOpen = false },
            confirmButton = {
                Button(
                    onClick = {
                        val minutes = minutesText.trim().toLongOrNull()
                        if (minutes != null && minutes > 0) {
                            maxTimeSeconds = minutes * 60L
                            timeLeftSeconds = maxTimeSeconds
                            isRunning = false
                            isSetTimeDialogOpen = false
                        }
                    },
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                Button(onClick = { isSetTimeDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Remaining time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter remaining time in minutes:")
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true,
                    )
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = "Round $roundNum",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 84.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 24.dp, end = 24.dp),
        )

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(
                    enabled = !isRunning,
                    onClick = { if (roundNum > 1) roundNum -= 1 },
                ) {
                    Text("−")
                }
                AppTextButton(
                    enabled = !isRunning,
                    onClick = { roundNum += 1 },
                ) {
                    Text("+")
                }
                AppTextButton(
                    onClick = {
                        if (hasFinished) {
                            timeLeftSeconds = maxTimeSeconds
                        }
                        isRunning = !isRunning
                    },
                ) {
                    Text(if (isRunning) "Pause" else "Start")
                }
                AppTextButton(
                    onClick = {
                        isRunning = false
                        timeLeftSeconds = maxTimeSeconds
                    },
                ) {
                    Text("Reset")
                }
                AppTextButton(
                    enabled = !isRunning,
                    onClick = {
                        minutesText = (maxTimeSeconds / 60).toString()
                        isSetTimeDialogOpen = true
                    },
                ) {
                    Text("Set time")
                }
            }

            if (hasFinished) {
                Text(
                    text = "Time is up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
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
