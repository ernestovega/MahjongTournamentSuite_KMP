package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.close
import mahjongtournamentsuite.ui.generated.resources.draw_help_button
import mahjongtournamentsuite.ui.generated.resources.draw_help_how
import mahjongtournamentsuite.ui.generated.resources.draw_help_how_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_intro
import mahjongtournamentsuite.ui.generated.resources.draw_help_random
import mahjongtournamentsuite.ui.generated.resources.draw_help_random_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_rules
import mahjongtournamentsuite.ui.generated.resources.draw_help_rules_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_title
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

/** Opens [DrawExplanationDialog]: shown just above the buttons of every dialog that makes a draw. */
@Composable
fun DrawExplanationButton(onClick: () -> Unit) {
    FocusedTextButton(onClick = onClick, modifier = Modifier.testTag("drawExplanationButton")) {
        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.draw_help_button))
    }
}

/** "How does the draw work?" as a top bar button or menu item: the Player app has it in its three-dots menu. */
@Composable
fun DrawExplanationTopBarButton(onClick: () -> Unit) {
    AppTopBarButton(
        text = stringResource(Res.string.draw_help_button),
        icon = Icons.Default.School,
        onClick = onClick,
    )
}
