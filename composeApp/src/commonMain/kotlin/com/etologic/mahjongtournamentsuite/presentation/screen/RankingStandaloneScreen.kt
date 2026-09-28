package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.BestHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.ChickenHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingSnapshot
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.rememberThemeController
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun RankingStandaloneScreen(
    tournamentId: String,
    tournamentName: String?,
) {
    val appThemeController = rememberThemeController()
    var themeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val useDarkTheme = themeOverride ?: appThemeController.isDarkTheme

    MtsTheme(useDarkTheme = useDarkTheme) {
        RankingStandaloneContent(
            tournamentId = tournamentId,
            useDarkTheme = useDarkTheme,
            onToggleTheme = { themeOverride = !useDarkTheme },
        )
    }
}

@Composable
private fun RankingStandaloneContent(
    tournamentId: String,
    useDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val presenter = koinInject<RankingPresenter>()
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<RankingSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var rowsPerScroll by remember { mutableIntStateOf(DEFAULT_ROWS_PER_SCROLL) }
    var intervalSeconds by remember { mutableIntStateOf(DEFAULT_INTERVAL_SECONDS) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var remainingSeconds by remember { mutableIntStateOf(intervalSeconds) }
    var maxRowsPerScreen by remember { mutableIntStateOf(MAX_ROWS_PER_SCROLL) }
    var playing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun refresh() = scope.launch {
        loading = true
        error = null
        when (val result = presenter.load(tournamentId)) {
            is AppResult.Success -> {
                snapshot = result.value
                pageIndex = 0
                listState.scrollToItem(0)
                remainingSeconds = intervalSeconds
            }
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        loading = false
    }

    LaunchedEffect(tournamentId) { refresh() }
    val pages = remember(snapshot) { snapshot?.toPages().orEmpty() }
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) pageIndex = pageIndex.coerceIn(0, pages.lastIndex)
    }
    LaunchedEffect(pageIndex, pages.getOrNull(pageIndex)?.key) { listState.scrollToItem(0) }

    LaunchedEffect(maxRowsPerScreen) {
        rowsPerScroll = rowsPerScroll.coerceIn(MIN_ROWS_PER_SCROLL, maxRowsPerScreen)
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

    Surface(modifier = Modifier.fillMaxSize().appFocusGroup()) {
        Box(modifier = Modifier.fillMaxSize().appFocusGroup()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp).appFocusGroup(),
            ) {
                error?.let {
                    AppErrorDialog(
                        message = it,
                        onDismiss = { error = null },
                    )
                }
                when {
                    loading && snapshot == null -> CenteredMessage(
                        message = "Loading rankings…",
                        modifier = Modifier.weight(1f),
                    )
                    pages.isEmpty() -> CenteredMessage(
                        message = "No ranking data is available.",
                        modifier = Modifier.weight(1f),
                    )
                    else -> RankingPage(
                        page = pages[pageIndex],
                        listState = listState,
                        rowsPerScroll = rowsPerScroll,
                        onMaxRowsChanged = { maxRowsPerScreen = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (playing) {
                Text(
                    text = remainingSeconds.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                )
            }
            RankingControls(
                playing = playing,
                loading = loading,
                useDarkTheme = useDarkTheme,
                rowsPerScroll = rowsPerScroll,
                maxVisibleRows = maxRowsPerScreen,
                intervalSeconds = intervalSeconds,
                pageIndex = pageIndex,
                pageCount = pages.size,
                onTogglePlaying = { playing = !playing },
                onRowsChanged = { rowsPerScroll = it.coerceIn(MIN_ROWS_PER_SCROLL, maxRowsPerScreen) },
                onIntervalChanged = { intervalSeconds = it; remainingSeconds = it },
                onPrevious = {
                    if (pages.isNotEmpty()) pageIndex = (pageIndex - 1 + pages.size) % pages.size
                },
                onNext = {
                    if (pages.isNotEmpty()) pageIndex = (pageIndex + 1) % pages.size
                },
                onRefresh = { refresh() },
                onToggleTheme = onToggleTheme,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun RankingControls(
    playing: Boolean,
    loading: Boolean,
    useDarkTheme: Boolean,
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
    onRefresh: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocusRequester = remember { FocusRequester() }
    val previousFocusRequester = remember { FocusRequester() }
    val nextFocusRequester = remember { FocusRequester() }
    val fewerRowsFocusRequester = remember { FocusRequester() }
    val moreRowsFocusRequester = remember { FocusRequester() }
    val shorterIntervalFocusRequester = remember { FocusRequester() }
    val longerIntervalFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val themeFocusRequester = remember { FocusRequester() }

    val activeFocusRequesters = buildList {
        add(playFocusRequester)
        if (pageCount > 1) {
            add(previousFocusRequester)
            add(nextFocusRequester)
        }
        if (rowsPerScroll > MIN_ROWS_PER_SCROLL) add(fewerRowsFocusRequester)
        if (rowsPerScroll < minOf(MAX_ROWS_PER_SCROLL, maxVisibleRows)) add(moreRowsFocusRequester)
        if (intervalSeconds > MIN_INTERVAL_SECONDS) add(shorterIntervalFocusRequester)
        add(longerIntervalFocusRequester)
        if (!loading) add(refreshFocusRequester)
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

    Column(
        modifier = modifier
            .alpha(if (playing) 0.12f else 1f)
            .verticalScroll(rememberScrollState())
            .appFocusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FocusedIconButton(
            onClick = onTogglePlaying,
            focusRequester = playFocusRequester,
            buttonModifier = Modifier
                .size(56.dp)
                .then(focusLoopFor(playFocusRequester)),
            showFocusHighlight = false,
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause automatic paging" else "Start automatic paging",
                modifier = Modifier.size(34.dp),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FocusedIconButton(
                onClick = onPrevious,
                enabled = pageCount > 1,
                focusRequester = previousFocusRequester,
                buttonModifier = focusLoopFor(previousFocusRequester),
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous ranking list")
            }
            Text("${if (pageCount == 0) 0 else pageIndex + 1} / $pageCount")
            FocusedIconButton(
                onClick = onNext,
                enabled = pageCount > 1,
                focusRequester = nextFocusRequester,
                buttonModifier = focusLoopFor(nextFocusRequester),
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next ranking list")
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FocusedIconButton(
                onClick = { onRowsChanged((rowsPerScroll - 1).coerceAtLeast(MIN_ROWS_PER_SCROLL)) },
                enabled = rowsPerScroll > MIN_ROWS_PER_SCROLL,
                focusRequester = fewerRowsFocusRequester,
                buttonModifier = focusLoopFor(fewerRowsFocusRequester),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Show fewer rows")
            }
            Text("$rowsPerScroll rows")
            FocusedIconButton(
                onClick = {
                    onRowsChanged((rowsPerScroll + 1).coerceAtMost(minOf(MAX_ROWS_PER_SCROLL, maxVisibleRows)))
                },
                enabled = rowsPerScroll < minOf(MAX_ROWS_PER_SCROLL, maxVisibleRows),
                focusRequester = moreRowsFocusRequester,
                buttonModifier = focusLoopFor(moreRowsFocusRequester),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Show more rows")
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FocusedIconButton(
                onClick = { onIntervalChanged((intervalSeconds - 1).coerceAtLeast(MIN_INTERVAL_SECONDS)) },
                enabled = intervalSeconds > MIN_INTERVAL_SECONDS,
                focusRequester = shorterIntervalFocusRequester,
                buttonModifier = focusLoopFor(shorterIntervalFocusRequester),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Use a shorter page interval")
            }
            Text("$intervalSeconds s")
            FocusedIconButton(
                onClick = { onIntervalChanged(intervalSeconds + 1) },
                focusRequester = longerIntervalFocusRequester,
                buttonModifier = focusLoopFor(longerIntervalFocusRequester),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Use a longer page interval")
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FocusedIconButton(
                onClick = onRefresh,
                enabled = !loading,
                focusRequester = refreshFocusRequester,
                buttonModifier = focusLoopFor(refreshFocusRequester),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh rankings")
            }
            FocusedIconButton(
                onClick = onToggleTheme,
                focusRequester = themeFocusRequester,
                buttonModifier = focusLoopFor(themeFocusRequester),
            ) {
                Icon(
                    imageVector = if (useDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (useDarkTheme) "Use light theme" else "Use dark theme",
                )
            }
        }
    }
}

@Composable
private fun RankingPage(
    page: RankingPage,
    listState: LazyListState,
    rowsPerScroll: Int,
    onMaxRowsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = RankingHorizontalPadding)
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            val headerHeight = 28.dp
            val availableRowsHeight = (maxHeight - headerHeight).coerceAtLeast(0.dp)
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
            val textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontSize = fontSize,
                lineHeight = (fontSize.value + 2f).sp,
                fontWeight = FontWeight.Bold,
            )
            val headerStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            val widths = rememberRankingColumnWidths(page, textStyle)

            Column(modifier = Modifier.width(RankingTableWidth).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        RankingColumnSpacing,
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    RankingCell("#", widths.position, TextAlign.Center, headerStyle)
                    if (page.showCountry) RankingCell("Country", widths.country, TextAlign.Center, headerStyle)
                    RankingCell(page.nameHeader, widths.name, TextAlign.Start, headerStyle)
                    if (page.extraHeader != null) {
                        RankingCell(page.extraHeader, widths.extra, TextAlign.Center, headerStyle)
                    }
                    if (page.showPoints) RankingCell("Points", widths.points, TextAlign.Center, headerStyle)
                    if (page.showScore) RankingCell("Score", widths.score, TextAlign.Center, headerStyle)
                    if (page.teamHeader != null) RankingCell(page.teamHeader, widths.team, TextAlign.Center, headerStyle)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                                vertical = 2.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(
                                RankingColumnSpacing,
                                Alignment.CenterHorizontally,
                            ),
                        ) {
                            RankingCell(
                                row.position.toString(),
                                widths.position,
                                TextAlign.Center,
                                textStyle,
                            )
                            if (page.showCountry) RankingCountryCell(row.country, widths.country, textStyle)
                            RankingCell(row.name, widths.name, TextAlign.Start, textStyle)
                            if (page.extraHeader != null) {
                                RankingCell(row.extra.orEmpty(), widths.extra, TextAlign.Center, textStyle)
                            }
                            if (page.showPoints) {
                                RankingCell(formatPoints(row.points), widths.points, TextAlign.Center, textStyle)
                            }
                            if (page.showScore) {
                                RankingCell(row.score.toString(), widths.score, TextAlign.Center, textStyle)
                            }
                            if (page.teamHeader != null) {
                                RankingCell(row.team.orEmpty(), widths.team, TextAlign.Center, textStyle)
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
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

@Composable
private fun RankingCountryCell(
    countryCode: String,
    width: Dp,
    textStyle: TextStyle,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val flagWidth = with(density) { (textStyle.fontSize.toDp() * 1.25f).coerceIn(24.dp, 64.dp) }
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.Center,
    ) {
        CountryFlag(
            code = countryCode,
            width = flagWidth,
            contentDescription = countryCode.ifBlank { "No country" },
        )
    }
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
)

@Composable
private fun rememberRankingColumnWidths(
    page: RankingPage,
    textStyle: TextStyle,
): RankingColumnWidths {
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(page, textStyle) {
        fun widthFor(values: List<String>, minimum: Dp = 0.dp): Dp {
            val widest = values.ifEmpty { listOf("") }
                .maxOf { textMeasurer.measure(it, textStyle).size.width }
            return maxOf(minimum, with(density) { widest.toDp() + 14.dp })
        }

        RankingColumnWidths(
            position = widthFor(listOf("#") + page.rows.map { it.position.toString() }, 34.dp),
            country = widthFor(listOf("Country") + page.rows.map { it.country }, 34.dp),
            name = widthFor(listOf(page.nameHeader) + page.rows.map { it.name }, 80.dp),
            extra = widthFor(listOfNotNull(page.extraHeader) + page.rows.mapNotNull { it.extra }, 34.dp),
            points = widthFor(listOf("Points") + page.rows.map { formatPoints(it.points) }, 50.dp),
            score = widthFor(listOf("Score") + page.rows.map { it.score.toString() }, 50.dp),
            team = widthFor(listOfNotNull(page.teamHeader) + page.rows.mapNotNull { it.team }, 50.dp),
            showCountry = page.showCountry,
            showExtra = page.extraHeader != null,
            showPoints = page.showPoints,
            showScore = page.showScore,
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
)

private fun RankingSnapshot.toPages(): List<RankingPage> {
    val slotsById = tournamentPlayers.associateBy { it.id }
    val playersByEma = basePlayers.associateBy { it.emaId }
    val teamNamesById = tournamentTeams.associate { it.id to it.name }
    val pages = mutableListOf<RankingPage>()

    if (rankings.players.isNotEmpty()) {
        pages += RankingPage(
            key = "players",
            title = "Players",
            nameHeader = "Player",
            teamHeader = "Team",
            rows = rankings.players.map { it.toRow(slotsById, playersByEma, teamNamesById) },
        )
    }
    if (isTeams && rankings.teams.isNotEmpty()) {
        pages += RankingPage(
            key = "teams",
            title = "Teams",
            nameHeader = "Team",
            showCountry = false,
            rows = rankings.teams.map { it.toRow(teamNamesById) },
        )
    }
    if (rankings.chickenHands.isNotEmpty()) {
        pages += RankingPage(
            key = "chicken-hands",
            title = "Chicken hands",
            nameHeader = "Player",
            extraHeader = "Chicken hands",
            rows = rankings.chickenHands.map { it.toRow(slotsById, playersByEma) },
        )
    }
    if (rankings.bestHands.isNotEmpty()) {
        pages += RankingPage(
            key = "best-hands",
            title = "Best hands",
            nameHeader = "Player",
            extraHeader = "Hand score",
            showPoints = false,
            showScore = false,
            rows = rankings.bestHands.map { it.toRow(slotsById, playersByEma) },
        )
    }
    return pages
}

private fun PlayerRanking.toRow(
    slots: Map<Int, TournamentPlayer>,
    players: Map<String, Player>,
    teamNames: Map<Int, String>,
) = RankingRow(
    key = "player-$playerId",
    position = position,
    name = displayName(playerId, slots, players),
    team = teamNames[teamId] ?: "Team $teamId",
    points = points,
    score = score,
    country = displayCountry(playerId, slots, players),
)

private fun TeamRanking.toRow(teamNames: Map<Int, String>) = RankingRow(
    key = "team-$teamId",
    position = position,
    name = teamNames[teamId] ?: "Team $teamId",
    points = points,
    score = score,
)

private fun ChickenHandRanking.toRow(slots: Map<Int, TournamentPlayer>, players: Map<String, Player>) = RankingRow(
    key = "chicken-$playerId",
    position = position,
    name = displayName(playerId, slots, players),
    extra = chickenHands.toString(),
    points = points,
    score = score,
    country = displayCountry(playerId, slots, players),
)

private fun BestHandRanking.toRow(slots: Map<Int, TournamentPlayer>, players: Map<String, Player>) = RankingRow(
    key = "best-$position-$playerId",
    position = position,
    name = displayName(playerId, slots, players),
    extra = handScore.toString(),
    points = points,
    score = score,
    country = displayCountry(playerId, slots, players),
)

private fun displayName(playerId: Int, slots: Map<Int, TournamentPlayer>, players: Map<String, Player>): String =
    (slots[playerId]?.assignedEmaId?.let(players::get)?.displayName ?: slots[playerId]?.nonMember?.displayName)
        ?.takeIf { it.isNotBlank() }
        ?: "PLAYER $playerId"

private fun displayCountry(playerId: Int, slots: Map<Int, TournamentPlayer>, players: Map<String, Player>): String {
    return slots[playerId]?.assignedEmaId?.let(players::get)?.country
        ?: slots[playerId]?.nonMember?.country.orEmpty()
}

private fun formatPoints(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private const val DEFAULT_ROWS_PER_SCROLL = 20
private const val MIN_ROWS_PER_SCROLL = 16
private const val MAX_ROWS_PER_SCROLL = 50
private const val DEFAULT_INTERVAL_SECONDS = 7
private const val MIN_INTERVAL_SECONDS = 1
private const val MIN_RANKING_FONT_SIZE_SP = 11f
private const val MAX_RANKING_FONT_SIZE_SP = 54f
private const val ROW_LAYOUT_SAFETY_ROWS = 1
private val ROW_VERTICAL_OVERHEAD = 6.dp
private val RankingTableWidth = 1_100.dp
private val RankingHorizontalPadding = 32.dp
private val RankingColumnSpacing = 28.dp
