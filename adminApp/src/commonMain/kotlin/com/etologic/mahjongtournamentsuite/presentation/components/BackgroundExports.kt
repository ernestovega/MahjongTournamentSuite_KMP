package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsSpacing
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.background_export_close
import mahjongtournamentsuite.adminapp.generated.resources.background_export_failed
import mahjongtournamentsuite.adminapp.generated.resources.background_export_ready
import mahjongtournamentsuite.adminapp.generated.resources.background_export_running
import mahjongtournamentsuite.adminapp.generated.resources.background_export_running_hint
import mahjongtournamentsuite.adminapp.generated.resources.background_export_save
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** A file made by an export, ready to be saved where the user picks. */
class ExportFile(val fileName: String, val content: ByteArray, val mimeType: String, val dialogTitle: String)

/**
 * Runs exports that can take long (ID cards, ID list, EMA report) outside the screen that started them, so the user
 * can keep working. A card at the bottom of the app shows each export: it spins while it runs, then offers a Save
 * button. The user saves with a click, so no save dialog opens by surprise on another screen.
 */
@Stable
class BackgroundExports(private val scope: CoroutineScope) {
    sealed interface Status {
        data object Running : Status
        class Ready(val file: ExportFile) : Status
        class Failed(val message: UiText) : Status
    }

    @Stable
    class Task(val id: Long, val key: String, val label: UiText) {
        var status: Status by mutableStateOf(Status.Running)
            internal set
    }

    private var nextId = 0L
    val tasks = mutableStateListOf<Task>()

    /**
     * Starts [work] unless an export with the same [key] is still running. [label] names the file for the user, such
     * as "ID cards". Gives false when the export was already running.
     */
    fun start(key: String, label: UiText, work: suspend () -> AppResult<ExportFile>): Boolean {
        if (tasks.any { it.key == key && it.status is Status.Running }) return false
        tasks.removeAll { it.key == key }
        val task = Task(nextId++, key, label)
        tasks.add(task)
        scope.launch {
            val result = try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Unexpected(error.message))
            }
            task.status = when (result) {
                is AppResult.Success -> Status.Ready(result.value)
                is AppResult.Failure -> Status.Failed(result.error.toUiText())
            }
        }
        return true
    }

    fun isRunning(key: String): Boolean = tasks.any { it.key == key && it.status is Status.Running }

    fun dismiss(task: Task) {
        tasks.remove(task)
    }

    fun save(task: Task) {
        val ready = task.status as? Status.Ready ?: return
        saveBinaryFile(ready.file.fileName, ready.file.content, ready.file.mimeType, ready.file.dialogTitle)
        dismiss(task)
    }
}

/** The exports of the app. A screen drawn alone, as in a test, has none. */
val LocalBackgroundExports = staticCompositionLocalOf<BackgroundExports?> { null }

/** The exports of the app, or exports of this screen alone when the app gives none. */
@Composable
fun rememberBackgroundExports(): BackgroundExports {
    LocalBackgroundExports.current?.let { return it }
    val scope = rememberCoroutineScope()
    return remember(scope) { BackgroundExports(scope) }
}

/** Draws [content] with the cards of the background exports over it, at the bottom. */
@Composable
fun BackgroundExportsHost(exports: BackgroundExports, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(MtsSpacing.lg)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(MtsSpacing.sm),
        ) {
            exports.tasks.forEach { task ->
                key(task.id) {
                    BackgroundExportCard(
                        task = task,
                        onSave = { exports.save(task) },
                        onDismiss = { exports.dismiss(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundExportCard(task: BackgroundExports.Task, onSave: () -> Unit, onDismiss: () -> Unit) {
    val label = task.label.asString()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = MtsSpacing.lg, end = MtsSpacing.xs, top = MtsSpacing.sm, bottom = MtsSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MtsSpacing.md),
        ) {
            when (val status = task.status) {
                BackgroundExports.Status.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.inversePrimary,
                )
                is BackgroundExports.Status.Ready -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inversePrimary,
                )
                is BackgroundExports.Status.Failed -> Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.errorContainer,
                )
            }
            Column(Modifier.weight(1f).padding(vertical = MtsSpacing.xs)) {
                when (val status = task.status) {
                    BackgroundExports.Status.Running -> {
                        Text(stringResource(Res.string.background_export_running, label), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(Res.string.background_export_running_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is BackgroundExports.Status.Ready -> Text(
                        stringResource(Res.string.background_export_ready, label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is BackgroundExports.Status.Failed -> {
                        Text(stringResource(Res.string.background_export_failed, label), style = MaterialTheme.typography.bodyMedium)
                        Text(status.message.asString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (task.status is BackgroundExports.Status.Ready) {
                TextButton(onClick = onSave) {
                    Text(stringResource(Res.string.background_export_save), color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
            if (task.status !is BackgroundExports.Status.Running) {
                DebouncedIconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.background_export_close))
                }
            }
        }
    }
}
