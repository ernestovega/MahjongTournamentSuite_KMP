package com.etologic.mahjongtournamentsuite.presentation.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryViewKind
import kotlin.time.TimeSource

/** The telemetry of the app. The app root provides it. Without it nothing is counted. */
val LocalTelemetry = staticCompositionLocalOf<Telemetry> { NoTelemetry }

/** The name of the screen that is open. Dialogs and tabs send it, so a report can group them by screen. */
private val LocalScreenName = compositionLocalOf<String?> { null }

/** A view that stays open longer than this counts as this long. The app may sit in the background. */
private const val MAX_VIEW_SECONDS = 1800L

/**
 * Counts the visit of a screen and measures how long it stays open.
 * The [name] is a fixed word, never an id. The navigation helper of each app calls this for every route.
 */
@Composable
fun TrackedScreen(name: String, content: @Composable () -> Unit) {
    TrackView(TelemetryViewKind.SCREEN, name, parent = null)
    CompositionLocalProvider(LocalScreenName provides name, content = content)
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
