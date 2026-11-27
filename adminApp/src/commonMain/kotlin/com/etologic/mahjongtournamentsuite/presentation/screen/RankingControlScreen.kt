package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
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
import mahjongtournamentsuite.adminapp.generated.resources.next_ranking_list
import mahjongtournamentsuite.adminapp.generated.resources.open_projected_ranking
import mahjongtournamentsuite.adminapp.generated.resources.organizer_logo_on_screen
import mahjongtournamentsuite.adminapp.generated.resources.paging_pause
import mahjongtournamentsuite.adminapp.generated.resources.paging_start
import mahjongtournamentsuite.adminapp.generated.resources.podium_revealed
import mahjongtournamentsuite.adminapp.generated.resources.previous_ranking_list
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
import mahjongtournamentsuite.adminapp.generated.resources.reveal_first_place
import mahjongtournamentsuite.adminapp.generated.resources.reveal_second_place
import mahjongtournamentsuite.adminapp.generated.resources.reveal_third_place
import mahjongtournamentsuite.adminapp.generated.resources.rows_count
import mahjongtournamentsuite.adminapp.generated.resources.seconds_short
import mahjongtournamentsuite.adminapp.generated.resources.show_fewer_rows
import mahjongtournamentsuite.adminapp.generated.resources.show_first_3
import mahjongtournamentsuite.adminapp.generated.resources.show_more_rows
import mahjongtournamentsuite.adminapp.generated.resources.show_on_screen
import mahjongtournamentsuite.adminapp.generated.resources.use_a_longer_page_interval
import mahjongtournamentsuite.adminapp.generated.resources.use_a_shorter_page_interval
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
                        stringResource(Res.string.ranking_control_on_screen, "${it.title} (${status.pageIndex + 1}/${pages.size})"),
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
            }
            FocusedIconButton(
                onClick = { pages.getOrNull((status.pageIndex - 1 + pages.size) % pages.size.coerceAtLeast(1))?.let { page -> change { it.showPage(page.key) } } },
                enabled = pages.size > 1,
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(Res.string.previous_ranking_list))
            }
            FocusedIconButton(
                onClick = { pages.getOrNull((status.pageIndex + 1) % pages.size.coerceAtLeast(1))?.let { page -> change { it.showPage(page.key) } } },
                enabled = pages.size > 1,
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(Res.string.next_ranking_list))
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
            checked = control.showTopThree,
            onCheckedChange = { show -> change { it.withShowTopThree(show) } },
        ) {
            Text(stringResource(Res.string.show_first_3))
        }
        status.pages.forEachIndexed { index, page ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    page.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (index == status.pageIndex) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                FocusedOutlinedButton(onClick = { change { it.showPage(page.key) } }) {
                    ButtonText(stringResource(Res.string.show_on_screen))
                }
                val nextReveal = control.nextReveal(page)
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
                    !control.showTopThree && page.podium > 0 -> Text(
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusedIconButton(onClick = { change { it.copy(rowsPerScroll = (rows - 1).coerceAtLeast(MIN_ROWS_PER_SCROLL)) } }, enabled = rows > MIN_ROWS_PER_SCROLL) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.show_fewer_rows))
                }
                Text(pluralStringResource(Res.plurals.rows_count, rows, rows))
                FocusedIconButton(onClick = { change { it.copy(rowsPerScroll = (rows + 1).coerceAtMost(maxRows)) } }, enabled = rows < maxRows) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.show_more_rows))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusedIconButton(
                    onClick = { change { it.copy(intervalSeconds = (it.intervalSeconds - 1).coerceAtLeast(MIN_INTERVAL_SECONDS)) } },
                    enabled = control.intervalSeconds > MIN_INTERVAL_SECONDS,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.use_a_shorter_page_interval))
                }
                Text(stringResource(Res.string.seconds_short, control.intervalSeconds))
                FocusedIconButton(onClick = { change { it.copy(intervalSeconds = it.intervalSeconds + 1) } }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.use_a_longer_page_interval))
                }
            }
        }
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
