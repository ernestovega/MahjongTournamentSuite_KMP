package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroup
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.model.ErrorReportEntry
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.StaggeredColumns
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.presenter.ErrorsPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_errors
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.errors_app_admin
import mahjongtournamentsuite.adminapp.generated.resources.errors_app_all
import mahjongtournamentsuite.adminapp.generated.resources.errors_app_player
import mahjongtournamentsuite.adminapp.generated.resources.errors_by_app
import mahjongtournamentsuite.adminapp.generated.resources.errors_by_platform
import mahjongtournamentsuite.adminapp.generated.resources.errors_col_count
import mahjongtournamentsuite.adminapp.generated.resources.errors_col_last
import mahjongtournamentsuite.adminapp.generated.resources.errors_col_problem
import mahjongtournamentsuite.adminapp.generated.resources.errors_col_where
import mahjongtournamentsuite.adminapp.generated.resources.errors_copy_stack
import mahjongtournamentsuite.adminapp.generated.resources.errors_days_ago
import mahjongtournamentsuite.adminapp.generated.resources.errors_first_seen
import mahjongtournamentsuite.adminapp.generated.resources.errors_hours_ago
import mahjongtournamentsuite.adminapp.generated.resources.errors_just_now
import mahjongtournamentsuite.adminapp.generated.resources.errors_kind_all
import mahjongtournamentsuite.adminapp.generated.resources.errors_kind_crash
import mahjongtournamentsuite.adminapp.generated.resources.errors_kind_handled
import mahjongtournamentsuite.adminapp.generated.resources.errors_kpi_crashes
import mahjongtournamentsuite.adminapp.generated.resources.errors_kpi_last
import mahjongtournamentsuite.adminapp.generated.resources.errors_kpi_occurrences
import mahjongtournamentsuite.adminapp.generated.resources.errors_kpi_problems
import mahjongtournamentsuite.adminapp.generated.resources.errors_last_days
import mahjongtournamentsuite.adminapp.generated.resources.errors_last_hours
import mahjongtournamentsuite.adminapp.generated.resources.errors_last_seen
import mahjongtournamentsuite.adminapp.generated.resources.errors_load_more_reports
import mahjongtournamentsuite.adminapp.generated.resources.errors_loading_count
import mahjongtournamentsuite.adminapp.generated.resources.errors_loading_detail
import mahjongtournamentsuite.adminapp.generated.resources.errors_minutes_ago
import mahjongtournamentsuite.adminapp.generated.resources.errors_no_reports
import mahjongtournamentsuite.adminapp.generated.resources.errors_no_stack
import mahjongtournamentsuite.adminapp.generated.resources.errors_none_logged
import mahjongtournamentsuite.adminapp.generated.resources.errors_none_match
import mahjongtournamentsuite.adminapp.generated.resources.errors_period_all
import mahjongtournamentsuite.adminapp.generated.resources.errors_platform_all
import mahjongtournamentsuite.adminapp.generated.resources.errors_platform_android
import mahjongtournamentsuite.adminapp.generated.resources.errors_platform_desktop
import mahjongtournamentsuite.adminapp.generated.resources.errors_platform_web
import mahjongtournamentsuite.adminapp.generated.resources.errors_prod_only
import mahjongtournamentsuite.adminapp.generated.resources.errors_recent_reports
import mahjongtournamentsuite.adminapp.generated.resources.errors_reload
import mahjongtournamentsuite.adminapp.generated.resources.errors_report_id
import mahjongtournamentsuite.adminapp.generated.resources.errors_report_reason
import mahjongtournamentsuite.adminapp.generated.resources.errors_search
import mahjongtournamentsuite.adminapp.generated.resources.errors_sort_frequent
import mahjongtournamentsuite.adminapp.generated.resources.errors_sort_latest
import mahjongtournamentsuite.adminapp.generated.resources.errors_stack_trace
import mahjongtournamentsuite.adminapp.generated.resources.errors_summary
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton

private const val MILLIS_PER_HOUR = 3_600_000L
private val PERIOD_HOURS = listOf(24, 24 * 7, 24 * 30)
private val APPS = listOf("admin", "player")
private val PLATFORMS = listOf("android", "web", "desktop")
private val ERROR_KINDS = listOf("crash", "handled")
private val CountColumnWidth = 72.dp
private val LastSeenColumnWidth = 140.dp

/** The error log of the apps for administrators: each problem once, with filters and a detail dialog. */
@Composable
fun ErrorsScreen(navController: NavHostController) {
    val presenter = koinInject<ErrorsPresenter>()
    var groups by remember { mutableStateOf<List<ErrorGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var periodHours by rememberSaveable { mutableStateOf<Int?>(null) }
    var app by rememberSaveable { mutableStateOf<String?>(null) }
    var platform by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var productionOnly by rememberSaveable { mutableStateOf(true) }
    var sortFrequent by rememberSaveable { mutableStateOf(false) }
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreGroupId by remember { mutableStateOf<String?>(null) }
    val reloadFocusRequester = remember { FocusRequester() }
    val rowFocusRequesters = remember(groups.map(ErrorGroup::id)) { groups.associate { it.id to FocusRequester() } }

    var reloadCount by remember { mutableStateOf(0) }

    // The period goes to the server. The screen then reads every page of that period, so no problem is left out.
    // The other filters work on the loaded problems.
    LaunchedEffect(periodHours, reloadCount) {
        loading = true
        groups = emptyList()
        val since = periodHours?.let { Clock.System.now().toEpochMilliseconds() - it * MILLIS_PER_HOUR }
        var cursor: ErrorLogCursor? = null
        do {
            when (val result = presenter.loadGroups(since, cursor)) {
                is AppResult.Success -> {
                    groups = groups + result.value.groups
                    cursor = result.value.nextCursor
                }
                is AppResult.Failure -> {
                    error = result.error.toUiText()
                    cursor = null
                }
            }
        } while (cursor != null)
        loading = false
    }

    val now = Clock.System.now().toEpochMilliseconds()
    val filters = ErrorFilters(
        query = query,
        periodHours = periodHours,
        app = app,
        platform = platform,
        kind = kind,
        productionOnly = productionOnly,
        sort = if (sortFrequent) ErrorSort.FREQUENT else ErrorSort.LATEST,
    )
    val shown = remember(groups, filters, now / 60_000) { groups.applyFilters(filters, now) }

    LaunchedEffect(openGroupId, loading) {
        if (openGroupId == null && !loading) {
            val id = restoreGroupId
            if (id != null) rowFocusRequesters[id]?.requestFocus() else reloadFocusRequester.requestFocus()
            restoreGroupId = null
        }
    }

    AppScaffold(
        title = stringResource(Res.string.app_errors),
        isLoading = loading,
        showBackgroundLogo = loading && groups.isEmpty(),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { AppErrorDialog(it, onDismiss = { error = null }) }
            ErrorKpis(shown, now)
            ErrorFiltersBar(
                query = query,
                onQueryChange = { query = it },
                periodHours = periodHours,
                onPeriod = { periodHours = it },
                app = app,
                onApp = { app = it },
                platform = platform,
                onPlatform = { platform = it },
                kind = kind,
                onKind = { kind = it },
                productionOnly = productionOnly,
                onProductionOnly = { productionOnly = it },
                sortFrequent = sortFrequent,
                onSortFrequent = { sortFrequent = it },
                reloadFocusRequester = reloadFocusRequester,
                onReload = { reloadCount++ },
                reloadEnabled = !loading,
            )
            SectionCard(
                title = stringResource(Res.string.app_errors),
                subtitle = if (loading) stringResource(Res.string.errors_loading_count, groups.size) else stringResource(Res.string.errors_summary, shown.size, groups.size),
            ) {
                ErrorGroupsTable(
                    groups = shown,
                    now = now,
                    emptyText = stringResource(if (groups.isEmpty()) Res.string.errors_none_logged else Res.string.errors_none_match),
                    showEmpty = !loading,
                    rowFocusRequesters = rowFocusRequesters,
                    onOpen = {
                        restoreGroupId = it.id
                        openGroupId = it.id
                    },
                )
            }
        }
    }

    openGroupId?.let { id ->
        ErrorGroupDialog(
            id = id,
            summary = groups.firstOrNull { it.id == id },
            now = now,
            loadGroup = { cursor -> presenter.loadGroup(id, cursor) },
            onDismiss = { openGroupId = null },
        )
    }
}

@Composable
private fun ErrorKpis(shown: List<ErrorGroup>, now: Long) {
    val lastSeen = shown.maxOfOrNull { it.lastSeen }
    StaggeredColumns(minColumnWidth = 200.dp, spacing = 12.dp) {
        KpiTile(stringResource(Res.string.errors_kpi_problems), shown.size.toString())
        KpiTile(stringResource(Res.string.errors_kpi_occurrences), shown.sumOf { it.count }.toString())
        KpiTile(
            stringResource(Res.string.errors_kpi_crashes),
            shown.count { it.lastKind == "crash" }.toString(),
            valueColor = if (shown.any { it.lastKind == "crash" }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        KpiTile(stringResource(Res.string.errors_kpi_last), lastSeen?.let { relativeTime(it, now) } ?: "–")
    }
}

@Composable
private fun KpiTile(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun ErrorFiltersBar(
    query: String,
    onQueryChange: (String) -> Unit,
    periodHours: Int?,
    onPeriod: (Int?) -> Unit,
    app: String?,
    onApp: (String?) -> Unit,
    platform: String?,
    onPlatform: (String?) -> Unit,
    kind: String?,
    onKind: (String?) -> Unit,
    productionOnly: Boolean,
    onProductionOnly: (Boolean) -> Unit,
    sortFrequent: Boolean,
    onSortFrequent: (Boolean) -> Unit,
    reloadFocusRequester: FocusRequester,
    onReload: () -> Unit,
    reloadEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.appFocusGroup()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(Res.string.errors_search)) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Text("×") }
                },
                modifier = Modifier.weight(1f),
            )
            FocusedButton(onClick = onReload, enabled = reloadEnabled, focusRequester = reloadFocusRequester) {
                Text(stringResource(Res.string.errors_reload))
            }
        }
        ChipRow {
            FilterChip(selected = periodHours == null, onClick = { onPeriod(null) }, label = { Text(stringResource(Res.string.errors_period_all)) })
            PERIOD_HOURS.forEach { hours ->
                val label = if (hours < 48) stringResource(Res.string.errors_last_hours, hours) else stringResource(Res.string.errors_last_days, hours / 24)
                FilterChip(selected = periodHours == hours, onClick = { onPeriod(hours) }, label = { Text(label) })
            }
            FilterChip(selected = productionOnly, onClick = { onProductionOnly(!productionOnly) }, label = { Text(stringResource(Res.string.errors_prod_only)) })
        }
        ChipRow {
            FilterChip(selected = app == null, onClick = { onApp(null) }, label = { Text(stringResource(Res.string.errors_app_all)) })
            APPS.forEach { value ->
                FilterChip(selected = app == value, onClick = { onApp(value) }, label = { Text(appLabel(value)) })
            }
            FilterChip(selected = platform == null, onClick = { onPlatform(null) }, label = { Text(stringResource(Res.string.errors_platform_all)) })
            PLATFORMS.forEach { value ->
                FilterChip(selected = platform == value, onClick = { onPlatform(value) }, label = { Text(platformLabel(value)) })
            }
        }
        ChipRow {
            FilterChip(selected = kind == null, onClick = { onKind(null) }, label = { Text(stringResource(Res.string.errors_kind_all)) })
            ERROR_KINDS.forEach { value ->
                FilterChip(selected = kind == value, onClick = { onKind(value) }, label = { Text(kindLabel(value)) })
            }
            FilterChip(selected = !sortFrequent, onClick = { onSortFrequent(false) }, label = { Text(stringResource(Res.string.errors_sort_latest)) })
            FilterChip(selected = sortFrequent, onClick = { onSortFrequent(true) }, label = { Text(stringResource(Res.string.errors_sort_frequent)) })
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun ErrorGroupsTable(
    groups: List<ErrorGroup>,
    now: Long,
    emptyText: String,
    showEmpty: Boolean,
    rowFocusRequesters: Map<String, FocusRequester>,
    onOpen: (ErrorGroup) -> Unit,
) {
    if (groups.isEmpty()) {
        if (showEmpty) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumnWithScrollbar(
        state = rememberPersistentLazyListState("errors"),
        modifier = Modifier.fillMaxWidth().appFocusGroup(),
    ) {
        item {
            DataTableHeaderRow {
                Text(stringResource(Res.string.errors_col_last), modifier = Modifier.width(LastSeenColumnWidth), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.errors_col_problem), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.errors_col_where), modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.errors_col_count), modifier = Modifier.width(CountColumnWidth), style = MaterialTheme.typography.labelLarge)
            }
            DataTableDivider()
        }
        items(groups, key = { it.id }) { group ->
            DataTableRow(
                modifier = Modifier.focusRequesterFor(rowFocusRequesters[group.id]),
                onClick = { onOpen(group) },
            ) {
                Column(Modifier.width(LastSeenColumnWidth)) {
                    Text(relativeTime(group.lastSeen, now), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        dateTime(group.lastSeen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(group.errorType, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(
                            kindLabel(group.lastKind),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (group.lastKind == "crash") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (group.area.isNotBlank()) {
                        Text(group.area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (group.message.isNotBlank()) {
                        Text(group.message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(Modifier.weight(0.4f)) {
                    Text(appLabel(group.lastApp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        listOf(platformLabel(group.lastPlatform), group.lastVersion).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(group.count.toString(), modifier = Modifier.width(CountColumnWidth), fontWeight = FontWeight.Bold)
            }
            DataTableDivider()
        }
    }
}

private fun Modifier.focusRequesterFor(requester: FocusRequester?): Modifier = if (requester == null) this else this.then(Modifier.focusRequester(requester))

/**
 * The stack trace needs a wide canvas, so this dialog uses a custom width with a 16 dp edge padding.
 * The dialog loads the sample stack trace and the newest reports of the group.
 */
@Composable
private fun ErrorGroupDialog(
    id: String,
    summary: ErrorGroup?,
    now: Long,
    loadGroup: suspend (ErrorLogCursor?) -> AppResult<ErrorGroupDetail>,
    onDismiss: () -> Unit,
) {
    var detail by remember(id) { mutableStateOf<ErrorGroupDetail?>(null) }
    var error by remember(id) { mutableStateOf<UiText?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val closeFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }

    var loadingMore by remember(id) { mutableStateOf(false) }

    fun load(cursor: ErrorLogCursor?) {
        scope.launch {
            loadingMore = true
            when (val result = loadGroup(cursor)) {
                // A next page adds its reports to the ones on the screen.
                is AppResult.Success -> detail = detail?.let { it.copy(reports = it.reports + result.value.reports, nextReportCursor = result.value.nextReportCursor) } ?: result.value
                is AppResult.Failure -> error = result.error.toUiText()
            }
            loadingMore = false
        }
    }
    LaunchedEffect(id) { load(null) }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    error?.let {
        AppErrorDialog(it, onDismiss = {
            error = null
            onDismiss()
        })
    }

    val group = detail?.group ?: summary
    TrackedDialog("ErrorGroup") {
        AlertDialog(
            modifier = Modifier.appFocusGroup().padding(16.dp).widthIn(max = 900.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { Text(group?.errorType.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                val scroll = rememberScrollState()
                ScrollableColumnWithScrollbar(state = scroll, modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (group != null) ErrorGroupFacts(group, now)
                        val loaded = detail
                        if (loaded == null) {
                            Text(stringResource(Res.string.errors_loading_detail), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(stringResource(Res.string.errors_stack_trace), style = MaterialTheme.typography.titleSmall)
                            SelectionContainer {
                                Text(
                                    loaded.stackTrace.ifBlank { stringResource(Res.string.errors_no_stack) },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                            Text(stringResource(Res.string.errors_recent_reports), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(Res.string.errors_no_reports),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    loaded.reports.forEach { ErrorReportRow(it) }
                                }
                            }
                            loaded.nextReportCursor?.let { next ->
                                FocusedOutlinedButton(onClick = { load(next) }, enabled = !loadingMore) {
                                    Text(stringResource(Res.string.errors_load_more_reports))
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                FocusedTextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(detail?.stackTrace.orEmpty()))
                    },
                    enabled = detail?.stackTrace?.isNotBlank() == true,
                    focusRequester = copyFocus,
                    buttonModifier = Modifier.focusLoop(previous = closeFocus, next = closeFocus),
                ) { Text(stringResource(Res.string.errors_copy_stack)) }
            },
            confirmButton = {
                FocusedButton(
                    onClick = onDismiss,
                    focusRequester = closeFocus,
                    buttonModifier = Modifier.focusLoop(previous = copyFocus, next = copyFocus),
                ) { Text(stringResource(Res.string.close)) }
            },
        )
    }
}

@Composable
private fun ErrorGroupFacts(group: ErrorGroup, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (group.area.isNotBlank()) Text(group.area, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (group.message.isNotBlank()) SelectionContainer { Text(group.message, style = MaterialTheme.typography.bodyMedium) }
        Text(
            "${stringResource(Res.string.errors_first_seen)}: ${dateTime(group.firstSeen)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${stringResource(Res.string.errors_last_seen)}: ${dateTime(group.lastSeen)} (${relativeTime(group.lastSeen, now)})",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${stringResource(Res.string.errors_kpi_occurrences)}: ${group.count}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (group.platforms.isNotEmpty()) {
            Text(
                "${stringResource(Res.string.errors_by_platform)}: " +
                    group.platforms.entries.sortedByDescending { it.value }.map { "${platformLabel(it.key)} ${it.value}" }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (group.apps.isNotEmpty()) {
            Text(
                "${stringResource(Res.string.errors_by_app)}: " +
                    group.apps.entries.sortedByDescending { it.value }.map { "${appLabel(it.key)} ${it.value}" }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ErrorReportRow(report: ErrorReportEntry) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOf(
                    dateTime(report.createdAt),
                    appLabel(report.app),
                    platformLabel(report.platform),
                    report.appVersion,
                    kindLabel(report.kind),
                    report.env,
                    report.role.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (report.reportId.isNotBlank()) {
                Text(stringResource(Res.string.errors_report_id, report.reportId), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            val reason = listOfNotNull(report.reason.takeIf { it.isNotBlank() }, report.httpStatus?.toString()).joinToString(" ")
            if (reason.isNotBlank()) Text(stringResource(Res.string.errors_report_reason, reason), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun appLabel(app: String): String = when (app) {
    "admin" -> stringResource(Res.string.errors_app_admin)
    "player" -> stringResource(Res.string.errors_app_player)
    else -> app
}

@Composable
private fun platformLabel(platform: String): String = when (platform) {
    "android" -> stringResource(Res.string.errors_platform_android)
    "web" -> stringResource(Res.string.errors_platform_web)
    "desktop" -> stringResource(Res.string.errors_platform_desktop)
    else -> platform
}

@Composable
private fun kindLabel(kind: String): String = when (kind) {
    "crash" -> stringResource(Res.string.errors_kind_crash)
    "handled" -> stringResource(Res.string.errors_kind_handled)
    else -> kind
}

private fun dateTime(millis: Long): String = localizedDateTimeOrNull(Instant.fromEpochMilliseconds(millis).toString()).orEmpty()

@Composable
private fun relativeTime(millis: Long, now: Long): String {
    val minutes = ((now - millis) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(Res.string.errors_just_now)
        minutes < 60 -> stringResource(Res.string.errors_minutes_ago, minutes.toInt())
        minutes < 60 * 24 -> stringResource(Res.string.errors_hours_ago, (minutes / 60).toInt())
        else -> stringResource(Res.string.errors_days_ago, (minutes / (60 * 24)).toInt())
    }
}
