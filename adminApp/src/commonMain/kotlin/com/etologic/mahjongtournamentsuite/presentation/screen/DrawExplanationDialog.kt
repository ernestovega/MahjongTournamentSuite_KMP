package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.presentation.components.DrawFlowDiagram
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_how
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_how_title
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_intro
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_random
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_random_title
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_rules
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_rules_title
import mahjongtournamentsuite.adminapp.generated.resources.draw_help_title
import org.jetbrains.compose.resources.stringResource

/** How the pairings of a tournament are drawn: a flowchart, the rules it keeps and how random it is. */
@Composable
fun DrawExplanationDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocusRequester.requestFocus() } }

    TrackedDialog("DrawExplanation") {
        AlertDialog(
            // Wider than the standard 560 dp, so the flowchart has room on a computer. A phone keeps 16 dp on each side.
            modifier = Modifier.appFocusGroup().padding(16.dp).widthIn(max = 680.dp).fillMaxWidth().testTag("drawExplanationDialog"),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.draw_help_title)) },
            text = {
                ScrollableColumnWithScrollbar(
                    state = scrollState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(Res.string.draw_help_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DrawFlowDiagram(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        HorizontalDivider()
                        HelpSection(stringResource(Res.string.draw_help_how_title), stringResource(Res.string.draw_help_how))
                        HelpSection(stringResource(Res.string.draw_help_rules_title), stringResource(Res.string.draw_help_rules))
                        HelpSection(stringResource(Res.string.draw_help_random_title), stringResource(Res.string.draw_help_random))
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = onDismiss, focusRequester = closeFocusRequester) { Text(stringResource(Res.string.close)) }
                }
            },
        )
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        // Each line is its own paragraph. A bullet line hangs, so its text lines up under the first word.
        body.split("\n").filter { it.isNotBlank() }.forEach { line ->
            if (line.startsWith("• ")) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                    Text(line.removePrefix("• "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            } else {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
