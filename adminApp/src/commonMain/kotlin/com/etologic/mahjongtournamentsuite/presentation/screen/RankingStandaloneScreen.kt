package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.BestHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.ChickenHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppLoading
import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.DialogBackHandler
import com.etologic.mahjongtournamentsuite.presentation.components.EmaLogo
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.OrganizationLogo
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerPhoto
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.organizationColor
import com.etologic.mahjongtournamentsuite.presentation.components.rememberSubstitutionLabel
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingSnapshot
import com.etologic.mahjongtournamentsuite.presentation.sync.TournamentListPoll
import com.etologic.mahjongtournamentsuite.presentation.sync.waitOrUntilHidden
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsBrandTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import com.etologic.mahjongtournamentsuite.presentation.theme.contentDescription
import com.etologic.mahjongtournamentsuite.presentation.theme.icon
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberScreenThemeController
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.dataStatus
import com.etologic.mahjongtournamentsuite.presentation.util.rememberTemplate
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.best_hands
import mahjongtournamentsuite.adminapp.generated.resources.chicken_hands
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.hand_score
import mahjongtournamentsuite.adminapp.generated.resources.next_ranking_list
import mahjongtournamentsuite.adminapp.generated.resources.no_country
import mahjongtournamentsuite.adminapp.generated.resources.no_ranking_yet
import mahjongtournamentsuite.adminapp.generated.resources.paging_pause
import mahjongtournamentsuite.adminapp.generated.resources.paging_start
import mahjongtournamentsuite.adminapp.generated.resources.player
import mahjongtournamentsuite.adminapp.generated.resources.player_n
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.points
import mahjongtournamentsuite.adminapp.generated.resources.previous_ranking_list
import mahjongtournamentsuite.adminapp.generated.resources.ranking_hidden_from_public_message
import mahjongtournamentsuite.adminapp.generated.resources.ranking_hidden_from_public_title
import mahjongtournamentsuite.adminapp.generated.resources.reveal_first_place
import mahjongtournamentsuite.adminapp.generated.resources.reveal_second_place
import mahjongtournamentsuite.adminapp.generated.resources.reveal_third_place
import mahjongtournamentsuite.adminapp.generated.resources.rows_count
import mahjongtournamentsuite.adminapp.generated.resources.score
import mahjongtournamentsuite.adminapp.generated.resources.seconds_short
import mahjongtournamentsuite.adminapp.generated.resources.settings
import mahjongtournamentsuite.adminapp.generated.resources.show_fewer_rows
import mahjongtournamentsuite.adminapp.generated.resources.show_first_3
import mahjongtournamentsuite.adminapp.generated.resources.show_more_rows
import mahjongtournamentsuite.adminapp.generated.resources.show_ranking_here_anyway
import mahjongtournamentsuite.adminapp.generated.resources.team
import mahjongtournamentsuite.adminapp.generated.resources.team_n
import mahjongtournamentsuite.adminapp.generated.resources.teams
import mahjongtournamentsuite.adminapp.generated.resources.the_ranking_shows_when_the_first
import mahjongtournamentsuite.adminapp.generated.resources.use_a_longer_page_interval
import mahjongtournamentsuite.adminapp.generated.resources.use_a_shorter_page_interval
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun RankingStandaloneScreen(
    tournamentId: String,
    /** The clock of the "loaded at" label. The screenshot tests fix it. */
    clock: Clock = Clock.System,
    /**
     * True: the room sees the ranking with no buttons, and "Ranking control" in another window or tab runs it
     * ([RankingDisplayLink]). False: the buttons are on the ranking itself, as on Android and iOS.
     */
    separateControls: Boolean = rankingControlsInSeparateScreen,
) {
    val themeController = rememberScreenThemeController()
    val linked by RankingDisplayLink.control.collectAsState()
    val linkedDark = if (separateControls) linked[tournamentId]?.dark else null

    MtsTheme(useDarkTheme = linkedDark ?: themeController.isDarkTheme) {
        RankingStandaloneContent(
            tournamentId = tournamentId,
            clock = clock,
            separateControls = separateControls,
            themePreference = themeController.preference,
            onToggleTheme = themeController.onTogglePreference,
        )
    }
}

@Composable
private fun RankingStandaloneContent(
    tournamentId: String,
    clock: Clock,
    separateControls: Boolean,
    themePreference: ThemePreference,
    onToggleTheme: () -> Unit,
) {
    val presenter = koinInject<RankingPresenter>()
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<RankingSnapshot?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var loadedAt by remember { mutableStateOf<Instant?>(null) }
    var loading by remember { mutableStateOf(false) }
    // What runs the ranking: the control screen in another window ([RankingDisplayLink]), or the buttons of this
    // screen, which start paused.
    val linked by RankingDisplayLink.control.collectAsState()
    var localControl by remember { mutableStateOf(RankingDisplayControl(playing = false)) }
    val control = if (separateControls) linked[tournamentId] ?: RankingDisplayControl() else localControl
    fun change(update: (RankingDisplayControl) -> RankingDisplayControl) {
        if (separateControls) RankingDisplayLink.updateControl(tournamentId, update) else localControl = update(localControl)
    }
    var maxRowsPerScreen by remember { mutableIntStateOf(MAX_ROWS_PER_SCROLL) }
    val rowsPerScroll = control.rowsPerScroll.coerceIn(MIN_ROWS_PER_SCROLL, maxOf(MIN_ROWS_PER_SCROLL, maxRowsPerScreen))
    val intervalSeconds = control.intervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS)
    val showTopThree = control.showTopThree
    // The prize giving: how many of the hidden top three each page shows, from the third up (Ernesto, 2026-11-27).
    val revealedByPage = control.revealed
    // While the public cannot see the results, the room sees them only after the organizer allows it.
    val showHiddenResults = control.showHiddenResults
    val playing = control.playing
    val showOrganizerLogo = control.showOrganizerLogo
    val showEmaLogo = control.showEmaLogo
    var pageIndex by remember { mutableIntStateOf(0) }
    var remainingSeconds by remember { mutableIntStateOf(intervalSeconds) }
    val listState = rememberLazyListState()

    fun refresh(force: Boolean = false) = scope.launch {
        loading = true
        error = null
        when (val result = presenter.load(tournamentId, force)) {
            is AppResult.Success -> {
                snapshot = result.value
                loadedAt = clock.now()
                pageIndex = 0
                remainingSeconds = intervalSeconds
            }
            is AppResult.Failure -> error = result.error.toUiText()
        }
        loading = false
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tournamentId) {
        refresh().join()
        // The ranking stays on a screen of the venue for hours, so it checks for new hands while it is open, quietly:
        // the page that shows stays. [TournamentListPoll.rankingDelay] sets how often. A hidden tab does not check.
        val isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
        while (true) {
            waitOrUntilHidden(TournamentListPoll.rankingDelay(snapshot?.tournament), isStarted)
            isStarted.first { it }
            val result = presenter.load(tournamentId, checkChanges = true)
            if (result is AppResult.Success) {
                if (result.value != snapshot) snapshot = result.value
                // The label tells how current the data is, so it moves on with every good check.
                loadedAt = clock.now()
            }
        }
    }
    val labels = rememberRankingLabels()
    val pages = remember(snapshot, showTopThree, revealedByPage, labels) {
        snapshot?.toPages(showTopThree, revealedByPage, labels).orEmpty()
    }
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) pageIndex = pageIndex.coerceIn(0, pages.lastIndex)
    }
    LaunchedEffect(pageIndex, pages.getOrNull(pageIndex)?.key) {
        if (pages.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > 0 }
            withFrameNanos { }
            listState.scrollToItem(0)
        }
    }

    val currentPage = pages.getOrNull(pageIndex)

    // The control screen asked for a list, or revealed a place of one: the room goes to that list, at its top.
    LaunchedEffect(control.pageRequest) {
        if (control.pageRequest == 0) return@LaunchedEffect
        val index = pages.indexOfFirst { it.key == control.pageKey }
        if (index < 0) return@LaunchedEffect
        if (index != pageIndex) {
            pageIndex = index
        } else if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(0)
        }
        remainingSeconds = intervalSeconds
    }

    // The control screen shows what the room sees.
    if (separateControls) {
        val status = RankingDisplayStatus(
            pages = pages.map { page -> RankingDisplayPage(page.key, page.title, podium = minOf(3, page.rows.size)) },
            pageIndex = pageIndex,
            remainingSeconds = remainingSeconds,
            maxRowsPerScroll = maxRowsPerScreen,
            resultsHidden = snapshot?.tournament?.resultsHiddenFromRound != null,
            loading = loading && snapshot == null,
        )
        LaunchedEffect(status) { RankingDisplayLink.setStatus(tournamentId, status) }
        DisposableEffect(tournamentId) {
            RankingDisplayLink.hello(tournamentId)
            onDispose { RankingDisplayLink.closeStatus(tournamentId) }
        }
    }

    LaunchedEffect(playing, pageIndex, intervalSeconds, rowsPerScroll, pages.size) {
        if (!playing || pages.isEmpty()) return@LaunchedEffect
        while (true) {
            remainingSeconds = intervalSeconds
            while (remainingSeconds > 0) {
                delay(1_000)
                remainingSeconds -= 1
            }
            val currentPage = pages.getOrNull(pageIndex) ?: break
            val lastVisibleRow = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            val nextRow = listState.firstVisibleItemIndex + rowsPerScroll
            if (lastVisibleRow < currentPage.rows.lastIndex && nextRow < currentPage.rows.size) {
                listState.animateScrollToItem(nextRow)
            } else {
                pageIndex = (pageIndex + 1) % pages.size
            }
        }
    }

    // The room sees the color of the main organizer of the tournament, not the green of the suite.
    MtsBrandTheme(snapshot?.tournament?.let { organizationColor(it.primaryColor) }) {
        Surface(modifier = Modifier.fillMaxSize().appFocusGroup()) {
            // Edge to edge: the background fills the whole window, the content stays clear of the system bars and the cutout.
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(modifier = Modifier.fillMaxSize().appFocusGroup()) {
                    val compact = LocalCompactLayout.current
                    // On a phone the switches under the logos would cover the first rows: while paused, the table
                    // goes down to leave them room.
                    // On a phone the title goes between the logos (Ernesto, 2026-11-27), in the band they take: the
                    // logos and, while paused, their switches. The table starts under that band.
                    val hasLogos = snapshot?.tournament != null
                    val compactBand = if (playing || separateControls) COMPACT_LOGO_SIZE else COMPACT_LOGO_BAND_WITH_SWITCHES
                    val compactTop = if (hasLogos) COMPACT_LOGO_TOP + compactBand + 8.dp else 24.dp
                    val showsPage = currentPage != null && !(snapshot?.tournament?.resultsHiddenFromRound != null && !showHiddenResults)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (compact) PaddingValues(start = 8.dp, top = compactTop, end = 8.dp, bottom = 132.dp) else PaddingValues(MtsTheme.spacing.lg))
                            .appFocusGroup(),
                    ) {
                        error?.let {
                            AppErrorDialog(
                                message = it,
                                onDismiss = { error = null },
                            )
                        }
                        when {
                            loading && snapshot == null -> AppLoading(Modifier.weight(1f), containerColor = Color.Transparent)
                            currentPage == null -> AppEmptyState(
                                icon = Icons.Default.Leaderboard,
                                title = stringResource(Res.string.no_ranking_yet),
                                message = stringResource(Res.string.the_ranking_shows_when_the_first),
                                modifier = Modifier.weight(1f),
                            )
                            snapshot?.tournament?.resultsHiddenFromRound != null && !showHiddenResults -> Column(
                                modifier = Modifier.weight(1f).fillMaxWidth().testTag("ranking_hidden_results"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                AppEmptyState(
                                    icon = Icons.Default.VisibilityOff,
                                    title = stringResource(Res.string.ranking_hidden_from_public_title),
                                    message = stringResource(Res.string.ranking_hidden_from_public_message),
                                )
                                if (!separateControls) {
                                    FocusedButton(onClick = { change { it.copy(showHiddenResults = true) } }) {
                                        ButtonText(stringResource(Res.string.show_ranking_here_anyway))
                                    }
                                }
                            }
                            else -> RankingPage(
                                page = currentPage,
                                showTitle = !(compact && hasLogos),
                                listState = listState,
                                rowsPerScroll = rowsPerScroll,
                                onMaxRowsChanged = { maxRowsPerScreen = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (compact && hasLogos && showsPage && currentPage != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = COMPACT_LOGO_TOP, start = COMPACT_TITLE_SIDE, end = COMPACT_TITLE_SIDE)
                                .height(compactBand)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            RankingTitle(currentPage.title, compact = true)
                        }
                    }
                    snapshot?.let { held ->
                        // Which data this screen shows. The screen stays still, so it also says when it loaded.
                        Text(
                            text = dataStatus(held.dataRevision, loadedAt, loaded = true).asString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        )
                    }
                    snapshot?.tournament?.let { tournament ->
                        // The logos of the main organizer (when it has one), over the controls, and of the EMA.
                        val logoSize = if (compact) COMPACT_LOGO_SIZE else LOGO_SIZE
                        tournament.mainOrganizerLogo()?.let { logoUrl ->
                            ProjectionLogo(
                                // With the control screen the room never sees the switch: it is there.
                                running = playing || separateControls,
                                showWhileRunning = showOrganizerLogo,
                                onShowWhileRunningChange = { show -> change { it.copy(showOrganizerLogo = show) } },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = if (compact) 8.dp else 24.dp, top = if (compact) COMPACT_LOGO_TOP else 40.dp),
                            ) {
                                OrganizationLogo(
                                    name = tournament.mainOrganizerName(),
                                    logoUrl = logoUrl,
                                    primaryColor = tournament.primaryColor,
                                    size = logoSize,
                                )
                            }
                        }
                        ProjectionLogo(
                            running = playing || separateControls,
                            showWhileRunning = showEmaLogo,
                            onShowWhileRunningChange = { show -> change { it.copy(showEmaLogo = show) } },
                            // On a wide screen at the bottom left, under the controls, so the table keeps its width.
                            modifier = if (compact) {
                                Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = COMPACT_LOGO_TOP)
                            } else {
                                Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 24.dp)
                            },
                        ) {
                            EmaLogo(size = logoSize)
                        }
                    }
                    if (playing) {
                        // The seconds to the next page, in the bottom corner: the top corners hold the logos.
                        Text(
                            text = remainingSeconds.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold).inTextFont(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        )
                    }
                    if (!separateControls) {
                        RankingControls(
                            playing = playing,
                            themePreference = themePreference,
                            rowsPerScroll = rowsPerScroll,
                            maxVisibleRows = maxRowsPerScreen,
                            intervalSeconds = intervalSeconds,
                            pageIndex = pageIndex,
                            pageCount = pages.size,
                            onTogglePlaying = { change { it.copy(playing = !it.playing) } },
                            onRowsChanged = { rows -> change { it.copy(rowsPerScroll = rows.coerceIn(MIN_ROWS_PER_SCROLL, maxRowsPerScreen)) } },
                            onIntervalChanged = { seconds ->
                                change { it.copy(intervalSeconds = seconds) }
                                remainingSeconds = seconds
                            },
                            onPrevious = {
                                if (pages.isNotEmpty()) pageIndex = (pageIndex - 1 + pages.size) % pages.size
                            },
                            onNext = {
                                if (pages.isNotEmpty()) pageIndex = (pageIndex + 1) % pages.size
                            },
                            onToggleTheme = onToggleTheme,
                            showTopThree = showTopThree,
                            onShowTopThreeChanged = { show -> change { it.withShowTopThree(show) } },
                            // The place that the next press shows: the lowest hidden one of the page, 3, then 2, then 1.
                            nextReveal = currentPage?.rows?.take(3)?.indexOfLast { it.isMasked }?.takeIf { it >= 0 }?.plus(1),
                            onReveal = {
                                // The room watches the podium: the paging stops and the page goes back to its top.
                                currentPage?.let { page -> change { it.reveal(page.key) } }
                            },
                            modifier = Modifier
                                .align(if (compact) Alignment.BottomCenter else Alignment.CenterStart)
                                .padding(if (compact) 8.dp else 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RankingControls(
    playing: Boolean,
    themePreference: ThemePreference,
    rowsPerScroll: Int,
    maxVisibleRows: Int,
    intervalSeconds: Int,
    pageIndex: Int,
    pageCount: Int,
    onTogglePlaying: () -> Unit,
    onRowsChanged: (Int) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleTheme: () -> Unit,
    showTopThree: Boolean,
    onShowTopThreeChanged: (Boolean) -> Unit,
    /** The place (3, 2 or 1) that [onReveal] shows on this page, or null when its top three shows. */
    nextReveal: Int?,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocusRequester = remember { FocusRequester() }
    val previousFocusRequester = remember { FocusRequester() }
    val nextFocusRequester = remember { FocusRequester() }
    val fewerRowsFocusRequester = remember { FocusRequester() }
    val moreRowsFocusRequester = remember { FocusRequester() }
    val shorterIntervalFocusRequester = remember { FocusRequester() }
    val longerIntervalFocusRequester = remember { FocusRequester() }
    val themeFocusRequester = remember { FocusRequester() }
    val revealFocusRequester = remember { FocusRequester() }

    val compact = LocalCompactLayout.current
    val settingsFocusRequester = remember { FocusRequester() }
    var settingsOpen by remember { mutableStateOf(false) }
    val activeFocusRequesters = buildList {
        add(playFocusRequester)
        if (nextReveal != null) add(revealFocusRequester)
        if (pageCount > 1) {
            add(previousFocusRequester)
            add(nextFocusRequester)
        }
        // On a phone the other settings are in a bottom sheet, so the bar has only its own buttons.
        if (compact) {
            add(settingsFocusRequester)
            return@buildList
        }
        if (rowsPerScroll > MIN_ROWS_PER_SCROLL) add(fewerRowsFocusRequester)
        if (rowsPerScroll < minOf(MAX_ROWS_PER_SCROLL, maxVisibleRows)) add(moreRowsFocusRequester)
        if (intervalSeconds > MIN_INTERVAL_SECONDS) add(shorterIntervalFocusRequester)
        add(longerIntervalFocusRequester)
        add(themeFocusRequester)
    }

    fun focusLoopFor(requester: FocusRequester): Modifier {
        val index = activeFocusRequesters.indexOf(requester).coerceAtLeast(0)
        return Modifier.focusLoop(
            previous = activeFocusRequesters[(index - 1 + activeFocusRequesters.size) % activeFocusRequesters.size],
            next = activeFocusRequesters[(index + 1) % activeFocusRequesters.size],
        )
    }

    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    val playButton: @Composable () -> Unit = {
        FocusedIconButton(
            onClick = onTogglePlaying,
            focusRequester = playFocusRequester,
            buttonModifier = Modifier
                .size(if (LocalCompactLayout.current) 44.dp else 56.dp)
                .then(focusLoopFor(playFocusRequester)),
            showFocusHighlight = false,
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (playing) Res.string.paging_pause else Res.string.paging_start),
                modifier = Modifier.size(34.dp),
            )
        }
    }
    val topThree: @Composable () -> Unit = {
        MtsCheckboxRow(
            text = stringResource(Res.string.show_first_3),
            checked = showTopThree,
            onCheckedChange = onShowTopThreeChanged,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    val reveal: @Composable () -> Unit = {
        if (nextReveal != null) {
            FocusedButton(
                onClick = onReveal,
                focusRequester = revealFocusRequester,
                // Beside the ranking the button keeps to the width of the other controls, in two lines if needed.
                buttonModifier = focusLoopFor(revealFocusRequester)
                    .then(if (compact) Modifier else Modifier.widthIn(max = 136.dp))
                    .testTag("ranking_reveal_next"),
                contentPadding = if (compact) ButtonDefaults.ContentPadding else PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                ButtonText(
                    textAlign = TextAlign.Center,
                    text = stringResource(
                        when (nextReveal) {
                            3 -> Res.string.reveal_third_place
                            2 -> Res.string.reveal_second_place
                            else -> Res.string.reveal_first_place
                        },
                    ),
                )
            }
        }
    }
    val pager: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusedIconButton(
                onClick = onPrevious,
                enabled = pageCount > 1,
                focusRequester = previousFocusRequester,
                buttonModifier = focusLoopFor(previousFocusRequester),
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(Res.string.previous_ranking_list))
            }
            Text("${if (pageCount == 0) 0 else pageIndex + 1} / $pageCount")
            FocusedIconButton(
                onClick = onNext,
                enabled = pageCount > 1,
                focusRequester = nextFocusRequester,
                buttonModifier = focusLoopFor(nextFocusRequester),
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(Res.string.next_ranking_list))
            }
        }
    }
    val settings: @Composable () -> Unit = {
        if (compact) topThree()
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusedIconButton(
                onClick = { onRowsChanged((rowsPerScroll - 1).coerceAtLeast(MIN_ROWS_PER_SCROLL)) },
                enabled = rowsPerScroll > MIN_ROWS_PER_SCROLL,
                focusRequester = fewerRowsFocusRequester,
                buttonModifier = focusLoopFor(fewerRowsFocusRequester),
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.show_fewer_rows))
            }
            Text(pluralStringResource(Res.plurals.rows_count, rowsPerScroll, rowsPerScroll))
            FocusedIconButton(
                onClick = {
                    onRowsChanged((rowsPerScroll + 1).coerceAtMost(minOf(MAX_ROWS_PER_SCROLL, maxVisibleRows)))
                },
                enabled = rowsPerScroll < minOf(MAX_ROWS_PER_SCROLL, maxVisibleRows),
                focusRequester = moreRowsFocusRequester,
                buttonModifier = focusLoopFor(moreRowsFocusRequester),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.show_more_rows))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusedIconButton(
                onClick = { onIntervalChanged((intervalSeconds - 1).coerceAtLeast(MIN_INTERVAL_SECONDS)) },
                enabled = intervalSeconds > MIN_INTERVAL_SECONDS,
                focusRequester = shorterIntervalFocusRequester,
                buttonModifier = focusLoopFor(shorterIntervalFocusRequester),
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.use_a_shorter_page_interval))
            }
            Text(stringResource(Res.string.seconds_short, intervalSeconds))
            FocusedIconButton(
                onClick = { onIntervalChanged(intervalSeconds + 1) },
                focusRequester = longerIntervalFocusRequester,
                buttonModifier = focusLoopFor(longerIntervalFocusRequester),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.use_a_longer_page_interval))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusedIconButton(
                onClick = onToggleTheme,
                focusRequester = themeFocusRequester,
                buttonModifier = focusLoopFor(themeFocusRequester),
            ) {
                Icon(
                    imageVector = themePreference.icon,
                    contentDescription = themePreference.contentDescription,
                )
            }
        }
    }
    if (compact) {
        // A phone has no room at the side of the ranking. A small bar at the bottom keeps play and the pages
        // always at hand, and its settings button opens the other settings in a bottom sheet.
        Surface(
            modifier = modifier.alpha(if (playing) 0.4f else 1f).appFocusGroup(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                playButton()
                reveal()
                pager()
                FocusedIconButton(
                    onClick = { settingsOpen = true },
                    focusRequester = settingsFocusRequester,
                    buttonModifier = focusLoopFor(settingsFocusRequester),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(Res.string.settings))
                }
            }
        }
        if (settingsOpen) {
            TrackedDialog("RankingsSettings") {
                DialogBackHandler(onDismiss = { settingsOpen = false })
                ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).appFocusGroup(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(Res.string.settings), style = MaterialTheme.typography.titleMedium)
                        settings()
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .alpha(if (playing) 0.12f else 1f)
                .verticalScrollWithIndicators(rememberScrollState())
                .appFocusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
        ) {
            playButton()
            topThree()
            reveal()
            pager()
            settings()
        }
    }
}

/** The title of a page of the ranking: Players, Teams... */
@Composable
private fun RankingTitle(title: String, compact: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium).copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun RankingPage(
    page: RankingPage,
    listState: LazyListState,
    rowsPerScroll: Int,
    onMaxRowsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** False when the title goes elsewhere: between the logos on a phone. */
    showTitle: Boolean = true,
) {
    // A phone reads the ranking in the hand: a normal text size, the full width, and the name column takes the space left.
    val compact = LocalCompactLayout.current
    // On a phone the columns sit closer, so the name keeps the room (Ernesto, 2026-11-27).
    val spacing = if (compact) 6.dp else RankingColumnSpacing
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = if (compact) 0.dp else RankingHorizontalPadding)
                .then(if (compact) Modifier else Modifier.horizontalScrollWithIndicators(rememberScrollState())),
            contentAlignment = Alignment.TopCenter,
        ) {
            val headerHeight = 28.dp
            // The title goes with the table, so the two are centered together when the rows do not fill the screen.
            val titleHeight = when {
                !showTitle -> 0.dp
                compact -> COMPACT_RANKING_TITLE_HEIGHT
                else -> RANKING_TITLE_HEIGHT
            }
            val availableRowsHeight = (maxHeight - titleHeight - headerHeight).coerceAtLeast(0.dp)
            val rowHeight = availableRowsHeight / (rowsPerScroll + ROW_LAYOUT_SAFETY_ROWS).toFloat()
            val fontSize = (rowHeight - ROW_VERTICAL_OVERHEAD).value
                .coerceIn(MIN_RANKING_FONT_SIZE_SP, MAX_RANKING_FONT_SIZE_SP)
                .sp
            val maxRowsAtReadableFont = (
                availableRowsHeight.value / (MIN_RANKING_FONT_SIZE_SP + ROW_VERTICAL_OVERHEAD.value)
                ).toInt().minus(ROW_LAYOUT_SAFETY_ROWS)
                .coerceIn(MIN_ROWS_PER_SCROLL, MAX_ROWS_PER_SCROLL)
            LaunchedEffect(page.key, maxRowsAtReadableFont) {
                onMaxRowsChanged(maxRowsAtReadableFont)
            }
            val textStyle = if (compact) {
                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize = fontSize,
                    lineHeight = (fontSize.value + 2f).sp,
                    fontWeight = FontWeight.Bold,
                ).inTextFont()
            }
            // The photo is as tall as a line of text, so it does not change the rows that fit on a page.
            val photoSize = with(androidx.compose.ui.platform.LocalDensity.current) { textStyle.lineHeight.toDp() }
            val headerStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            // The team in a smaller text, up to two lines in the height of one line (Ernesto, 2026-11-27).
            val teamStyle = textStyle.copy(
                fontSize = (textStyle.lineHeight.value * TEAM_TEXT_SCALE).sp,
                lineHeight = (textStyle.lineHeight.value / 2f).sp,
            )
            val measuredWidths = rememberRankingColumnWidths(page, textStyle, headerStyle, teamStyle)
            val tableWidth = if (compact) maxWidth else RankingTableWidth
            val widths = measuredWidths.fitNameTo(tableWidth, spacing, if (page.showPhoto) photoSize else null, compact)
            // When every row fits, the room left goes half between the rows and half above and below, the same on
            // both sides, so a short page fills the screen and stays centered (Ernesto, 2026-11-27).
            val fill = rankingFill(page.rows.size, photoSize + ROW_BASE_VERTICAL_PADDING * 2, availableRowsHeight)

            Column(
                modifier = Modifier.width(tableWidth).fillMaxHeight().padding(vertical = fill.margin),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showTitle) {
                    Box(modifier = Modifier.height(titleHeight), contentAlignment = Alignment.TopCenter) {
                        RankingTitle(page.title, compact)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(
                        spacing,
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    // The flag goes right after the position (Ernesto, 2026-11-27), then the photo.
                    RankingCell("#", widths.position, TextAlign.Center, headerStyle)
                    if (page.showCountry) RankingCell(if (compact) "" else stringResource(Res.string.country), widths.country, TextAlign.Center, headerStyle)
                    if (page.showPhoto) RankingCell("", photoSize, TextAlign.Center, headerStyle)
                    RankingCell(page.nameHeader, widths.name, TextAlign.Center, headerStyle)
                    if (page.extraHeader != null) {
                        RankingCell(page.extraHeader, widths.extra, TextAlign.Center, headerStyle)
                    }
                    if (page.showPoints) RankingCell(stringResource(Res.string.points), widths.points, TextAlign.Center, headerStyle)
                    if (page.showScore) RankingCell(stringResource(Res.string.score), widths.score, TextAlign.Center, headerStyle)
                    if (widths.showTeam && page.teamHeader != null) RankingCell(page.teamHeader, widths.team, TextAlign.Center, headerStyle)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .scrollIndicators(listState),
                ) {
                    itemsIndexed(page.rows, key = { _, row -> row.key }) { index, row ->
                        DataTableRow(
                            backgroundColor = if (index % 2 == 0) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 0.dp,
                                vertical = ROW_BASE_VERTICAL_PADDING + fill.extraPerRow / 2,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(
                                spacing,
                                Alignment.CenterHorizontally,
                            ),
                        ) {
                            RankingCell(
                                row.position.toString(),
                                widths.position,
                                TextAlign.Center,
                                textStyle,
                            )
                            if (page.showCountry) {
                                if (row.isMasked) {
                                    // A phone has room for one sign only.
                                    RankingCell(if (compact) "?" else "???", widths.country, TextAlign.Center, textStyle)
                                } else {
                                    RankingCountryCell(row.country, widths.country, textStyle)
                                }
                            }
                            if (page.showPhoto) {
                                // A hidden top three shows the empty photo, so the photo does not tell the name.
                                PlayerPhoto(if (row.isMasked) null else row.photoUrl, row.name, size = photoSize)
                            }
                            RankingCell(
                                text = if (row.isMasked) "???" else row.name,
                                width = widths.name,
                                textAlign = TextAlign.Start,
                                style = textStyle,
                                strikethrough = row.isExcluded,
                            )
                            if (page.extraHeader != null) {
                                RankingCell(
                                    if (row.isMasked) "???" else row.extra.orEmpty(),
                                    widths.extra,
                                    TextAlign.Center,
                                    textStyle,
                                )
                            }
                            if (page.showPoints) {
                                RankingCell(
                                    if (row.isMasked) "???" else formatPoints(row.points),
                                    widths.points,
                                    TextAlign.Center,
                                    textStyle,
                                )
                            }
                            if (page.showScore) {
                                RankingCell(
                                    if (row.isMasked) "???" else row.score.toString(),
                                    widths.score,
                                    TextAlign.Center,
                                    textStyle,
                                )
                            }
                            if (widths.showTeam) {
                                if (row.isMasked) {
                                    RankingCell("???", widths.team, TextAlign.Center, textStyle)
                                } else {
                                    RankingCell(row.team.orEmpty(), widths.team, TextAlign.Center, teamStyle, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingCell(
    text: String,
    width: Dp,
    textAlign: TextAlign,
    style: TextStyle,
    strikethrough: Boolean = false,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = if (strikethrough) style.copy(textDecoration = TextDecoration.LineThrough) else style,
    )
}

@Composable
private fun RankingCountryCell(
    countryCode: String,
    width: Dp,
    textStyle: TextStyle,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val flagWidth = with(density) { rankingFlagWidth(textStyle.fontSize.toDp()) }
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.Center,
    ) {
        CountryFlag(
            code = countryCode,
            width = flagWidth,
            contentDescription = countryCode.ifBlank { stringResource(Res.string.no_country) },
        )
    }
}

/** The width of a flag for a text of [fontSize]. */
private fun rankingFlagWidth(fontSize: Dp): Dp = (fontSize * 1.25f).coerceIn(24.dp, 64.dp)

/**
 * Gives the name column, and on a wide screen also the team, the width that the other columns leave free in [total].
 * The flag and the points keep only the room they need (Ernesto, 2026-11-27).
 */
private fun RankingColumnWidths.fitNameTo(total: Dp, spacing: Dp, photo: Dp?, compact: Boolean): RankingColumnWidths {
    // On a phone the flag alone tells the country, so the column loses the width of its title. The team does not fit
    // (the player app shows it) and the extra column is cut short, so the name keeps most of the width.
    val fitted = if (compact) {
        copy(
            country = if (showCountry) 28.dp else country,
            extra = extra.coerceAtMost(72.dp),
            showTeam = false,
        )
    } else {
        this
    }
    val fixed = with(fitted) {
        listOfNotNull(
            position,
            photo,
            country.takeIf { showCountry },
            extra.takeIf { showExtra },
            points.takeIf { showPoints },
            score.takeIf { showScore },
        )
    }
    val columns = fixed.size + 1 + (if (fitted.showTeam) 1 else 0)
    val used = fixed.fold(0.dp) { sum, width -> sum + width } + spacing * (columns - 1)
    val free = total - used
    if (compact || !fitted.showTeam) {
        val teamWidth = if (fitted.showTeam) fitted.team else 0.dp
        return fitted.copy(name = (free - teamWidth).coerceAtLeast(80.dp))
    }
    // A wide screen shares the free room: the team takes what it needs, up to a third, in two lines if it must.
    val team = fitted.team.coerceAtMost(free / 3f)
    return fitted.copy(team = team, name = (free - team).coerceAtLeast(80.dp))
}

private data class RankingColumnWidths(
    val position: Dp,
    val country: Dp,
    val name: Dp,
    val extra: Dp,
    val points: Dp,
    val score: Dp,
    val team: Dp,
    val showCountry: Boolean,
    val showExtra: Boolean,
    val showPoints: Boolean,
    val showScore: Boolean,
    val showTeam: Boolean,
)

@Composable
private fun rememberRankingColumnWidths(
    page: RankingPage,
    textStyle: TextStyle,
    headerStyle: TextStyle,
    teamStyle: TextStyle,
): RankingColumnWidths {
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val countryHeader = stringResource(Res.string.country)
    val pointsHeader = stringResource(Res.string.points)
    val scoreHeader = stringResource(Res.string.score)
    return remember(page, textStyle, headerStyle, teamStyle, countryHeader, pointsHeader, scoreHeader) {
        fun widthFor(values: List<String>, minimum: Dp = 0.dp, style: TextStyle = textStyle, margin: Dp = 14.dp): Dp {
            val widest = values.ifEmpty { listOf("") }
                .maxOf { textMeasurer.measure(it, style).size.width }
            return maxOf(minimum, with(density) { widest.toDp() + margin })
        }
        val flag = with(density) { rankingFlagWidth(textStyle.fontSize.toDp()) }

        RankingColumnWidths(
            position = widthFor(listOf("#") + page.rows.map { it.position.toString() }, 34.dp),
            // The flag and its title, no more (the title is small).
            country = maxOf(flag, widthFor(listOf(countryHeader), style = headerStyle, margin = 0.dp)) + 6.dp,
            name = widthFor(listOf(page.nameHeader) + page.rows.map { it.name }, 80.dp),
            extra = widthFor(listOfNotNull(page.extraHeader) + page.rows.mapNotNull { it.extra }, 34.dp),
            points = maxOf(
                widthFor(listOf(pointsHeader), style = headerStyle, margin = 6.dp),
                // A hidden top three shows "???" in the column.
                widthFor(page.rows.map { if (it.isMasked) "???" else formatPoints(it.points) }, margin = 6.dp),
            ),
            score = widthFor(listOf(scoreHeader) + page.rows.map { it.score.toString() }, 50.dp) + 10.dp,
            team = maxOf(
                widthFor(listOfNotNull(page.teamHeader), style = headerStyle),
                widthFor(page.rows.mapNotNull { it.team }, 50.dp, style = teamStyle),
            ),
            showCountry = page.showCountry,
            showExtra = page.extraHeader != null,
            showPoints = page.showPoints,
            showScore = page.showScore,
            showTeam = page.teamHeader != null,
        )
    }
}

private data class RankingPage(
    val key: String,
    val title: String,
    val nameHeader: String,
    val extraHeader: String? = null,
    val teamHeader: String? = null,
    val showCountry: Boolean = true,
    /** A team has no photo. */
    val showPhoto: Boolean = showCountry,
    val showPoints: Boolean = true,
    val showScore: Boolean = true,
    val rows: List<RankingRow>,
)

private data class RankingRow(
    val key: String,
    val position: Int,
    val name: String,
    val extra: String? = null,
    val team: String? = null,
    val points: Double,
    val score: Int,
    val country: String = "",
    val photoUrl: String? = null,
    val isMasked: Boolean = false,
    /** The player is excluded from the EMA ranking. The name shows with strikethrough. */
    val isExcluded: Boolean = false,
)

/** The texts of the ranking pages, resolved while the screen draws so the page builder needs no composable. */
private data class RankingLabels(
    val players: String,
    val teams: String,
    val chickenHands: String,
    val bestHands: String,
    val player: String,
    val team: String,
    val handScore: String,
    val playerN: (Any) -> String,
    val teamN: (Any) -> String,
    val substitution: (PlayerSubstitution) -> String,
)

@Composable
private fun rememberRankingLabels(): RankingLabels {
    val players = stringResource(Res.string.players)
    val teams = stringResource(Res.string.teams)
    val chickenHands = stringResource(Res.string.chicken_hands)
    val bestHands = stringResource(Res.string.best_hands)
    val player = stringResource(Res.string.player)
    val team = stringResource(Res.string.team)
    val handScore = stringResource(Res.string.hand_score)
    val playerN = rememberTemplate(Res.string.player_n)
    val teamN = rememberTemplate(Res.string.team_n)
    val substitution = rememberSubstitutionLabel()
    return remember(players, teams, chickenHands, bestHands, player, team, handScore, playerN, teamN, substitution) {
        RankingLabels(players, teams, chickenHands, bestHands, player, team, handScore, playerN, teamN, substitution)
    }
}

private fun RankingSnapshot.toPages(showTopThree: Boolean, revealed: Map<String, Int>, labels: RankingLabels): List<RankingPage> {
    val slotsById = tournamentPlayers.associateBy { it.id }
    val playersByEma = basePlayers.associateBy { it.emaId }
    val teamNamesById = tournamentTeams.associate { it.id to it.name }
    val pages = mutableListOf<RankingPage>()
    // Before the first hand every player has 0 points: no page, the screen says that the ranking comes later.
    if (!rankings.hasResults) return pages

    if (rankings.players.isNotEmpty()) {
        pages += RankingPage(
            key = "players",
            title = labels.players,
            nameHeader = labels.player,
            teamHeader = labels.team,
            rows = rankings.players.map {
                it.toRow(slotsById, playersByEma, teamNamesById, labels)
            }.maskTopThree(!showTopThree, revealed["players"] ?: 0),
        )
    }
    if (isTeams && rankings.teams.isNotEmpty()) {
        pages += RankingPage(
            key = "teams",
            title = labels.teams,
            nameHeader = labels.team,
            showCountry = false,
            rows = rankings.teams.map { it.toRow(teamNamesById, labels) }.maskTopThree(!showTopThree, revealed["teams"] ?: 0),
        )
    }
    if (tournament.countChickenHands && rankings.chickenHands.isNotEmpty()) {
        pages += RankingPage(
            key = "chicken-hands",
            title = labels.chickenHands,
            nameHeader = labels.player,
            extraHeader = labels.chickenHands,
            rows = rankings.chickenHands.map {
                it.toRow(slotsById, playersByEma, labels)
            }.maskTopThree(!showTopThree, revealed["chicken-hands"] ?: 0),
        )
    }
    if (tournament.countBestHands && rankings.bestHands.isNotEmpty()) {
        pages += RankingPage(
            key = "best-hands",
            title = labels.bestHands,
            nameHeader = labels.player,
            extraHeader = labels.handScore,
            showPoints = false,
            showScore = false,
            rows = rankings.bestHands.map {
                it.toRow(slotsById, playersByEma, labels)
            }.maskTopThree(!showTopThree, revealed["best-hands"] ?: 0),
        )
    }
    return pages
}

/** Hides the top three of a page, except the [revealed] of its places from the third up. */
private fun List<RankingRow>.maskTopThree(mask: Boolean, revealed: Int): List<RankingRow> = mapIndexed { index, row ->
    row.copy(isMasked = mask && index < 3 - revealed)
}

private fun PlayerRanking.toRow(
    slots: Map<Int, TournamentPlayer>,
    players: Map<String, Player>,
    teamNames: Map<Int, String>,
    labels: RankingLabels,
) = RankingRow(
    key = "player-$playerId",
    position = position,
    name = displayName(playerId, slots, players, labels) + (substitution?.let { " (${labels.substitution(it)})" } ?: ""),
    team = teamNames[teamId] ?: labels.teamN(teamId),
    points = points,
    score = score,
    country = displayCountry(playerId, slots, players),
    photoUrl = displayPhoto(playerId, slots, players),
    isExcluded = substitution?.struckThrough == true,
)

private fun TeamRanking.toRow(teamNames: Map<Int, String>, labels: RankingLabels) = RankingRow(
    key = "team-$teamId",
    position = position,
    name = teamNames[teamId] ?: labels.teamN(teamId),
    points = points,
    score = score,
)

private fun ChickenHandRanking.toRow(slots: Map<Int, TournamentPlayer>, players: Map<String, Player>, labels: RankingLabels) = RankingRow(
    key = "chicken-$playerId",
    position = position,
    name = displayName(playerId, slots, players, labels),
    extra = chickenHands.toString(),
    points = points,
    score = score,
    country = displayCountry(playerId, slots, players),
    photoUrl = displayPhoto(playerId, slots, players),
)

private fun BestHandRanking.toRow(slots: Map<Int, TournamentPlayer>, players: Map<String, Player>, labels: RankingLabels) = RankingRow(
    key = "best-$position-$playerId",
    position = position,
    name = displayName(playerId, slots, players, labels),
    extra = handScore.toString(),
    points = points,
    score = score,
    country = displayCountry(playerId, slots, players),
    photoUrl = displayPhoto(playerId, slots, players),
)

private fun displayName(playerId: Int, slots: Map<Int, TournamentPlayer>, players: Map<String, Player>, labels: RankingLabels): String = slots[playerId]?.assignedEmaId?.let(players::get)?.displayName
    ?.takeIf { it.isNotBlank() }
    ?: labels.playerN(playerId).uppercase()

private fun displayCountry(playerId: Int, slots: Map<Int, TournamentPlayer>, players: Map<String, Player>): String = slots[playerId]?.assignedEmaId?.let(players::get)?.country.orEmpty()

private fun displayPhoto(playerId: Int, slots: Map<Int, TournamentPlayer>, players: Map<String, Player>): String? = slots[playerId]?.assignedEmaId?.let(players::get)?.publicPhotoUrl?.takeIf { it.isNotBlank() }

private fun formatPoints(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private const val MIN_RANKING_FONT_SIZE_SP = 11f
private const val MAX_RANKING_FONT_SIZE_SP = 54f
private const val ROW_LAYOUT_SAFETY_ROWS = 1

/** The padding above and below the text of a row. */
private val ROW_BASE_VERTICAL_PADDING = 2.dp

/** The height of the title of a page over the table, with its gap. */
private val RANKING_TITLE_HEIGHT = 44.dp
private val COMPACT_RANKING_TITLE_HEIGHT = 36.dp

/** How a page whose rows all fit spreads over the screen: more room in each row, and equal margins above and below. */
private class RankingFill(val extraPerRow: Dp, val margin: Dp)

/**
 * Half the room left by [rows] rows of [rowHeight] in [available] goes into the rows, at most 60 % of a row each, and
 * the rest into two equal margins. A page that does not fit is left as it is.
 */
private fun rankingFill(rows: Int, rowHeight: Dp, available: Dp): RankingFill {
    val free = available - rowHeight * rows
    if (rows == 0 || free <= 0.dp) return RankingFill(0.dp, 0.dp)
    val extra = minOf(free / 2f / rows.toFloat(), rowHeight * 0.6f)
    return RankingFill(extra, (free - extra * rows.toFloat()) / 2f)
}
private val ROW_VERTICAL_OVERHEAD = 6.dp
private val RankingTableWidth = 1_100.dp
private val RankingHorizontalPadding = 32.dp
private val RankingColumnSpacing = 28.dp

/** The size of the team text against the height of a line: two lines of it fit in one line of the ranking. */
private const val TEAM_TEXT_SCALE = 0.45f

/** The logos of the main organizer and of the EMA in the corners of the ranking: big on a wide screen, small on a phone. */
private val LOGO_SIZE = 120.dp
private val COMPACT_LOGO_SIZE = 64.dp

/** Where the small logos start on a phone: under the line that says which data the screen shows. */
private val COMPACT_LOGO_TOP = 24.dp

/** The height of a small logo with its switch under it, while paused. */
private val COMPACT_LOGO_BAND_WITH_SWITCHES = COMPACT_LOGO_SIZE + 64.dp

/** The room the title keeps on each side for the logos and their switches. */
private val COMPACT_TITLE_SIDE = 104.dp
