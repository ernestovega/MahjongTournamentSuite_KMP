package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SelectMenu
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.rememberSelectMenuState
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.ranking_after_round
import mahjongtournamentsuite.playerapp.generated.resources.ranking_close
import mahjongtournamentsuite.playerapp.generated.resources.ranking_now
import mahjongtournamentsuite.playerapp.generated.resources.ranking_share_title
import mahjongtournamentsuite.playerapp.generated.resources.round_n
import mahjongtournamentsuite.playerapp.generated.resources.summary_share
import mahjongtournamentsuite.playerapp.generated.resources.summary_share_error
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** The test tags of the ranking by round and of its sharing. */
internal const val RANKING_ROUND_BUTTON_TAG = "ranking-round"
internal const val RANKING_SHARE_BUTTON_TAG = "ranking-share"
internal const val RANKING_SHARE_DIALOG_TAG = "ranking-share-dialog"
internal const val RANKING_SHARE_IMAGE_TAG = "ranking-share-image"

/**
 * The button that chooses which ranking the tab shows: the one of now, or the one at the end of a past round, like it
 * was then. [rounds] are the rounds with a ranking, the first one first. Null is now.
 */
@Composable
internal fun RankingRoundButton(rounds: List<Int>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val menu = rememberSelectMenuState()
    // The newest round goes first, right under "Now": it is the one people look back to most.
    val options = listOf(SelectOption<Int>(NOW, stringResource(Res.string.ranking_now))) +
        rounds.reversed().map { SelectOption(it, stringResource(Res.string.ranking_after_round, it)) }
    Box(modifier) {
        OutlinedButton(
            onClick = menu::open,
            modifier = Modifier.height(44.dp).testTag(RANKING_ROUND_BUTTON_TAG),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 4.dp),
        ) {
            ButtonText(
                if (selected == null) stringResource(Res.string.ranking_now) else stringResource(Res.string.round_n, selected),
                maxLines = 1,
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        SelectMenu(menu, options, selected ?: NOW, { onSelect(it.takeIf { round -> round != NOW }) })
    }
}

/** The key of "Now" in the menu of the rounds. A round is never 0. */
private const val NOW = 0

/** The Share button of the ranking. On a phone only its icon, else with its text. */
@Composable
internal fun RankingShareButton(showLabel: Boolean, onClick: () -> Unit) {
    val title = stringResource(Res.string.summary_share)
    if (showLabel) {
        TextButton(onClick = onClick, modifier = Modifier.testTag(RANKING_SHARE_BUTTON_TAG)) {
            Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(MtsTheme.spacing.sm))
            ButtonText(title, maxLines = 1)
        }
    } else {
        IconButton(onClick = onClick, modifier = Modifier.testTag(RANKING_SHARE_BUTTON_TAG)) {
            Icon(Icons.Default.Share, contentDescription = title, tint = MtsTheme.colors.link)
        }
    }
}

/** One line of a shared ranking: the place, the player or team, and its numbers. */
@Immutable
internal data class SharedRankingLine(
    val position: Int,
    val name: String,
    /** The flag of a player. Null for a team. */
    val country: String?,
    /** The main number, as the list shows it: the table points, the chicken hands or the points of the best hand. */
    val main: String,
    /** The second number, the score, when the ranking has one. */
    val second: String?,
    val mine: Boolean,
    val excluded: Boolean = false,
)

/**
 * A preview of the ranking as the image that is shared, with a Share button. The image has all the lines, also the
 * ones that the dialog shows only when scrolled. [subtitle] says which ranking it is, like "After round 3".
 */
@Composable
internal fun RankingShareDialog(
    tournament: String,
    subtitle: String,
    lines: List<SharedRankingLine>,
    pastRound: Boolean,
    onDismiss: () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    var error by remember { mutableStateOf(false) }
    if (error) AppErrorDialog(UiText.of(Res.string.summary_share_error), onDismiss = { error = false })
    val title = stringResource(Res.string.summary_share)
    val share = rememberShareLayer(
        layer = layer,
        fileName = summaryFileName(tournament, subtitle),
        title = title,
        event = TelemetryEvent.RANKING_SHARED,
        onError = { error = true },
        params = mapOf(TelemetryParam.SCOPE to if (pastRound) "round" else "now"),
    )
    val scroll = rememberScrollState()
    val background = MaterialTheme.colorScheme.background
    TrackedDialog("RankingShare") {
        AlertDialog(
            // A custom width: the dialog is a preview of the image, which needs room for the names and the numbers.
            // A phone keeps 16 dp on each side.
            modifier = Modifier.appFocusGroup().padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().testTag(RANKING_SHARE_DIALOG_TAG),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.ranking_share_title)) },
            text = {
                ScrollableColumnWithScrollbar(scroll, Modifier.fillMaxWidth().heightIn(max = 520.dp), inDialog = true) {
                    // What this box draws becomes the image: the card with a margin of the background of the app.
                    Box(
                        Modifier
                            .testTag(RANKING_SHARE_IMAGE_TAG)
                            .drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            }
                            .background(background)
                            .padding(8.dp),
                    ) {
                        SharedRankingCard(tournament, subtitle, lines)
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = share) {
                        Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(MtsTheme.spacing.sm))
                        ButtonText(title)
                    }
                    FocusedTextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.ranking_close)) }
                }
            },
        )
    }
}

/** The ranking as a card of its own: the tournament and which ranking it is on top, then one line for each place. */
@Composable
private fun SharedRankingCard(tournament: String, subtitle: String, lines: List<SharedRankingLine>) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.surface, shadowElevation = 2.dp) {
        Column {
            Column(
                Modifier.fillMaxWidth().background(colors.primary).padding(horizontal = MtsTheme.spacing.lg, vertical = MtsTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
            ) {
                Text(tournament, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.titleSmall, color = colors.onPrimary.copy(alpha = 0.85f), maxLines = 1)
            }
            lines.forEachIndexed { index, line ->
                val background = when {
                    // The own line, softly tinted so its numbers stay easy to read.
                    line.mine -> colors.primary.copy(alpha = 0.14f).compositeOver(colors.surface)
                    index % 2 == 1 -> colors.surfaceContainerHigh
                    else -> colors.surface
                }
                Row(
                    Modifier.fillMaxWidth().height(32.dp).background(background).padding(horizontal = MtsTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                ) {
                    val number = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    Text("${line.position}", Modifier.width(28.dp), style = number, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    if (line.country != null) PlayerFlag(line.country, width = 20.dp)
                    Text(
                        line.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (line.mine) FontWeight.Bold else null,
                        textDecoration = if (line.excluded) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(line.main, Modifier.widthIn(min = 36.dp), style = number, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
                    line.second?.let {
                        Text(it, Modifier.widthIn(min = 52.dp), style = number, color = if (it.startsWith("-")) colors.error else colors.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1)
                    }
                }
            }
            Text(
                "Mahjong Tournament Suite",
                Modifier.fillMaxWidth().padding(horizontal = MtsTheme.spacing.md, vertical = MtsTheme.spacing.sm),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}
