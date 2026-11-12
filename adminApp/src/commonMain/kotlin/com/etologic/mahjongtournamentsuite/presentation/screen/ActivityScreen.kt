package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.ActivityEntry
import com.etologic.mahjongtournamentsuite.domain.model.ActivityFieldChange
import com.etologic.mahjongtournamentsuite.domain.model.ActivityFilter
import com.etologic.mahjongtournamentsuite.domain.model.ActivityOrigin
import com.etologic.mahjongtournamentsuite.domain.model.ActivitySource
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDatePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentTimePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.toDisplayTournamentDate
import com.etologic.mahjongtournamentsuite.presentation.components.toIsoTournamentDateOrNull
import com.etologic.mahjongtournamentsuite.presentation.presenter.ActivityPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.localizedDateTimeOrNull
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_account_send
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_member_add
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_member_remove
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_player_assign
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_player_clear_substitution
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_player_email
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_player_import
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_player_substitute
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_registry_create
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_registry_photo
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_registry_update
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_script_run
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_table_conflict
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_table_reset
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_table_save
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_team_update
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_tournament_create
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_tournament_delete
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_tournament_rename
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_tournament_update
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_user_create
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_user_disable
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_user_enable
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_user_password_reset
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_user_photo
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_user_photo_remove
import mahjongtournamentsuite.adminapp.generated.resources.activity_action_user_update
import mahjongtournamentsuite.adminapp.generated.resources.activity_all_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.activity_changes
import mahjongtournamentsuite.adminapp.generated.resources.activity_changes_count
import mahjongtournamentsuite.adminapp.generated.resources.activity_col_what
import mahjongtournamentsuite.adminapp.generated.resources.activity_col_when
import mahjongtournamentsuite.adminapp.generated.resources.activity_col_who
import mahjongtournamentsuite.adminapp.generated.resources.activity_details
import mahjongtournamentsuite.adminapp.generated.resources.activity_filter_kind
import mahjongtournamentsuite.adminapp.generated.resources.activity_filter_origin
import mahjongtournamentsuite.adminapp.generated.resources.activity_filter_period
import mahjongtournamentsuite.adminapp.generated.resources.activity_filter_person
import mahjongtournamentsuite.adminapp.generated.resources.activity_from_editor
import mahjongtournamentsuite.adminapp.generated.resources.activity_from_player
import mahjongtournamentsuite.adminapp.generated.resources.activity_from_system
import mahjongtournamentsuite.adminapp.generated.resources.activity_hand
import mahjongtournamentsuite.adminapp.generated.resources.activity_load_more
import mahjongtournamentsuite.adminapp.generated.resources.activity_loaded_count
import mahjongtournamentsuite.adminapp.generated.resources.activity_no_changes
import mahjongtournamentsuite.adminapp.generated.resources.activity_none
import mahjongtournamentsuite.adminapp.generated.resources.activity_only_person
import mahjongtournamentsuite.adminapp.generated.resources.activity_origin_all
import mahjongtournamentsuite.adminapp.generated.resources.activity_period_clear
import mahjongtournamentsuite.adminapp.generated.resources.activity_period_from_short
import mahjongtournamentsuite.adminapp.generated.resources.activity_period_to_short
import mahjongtournamentsuite.adminapp.generated.resources.activity_person_filter
import mahjongtournamentsuite.adminapp.generated.resources.activity_source_all
import mahjongtournamentsuite.adminapp.generated.resources.activity_source_other
import mahjongtournamentsuite.adminapp.generated.resources.activity_source_tables
import mahjongtournamentsuite.adminapp.generated.resources.activity_table
import mahjongtournamentsuite.adminapp.generated.resources.activity_tournament
import mahjongtournamentsuite.adminapp.generated.resources.activity_unknown_person
import mahjongtournamentsuite.adminapp.generated.resources.activity_version
import mahjongtournamentsuite.adminapp.generated.resources.app_activity
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.errors_reload
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

private val WhenColumnWidth = 150.dp

/** From this width, the filters take two columns. Below it, for example on a phone, they take one. */
private val WIDE_FILTERS_MIN_WIDTH = 720.dp
private val FILTER_COLUMN_GAP = 24.dp
private const val MILLIS_PER_MINUTE = 60_000L

/** The uid of the activity records of the maintenance scripts. */
private const val SCRIPT_UID = "script"

internal enum class PeriodEnd { FROM, TO }

/** Asks for a day and then for a time. The result is a local time of this device, in milliseconds since the epoch. */
@Composable
private fun PeriodPicker(initialMillis: Long?, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val initial = remember(initialMillis) {
        Instant.fromEpochMilliseconds(initialMillis ?: Clock.System.now().toEpochMilliseconds()).toLocalDateTime(zone)
    }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val date = pickedDate
    if (date == null) {
        TournamentDatePickerDialog(
            selectedDisplayDate = initial.date.toString().toDisplayTournamentDate(),
            onDismiss = onDismiss,
            onDateSelected = { display -> pickedDate = display.toIsoTournamentDateOrNull()?.let(LocalDate::parse) },
        )
    } else {
        val hour = initial.hour.toString().padStart(2, '0')
        val minute = initial.minute.toString().padStart(2, '0')
        TournamentTimePickerDialog(
            selectedTime = "$hour:$minute",
            onDismiss = onDismiss,
            onTimeSelected = { time ->
                val parts = time.split(':')
                val localTime = LocalTime(parts[0].toInt(), parts.getOrNull(1)?.toIntOrNull() ?: 0)
                onPicked(LocalDateTime(date, localTime).toInstant(zone).toEpochMilliseconds())
            },
        )
    }
}

/** The key of the "all tournaments" option of the tournament filter. */
private const val ALL_TOURNAMENTS = ""

/** Who wrote what and when, for administrators: table saves and every other change, newest first, with filters. */
@Composable
fun ActivityScreen(navController: NavHostController) {
    val presenter = koinInject<ActivityPresenter>()
    var entries by remember { mutableStateOf<List<ActivityEntry>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var tournamentId by rememberSaveable { mutableStateOf(ALL_TOURNAMENTS) }
    var sourceName by rememberSaveable { mutableStateOf(ActivitySource.ALL.name) }
    val source = ActivitySource.valueOf(sourceName)
    var originName by rememberSaveable { mutableStateOf<String?>(null) }
    val origin = originName?.let { ActivityOrigin.valueOf(it) }
    var fromMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var toMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var periodPicker by remember { mutableStateOf<PeriodEnd?>(null) }
    var personUid by rememberSaveable { mutableStateOf<String?>(null) }
    var personName by rememberSaveable { mutableStateOf<String?>(null) }
    var openEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreEntryId by remember { mutableStateOf<String?>(null) }
    var reloadCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val reloadFocusRequester = remember { FocusRequester() }
    val rowFocusRequesters = remember(entries.map(ActivityEntry::id)) { entries.associate { it.id to FocusRequester() } }
    val filter = ActivityFilter(
        tournamentId = tournamentId.ifEmpty { null },
        uid = personUid,
        source = source,
        origin = origin,
        fromMillis = fromMillis,
        toMillis = toMillis,
    )

    LaunchedEffect(Unit) {
        when (val result = presenter.loadTournaments()) {
            is AppResult.Success -> tournaments = result.value.sortedBy { it.name.lowercase() }
            // The filter then offers only "all tournaments". The list still works.
            is AppResult.Failure -> Unit
        }
    }

    suspend fun loadPage(cursor: String?) {
        loading = true
        when (val result = presenter.loadPage(filter, cursor)) {
            is AppResult.Success -> {
                entries = if (cursor == null) result.value.entries else entries + result.value.entries
                nextCursor = result.value.nextCursor
            }
            is AppResult.Failure -> error = result.error.toUiText()
        }
        loading = false
    }

    // A change of a filter reads the list again from the newest entry.
    LaunchedEffect(filter, reloadCount) {
        entries = emptyList()
        nextCursor = null
        loadPage(null)
    }

    LaunchedEffect(openEntryId, loading) {
        if (openEntryId == null && !loading) {
            val id = restoreEntryId
            if (id != null) rowFocusRequesters[id]?.requestFocus() else reloadFocusRequester.requestFocus()
            restoreEntryId = null
        }
    }

    AppScaffold(
        title = stringResource(Res.string.app_activity),
        isLoading = loading && entries.isEmpty(),
        showProgress = loading,
        showBackgroundLogo = loading && entries.isEmpty(),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarButton(
                text = stringResource(Res.string.errors_reload),
                icon = Icons.Default.Refresh,
                onClick = { reloadCount++ },
                focusRequester = reloadFocusRequester,
                enabled = !loading,
            )
        },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { AppErrorDialog(it, onDismiss = { error = null }) }
            // The filters are the first item of the list, so they scroll away with the entries.
            val filtersBar: @Composable () -> Unit = {
                ActivityFiltersBar(
                    tournaments = tournaments,
                    tournamentId = tournamentId,
                    onTournament = { tournamentId = it },
                    source = source,
                    onSource = { sourceName = it.name },
                    origin = origin,
                    onOrigin = { originName = it?.name },
                    fromMillis = fromMillis,
                    toMillis = toMillis,
                    onPickPeriod = { periodPicker = it },
                    onClearPeriod = {
                        fromMillis = null
                        toMillis = null
                    },
                    personName = if (personUid == null) null else personName ?: personUid,
                    onClearPerson = {
                        personUid = null
                        personName = null
                    },
                )
            }
            ActivityTable(
                filters = filtersBar,
                loadedCount = entries.size,
                entries = entries,
                showEmpty = !loading,
                rowFocusRequesters = rowFocusRequesters,
                hasMore = nextCursor != null,
                loading = loading,
                onLoadMore = { scope.launch { loadPage(nextCursor) } },
                onOpen = {
                    restoreEntryId = it.id
                    openEntryId = it.id
                },
            )
        }
    }

    periodPicker?.let { end ->
        PeriodPicker(
            initialMillis = if (end == PeriodEnd.FROM) fromMillis else toMillis?.minus(MILLIS_PER_MINUTE),
            onDismiss = { periodPicker = null },
            onPicked = { millis ->
                // "Until 15:00" keeps the changes of the minute 15:00, so the end of the period is one minute later.
                if (end == PeriodEnd.FROM) fromMillis = millis else toMillis = millis + MILLIS_PER_MINUTE
                periodPicker = null
            },
        )
    }

    entries.firstOrNull { it.id == openEntryId }?.let { entry ->
        ActivityEntryDialog(
            entry = entry,
            canFilterByPerson = entry.uid != null && entry.uid != personUid,
            onFilterByPerson = {
                personUid = entry.uid
                personName = entry.name
                restoreEntryId = null
                openEntryId = null
            },
            onDismiss = { openEntryId = null },
        )
    }
}

@Composable
private fun ActivityFiltersBar(
    tournaments: List<Tournament>,
    tournamentId: String,
    onTournament: (String) -> Unit,
    source: ActivitySource,
    onSource: (ActivitySource) -> Unit,
    origin: ActivityOrigin?,
    onOrigin: (ActivityOrigin?) -> Unit,
    fromMillis: Long?,
    toMillis: Long?,
    onPickPeriod: (PeriodEnd) -> Unit,
    onClearPeriod: () -> Unit,
    personName: String?,
    onClearPerson: () -> Unit,
) {
    val allLabel = stringResource(Res.string.activity_all_tournaments)
    val options = remember(tournaments, allLabel) { listOf(ALL_TOURNAMENTS to allLabel) + tournaments.map { it.id to it.name } }
    val tournamentField: @Composable (Modifier) -> Unit = { modifier ->
        SelectField(
            label = stringResource(Res.string.activity_tournament),
            options = options,
            selected = tournamentId,
            onSelect = onTournament,
            modifier = modifier,
        )
    }
    val periodGroup: @Composable (Modifier) -> Unit = { modifier ->
        FilterGroup(stringResource(Res.string.activity_filter_period), modifier) {
            PeriodChip(Res.string.activity_period_from_short, fromMillis, selected = fromMillis != null) { onPickPeriod(PeriodEnd.FROM) }
            PeriodChip(Res.string.activity_period_to_short, toMillis?.minus(MILLIS_PER_MINUTE), selected = toMillis != null) { onPickPeriod(PeriodEnd.TO) }
            if (fromMillis != null || toMillis != null) {
                FocusedIconButton(onClick = onClearPeriod) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.activity_period_clear))
                }
            }
        }
    }
    val personGroup: @Composable () -> Unit = {
        if (personName != null) {
            FilterGroup(stringResource(Res.string.activity_filter_person), Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = true,
                    onClick = onClearPerson,
                    label = { Text(stringResource(Res.string.activity_person_filter, personName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Text("×") },
                )
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().appFocusGroup()) {
        if (maxWidth >= WIDE_FILTERS_MIN_WIDTH) {
            // Two equal columns, so each row lines up: tournament over type on the left, period over origin on the right.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(FILTER_COLUMN_GAP), verticalAlignment = Alignment.Bottom) {
                    tournamentField(Modifier.weight(1f))
                    periodGroup(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(FILTER_COLUMN_GAP), verticalAlignment = Alignment.Bottom) {
                    FilterGroup(stringResource(Res.string.activity_filter_kind), Modifier.weight(1f)) {
                        ActivitySource.entries.forEach { value ->
                            WideChip(sourceLabel(value), selected = source == value) { onSource(value) }
                        }
                    }
                    FilterGroup(stringResource(Res.string.activity_filter_origin), Modifier.weight(1f)) {
                        WideChip(stringResource(Res.string.activity_origin_all), selected = origin == null) { onOrigin(null) }
                        ActivityOrigin.entries.forEach { value ->
                            WideChip(originLabel(value.wire), selected = origin == value) { onOrigin(value) }
                        }
                    }
                }
                personGroup()
            }
        } else {
            // One column on a phone. Each filter takes one full line, and the lists of options become menus.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tournamentField(Modifier.fillMaxWidth())
                periodGroup(Modifier.fillMaxWidth())
                SelectField(
                    label = stringResource(Res.string.activity_filter_kind),
                    options = ActivitySource.entries.map { SelectOption(it, sourceLabel(it)) },
                    selected = source,
                    onSelect = onSource,
                    modifier = Modifier.fillMaxWidth(),
                )
                val allOrigins = stringResource(Res.string.activity_origin_all)
                val originOptions = listOf(SelectOption<ActivityOrigin?>(null, allOrigins)) +
                    ActivityOrigin.entries.map { SelectOption<ActivityOrigin?>(it, originLabel(it.wire)) }
                SelectField(
                    label = stringResource(Res.string.activity_filter_origin),
                    options = originOptions,
                    selected = origin,
                    onSelect = onOrigin,
                    modifier = Modifier.fillMaxWidth(),
                )
                personGroup()
            }
        }
    }
}

/** A titled line of filter chips. The chips share the width of the line. */
@Composable
private fun FilterGroup(title: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun RowScope.WideChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        modifier = Modifier.weight(1f),
    )
}

/** "From" or "Until", with the chosen day and time when there is one. */
@Composable
private fun RowScope.PeriodChip(word: StringResource, millis: Long?, selected: Boolean, onClick: () -> Unit) {
    val text = stringResource(word) + (millis?.let { " ${shortDateTime(it)}" } ?: "")
    WideChip(text, selected, onClick)
}

/** A short local date and time, with the year only when it is not this year. */
private fun shortDateTime(millis: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    val thisYear = Clock.System.now().toLocalDateTime(zone).year
    val day = time.day.toString().padStart(2, '0')
    val month = time.month.number.toString().padStart(2, '0')
    val year = if (time.year == thisYear) "" else "/${time.year % 100}"
    return "$day/$month$year ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

@Composable
private fun ActivityTable(
    filters: @Composable () -> Unit,
    loadedCount: Int,
    entries: List<ActivityEntry>,
    showEmpty: Boolean,
    rowFocusRequesters: Map<String, FocusRequester>,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (ActivityEntry) -> Unit,
) {
    LazyColumnWithScrollbar(
        state = rememberPersistentLazyListState("activity"),
        modifier = Modifier.fillMaxWidth().appFocusGroup(),
    ) {
        item(key = "filters") { filters() }
        item(key = "title") {
            Column(Modifier.padding(top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.app_activity), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.activity_loaded_count, loadedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entries.isEmpty()) {
            if (showEmpty) {
                item(key = "empty") {
                    Text(stringResource(Res.string.activity_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@LazyColumnWithScrollbar
        }
        item(key = "header") {
            DataTableHeaderRow {
                Text(stringResource(Res.string.activity_col_when), modifier = Modifier.width(WhenColumnWidth), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.activity_col_who), modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.activity_col_what), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }
            DataTableDivider()
        }
        items(entries, key = { it.id }) { entry ->
            DataTableRow(
                modifier = rowFocusRequesters[entry.id]?.let { Modifier.focusRequester(it) } ?: Modifier,
                onClick = { onOpen(entry) },
            ) {
                Text(dateTime(entry.at), modifier = Modifier.width(WhenColumnWidth), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.weight(0.6f)) {
                    Text(personLabel(entry), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(originLabel(originOf(entry)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        actionLabel(entry.action),
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.action == "table.conflict") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val where = whereLabel(entry)
                    if (where.isNotBlank()) {
                        Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (entry.isTableSave && entry.changes.isNotEmpty()) {
                        Text(stringResource(Res.string.activity_changes_count, entry.changes.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            DataTableDivider()
        }
        if (hasMore) {
            item {
                FocusedOutlinedButton(onClick = onLoadMore, enabled = !loading, modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(Res.string.activity_load_more))
                }
            }
        }
    }
}

/** A routine detail dialog, so it keeps the standard platform width and scrolls its content. */
@Composable
private fun ActivityEntryDialog(
    entry: ActivityEntry,
    canFilterByPerson: Boolean,
    onFilterByPerson: () -> Unit,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    val personFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    TrackedDialog("ActivityEntry") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { Text(actionLabel(entry.action), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                ScrollableColumnWithScrollbar(state = rememberScrollState(), modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    SelectionContainer {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(dateTime(entry.at), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOf(personLabel(entry), originLabel(originOf(entry))).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            entry.uid?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                            val where = whereLabel(entry)
                            if (where.isNotBlank()) Text(where, style = MaterialTheme.typography.bodyMedium)
                            entry.version?.let { Text(stringResource(Res.string.activity_version, it), style = MaterialTheme.typography.bodySmall) }
                            if (entry.details.isNotEmpty()) {
                                Text(stringResource(Res.string.activity_details), style = MaterialTheme.typography.titleSmall)
                                entry.details.forEach { (key, value) -> KeyValueRow(key, value) }
                            }
                            if (entry.isTableSave) {
                                Text(stringResource(Res.string.activity_changes), style = MaterialTheme.typography.titleSmall)
                                if (entry.changes.isEmpty()) {
                                    Text(stringResource(Res.string.activity_no_changes), style = MaterialTheme.typography.bodySmall)
                                } else {
                                    entry.changes.forEach { ChangeRow(it) }
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (canFilterByPerson) {
                    FocusedTextButton(
                        onClick = onFilterByPerson,
                        focusRequester = personFocus,
                        buttonModifier = Modifier.focusLoop(previous = closeFocus, next = closeFocus),
                    ) { Text(stringResource(Res.string.activity_only_person)) }
                }
            },
            confirmButton = {
                FocusedButton(
                    onClick = onDismiss,
                    focusRequester = closeFocus,
                    buttonModifier = if (canFilterByPerson) Modifier.focusLoop(previous = personFocus, next = personFocus) else Modifier,
                ) { Text(stringResource(Res.string.close)) }
            },
        )
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Text("$key: $value", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChangeRow(change: ActivityFieldChange) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            val field = change.handId?.let { "${stringResource(Res.string.activity_hand, it)} · ${change.field}" } ?: change.field
            Text(field, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("${change.from ?: "–"} → ${change.to ?: "–"}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

/** A change of the activity log comes from the admin app, or from a maintenance script. */
private fun originOf(entry: ActivityEntry): String = entry.source ?: if (entry.uid == SCRIPT_UID) ActivityOrigin.AUTOMATIC.wire else ActivityOrigin.ADMIN_APP.wire

@Composable
private fun personLabel(entry: ActivityEntry): String = entry.name ?: entry.uid ?: stringResource(Res.string.activity_unknown_person)

/** The tournament, and the table or the target of the change. */
@Composable
private fun whereLabel(entry: ActivityEntry): String {
    val roundId = entry.roundId
    val tableId = entry.tableId
    val table = if (roundId != null && tableId != null) stringResource(Res.string.activity_table, roundId, tableId) else null
    return listOfNotNull(entry.tournamentName ?: entry.tournamentId, table, entry.target).joinToString(" · ")
}

@Composable
private fun sourceLabel(source: ActivitySource): String = stringResource(
    when (source) {
        ActivitySource.ALL -> Res.string.activity_source_all
        ActivitySource.TABLES -> Res.string.activity_source_tables
        ActivitySource.OTHER -> Res.string.activity_source_other
    },
)

@Composable
private fun originLabel(source: String): String = when (source) {
    "editor", "admin" -> stringResource(Res.string.activity_from_editor)
    "player" -> stringResource(Res.string.activity_from_player)
    "system" -> stringResource(Res.string.activity_from_system)
    else -> source
}

private val ACTION_LABELS: Map<String, StringResource> = mapOf(
    "tournament.create" to Res.string.activity_action_tournament_create,
    "tournament.update" to Res.string.activity_action_tournament_update,
    "tournament.rename" to Res.string.activity_action_tournament_rename,
    "tournament.delete" to Res.string.activity_action_tournament_delete,
    "player.assign" to Res.string.activity_action_player_assign,
    "player.import" to Res.string.activity_action_player_import,
    "player.substitute" to Res.string.activity_action_player_substitute,
    "player.clearSubstitution" to Res.string.activity_action_player_clear_substitution,
    "team.update" to Res.string.activity_action_team_update,
    "member.add" to Res.string.activity_action_member_add,
    "member.remove" to Res.string.activity_action_member_remove,
    "account.send" to Res.string.activity_action_account_send,
    "player.email" to Res.string.activity_action_player_email,
    "user.create" to Res.string.activity_action_user_create,
    "user.passwordReset" to Res.string.activity_action_user_password_reset,
    "user.update" to Res.string.activity_action_user_update,
    "user.disable" to Res.string.activity_action_user_disable,
    "user.enable" to Res.string.activity_action_user_enable,
    "user.photo" to Res.string.activity_action_user_photo,
    "user.photo.remove" to Res.string.activity_action_user_photo_remove,
    "registry.create" to Res.string.activity_action_registry_create,
    "registry.update" to Res.string.activity_action_registry_update,
    "registry.photo" to Res.string.activity_action_registry_photo,
    "table.save" to Res.string.activity_action_table_save,
    "table.reset" to Res.string.activity_action_table_reset,
    "table.conflict" to Res.string.activity_action_table_conflict,
    "script.run" to Res.string.activity_action_script_run,
)

/** A new action of the server shows its code until the app has a text for it. */
@Composable
private fun actionLabel(action: String): String = ACTION_LABELS[action]?.let { stringResource(it) } ?: action

private fun dateTime(millis: Long): String = localizedDateTimeOrNull(Instant.fromEpochMilliseconds(millis).toString()).orEmpty()
