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
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.close
import mahjongtournamentsuite.ui.generated.resources.draw_backup_how
import mahjongtournamentsuite.ui.generated.resources.draw_backup_intro
import mahjongtournamentsuite.ui.generated.resources.draw_backup_running
import mahjongtournamentsuite.ui.generated.resources.draw_backup_time
import mahjongtournamentsuite.ui.generated.resources.draw_backup_time_title
import mahjongtournamentsuite.ui.generated.resources.draw_backup_title
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_how
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_intro
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_button
import mahjongtournamentsuite.ui.generated.resources.draw_help_first_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_how
import mahjongtournamentsuite.ui.generated.resources.draw_help_how_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_intro
import mahjongtournamentsuite.ui.generated.resources.draw_help_random
import mahjongtournamentsuite.ui.generated.resources.draw_help_random_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_rules
import mahjongtournamentsuite.ui.generated.resources.draw_help_rules_title
import mahjongtournamentsuite.ui.generated.resources.draw_help_title
import org.jetbrains.compose.resources.stringResource

/**
 * How the pairings of a tournament are drawn: the first way (a flowchart, the rules it keeps and how random it is);
 * after it, the second way that runs when the first takes too long, with its own flowchart and its time limits; and
 * last, the draw in blocks for more rounds than fit without repeats, with its flowchart.
 */
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
                        MethodTitle(stringResource(Res.string.draw_help_first_title))
                        DrawFlowDiagram(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        HorizontalDivider()
                        HelpSection(stringResource(Res.string.draw_help_how_title), stringResource(Res.string.draw_help_how))
                        HelpSection(stringResource(Res.string.draw_help_rules_title), stringResource(Res.string.draw_help_rules))
                        HelpSection(stringResource(Res.string.draw_help_random_title), stringResource(Res.string.draw_help_random))
                        // The second way, after all of the first, so the two cases never mix (Ernesto, 22-nov).
                        HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 2.dp)
                        MethodTitle(stringResource(Res.string.draw_backup_title), Modifier.testTag("drawBackupTitle"))
                        Text(
                            stringResource(Res.string.draw_backup_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DrawSwapFlowDiagram(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        HorizontalDivider()
                        HelpSection(stringResource(Res.string.draw_help_how_title), stringResource(Res.string.draw_backup_how))
                        HelpSection(stringResource(Res.string.draw_backup_time_title), stringResource(Res.string.draw_backup_time))
                        // The third case, more rounds than fit without repeats: the blocks (Ernesto, 22-nov and 23-nov).
                        HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 2.dp)
                        MethodTitle(stringResource(Res.string.draw_blocks_title), Modifier.testTag("drawBlocksTitle"))
                        Text(
                            stringResource(Res.string.draw_blocks_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DrawBlocksFlowDiagram(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("drawBlocksDiagram"))
                        HorizontalDivider()
                        HelpSection(stringResource(Res.string.draw_help_how_title), stringResource(Res.string.draw_blocks_how))
                        Spacer(Modifier.testTag("drawExplanationEnd"))
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = onDismiss, focusRequester = closeFocusRequester) { ButtonText(stringResource(Res.string.close)) }
                }
            },
        )
    }
}

/** The name of one way to draw, above its diagram. */
@Composable
private fun MethodTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.fillMaxWidth())
}

/** The line of a progress dialog while the second way to draw runs. */
@Composable
fun DrawBackupRunningNote() {
    Text(
        stringResource(Res.string.draw_backup_running),
        style = MaterialTheme.typography.bodyMedium,
        color = MtsTheme.colors.link,
        modifier = Modifier.testTag("drawBackupRunning"),
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MtsTheme.colors.link)
        // Each line is its own paragraph. A bullet line hangs, so its text lines up under the first word.
        body.split("\n").filter { it.isNotBlank() }.forEach { line ->
            if (line.startsWith("• ")) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MtsTheme.colors.link, modifier = Modifier.width(16.dp))
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
        ButtonText(stringResource(Res.string.draw_help_button))
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
