package com.etologic.mahjongtournamentsuite.presentation.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryViewKind
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** The telemetry of the app. The app root provides it. Without it nothing is counted. */
val LocalTelemetry = staticCompositionLocalOf<Telemetry> { NoTelemetry }

/** The name of the screen that is open. Dialogs and tabs send it, so a report can group them by screen. */
private val LocalScreenName = compositionLocalOf<String?> { null }

/** The load times of the screen that is open. */
private val LocalScreenLoad = compositionLocalOf<ScreenLoad?> { null }

/**
 * The load times of one open screen. The first load counts from the moment the screen opens. A later load, for
 * example after a tab change, counts from the moment the screen starts to load again.
 */
private class ScreenLoad(val name: String) {
    val openedAt = TimeSource.Monotonic.markNow()
    var loadedOnce = false
    var loadingSince: TimeSource.Monotonic.ValueTimeMark? = null
    var tab: String? = null
}

/** A load that ends and starts again within this time is one load: a screen often loads in steps. */
private const val LOAD_SETTLE_MILLIS = 100L

/** A load longer than this is not counted. The app was probably in the background, or offline. */
private const val MAX_LOAD_MILLIS = 120_000L

/** A view that stays open longer than this counts as this long. The app may sit in the background. */
private const val MAX_VIEW_SECONDS = 1800L

/**
 * Counts the visit of a screen and measures how long it stays open.
 * The [name] is a fixed word, never an id. The navigation helper of each app calls this for every route.
 */
@Composable
fun TrackedScreen(name: String, content: @Composable () -> Unit) {
    TrackView(TelemetryViewKind.SCREEN, name, parent = null)
    val load = remember(name) { ScreenLoad(name) }
    CompositionLocalProvider(LocalScreenName provides name, LocalScreenLoad provides load, content = content)
}

/**
 * Measures how long the open screen takes to show its content. The scaffold of the screen calls it with its
 * loading state. Each load sends `screen_load`, and on the web it is also kept for the benchmark (see
 * [publishScreenLoad]).
 */
@Composable
fun ReportScreenLoading(isLoading: Boolean) {
    val load = LocalScreenLoad.current ?: return
    val telemetry = LocalTelemetry.current
    LaunchedEffect(load, isLoading) {
        if (isLoading) {
            if (load.loadingSince == null) load.loadingSince = TimeSource.Monotonic.markNow()
            return@LaunchedEffect
        }
        val first = !load.loadedOnce
        val since = if (first) load.openedAt else load.loadingSince ?: return@LaunchedEffect
        val millis = since.elapsedNow().inWholeMilliseconds
        // A new load that starts soon after cancels this report, and the load goes on.
        delay(LOAD_SETTLE_MILLIS)
        load.loadedOnce = true
        load.loadingSince = null
        if (millis > MAX_LOAD_MILLIS) return@LaunchedEffect
        val kind = if (first) "open" else "tab"
        telemetry.event(
            TelemetryEvent.SCREEN_LOAD,
            buildMap {
                put(TelemetryParam.KIND, kind)
                put(TelemetryParam.SCREEN, load.name)
                load.tab?.let { put(TelemetryParam.TAB, it) }
                put(TelemetryParam.MILLISECONDS, millis)
            },
        )
        publishScreenLoad(load.name, load.tab, kind, millis)
    }
}

/** Counts every time the dialog opens and measures how long it stays open. Wrap the dialog in it. */
@Composable
fun TrackedDialog(name: String, content: @Composable () -> Unit) {
    TrackView(TelemetryViewKind.DIALOG, name, LocalScreenName.current)
    content()
}

/** Counts a tab when the user opens it. Call it only while the tab is selected. */
@Composable
fun TrackedTab(name: String) {
    TrackView(TelemetryViewKind.TAB, name, LocalScreenName.current)
    val load = LocalScreenLoad.current
    SideEffect { load?.tab = name }
}

@Composable
private fun TrackView(kind: String, name: String, parent: String?) {
    val telemetry = LocalTelemetry.current
    DisposableEffect(telemetry, kind, name) {
        val start = TimeSource.Monotonic.markNow()
        when (kind) {
            TelemetryViewKind.SCREEN -> telemetry.screen(name)
            TelemetryViewKind.DIALOG -> telemetry.event(TelemetryEvent.DIALOG_VIEW, viewParams(TelemetryParam.DIALOG, name, parent))
            else -> telemetry.event(TelemetryEvent.TAB_VIEW, viewParams(TelemetryParam.TAB, name, parent))
        }
        onDispose {
            val seconds = start.elapsedNow().inWholeSeconds.coerceAtMost(MAX_VIEW_SECONDS)
            if (seconds >= 1) {
                telemetry.event(
                    TelemetryEvent.VIEW_TIME,
                    viewParams(TelemetryParam.VIEW, name, parent) + mapOf(
                        TelemetryParam.KIND to kind,
                        TelemetryParam.SECONDS to seconds,
                    ),
                )
            }
        }
    }
}

private fun viewParams(key: String, name: String, parent: String?): Map<String, Any> = buildMap {
    put(key, name)
    if (parent != null) put(TelemetryParam.SCREEN, parent)
}
