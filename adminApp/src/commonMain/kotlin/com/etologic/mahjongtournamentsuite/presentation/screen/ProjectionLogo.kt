package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.projection_logo_show_while_running
import org.jetbrains.compose.resources.stringResource

/**
 * A logo on a screen that the venue projects (the Timer and the standalone ranking): the logo of the main organizer or
 * the logo of the EMA. While the screen is paused or stopped the logo always shows, with a switch under it that says
 * whether it stays while the screen runs. While it runs the switch hides, and so does the logo when the switch is off
 * (Ernesto, 2026-11-27).
 */
@Composable
internal fun ProjectionLogo(
    running: Boolean,
    showWhileRunning: Boolean,
    onShowWhileRunningChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    logo: @Composable () -> Unit,
) {
    if (running && !showWhileRunning) return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
    ) {
        logo()
        if (!running) {
            // The switch over its text, so it is as narrow as a small logo. A tap on the text changes it too.
            Column(
                modifier = Modifier
                    .width(PROJECTION_SWITCH_WIDTH)
                    .clip(MaterialTheme.shapes.small)
                    // Over a ranking the switch keeps its own background, so the rows under it do not show through.
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .toggleable(value = showWhileRunning, role = Role.Switch, onValueChange = onShowWhileRunningChange)
                    .padding(MtsTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Switch(checked = showWhileRunning, onCheckedChange = null)
                Text(
                    text = stringResource(Res.string.projection_logo_show_while_running),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Wide enough for the switch and two short lines of text. */
private val PROJECTION_SWITCH_WIDTH = 96.dp
