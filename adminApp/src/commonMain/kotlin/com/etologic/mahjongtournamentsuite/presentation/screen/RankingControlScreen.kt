package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsSwitchRow
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeSelector
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.ema_logo_on_screen
import mahjongtournamentsuite.adminapp.generated.resources.on_screen_badge
import mahjongtournamentsuite.adminapp.generated.resources.open_projected_ranking
import mahjongtournamentsuite.adminapp.generated.resources.organizer_logo_on_screen
import mahjongtournamentsuite.adminapp.generated.resources.paging_pause
import mahjongtournamentsuite.adminapp.generated.resources.paging_start
import mahjongtournamentsuite.adminapp.generated.resources.podium_revealed
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_display
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_hidden_message
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_lists
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_next_in
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_not_open_message
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_not_open_title
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_on_screen
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_paused
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_playback
import mahjongtournamentsuite.adminapp.generated.resources.ranking_control_show_hidden
import mahjongtournamentsuite.adminapp.generated.resources.ranking_rows_per_screen
import mahjongtournamentsuite.adminapp.generated.resources.ranking_seconds_per_page
import mahjongtournamentsuite.adminapp.generated.resources.reveal_first_place
import mahjongtournamentsuite.adminapp.generated.resources.reveal_second_place
import mahjongtournamentsuite.adminapp.generated.resources.reveal_third_place
import mahjongtournamentsuite.adminapp.generated.resources.seconds_short
import mahjongtournamentsuite.adminapp.generated.resources.show_first_3
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import com.etologic.mahjongtournamentsuite.presentation.components.MtsSegmentedButton as SegmentedButton

/**
 * Runs the projected ranking from another window or tab of the same computer (Ernesto, 2026-11-27): the paging, the
 * lists, the reveal of the podium one place at a time, the rows, the seconds, the logos and the theme. The room sees
 * only the ranking.
 */
@Composable
fun RankingControlScreen(
    tournamentId: String,
    onOpenProjection: () -> Unit,
    onBack: () -> Unit,
) {
    val controls by RankingDisplayLink.control.collectAsState()
    val statuses by RankingDisplayLink.status.collectAsState()
    val control = controls[tournamentId] ?: RankingDisplayControl()
    val status = statuses[tournamentId]
    fun change(update: (RankingDisplayControl) -> RankingDisplayControl) = RankingDisplayLink.updateControl(tournamentId, update)

    // A projection that is already open tells this screen what it shows.
    DisposableEffect(tournamentId) {
        RankingDisplayLink.hello(tournamentId)
        onDispose { }
    }

    AppScaffold(
        title = stringResource(Res.string.ranking_control),
        onBack = onBack,
        actions = {
            AppTopBarButton(
                text = stringResource(Res.string.open_projected_ranking),
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = onOpenProjection,
            )
        },
    ) {
        ScreenColumn(
            maxWidth = 760.dp,
            scrollable = true,
            contentPadding = PaddingValues(MtsTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        ) {
            if (status == null) {
                SectionCard(
                    modifier = Modifier.testTag("ranking_control_not_open"),
                    title = stringResource(Res.string.ranking_control_not_open_title),
                ) {
                    Text(stringResource(Res.string.ranking_control_not_open_message))
                    FocusedButton(onClick = onOpenProjection) {
                        ButtonText(stringResource(Res.string.open_projected_ranking))
                    }
                }
            } else {
                PlaybackCard(control, status, ::change)
                if (status.resultsHidden) HiddenResultsCard(control, ::change)
                ListsCard(control, status, ::change)
            }
            DisplayCard(control, status, ::change)
        }
    }
}

@Composable
private fun PlaybackCard(
    control: RankingDisplayControl,
    status: RankingDisplayStatus,
    change: ((RankingDisplayControl) -> RankingDisplayControl) -> Unit,
) {
    val pages = status.pages
    val current = pages.getOrNull(status.pageIndex)
    SectionCard(title = stringResource(Res.string.ranking_control_playback)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            FocusedIconButton(
                onClick = { change { it.copy(playing = !it.playing) } },
                buttonModifier = Modifier.size(56.dp).testTag("ranking_control_play"),
            ) {
                Icon(
                    imageVector = if (control.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (control.playing) Res.string.paging_pause else Res.string.paging_start),
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                current?.let {
                    Text(
                        stringResource(Res.string.ranking_control_on_screen, it.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    if (control.playing) {
                        stringResource(Res.string.ranking_control_next_in, status.remainingSeconds)
                    } else {
                        stringResource(Res.string.ranking_control_paused)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                status.dataStatus?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // One button for each list, like steps: the one on screen is selected (Ernesto, 2026-11-27).
        if (pages.size > 1) {
            // All the steps as tall as the tallest, when a name takes two lines (Ernesto, 2026-11-27).
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                pages.forEachIndexed { index, page ->
                    SegmentedButton(
                        selected = index == status.pageIndex,
                        onClick = { change { it.showPage(page.key) } },
                        shape = SegmentedButtonDefaults.itemShape(index, pages.size),
                        modifier = Modifier.fillMaxHeight().testTag("ranking_control_page_${page.key}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        icon = {},
                        label = {
                            Text(page.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenResultsCard(
    control: RankingDisplayControl,
    change: ((RankingDisplayControl) -> RankingDisplayControl) -> Unit,
) {
    Surface(
        color = MtsTheme.colors.info,
        contentColor = MtsTheme.colors.onInfo,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(MtsTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Text(stringResource(Res.string.ranking_control_hidden_message))
            }
            MtsSwitchRow(
                checked = control.showHiddenResults,
                onCheckedChange = { show -> change { it.copy(showHiddenResults = show) } },
                modifier = Modifier.testTag("ranking_control_show_hidden"),
            ) {
                Text(stringResource(Res.string.ranking_control_show_hidden), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ListsCard(
    control: RankingDisplayControl,
    status: RankingDisplayStatus,
    change: ((RankingDisplayControl) -> RankingDisplayControl) -> Unit,
) {
    SectionCard(title = stringResource(Res.string.ranking_control_lists)) {
        MtsCheckboxRow(
            checked = control.showsTopThree(status.podiumSecret),
            onCheckedChange = { show -> change { it.withShowTopThree(show) } },
        ) {
            Text(stringResource(Res.string.show_first_3))
        }
        // With the top three on screen there is nothing to reveal, so the lines of the reveal go (Ernesto, 2026-11-27).
        val revealLines = if (control.showsTopThree(status.podiumSecret)) emptyList() else status.pages
        revealLines.forEachIndexed { index, page ->
            // One line each, never wrapped (Ernesto, 2026-11-27).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    page.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (index == status.pageIndex) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The list that the room sees now: a mark in the middle between the name and the button (Ernesto, 2026-11-27).
                BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (index == status.pageIndex) {
                        // A "live" mark, a dot and a text, so it does not look like a button. The dot alone when the text
                        // does not fit (Ernesto, 2026-11-27).
                        val withText = maxWidth >= ON_SCREEN_TEXT_MIN_WIDTH
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("ranking_control_on_screen_${page.key}"),
                        ) {
                            Box(Modifier.size(if (withText) 8.dp else 10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            if (withText) {
                                Text(
                                    stringResource(Res.string.on_screen_badge),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                val nextReveal = control.nextReveal(page, status.podiumSecret)
                when {
                    nextReveal != null -> FocusedButton(
                        onClick = { change { it.reveal(page.key) } },
                        buttonModifier = Modifier.testTag("ranking_control_reveal_${page.key}"),
                    ) {
                        ButtonText(
                            stringResource(
                                when (nextReveal) {
                                    3 -> Res.string.reveal_third_place
                                    2 -> Res.string.reveal_second_place
                                    else -> Res.string.reveal_first_place
                                },
                            ),
                        )
                    }
                    !control.showsTopThree(status.podiumSecret) && page.podium > 0 -> Text(
                        stringResource(Res.string.podium_revealed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayCard(
    control: RankingDisplayControl,
    status: RankingDisplayStatus?,
    change: ((RankingDisplayControl) -> RankingDisplayControl) -> Unit,
) {
    val maxRows = minOf(MAX_ROWS_PER_SCROLL, maxOf(MIN_ROWS_PER_SCROLL, status?.maxRowsPerScroll ?: MAX_ROWS_PER_SCROLL))
    val rows = control.rowsPerScroll.coerceIn(MIN_ROWS_PER_SCROLL, maxRows)
    SectionCard(title = stringResource(Res.string.ranking_control_display)) {
        // Sliders with their value, not steppers (Ernesto, 2026-11-27).
        SliderSetting(
            label = stringResource(Res.string.ranking_rows_per_screen),
            value = rows,
            range = MIN_ROWS_PER_SCROLL..maxRows,
            valueText = { it.toString() },
            onValueChange = { value -> change { it.copy(rowsPerScroll = value) } },
            tag = "ranking_control_rows",
        )
        SliderSetting(
            label = stringResource(Res.string.ranking_seconds_per_page),
            value = control.intervalSeconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS),
            range = MIN_INTERVAL_SECONDS..MAX_INTERVAL_SECONDS,
            valueText = { stringResource(Res.string.seconds_short, it) },
            onValueChange = { value -> change { it.copy(intervalSeconds = value) } },
            tag = "ranking_control_seconds",
        )
        MtsSwitchRow(checked = control.showOrganizerLogo, onCheckedChange = { show -> change { it.copy(showOrganizerLogo = show) } }) {
            Text(stringResource(Res.string.organizer_logo_on_screen), modifier = Modifier.weight(1f))
        }
        MtsSwitchRow(checked = control.showEmaLogo, onCheckedChange = { show -> change { it.copy(showEmaLogo = show) } }) {
            Text(stringResource(Res.string.ema_logo_on_screen), modifier = Modifier.weight(1f))
        }
        // The theme of the projected ranking, not of this screen.
        val preference = when (control.dark) {
            null -> ThemePreference.System
            false -> ThemePreference.Light
            true -> ThemePreference.Dark
        }
        val select: (ThemePreference) -> Unit = { option ->
            change {
                it.copy(
                    dark = when (option) {
                        ThemePreference.System -> null
                        ThemePreference.Light -> false
                        ThemePreference.Dark -> true
                    },
                )
            }
        }
        CompositionLocalProvider(
            LocalThemeController provides ThemeController(
                preference = preference,
                isDarkTheme = control.dark == true,
                onTogglePreference = { select(preference.next()) },
                onSelectPreference = select,
            ),
        ) {
            ThemeSelector()
        }
    }
}

private const val MAX_INTERVAL_SECONDS = 60

private val ON_SCREEN_TEXT_MIN_WIDTH = 104.dp

/** A whole number setting: its name, and a slider with the value over its thumb and the minimum and the maximum at its ends. */
@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    range: IntRange,
    valueText: @Composable (Int) -> String,
    onValueChange: (Int) -> Unit,
    tag: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = range.last > range.first
    val valueLabel = valueText(value)
    // The chosen value over the thumb, and the minimum and the maximum at the ends (Ernesto, 2026-11-27).
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp)) {
            Text(valueText(range.first), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(range)) },
                valueRange = range.first.toFloat()..range.last.toFloat().coerceAtLeast(range.first + 1f),
                enabled = enabled,
                interactionSource = interactionSource,
                thumb = {
                    Box(contentAlignment = Alignment.Center) {
                        SliderDefaults.Thumb(interactionSource = interactionSource, enabled = enabled)
                        ValueIndicator(valueLabel)
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = MtsTheme.spacing.sm).testTag(tag),
            )
            Text(valueText(range.last), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The value over the thumb of a slider. It takes no room, so the thumb keeps its size. */
@Composable
private fun ValueIndicator(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        modifier = Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height - 14.dp.roundToPx()) }
            }
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}
