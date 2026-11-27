package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsInputModality

private fun listenToKeys(onKey: () -> Unit): Unit = js(
    """window.addEventListener('keydown', function (e) {
        if (['Shift', 'Control', 'Alt', 'Meta', 'AltGraph'].indexOf(e.key) < 0) onKey();
    }, true)""",
)

private fun listenToPointer(onPointer: () -> Unit): Unit = js(
    """(function () {
        window.addEventListener('pointerdown', function () { onPointer(); }, true);
        window.addEventListener('pointermove', function (e) { if (e.movementX || e.movementY) onPointer(); }, true);
        window.addEventListener('wheel', function () { onPointer(); }, true);
    })()""",
)

private object BrowserInputModality {
    /** Added once for the whole page. */
    val installed: Boolean = run {
        listenToKeys { MtsInputModality.keyboard = true }
        listenToPointer { MtsInputModality.keyboard = false }
        true
    }
}

@Composable
internal actual fun InputModalityTracker() {
    BrowserInputModality.installed
}
