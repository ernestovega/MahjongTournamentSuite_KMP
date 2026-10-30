package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import kotlinx.coroutines.delay
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.create_calculating
import mahjongtournamentsuite.adminapp.generated.resources.create_cancel_calculation
import mahjongtournamentsuite.adminapp.generated.resources.create_elapsed
import mahjongtournamentsuite.adminapp.generated.resources.create_parallel
import mahjongtournamentsuite.adminapp.generated.resources.create_progress_title
import mahjongtournamentsuite.adminapp.generated.resources.create_saving
import mahjongtournamentsuite.adminapp.generated.resources.create_speed
import mahjongtournamentsuite.adminapp.generated.resources.create_tried
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark

/** Number of load samples that the chart keeps. */
private const val LOAD_SAMPLES = 60
private const val WORKER_COLUMNS = 25
private const val WORKER_CELL_LIMIT = 200

/**
 * A dialog that shows the work of the schedule search: the phase, the number of tries, the time, the speed
 * and an animation of the tries that run at the same time. The button cancels the work.
 */
@Composable
fun CreateProgressDialog(
    progress: CreateTournamentPresenter.Progress?,
    startMark: TimeMark?,
    onCancel: () -> Unit,
) {
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startMark) {
        while (startMark != null) {
            elapsedMillis = startMark.elapsedNow().inWholeMilliseconds
            delay(250.milliseconds)
        }
    }
    val load = remember { mutableStateListOf<Float>() }
    LaunchedEffect(progress?.runningTries, progress?.tried) {
        val current = progress ?: return@LaunchedEffect
        if (current.phase != CreateTournamentPresenter.Phase.CALCULATING) return@LaunchedEffect
        load += current.runningTries.toFloat() / current.maxConcurrency.coerceAtLeast(1)
        while (load.size > LOAD_SAMPLES) load.removeAt(0)
    }

    val calculating = progress == null || progress.phase == CreateTournamentPresenter.Phase.CALCULATING
    TrackedDialog("CreateProgress") {
        AlertDialog(
            modifier = Modifier.widthIn(max = 560.dp),
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.create_progress_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(if (calculating) Res.string.create_calculating else Res.string.create_saving))
                    }
                    Text(stringResource(Res.string.create_elapsed, formatElapsed(elapsedMillis)))
                    if (calculating && progress != null) {
                        Text(stringResource(Res.string.create_tried, formatCount(progress.tried)))
                        val perSecond = if (elapsedMillis > 0) progress.tried * 1000 / elapsedMillis else 0L
                        Text(stringResource(Res.string.create_speed, formatCount(perSecond)))
                        Text(
                            stringResource(
                                Res.string.create_parallel,
                                progress.runningTries,
                                progress.maxConcurrency,
                            ),
                        )
                        LinearProgressIndicator(
                            progress = { progress.runningTries.toFloat() / progress.maxConcurrency.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        WorkerPulseGrid(progress.runningTries, progress.maxConcurrency)
                        LoadChart(load)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(onClick = onCancel) { Text(stringResource(Res.string.create_cancel_calculation)) }
            },
        )
    }
}

/** One cell for each parallel slot. A running cell pulses. The phase of the pulse differs for each cell. */
@Composable
private fun WorkerPulseGrid(running: Int, max: Int) {
    val cells = max.coerceIn(1, WORKER_CELL_LIMIT)
    // A large limit shows a share of the slots, so the grid keeps a fixed size.
    val active = if (max <= WORKER_CELL_LIMIT) running else (running.toLong() * cells / max).toInt()
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
    )
    val running = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant
    val columns = minOf(WORKER_COLUMNS, cells)
    val rows = (cells + columns - 1) / columns
    Canvas(modifier = Modifier.fillMaxWidth().height((rows * 14).dp)) {
        val cell = size.width / columns
        val side = cell * 0.7f
        for (index in 0 until cells) {
            val x = (index % columns) * cell + (cell - side) / 2
            val y = (index / columns) * cell + (cell - side) / 2
            val wave = ((phase + index.toFloat() / cells) % 1f)
            val alpha = if (index < active) 0.35f + 0.65f * (1f - kotlin.math.abs(wave * 2f - 1f)) else 1f
            drawRoundRect(
                color = if (index < active) running.copy(alpha = alpha) else idle,
                topLeft = Offset(x, y),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * 0.2f),
            )
        }
    }
}

/** A line chart of the share of parallel slots in use, from the oldest sample to the newest. */
@Composable
private fun LoadChart(samples: List<Float>) {
    val line = MaterialTheme.colorScheme.primary
    val frame = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        drawRoundRect(color = frame, style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
        if (samples.size < 2) return@Canvas
        val step = size.width / (LOAD_SAMPLES - 1)
        val offsetX = size.width - step * (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { index, value ->
            val x = offsetX + step * index
            val y = size.height - value.coerceIn(0f, 1f) * (size.height - 4.dp.toPx()) - 2.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/** The number with a dot between groups of three digits. */
private fun formatCount(value: Long): String {
    val digits = value.toString()
    return digits.reversed().chunked(3).joinToString(".").reversed()
}
